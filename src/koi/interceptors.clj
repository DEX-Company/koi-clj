(ns koi.interceptors
  (:require [sieppari.core :as si]
            [clojure.java.io :as io]
            [koi.utils :as ut]
            [koi.config :as cf]
            [clojure.data.json :as json]
            [taoensso.timbre :as timbre
             :refer [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [starfish.core :as s]
            [koi.utils :as utils])
  (:import [sg.dex.starfish.util JSON]))


(defn map-validator
  "return true if actual conforms to the map-spec"
  [map-spec actual predicate]
  (let [spec-resp
        (if (map? map-spec)
          (mapv (fn [[k {:keys [type required]}]]
                  (if required
                    (if-let [k1 (get actual k)]
                      (if (= :asset (keyword type))
                        (predicate k1)
                        ;;assumes that for non-asset type, any non-nil object is ok
                        (do
                          (println " not-asset " k " k1 " k1)
                          (not (nil? k1))))
                      false)
                    true))
                map-spec)
          [false])]
    (println " map-validator  " map-spec
             " -- " spec-resp)
    (every? identity spec-resp)))

(defn asset-validator
  "returns true if the value against the keys is a starfish asset.
  Used for output validation"
  [map-spec actual]
  (map-validator map-spec actual (partial s/asset?)))

(defn did-validator
  "returns true if the asset is a map with a key :did and value string.
  used for input validation"
  [map-spec actual]
  (map-validator map-spec actual (fn[i] (and (:did i)
                                             (string? (:did i))))))

(defn param-validator
  "validates the input param's map keys against the metadata.
  a)mandatory keys should be present
  b)asset type (in spec) should be starfish asset in actual
  c)non-mandatory and unspecified keys are allowed"
  [param-spec]
  {:enter
   (fn [ctx]
     (info " req "(get-in ctx [:request]) " spec " param-spec)
     (let [req (get-in ctx [:request])
           all-keys-present? (did-validator param-spec req)]
       (if all-keys-present?
         ctx
         (assoc ctx :error (Exception. " mandatory params missing")))))})

(defn wrap-error-cause 
  "remove the exception and wrap it in an error cause."
  []
  {:error (fn[ctx]
            (if-let [exception (:error ctx)]
              (-> ctx (dissoc :error)
                  (update-in [:response :error]
                             (constantly
                              (do
                                (error exception)
                                {:cause (.getMessage exception)}))))
              ctx))})

(defn result-validator
  "validates the output result  map keys against the metadata.
  a)mandatory keys should be present
  b)asset type (in spec) should be starfish asset in actual
  )non-mandatory and unspecified keys are allowed"
  [result-spec]
  {:leave
   (fn [ctx]
     (let [all-keys-present? (asset-validator result-spec (get-in ctx [:response]))]
       (if all-keys-present?
         ctx
         (assoc ctx :response {:error {:cause "mandatory result keys missing"}}))))})

(defn input-asset-retrieval
  "For assets that reference asset ids/dids, an agent replaces the did reference with
  the retrieved asset object. The retrieval-fn retrieves the assets, and may be configured to
  get it from a local or remote agent"
  [retrieval-fn]
  (let [dependencies (atom [])]
    {:enter
     (fn [ctx]
       (let [resp 
             (update-in ctx [:request]
                        #(reduce-kv
                          (fn[acc k v]
                            (assoc acc k
                                   (if-let [did (:did v)]
                                     (if-let [resp (retrieval-fn did)]
                                       resp
                                       (throw (Exception. " mandatory asset could not be retrieved")))
                                     v)))
                          {} %))
             req (:request resp)
             ]
         ;;set the inputs so that they can be used in the outputs.
         ;(reset! dependencies (vals req))
         (update-in resp [:request :dependencies]
                    (fn[_] (filterv s/asset? (vals req))))))
     :error (fn[ctx] (-> ctx (dissoc :error)
                         (update-in [:response :error]
                                    (constantly {:cause (.getMessage (:error ctx))}))))}))

(defn output-asset-upload
  "For operations that generate output asset(s),this interceptor
  a) registers and upload the asset using an agent,
  b)replaces the asset object with the did"
  [upload-fn]
  (let [input-args (atom nil)]
    ;;save the input-args before they get changed by other interceptors
    ;;these are required at the time of generating provenance.
    {:enter
     (fn[ctx]
       ;(println " output-asset-upload enter " (:request ctx))
       (reset! input-args (:request ctx))
       ctx)
     :leave
     (fn [ctx]
       (let [dep (get-in ctx [:request :dependencies] )
             resp
             (try 
               (reduce-kv
                (fn[acc k v]
                  (assoc acc k
                         (if (s/asset? v)
                           {:did (upload-fn v k dep
                                            (deref input-args))}
                           v)))
                {} (:response ctx))
               (catch Exception e
                 {:error {:cause "upload function threw an exception"}}))]
         (assoc ctx :response resp)))}))

(defn wrap-result
  "wrap the result from the operation in an map against the :results key"
  []
  {:leave
   (fn [ctx]
     (if-not (get-in ctx [:response :error])
       (do
         (update-in ctx [:response]
                    #(assoc {} :results %)))
       ctx))})

(defn materialize-handler
  "Given handler configuration, return a function that can be run
  at the end of the chain"
  [handler]
  (fn [inp]
    (require (symbol (first (.split handler "/"))))
    ((-> handler symbol resolve) inp)))

(defn asset-reg-upload
  [ragent]
  (fn[ast]
    (do
      (s/register ragent ast)
      (s/asset-id (s/upload ragent ast)))))

(defn add-prov
  [ragent]
  (fn[ast param-name dependency-list
      params]
    (let [upload-fn (asset-reg-upload (:remote-agent ragent))]
      (try 
        (if-not (or (nil? dependency-list)
                    (empty? dependency-list))
          (let[inv-metadata
               (ut/invoke-metadata ragent (name param-name)
                                   dependency-list (JSON/toString params))
               ast (s/asset (s/memory-asset (merge (s/metadata ast)
                                                   inv-metadata)
                                            (s/content ast)))]
            (upload-fn ast))
          (upload-fn ast))
        (catch Exception e
          (clojure.stacktrace/print-stack-trace e))))))

(defn run-chain
  [interceptors f]
  (partial si/execute (conj interceptors f)))

(defn add-metadata-hash
  [registry]
  (reduce-kv (fn[acc k v]
               (let [metadata (:metadata v)
                     metadata-str (json/write-str metadata)
                     asset-id (s/digest metadata-str)]
                 (assoc acc k v
                        (keyword asset-id) v)))
             {}
             registry))

(defn middleware-wrapped-handler
  [config]
  (let [op-registry (-> config :operation-registry add-metadata-hash)
        agent-conf (:agent-conf config)
        remote-agent (cf/get-remote-agent agent-conf)
        ragent (:remote-agent remote-agent)
        ;;lets pre-register an asset that will be used
        ret-fn (partial s/get-asset ragent)]
    (fn [operation]
      (let [param-spec (get-in op-registry [operation :metadata :operation  :params])
            result-spec (get-in op-registry [operation :metadata :operation  :results])
            operation-var (get-in op-registry [operation :handler])
            ]
        ;;when operation is invalid, return nil
        ;;the sequence is:
        ;;first to last all the :enter fns are run
        ;;then the handler is run
        ;;last to first :leave fns are run
        (when operation-var
          (run-chain
           [
            (wrap-error-cause)
            (wrap-result)
            (output-asset-upload (add-prov remote-agent))
            (param-validator param-spec)
            ;;put output asset upload on the return trip after input-asset-retrieval
            ;;because it has to add prov metadata
            (input-asset-retrieval ret-fn)
            (result-validator result-spec)
            ]
           (materialize-handler operation-var)))))))

(comment
  (def i-metadata (-> (io/resource "hashing_asset_metadata.json")
                      slurp 
                      (json/read-str :key-fn keyword)
                      ))
  (si/execute [(param-validator i-metadata)
               sample-operation]
              {:to-hash "abcd"})
  ;;that works, now misspell the key

  (si/execute [(param-validator i-metadata)
               sample-operation]
              {:to-has "abcd"})

  ;;returns an error

  ;;now validate the results too
  (si/execute [(param-validator i-metadata)
               (result-validator i-metadata)
               sample-operation]
              {:to-hash "abcd"})

  ;;that works, lets run an operation which doesn't have the :hash-value key
  (si/execute [(param-validator i-metadata)
               (result-validator i-metadata)
               (fn[_] {})]
              {:to-hash "abcd"})
  )

