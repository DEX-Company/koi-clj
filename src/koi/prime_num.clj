(ns koi.prime-num
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [koi.utils :as utils :refer [register-asset get-asset-content surfer]]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            get-params]]
   [koi.invokespec :as ispec]
   [clojure.java.io :as io]
   [aero.core :refer (read-config)]
   [spec-tools.json-schema :as jsc]))

(sp/def ::first-n string?)

(sp/def ::params (sp/keys :req-un [::first-n]))

(defn sieve-primes [n]
  (loop [p 2 ; First prime
         marked #{} ; Found composites
         primes []]
    (let [mults (->> n
                     (range p)
                     (map #(* p %))
                     (take-while #(< % n)))

          next-p (->> p
                      (inc)
                      (iterate inc)
                      (remove marked)
                      (first))

          new-primes (conj primes p)]

      (if (>= next-p n)
        new-primes
        (recur next-p (into marked mults) new-primes)))))

(defn first-n
  [n]
  (let [_ (println " called get primes "  n)
        res (clojure.string/join "\n" (sieve-primes (Integer/parseInt n)))
        reg-asset-id (register-asset surfer res)]
    {:results {:primes {:did reg-asset-id}}}))

(deftype PrimeNumbers [jobs jobids]

  prot/PSyncInvoke
  (invoke-sync [_ args]
    (let [till (:first-n args)]
      (first-n till)))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (println " invoke args "(:first-n args))
    (let [n (:first-n args)
          jobid (swap! jobids inc)
          _ (println " jobid " jobid)]
      (doto (Thread. (fn []
                       (swap! jobs assoc jobid {:status :scheduled})
                       (try (Thread/sleep 10000)
                            (catch Exception e))
                       (swap! jobs assoc jobid {:status :running})
                       (try (let [res (first-n n)]
                              (swap! jobs assoc jobid
                                     {:status :succeeded
                                      :results (:results res)}))
                            (catch Exception e
                              (println " got exception " e )
                              (clojure.stacktrace/print-stack-trace e)
                              (let [resp {:status :failed
                                          :errorcode 8005
                                          :description (str "Got exception " (.getMessage e))}]
                                (swap! jobs assoc jobid resp)
                                resp)))))
        .start)
      {:jobid jobid}))
  
  prot/PParams
  (get-params [_]
    ::params))

(defn new-primes
  [jobs jobids]
  (PrimeNumbers. jobs jobids))
