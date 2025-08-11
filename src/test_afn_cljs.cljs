(ns test-afn-cljs
  (:require [await-cps :refer [await run-async]])
  (:require-macros [await-cps :refer [afn]]))

;; Test the original afn from await-cps in ClojureScript
(println "Testing original afn in ClojureScript with run-async")

;; First test: simple afn without await
(def test-simple-afn
  (afn [x]
    (str "simple-result: " x)))

(println "Created simple afn function")

;; Test simple afn
(test-simple-afn "hello"
  #(println "SIMPLE AFN SUCCESS:" %)
  #(println "SIMPLE AFN ERROR:" %))

;; Second test: afn with await using CPS style (like original await-cps expects)
(def test-afn
  (afn [x]
    ;; Original await-cps expects: (await cps-fn & args)
    ;; where cps-fn takes (resolve reject) as final args
    (await (fn [resolve reject & args]
             (js/setTimeout #(resolve (str "afn-result: " x)) 100)))))

(println "Created afn function")

;; Test it by calling with resolve/reject callbacks
(test-afn "hello"
  #(println "AFN SUCCESS:" %)
  #(println "AFN ERROR:" %))

(defn ^:export -main []
  (println "AFN test main called"))