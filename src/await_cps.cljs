(ns await-cps
  "async/await syntax for functions that take a successful- and an exceptional
   callback in the last two arguments, a pattern known as continuation-passing
   style (CPS) and popularised by Ring and clj-http."
  (:refer-clojure :exclude [await bound-fn])
  (:require-macros await-cps))

;; Immediate value optimization: Functions can return immediate values
;; for synchronous operations to bypass CPS machinery

(deftype ImmediateValue [val])

(defn immediate 
  "Wrap a value as immediately available"
  [v] 
  (ImmediateValue. v))

(defn immediate? 
  "Check if value is immediately available"
  [v] 
  (instance? ImmediateValue v))

(defn unwrap-immediate 
  "Extract value from immediate wrapper"
  [^ImmediateValue v] 
  (.-val v))

(def ^:no-doc bound-fn identity)

(defn ^:no-doc do-await
  [r e f & args]
  ;; f should be a CPS function since IOC handles immediate values
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
    (let [return-val (apply f (concat args [resolve raise]))]
      (when (immediate? return-val)
        (println "WARNING: CPS function returned immediate value instead of calling callbacks")))
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
        :resolved (fn [] (safe-r x))  ; Fast path: callback was called synchronously, trampolined
        :raised (fn [] (e x))         ; Fast path: error thrown synchronously, trampolined  
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