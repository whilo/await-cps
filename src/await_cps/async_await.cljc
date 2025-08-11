(ns await-cps.async-await
  "ClojureScript async/await implementation using await-cps coroutine transformation"
  (:refer-clojure :exclude [await])
  #?(:clj (:require [await-cps.ioc :refer [coroutine]]
                    [clojure.pprint]
                    [clojure.walk])))

;; Copy the exact await function pattern from await-cps.clj
(defn await
  "Awaits the asynchronous execution of a CPS function or JavaScript Promise.
   
   Must be called in an async block. Note that any nested functions
   defined with fn, letfn, reify or deftype are considered outside of
   asynchronous scope."
  [async-value & args]
  #?(:cljs (throw (js/Error. "await called outside of async block"))
     :clj  (throw (IllegalStateException. "await called outside of async block"))))

;; Copy the do-await pattern adapted for Promises  
#?(:cljs
   (do
     (defn ^:no-doc do-await
       "CPS handler for await transformation - matches await-cps pattern exactly"
       [r e async-value & args]
       ;; This should match the original do-await signature: [r e f & args]
       ;; r = resolve continuation, e = error continuation
       ;; async-value = either a Promise or CPS function, args = additional arguments
       (try
         (cond
           ;; Handle JavaScript Promises
           (instance? js/Promise async-value)
           (.then async-value 
                  (fn [result] (r result))
                  (fn [error] (e error)))
           
           ;; Handle CPS functions (original await-cps pattern)
           (fn? async-value)
           (apply async-value (concat args [(fn [result] (r result))
                                           (fn [error] (e error))]))
           
           ;; Handle direct values
           :else
           (r async-value))
         (catch js/Error err
           (e err))))

     (defn ^:no-doc run-async
       "ClojureScript version of run-async - executes async function with resolve/reject callbacks"
       [f resolve reject]
       ;; In ClojureScript, we don't need trampolining like in Clojure
       ;; Just execute the function directly
       (try
         (f resolve reject)
         nil
         (catch js/Error e
           (reject e))))))

;; Copy the terminators pattern exactly from await-cps.clj
(def ^:no-doc terminators
  {'await-cps.async-await/await 'await-cps.async-await/do-await})

;; Async macro that creates JavaScript Promises using coroutine transformation
(defmacro async
  "Creates an async function that returns a JavaScript Promise.
   Uses await-cps coroutine transformation to handle await calls."
  [& body]
  (if (:js-globals &env) ; ClojureScript
      `(js/Promise.
         (fn [resolve# reject#]
           ;; Use our coroutine macro with the terminators map
           ;; The key should be the symbol that appears in code, value is the actual function
           ;; Use the exact terminators pattern from await-cps.clj
           (let [async-fn# (coroutine ~terminators
                             (do ~@body))]
             ;; Execute the coroutine with Promise resolve/reject as callbacks
             (async-fn# resolve# reject#))))
    ;; Clojure - just execute synchronously
    `(do ~@body)))

;; Convenience macro for missionary compatibility
(defmacro sp
  "Alias for async (missionary compatibility)"
  [& body]
  `(async ~@body))

#?(:cljs
   (do
     ;; Helper function for converting callback-based operations to Promises
     (defn promisify
       "Converts a callback-based async function to a Promise"
       [callback-fn]
       (js/Promise.
         (fn [resolve reject]
           (callback-fn resolve reject))))

     ;; Make Promises callable for missionary compatibility
     (extend-type js/Promise
       IFn
       (-invoke
         ;; Called with no args - returns nil (cancel operation)
         ([_this] nil)
         ;; Called with success callback only
         ([this success]
          (.then this success))
         ;; Called with success and failure callbacks
         ([this success failure]
          (-> this
              (.then success)
              (.catch failure)))))))