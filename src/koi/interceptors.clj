(ns koi.interceptors
  (:require [sieppari.core :as si]
            [clojure.java.io :as io]
            [koi.config :as cf]
            [clojure.data.json :as json]
            [starfish.core :as s]))

(defn map-validator
  "return true if actual conforms to the map-spec"
  [map-spec actual]
  (let [spec-resp
        (if (map? map-spec)
          (mapv (fn [[k {:keys [type required]}]]
                  (if required
                    (if-let [k1 (get actual k)]
                      (if (= :asset (keyword type))
                        (s/asset? k1)
                        ;;assumes that for non-asset type, any non-nil object is ok
                        (not (nil? k1)))
                      false)
                    false))
                map-spec)
          [false])]
    (every? identity spec-resp)))

(defn param-validator
  "validates the input param's map keys against the metadata.
  a)mandatory keys should be present
  b)asset type (in spec) should be starfish asset in actual
  c)non-mandatory and unspecified keys are allowed"
  [param-spec]
  {:enter
   (fn [ctx]
     (let [req (get-in ctx [:request])
           all-keys-present? (map-validator param-spec req)]
       #_(println " param-validator " req " param-spec " param-spec
                " akp " all-keys-present?)
       (if all-keys-present?
         ctx
         (assoc ctx :error (Exception. " mandatory params missing")))))
   #_(fn[ctx] (-> ctx (dissoc :error)
                       (update-in [:response :error]
                                  (constantly {:cause " 234 mandatory params missing"}))))})

(defn wrap-error-cause 
  "remove the exception and wrap it in an error cause."
  []
  {:error (fn[ctx]
            (if-let [exception (:error ctx)]
              (-> ctx (dissoc :error)
                  (update-in [:response :error]
                             (constantly {:cause (.getMessage exception)})))
              ctx))})

(defn result-validator
  "validates the output result  map keys against the metadata.
  a)mandatory keys should be present
  b)asset type (in spec) should be starfish asset in actual
  )non-mandatory and unspecified keys are allowed"
  [result-spec]
  {:leave
   (fn [ctx]
     (let [all-keys-present? (map-validator result-spec (get-in ctx [:response]))]
       (if all-keys-present?
         ctx
         (assoc ctx :response {:error {:cause "mandatory result keys missing"}}))))})

(defn input-asset-retrieval
  "For assets that reference asset ids/dids, an agent replaces the did reference with
  the retrieved asset object. The retrieval-fn retrieves the assets, and may be configured to
  get it from a local or remote agent"
  [retrieval-fn]
  {:enter
   (fn [ctx]
     (update-in ctx [:request]
                #(reduce-kv
                  (fn[acc k v]
                    (assoc acc k
                           (if-let [did (:did v)]
                             (if-let [resp (retrieval-fn did)]
                               resp
                               (throw (Exception. " mandatory asset could not be retrieved")))
                             v)))
                  {} %)))
   :error (fn[ctx] (-> ctx (dissoc :error)
                       (update-in [:response :error]
                                  (constantly {:cause (.getMessage (:error ctx))}))))})

(defn output-asset-upload
  "For operations that generate output asset(s),this interceptor
  a) registers and upload the asset using an agent,
  b)replaces the asset object with the did"
  [upload-fn]
  {:leave
   (fn [ctx]
     (let [resp
           (try 
             (reduce-kv
              (fn[acc k v]
                (assoc acc k
                       (if (s/asset? v)
                         {:did (upload-fn v)} v)))
              {} (:response ctx))
             (catch Exception e
               {:error {:cause "upload function threw an exception"}}))]
       (assoc ctx :response resp)))})

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
  [ragent ]
  (fn[ast]
    (do (s/register ragent ast)
        (s/asset-id (s/upload ragent ast)))))

(defn run-chain
  [interceptors f]
  (partial si/execute (conj interceptors f)))

(defn middleware-wrapped-handler
  [config]
  (fn [operation]
    (let [op-registry (:operation-registry config)
          agent-conf (:agent-conf config)
          param-spec (get-in config [:operation-registry operation :metadata :params])
          result-spec (get-in config [:operation-registry operation :metadata :results])
          remote-agent (cf/get-remote-agent agent-conf)
          ragent (:remote-agent remote-agent)
          ;;lets pre-register an asset that will be used
          ret-fn (partial s/get-asset ragent)
          operation-var (get-in op-registry [operation :handler])]
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
          (input-asset-retrieval ret-fn)
          (param-validator param-spec)
          (output-asset-upload (asset-reg-upload ragent))
          (result-validator result-spec)
          ]
         (materialize-handler operation-var))))))

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
