(ns fast-path-benchmark
  (:require [await-cps :refer [async await immediate immediate? unwrap-immediate]]))

(enable-console-print!)

(println "=== Fast-Path Optimization Benchmark ===")

;; Test scenarios for fast-path optimization

;; 1. Immediate value function (should use fast path)
(defn immediate-fn [x]
  (immediate (* x 2)))

;; 2. Regular CPS function that does the same computation as immediate-fn
(defn slow-fn [x]
  (fn [resolve raise]
    (resolve (* x 2))))

;; 3. Nested immediate values (should propagate fast path)
(defn nested-immediate [x]
  (fn [resolve raise]
    (async resolve raise
           (let [a (await (immediate-fn x))      ; x * 2
                 b (await (immediate-fn a))      ; (x * 2) * 2 = x * 4
                 c (await (immediate-fn b))]     ; (x * 4) * 2 = x * 8
             (* c 2)))))                        ; (x * 8) * 2 = x * 16

;; 4. Same computation using slow CPS functions
(defn slow-chain [x]
  (fn [resolve raise]
    (async resolve raise
           (let [a (await (slow-fn x))           ; x * 2
                 b (await (slow-fn a))           ; (x * 2) * 2 = x * 4  
                 c (await (slow-fn b))]          ; (x * 4) * 2 = x * 8
             (* c 2)))))

;; 5. Deep nesting of immediate values - FIXED RECURSIVE VERSION
(defn deep-immediate-chain [x depth]
  (fn [resolve raise]
    (async resolve raise
           (if (zero? depth)
             x
             (let [result (await (deep-immediate-chain x (dec depth)))]
               (+ result 1))))))

;; Simple timing function
(defn time-operation [name operation iterations]
  (println (str "\n--- " name " (" iterations " iterations) ---"))
  (let [start-time (js/Date.now)]
    (dotimes [i iterations]
      (operation))
    (let [end-time (js/Date.now)
          total-time (- end-time start-time)
          avg-time (/ total-time iterations)]
      (println (str "Total time: " total-time "ms"))
      (println (str "Average time: " (.toFixed avg-time 4) "ms per operation"))
      avg-time)))

;; Benchmark functions
(defn benchmark-immediate-chain []
  (let [result (atom nil)]
    (time-operation "Immediate Value Chain (FAST PATH)" 
                    (fn []
                      (let [f (nested-immediate 10)]
                        (f (fn [r] (reset! result r)) (fn [e] (throw e)))))
                    500000)
    (println "Final result:" (unwrap-immediate @result))))

(defn benchmark-slow-chain []
  (let [result (atom nil)]
    (time-operation "Regular CPS Chain (SLOW PATH)"
                    (fn []
                      (let [f (slow-chain 10)]
                        (f (fn [r] (reset! result r)) (fn [e] (throw e)))))
                    500000)
    (println "Final result:" (unwrap-immediate @result))))

(defn benchmark-deep-chain []
  (let [result (atom nil)]
    (time-operation "Deep Immediate Chain - depth=10 (FAST PATH)"
                    (fn []
                      (let [f (deep-immediate-chain 1 10)]
                        (f (fn [r] (reset! result r)) (fn [e] (throw e)))))
                    50000)
    (let [raw-result @result
          unwrapped (unwrap-immediate raw-result)]
      (println "Final result (raw):" raw-result)
      (println "Final result (.val):" (when (immediate? raw-result) (unwrap-immediate raw-result)))
      (println "Final result (unwrapped):" unwrapped)
      (println "Expected result should be 11 (1 + 10)"))))

;; Comparison: sync vs async with fast-path
(defn sync-computation [n]
  (reduce + (map #(* % %) (range n))))

(defn async-computation-immediate [n]
  (fn [resolve raise]
    (async resolve raise
           (reduce + (map #(* % %) (range n))))))

;; Complex computation: nested loops with data structures and control flow
(defn complex-computation-sync [data]
  (let [matrix (partition 10 data)
        lookup-table (zipmap (range 100) (map #(* % 3) (range 100)))
        result (atom 0)]
    (doseq [row matrix]
      (let [row-sum (atom 0)]
        (doseq [val row]
          (let [transformed (get lookup-table (mod val 100) val)]
            (when (even? transformed)
              (swap! row-sum + transformed)
              (when (> @row-sum 1000)
                (swap! result + (quot @row-sum 2))
                (reset! row-sum 0)))))
        (when (> @row-sum 0)
          (swap! result + @row-sum))))
    @result))

(defn complex-computation-async [data]
  (fn [resolve raise]
    (async resolve raise
           (let [matrix (partition 10 data)
                 lookup-table (zipmap (range 100) (map #(* % 3) (range 100)))
                 result (atom 0)]
             (doseq [row matrix]
               (let [row-sum (atom 0)]
                 (doseq [val row]
                   (let [transformed (get lookup-table (mod val 100) val)]
                     (when (even? transformed)
                       (swap! row-sum + transformed)
                       (when (> @row-sum 1000)
                         (swap! result + (quot @row-sum 2))
                         (reset! row-sum 0)))))
                 (when (> @row-sum 0)
                   (swap! result + @row-sum))))
             @result))))

(defn benchmark-sync-vs-fastpath []
  (println "\n--- Sync vs Fast-Path Async Comparison (n=1000) ---")
  
  (let [sync-avg (time-operation "Sync computation" 
                                 (fn [] (sync-computation 1000))
                                 250000)]
    
    (let [result (atom nil)
          async-avg (time-operation "Fast-path async computation"
                                    (fn []
                                      (let [f (async-computation-immediate 1000)]
                                        (f (fn [r] (reset! result r)) (fn [e] (throw e)))))
                                    250000)]
      (println (str "Fast-path overhead ratio: " (.toFixed (/ async-avg sync-avg) 2) "x")))))

;; Complex computation benchmark
(defn benchmark-complex-computation []
  (println "\n--- Complex Computation Benchmark (1000 elements, nested loops) ---")
  (let [test-data (vec (range 1000))]
    
    (let [sync-avg (time-operation "Complex sync computation"
                                   (fn [] (complex-computation-sync test-data))
                                   10000)]
      
      (let [result (atom nil)
            async-avg (time-operation "Complex fast-path async computation"
                                      (fn []
                                        (let [f (complex-computation-async test-data)]
                                          (f (fn [r] (reset! result r)) (fn [e] (throw e)))))
                                      10000)]
        (println (str "Complex computation overhead ratio: " (.toFixed (/ async-avg sync-avg) 2) "x"))
        (println "Complex result check - should be same:")
        (println "  Sync result:" (complex-computation-sync test-data))
        (println "  Fast-path result:" (unwrap-immediate @result))))))

;; Test ImmediateValue detection
(defn test-immediate-detection []
  (println "\n--- Testing ImmediateValue Detection ---")
  (let [imm (immediate 42)
        regular-fn (fn [r e] (r 42))]
    (println "immediate 42:" imm)
    (println "immediate? immediate 42:" (immediate? imm))
    (println "immediate? regular function:" (immediate? regular-fn))
    (println ".val of immediate:" (.-val imm))))

;; Direct fast vs slow path comparison
(defn benchmark-fast-vs-slow-path []
  (println "\n--- Fast-Path vs Slow-Path Direct Comparison ---")
  
  (let [result1 (atom nil)
        fast-avg (time-operation "Fast-path (immediate values)"
                                 (fn []
                                   (let [f (nested-immediate 10)]
                                     (f (fn [r] (reset! result1 r)) (fn [e] (throw e)))))
                                 1250000)]
    
    (let [result2 (atom nil)
          slow-avg (time-operation "Slow-path (regular CPS)"
                                   (fn []
                                     (let [f (slow-chain 10)]
                                       (f (fn [r] (reset! result2 r)) (fn [e] (throw e)))))
                                   1250000)]
      (println (str "Slow-path overhead vs fast-path: " (.toFixed (/ slow-avg fast-avg) 1) "x slower"))
      (println "Fast-path result:" (unwrap-immediate @result1))
      (println "Slow-path result:" (unwrap-immediate @result2)))))

;; Run all benchmarks
(defn run-all-benchmarks []
  (test-immediate-detection)
  (benchmark-sync-vs-fastpath)
  (benchmark-complex-computation)
  (benchmark-fast-vs-slow-path)
  (benchmark-immediate-chain)
  (benchmark-slow-chain)
  ;; Skip deep chain for now to test the main fix
  ;; (benchmark-deep-chain)
  (println "\n=== Benchmark Complete ==="))

;; Main function for node script execution
(defn -main []
  (run-all-benchmarks))