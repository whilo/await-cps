(ns comprehensive-test
  (:require-macros [await-cps.async-await :refer [async]])
  (:require [await-cps.async-await :refer [await]]))

;; Helper functions for testing
(defn promise-delay [ms value]
  (js/Promise. 
    (fn [resolve _reject]
      (js/setTimeout #(resolve value) ms))))

(defn promise-reject [ms error]
  (js/Promise.
    (fn [_resolve reject]
      (js/setTimeout #(reject error) ms))))

(defn async-add [a b]
  (promise-delay 10 (+ a b)))

(defn async-multiply [a b]
  (promise-delay 15 (* a b)))

(defn async-divide [a b]
  (if (zero? b)
    (promise-reject 5 (js/Error. "Division by zero"))
    (promise-delay 20 (/ a b))))

;; Test cases
(println "=== Comprehensive Async/Await Test Suite ===")

;; Test 1: Basic await
(def test1
  (async
    (await (promise-delay 10 "basic-test"))))

;; Test 2: Let bindings with await
(def test2
  (async
    (let [x (await (async-add 5 3))
          y (await (async-multiply x 2))]
      {:x x :y y :sum (+ x y)})))

;; Test 3: Multiple sequential awaits
(def test3
  (async
    (let [a (await (promise-delay 10 10))
          b (await (promise-delay 15 20))
          c (await (promise-delay 5 30))]
      (+ a b c))))

;; Test 4: Conditional expressions with await
(def test4
  (async
    (let [condition (await (promise-delay 10 false))]
      (if condition
        (await (promise-delay 20 "condition-true"))
        (await (promise-delay 20 "condition-false"))))))

;; Test 5: Do blocks with await
(def test5
  (async
    (do
      (await (promise-delay 10 "first"))
      (await (promise-delay 15 "second"))
      (await (promise-delay 5 "final")))))

;; Test 6: Function calls with await arguments
(def test6
  (async
    (let [add-result (+ (await (async-add 10 5))
                        (await (async-add 20 3)))]
      (* add-result 2))))

;; Test 7: Collections with await
(def test7
  (async
    (let [items [(await (promise-delay 10 1))
                 (await (promise-delay 15 2))
                 (await (promise-delay 5 3))]
          sum (reduce + items)]
      {:items items :sum sum})))

;; Test 8: Map creation with await values
(def test8
  (async
    {:name (await (promise-delay 10 "John"))
     :age (await (promise-delay 15 30))
     :city (await (promise-delay 5 "NYC"))}))

;; Test 9: Threading macros with await
(def test9
  (async
    (-> (await (promise-delay 10 5))
        (+ 10)
        (* 2))))

;; Test 10: Loop with await (recur)
(def test10
  (async
    (loop [count 0
           acc 0]
      (if (< count 3)
        (let [value (await (async-add acc count))]
          (recur (inc count) value))
        acc))))

;; Test 11: Try-catch with await
(def test11
  (async
    (try
      (await (async-divide 10 2))
      (catch js/Error e
        (str "Error: " (.-message e))))))

;; Test 12: Try-catch with error
(def test12
  (async
    (try
      (await (async-divide 10 0))
      (catch js/Error e
        (str "Caught: " (.-message e))))))

;; Test 13: Nested async calls
(def test13
  (async
    (let [x (await (async
                     (let [a (await (promise-delay 10 5))]
                       (* a 2))))]
      (+ x 10))))

;; Test 14: Complex data transformations
(def test14
  (async
    (let [numbers (await (promise-delay 10 [1 2 3 4 5]))
          doubled (map #(* 2 %) numbers)
          sum (reduce + doubled)
          avg (/ sum (count doubled))]
      {:original numbers
       :doubled doubled 
       :sum sum
       :average avg})))

;; Test 15: Destructuring with await
(def test15
  (async
    (let [{:keys [x y]} (await (promise-delay 10 {:x 10 :y 20}))]
      (+ x y))))

;; Test 16: When/when-not with await
(def test16
  (async
    (when (await (promise-delay 10 true))
      (await (promise-delay 15 "when-success")))))

;; Test 17: Case statement with await
(def test17
  (async
    (let [value (await (promise-delay 10 :option-a))]
      (case value
        :option-a (await (promise-delay 5 "Option A selected"))
        :option-b (await (promise-delay 5 "Option B selected"))
        "Unknown option"))))

;; Test 18: Simple for comprehension (no await inside)
(def test18
  (async
    (let [base (await (promise-delay 10 5))]
      (for [i (range 3)]
        (* base i)))))

;; Test 19: Cond with await
(def test19
  (async
    (let [value (await (promise-delay 10 15))]
      (cond
        (< value 10) (await (promise-delay 5 "small"))
        (< value 20) (await (promise-delay 5 "medium"))
        :else (await (promise-delay 5 "large"))))))

;; Test 20: Multiple awaits in complex expression
(def test20
  (async
    (let [a (await (async-add 1 2))
          b (await (async-multiply 3 4))
          c (await (async-divide 20 4))]
      (+ (* a b) c))))

;; Test 21: Function arguments in scope during async execution
(defn async-function-with-args [x y z]
  (async
    (let [doubled-x (await (async-multiply x 2))
          sum-y-z (await (async-add y z))
          final-result (await (async-add doubled-x sum-y-z))]
      {:original-args [x y z]
       :doubled-x doubled-x
       :sum-y-z sum-y-z
       :final final-result})))

(def test21 (async-function-with-args 5 10 15))

;; Test 22: Nested function calls with argument scoping
(defn outer-async-fn [base multiplier]
  (async
    (let [step1 (await (async-multiply base multiplier))
          inner-fn (fn [offset]
                     (async
                       (let [step2 (await (async-add step1 offset))]
                         {:base base 
                          :multiplier multiplier 
                          :offset offset 
                          :result step2})))]
      (await (inner-fn 100)))))

(def test22 (outer-async-fn 7 8))

;; Test 23: Higher-order function with async callback
(defn process-with-async-callback [items callback]
  (async
    (let [first-item (first items)
          processed (await (callback first-item))]
      {:original-items items
       :first first-item
       :processed processed})))

(def test23
  (process-with-async-callback [1 2 3 4 5]
                               (fn [item]
                                 (async
                                   (await (async-multiply item 10))))))

;; Test 24: Destructured function arguments with async
(defn async-with-destructuring [{:keys [name age]} [first-hobby second-hobby]]
  (async
    (let [greeting (await (promise-delay 10 (str "Hello " name)))
          age-msg (await (promise-delay 15 (str "You are " age " years old")))
          hobby-msg (await (promise-delay 5 (str "You like " first-hobby " and " second-hobby)))]
      (str greeting ". " age-msg ". " hobby-msg "."))))

(def test24 (async-with-destructuring {:name "Alice" :age 30} ["reading" "coding"]))

;; Run all tests
(defn run-tests []
  (println "Starting comprehensive async/await tests...")
  
  ;; Test 1
  (.then test1 
    (fn [result] (println "TEST 1 SUCCESS:" result))
    (fn [error] (println "TEST 1 ERROR:" error)))
  
  ;; Test 2
  (.then test2
    (fn [result] (println "TEST 2 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 2 ERROR:" error)))
  
  ;; Test 3
  (.then test3
    (fn [result] (println "TEST 3 SUCCESS:" result))
    (fn [error] (println "TEST 3 ERROR:" error)))
  
  ;; Test 4
  (.then test4
    (fn [result] (println "TEST 4 SUCCESS:" result))
    (fn [error] (println "TEST 4 ERROR:" error)))
  
  ;; Test 5
  (.then test5
    (fn [result] (println "TEST 5 SUCCESS:" result))
    (fn [error] (println "TEST 5 ERROR:" error)))
  
  ;; Test 6
  (.then test6
    (fn [result] (println "TEST 6 SUCCESS:" result))
    (fn [error] (println "TEST 6 ERROR:" error)))
  
  ;; Test 7
  (.then test7
    (fn [result] (println "TEST 7 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 7 ERROR:" error)))
  
  ;; Test 8
  (.then test8
    (fn [result] (println "TEST 8 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 8 ERROR:" error)))
  
  ;; Test 9
  (.then test9
    (fn [result] (println "TEST 9 SUCCESS:" result))
    (fn [error] (println "TEST 9 ERROR:" error)))
  
  ;; Test 10
  (.then test10
    (fn [result] (println "TEST 10 SUCCESS:" result))
    (fn [error] (println "TEST 10 ERROR:" error)))
  
  ;; Test 11
  (.then test11
    (fn [result] (println "TEST 11 SUCCESS:" result))
    (fn [error] (println "TEST 11 ERROR:" error)))
  
  ;; Test 12
  (.then test12
    (fn [result] (println "TEST 12 SUCCESS:" result))
    (fn [error] (println "TEST 12 ERROR:" error)))
  
  ;; Test 13
  (.then test13
    (fn [result] (println "TEST 13 SUCCESS:" result))
    (fn [error] (println "TEST 13 ERROR:" error)))
  
  ;; Test 14
  (.then test14
    (fn [result] (println "TEST 14 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 14 ERROR:" error)))
  
  ;; Test 15
  (.then test15
    (fn [result] (println "TEST 15 SUCCESS:" result))
    (fn [error] (println "TEST 15 ERROR:" error)))
  
  ;; Test 16
  (.then test16
    (fn [result] (println "TEST 16 SUCCESS:" result))
    (fn [error] (println "TEST 16 ERROR:" error)))
  
  ;; Test 17
  (.then test17
    (fn [result] (println "TEST 17 SUCCESS:" result))
    (fn [error] (println "TEST 17 ERROR:" error)))
  
  ;; Test 18
  (.then test18
    (fn [result] (println "TEST 18 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 18 ERROR:" error)))
  
  ;; Test 19
  (.then test19
    (fn [result] (println "TEST 19 SUCCESS:" result))
    (fn [error] (println "TEST 19 ERROR:" error)))
  
  ;; Test 20
  (.then test20
    (fn [result] (println "TEST 20 SUCCESS:" result))
    (fn [error] (println "TEST 20 ERROR:" error)))
  
  ;; Test 21
  (.then test21
    (fn [result] (println "TEST 21 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 21 ERROR:" error)))
  
  ;; Test 22
  (.then test22
    (fn [result] (println "TEST 22 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 22 ERROR:" error)))
  
  ;; Test 23
  (.then test23
    (fn [result] (println "TEST 23 SUCCESS:" (js/JSON.stringify (clj->js result))))
    (fn [error] (println "TEST 23 ERROR:" error)))
  
  ;; Test 24
  (.then test24
    (fn [result] (println "TEST 24 SUCCESS:" result))
    (fn [error] (println "TEST 24 ERROR:" error)))
  
  ;; Wait a bit and print completion
  (js/setTimeout 
    #(println "\n=== Comprehensive Test Suite Complete ===") 
    3500))

;; Main function for node script
(defn -main []
  (println "=== Starting Comprehensive Async/Await Test ===")
  (run-tests))

;; Auto-run tests when loaded
(run-tests)