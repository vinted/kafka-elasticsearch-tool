(ns core.async
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.protocols :as impl]))

(defn seq-of-chan
  "Creates a lazy seq from a core.async channel."
  [c]
  (lazy-seq
    (let [fst (a/<!! c)]
      (if (nil? fst) nil (cons fst (seq-of-chan c))))))

(defn map-pipeline-async
  "Map for asynchronous functions, backed by clojure.core.async/pipeline-async.
  From an asynchronous function af, and a seq coll, creates a lazy seq that is
  the result of applying the asynchronous function af to each element of coll.
  af must be an asynchronous function as described in clojure.core.async/pipeline-async.
  takes an optional p parallelism number."
  ([af p coll]
   (let [ic (a/chan p)
         oc (a/chan p)]
     (a/onto-chan! ic coll)
     (a/pipeline-async (min p impl/MAX-QUEUE-SIZE) oc af ic)
     (seq-of-chan oc)))
  ([af coll] (map-pipeline-async af 200 coll)))

(defn map-pipeline
  "Parallel map for compute intensive functions, backed by clojure.core.async/pipeline."
  ([f p coll]
   (let [ic (a/chan p)
         oc (a/chan p)]
     (a/onto-chan! ic coll)
     (a/pipeline (min p impl/MAX-QUEUE-SIZE) oc (map f) ic)
     (seq-of-chan oc)))
  ([f coll] (map-pipeline f 16 coll)))
