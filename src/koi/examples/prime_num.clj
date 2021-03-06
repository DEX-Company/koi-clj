(ns koi.examples.prime-num
  (:require
   [starfish.core :as s]))

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
  [{:keys [first-n]}]
  (let [list-of-primes (sieve-primes (cond (int? first-n) first-n
                                           :default (Integer/parseInt first-n)))
        primes-str (clojure.string/join "\n" list-of-primes)]
    {:primes (s/asset (s/memory-asset primes-str))}))
