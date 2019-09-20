(ns koi.middleware
  (:require
   [starfish.core :as s]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report]]
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.data.json :as json])
  (:import [sg.dex.starfish Agent Asset]
           [sg.dex.starfish.util DID]))

(defn wrap-config
  [handler config-stream]
  (fn [args]
    (let [json-config (json/read-str
                       (slurp config-stream)
                       :key-fn keyword)]
      (dissoc (handler (assoc args :config (:operation json-config)))
              :config))))

(defn register-sideeffect
  [ragent asset]
  (do
    (s/register ragent asset)
    (s/upload ragent asset)))

(defn wrap-asset-store
  ([handler asset-store] (wrap-asset-store handler asset-store identity))
  ([handler asset-store asset-side-effect]
   (fn [args]
     (let [;_ (println " wrap-asset-store args " args)
           res
           (-> args
               (assoc :asset-store-fn asset-store)
               handler
               (dissoc :asset-store-fn)
               (as-> x
                   {:results (reduce-kv
                              (fn[acc k v]
                                (assoc acc k
                                       (if (s/asset? v)
                                         (do (asset-side-effect v)
                                             {:did (s/asset-id v)}) v)))
                              {} (:results x))}))]
       res))))

(defn reify-agent
  ""
  []
  (reify
    Agent
    (getDDO [this ] (println "hello"))
    (getDID [this] (println "hello"))
    (registerAsset [this asset] (println "hello") asset)
    (^Asset getAsset [this ^String id] (println "hello")
              (s/asset ""))
    (^Asset getAsset [this ^DID id] (println "hello")
              (s/asset ""))
    (uploadAsset [this asset] (println "hello")
                 (s/asset ""))))

(defn wrap-inputs
  [handler]
  (fn [args]
    (let [;_ (println " wrap-inputs " args)
          asset-store-fn (:asset-store-fn args)
          invoke-args (:invoke-args args)
          input 
          (apply merge
                 (mapv (fn[[k {:keys [:type]} :as inp]]
                         (select-keys
                          (if (= type "asset")
                            (update-in invoke-args [k] #(asset-store-fn (:did %)))
                            args)
                          [k]))
                       (get-in (:config args) [:params])))
          resp (handler input )]
      ;(println " wrap-input response " resp)
      resp)))

(defn wrap-results
  [handler]
  (fn [args]
    (let [resp {:results (handler args)}]
      ;(println " wrap-output response " resp)
      resp)))

(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (try
    (with-open [r (io/reader source)]
      (edn/read (java.io.PushbackReader. r)))

    (catch java.io.IOException e
      (printf "Couldn't open '%s': %s\n" source (.getMessage e)))))

(defn string->ns
  "returns a valid ns from a clojure file path,
  'src/koi/examples/hashing_simple.clj' -> 'koi.examples.hashing-simple'
  "
  [file-path]
  (-> (clojure.string/replace "src/koi/examples/hashing_simple.clj" #".clj" "")
      (clojure.string/replace #"src/" "")
      (clojure.string/replace #"/" ".")
      (clojure.string/replace #"_" "-")))

(defn default-middleware
  [op-name operation-config]
  (let [{:keys [handler metadata-path]} (op-name operation-config)]
    (if (and handler metadata-path)
      (-> handler symbol resolve
          (wrap-inputs)
          (wrap-results)
          (wrap-config (io/resource metadata-path))
          (wrap-asset-store
           {"1234" (s/memory-asset {:test :metadata} "content")}
           ;(partial s/get-asset ragent)
           )
          )
      nil)))

(defn test-middleware
  [op-config]
  (let [{:keys [handler metadata-path]} op-config]
    (when (and handler metadata-path)
      (-> handler symbol resolve
          (wrap-inputs)
          (wrap-results)
          (wrap-config (io/resource metadata-path))
          (wrap-asset-store {"1234" (s/memory-asset {:test :metadata} "content")})))))

(defn load-operation-config
  "returns a map of operation config, where keys are the operation name
  and value is a map with handler and metadata-path"
  [edn-config]
  (let [res
        (->> edn-config
             (reduce-kv
              (fn[acc k v]
                (merge acc
                       {k (update-in v [:handler]
                                     #(do (load-file %)
                                          (str (string->ns %) "/"
                                               (:fn v))))}))
              {}))
        ;;calculate the hash of the metadata, and merge a map where the values are the same,
        ;;and keys are the hashes of the metadata
        res2 (reduce-kv (fn[acc k v]
                          (let [dig (-> v :metadata-path io/resource slurp s/digest)]
                            (assoc acc (keyword dig) v)))
                            {} res)

        res3 {:operation-registry (merge res res2)}]
    res3))

(defn load-operations-reg
  [config]
  (reduce-kv (fn[acc k v]
               (do (println " k " k " v " v)
                   (assoc acc k (load-operation-config v))))
             {}
             config))

                                        ;(load-operations-reg (load-edn (io/resource "koi-config.edn")))
(comment
  
(let [config
      (->> (io/resource "koi-config.edn")
           (load-edn )
           load-operation-config
           :operation-registry)]
  (default-middleware :hashing config))

(let [hash-config
      (->> (io/resource "koi-config.edn")
           (load-edn )
           load-operation-config
           :operation-registry
           :hashing)
      ifn (test-middleware hash-config)]
  (ifn {:invoke-args {:to-hash {:did "1234"}
                      :algorithm "keccak256"}}))

(let [config
      (->> (io/resource "config.edn")
           aero.core/read-config
           :operations
           load-operation-config
           :operation-registry)]
  (default-middleware :hashing config))

(let [config
      (->> (io/resource "koi-config.edn")
           (load-edn )
           load-operation-config
           :operation-registry)]
  (-> config
      :hashing
      :metadata-path
      (io/resource)
      slurp))
)
