(ns await-test
  (:require [await-cps.async-await :refer [await]])
  (:require-macros [await-cps.async-await :refer [async]]))

(println "=== Await Test ===")

;; Test with Promise await
(def promise-test
  (async
    (await (js/Promise.resolve "resolved-value"))))

(println "Created promise await test")

(.then promise-test 
       #(println "PROMISE SUCCESS:" %)
       #(println "PROMISE ERROR:" %))

;; Test with CPS function await
(def cps-test
  (async
    (await (fn [resolve reject]
             (js/setTimeout #(resolve "timeout-value") 50)))))

(println "Created CPS await test")

(.then cps-test 
       #(println "CPS SUCCESS:" %)
       #(println "CPS ERROR:" %))

(defn -main []
  (println "Await test main called"))