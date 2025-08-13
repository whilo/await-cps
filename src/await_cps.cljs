(ns await-cps
  "async/await syntax for functions that take a successful- and an exceptional
   callback in the last two arguments, a pattern known as continuation-passing
   style (CPS) and popularised by Ring and clj-http."
  (:refer-clojure :exclude [await bound-fn])
  (:require-macros await-cps))

;; Unified callback approach: No need for ImmediateValue wrapper
;; All async functions return CPS functions that decide at call time
;; whether to invoke callbacks synchronously or asynchronously

(def ^:no-doc bound-fn identity)

(defn ^:no-doc do-await
  [r e f & args]
  ;; Unified approach: f is always a CPS function
  ;; Fast path is when f calls the callback synchronously (same execution tick)
  ;; Slow path is when f calls the callback asynchronously (different execution tick)
  (let [state (atom [:start])
        resolve (fn [v] (let [[[before r']]
                              (swap-vals! state
                                          #(case (first %)
                                             :start [:resolved v]
                                             :async [:completed]
                                             %))]
                          (when (= before :async) (r' v))))
        raise (fn [t] (let [[[before _ e']]
                            (swap-vals! state
                                        #(case (first %)
                                           :start [:raised t]
                                           :async [:completed]
                                           %))]
                        (when (= before :async) (e' t))))]
    (apply f (concat args [resolve raise]))
    (let [run (bound-fn trampoline)
          safe-r #(try (r %) (catch :default t (e t)))
          other-thread-r #(run safe-r %)
          other-thread-e #(run e %)
          [[before x]]
          (swap-vals! state
                      #(case (first %)
                         :start [:async other-thread-r other-thread-e]
                         :resolved [:completed]
                         :raised [:completed]
                         %))]
      (case before
        :resolved (partial safe-r x)  ; Fast path: callback was called synchronously
        :raised (partial e x)         ; Fast path: error thrown synchronously  
        nil))))                       ; Slow path: suspended to async

(defn ^:no-doc run-async
  [f resolve raise]
  (let [run (bound-fn trampoline)]
    (try
      (run f resolve raise)
      (catch :default e
        (raise e)))))

(defn await
  "Awaits the asynchronous execution of continuation-passing style function
   cps-fn, applying it to args and two extra callback functions: resolve and
   raise. cps-fn is expected to eventually either call resolve with the result,
   call raise with the exception or just throw in the calling thread. The
   return value of cps-fn is ignored. Effectively returns the value passed to
   resolve or throws the exception passed to raise (or thrown) but does not
   block the calling tread.

   Must be called in an asynchronous function. Note that any nested functions
   defined with fn, letfn, reify or deftype are considered outside of
   asynchronous scope."
  [cps-fn & args]
  (throw (new js/Error "await called outside of asynchronous scope")))