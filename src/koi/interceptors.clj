(ns koi.interceptors
  (:require [sieppari.core :as si]
            [clojure.java.io :as io]
            [koi.config :as cf]
            [clojure.data.json :as json]
            [starfish.core :as s]))

(defn map-validator
  "return true if actual conforms to the map-spec"
  [map-spec actual]
  (let [spec-resp (mapv (fn [[k {:keys [type required]}]]
                          (if required
                            (if-let [k1 (get actual k)]
                              (if (= "asset" type)
                                (s/asset? k1)
                                ;;assumes that for non-asset type, any non-nil object is ok
                                k1)
                              false)
                            false))
                        map-spec)]
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
       (if all-keys-present?
         ctx
         (throw (Exception. " mandatory params missing")))))
   :error (fn[ctx] (-> ctx (dissoc :error)
                       (update-in [:response :error]
                                  (constantly {:cause "mandatory params missing"}))))})

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
                                  (constantly {:cause "mandatory asset could not be retrieved"}))))
   })

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

(defn materialize-handler
  "Given handler configuration, return a function that can be run
  at the end of the chain"
  [handler]
  (fn [inp]
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
          param-spec (get-in config [:operation-registry :metadata :params])
          result-spec (get-in config [:operation-registry :metadata :params])
          remote-agent (cf/get-remote-agent agent-conf)
          ragent (:remote-agent remote-agent)
          ;;lets pre-register an asset that will be used
          ret-fn (partial s/get-asset ragent)
          operation-var (get-in op-registry [operation :handler])]
      ;;when operation is invalid, return nil
      (when operation-var
        (run-chain
         [(input-asset-retrieval ret-fn)
          (param-validator param-spec)
          (result-validator result-spec)
          (output-asset-upload (asset-reg-upload ragent))]
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


