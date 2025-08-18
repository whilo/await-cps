(ns await-cps
  "async/await syntax for functions that take a successful- and an exceptional
   callback in the last two arguments, a pattern known as continuation-passing
   style (CPS) and popularised by Ring and clj-http."
  (:refer-clojure :exclude [await bound-fn])
  (:require-macros await-cps))

(def ^:no-doc bound-fn identity)

(deftype Thunk [f]
  IFn
  (-invoke [_] (f)))

(defn ^:no-doc ->thunk
  "Create a thunk for trampolining"
  [f]
  (Thunk. f))

(defn ^:no-doc smart-trampoline
  "Trampoline that only bounces on Thunk instances, allowing functions to be returned as values"
  [f & args]
  (loop [result (apply f args)]
    (if (instance? Thunk result)
      (recur (result))  ; Continue trampolining thunks
      result)))         ; Stop - return any other value (including functions)

(defn ^:no-doc run-async
  [f resolve raise]
  (try
    (smart-trampoline f resolve raise)
    (catch :default e
      (raise e))))

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
    (apply f (concat args [resolve raise]))
    (let [safe-r #(try (r %) (catch :default t (e t)))
          other-thread-r (fn [v] 
                          ;; Call continuation and if it returns a Thunk, trampoline it
                          (let [result (safe-r v)]
                            (if (instance? Thunk result)
                              (smart-trampoline result)
                              result)))
          other-thread-e (fn [t]
                          ;; Call error handler and if it returns a Thunk, trampoline it  
                          (let [result (e t)]
                            (if (instance? Thunk result)
                              (smart-trampoline result)
                              result)))
          [[before x]]
          (swap-vals! state
                      #(case (first %)
                         :start [:async other-thread-r other-thread-e]
                         :resolved [:completed]
                         :raised [:completed]
                         %))]
      (case before
        :resolved (->thunk #(safe-r x))  ; Fast path: callback was called synchronously, trampolined
        :raised (->thunk #(e x))         ; Fast path: error thrown synchronously, trampolined  
        nil))))                          ; Slow path: suspended to async

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