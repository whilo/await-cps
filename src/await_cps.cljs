(ns await-cps
  "ClojureScript runtime support for await-cps macros"
  (:refer-clojure :exclude [await])
  (:require [await-cps.async-await :as impl]))

;; Provide the await function that the original afn macro expects
(def await impl/await)

;; Provide the do-await function for the terminators map
(def do-await impl/do-await)

;; Provide the run-async function that afn macro calls
(def run-async impl/run-async)