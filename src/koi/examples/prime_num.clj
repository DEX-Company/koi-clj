(ns koi.examples.prime-num
  (:require
   [clojure.spec.alpha :as sp]
   [starfish.core :as s]
   [koi.utils :as utils :refer [put-asset get-asset-content process]]
   [taoensso.timbre :as timbre
    :refer [log  trace  debug  info  warn  error  fatal  report
            logf tracef debugf infof warnf errorf fatalf reportf
            spy get-env]]
   [koi.protocols :as prot 
    :refer [invoke-sync
            invoke-async
            valid-args?]]
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

(defn compute-primes
  [agent {:keys [first-n]}]
  (fn []
    (let [list-of-primes (sieve-primes (cond (int? first-n) first-n
                                             :default (Integer/parseInt first-n)))
          primes-str (clojure.string/join "\n" list-of-primes)
          res {:dependencies []
               :results [{:param-name :primes
                          :type :asset
                          :content primes-str}]}]
      (info " result of execfn " res)
      res)))

(deftype PrimeNumbers [agent jobs jobids]
  :load-ns true
  prot/PSyncInvoke
  (invoke-sync [_ args]
    (process agent args compute-primes))

  prot/PAsyncInvoke
  (invoke-async [_ args]
    (let [jobid (swap! jobids inc)
          _ (info " jobid for async prime job " jobid)]
      (doto (Thread. (fn []
                       (swap! jobs assoc jobid {:status :scheduled})
                       (try (Thread/sleep 10000)
                            (catch Exception e))
                       (swap! jobs assoc jobid {:status :running})
                       (try (let [res (process agent args compute-primes)]
                              (swap! jobs assoc jobid
                                     {:status :succeeded
                                      :results (:results res)}))
                            (catch Exception e
                              (error " got exception running prime job " e )
                              (clojure.stacktrace/print-stack-trace e)
                              (let [resp {:status :failed
                                          :errorcode 8005
                                          :description (str "Got exception " (.getMessage e))}]
                                (swap! jobs assoc jobid resp)
                                resp)))))
        .start)
      {:jobid jobid}))
  
  prot/PValidParams
  (valid-args? [_ args]
    {:valid? (sp/valid? ::params args)})
  )
