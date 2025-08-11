(ns simple-async-test
  (:require [await-cps.async-await :refer [await]])
  (:require-macros [await-cps.async-await :refer [async]]))

(println "=== Simple Async Test ===")

;; Test the simplest case first
(def simple-test
  (async
    "just a string"))

(println "Created simple async block")

(.then simple-test 
       #(println "SIMPLE SUCCESS:" %)
       #(println "SIMPLE ERROR:" %))

(defn -main []
  (println "Simple async test main called"))