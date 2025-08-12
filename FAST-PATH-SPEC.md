# Fast-Path Optimization Specification for await-cps

## Overview
Implement a short-circuit mechanism for async/await blocks that avoids suspension overhead when values are immediately available.

## Core Design Principle
- Async blocks return either:
  1. An `ImmediateValue` wrapper when no suspension occurred
  2. A regular CPS function when suspension is needed
- The `await` mechanism dispatches based on the value type

## Implementation Components

### 1. ImmediateValue Type
```clojure
(deftype ImmediateValue [val])

(defn immediate [v])     ; Constructor
(defn immediate? [v])    ; Type check
```

**Purpose**: Wraps values that are immediately available without suspension.

### 2. Modified `do-await` Function

The `do-await` function needs to check if the awaited value is an `ImmediateValue`:

```clojure
(defn do-await [r e f & args]
  (if (immediate? f)
    (r (.-val f))  ; Fast path: unwrap and continue
    ; ... existing CPS suspension logic ...
    ))
```

### 3. Modified `invert` Function in ioc.clj

The CPS transformation needs two key changes:

#### 3.1 Non-terminator Case
When there are no terminators (no await calls), instead of:
```clojure
`(~r ~form)
```

We need:
```clojure
`(~r (immediate ~form))
```

This ensures that synchronous paths return `ImmediateValue`.

#### 3.2 Await Terminator Handling
When processing `await` calls, the generated code should:
1. Check if the awaited value is an `ImmediateValue`
2. If yes, unwrap and continue synchronously
3. If no, proceed with CPS suspension

The transformation for await should become:
```clojure
; Original: (await expr)
; Becomes:
(let [v# expr]
  (if (immediate? v#)
    (continue-with (.-val v#))  ; Fast path
    (do-await continue-with raise v#)))  ; Slow path
```

### 4. Return Value Protocol

#### For async blocks:
- If no `await` is hit → return `(immediate result)`
- If `await` is hit on a callback → return nil (current behavior)
- If `await` is hit on an `ImmediateValue` → may still return `(immediate result)` if no actual suspension

#### For regular CPS functions:
- Can optionally return `ImmediateValue` to signal immediate availability
- Example: `(fn [resolve raise] (resolve 42))` could become `(immediate 42)`

## Propagation Chain Example

```clojure
(defn inner []
  (immediate 5))  ; Returns ImmediateValue

(defn middle []
  (async resolve raise
    (let [x (await (inner))]  ; Detects ImmediateValue, no suspension
      (* x 2))))              ; Returns (immediate 10)

(defn outer []
  (async resolve raise
    (let [y (await (middle))]  ; Detects ImmediateValue, no suspension
      (+ y 1))))              ; Returns (immediate 11)
```

## Key Implementation Challenges

### 1. Modifying the `invert` Function
The `invert` function in `ioc.clj` needs to:
- Track whether any actual suspension occurred
- Wrap non-suspending results with `ImmediateValue`
- Generate conditional dispatch code for await points

### 2. Handling Edge Cases
- Error propagation (raised exceptions)
- Nested async blocks
- Interop with existing CPS functions
- Recursive async functions

### 3. Backward Compatibility
- Existing CPS functions that don't return `ImmediateValue` must still work
- The change should be transparent to users

## Implementation Steps

1. **Phase 1: Basic Infrastructure** ✓
   - Add `ImmediateValue` type to both Clojure and ClojureScript
   - Add `immediate`, `immediate?` helper functions
   - Modify `do-await` to handle `ImmediateValue`

2. **Phase 2: Invert Transformation** (TODO)
   - Modify non-terminator case to wrap with `immediate`
   - Generate conditional dispatch for await terminators
   - Track suspension state through the transformation

3. **Phase 3: Async Macro Integration** (TODO)
   - Ensure async blocks can return `ImmediateValue`
   - Modify `run-async` to handle `ImmediateValue` returns
   - Test with nested async blocks

4. **Phase 4: Testing & Optimization** (TODO)
   - Comprehensive test suite
   - Performance benchmarks
   - Edge case handling

## Testing Strategy

### Unit Tests
1. `ImmediateValue` wrapper functionality
2. `do-await` fast-path dispatch
3. Non-suspending async blocks
4. Nested async block propagation
5. Mixed immediate/CPS scenarios

### Performance Tests
1. Measure overhead reduction for non-suspending paths
2. Compare with original CPS implementation
3. Test with deeply nested async calls

## Expected Benefits

1. **Zero overhead** for synchronous execution paths
2. **Composable optimization** - chains of async calls maintain fast-path
3. **Runtime dispatch** - avoids compile-time analysis limitations
4. **Type-based dispatch** - cleaner than function checks

## Notes

- The `ImmediateValue` type acts as a monad-like wrapper
- This is similar to how JavaScript Promises can be "already resolved"
- The optimization is transparent to user code
- Can potentially extend to support other immediate value types in the future