(ns sink.file
  (:import (java.io PrintStream File)
           (java.nio.file Files Paths StandardOpenOption OpenOption)
           (java.nio.charset StandardCharsets)))

(defn prepare-print-stream [^String filename]
  (let [f (File. filename)]
    (when-let [parent-file (.getParentFile f)]
      (.mkdirs parent-file)))
  (PrintStream.
    (Files/newOutputStream
      (Paths/get "" (into-array ^String [filename]))
      (into-array OpenOption [StandardOpenOption/CREATE StandardOpenOption/APPEND]))
    false
    (.name StandardCharsets/UTF_8)))

(defn ^PrintStream prepare [^String filename]
  (if filename
    {:stream (prepare-print-stream filename)
     :close? true}
    {:stream System/out
     :close? false}))

(defn write-to-file [filename records]
  (let [{^PrintStream outputStream :stream close? :close?} (prepare filename)]
    (doseq [r records]
      (.println outputStream (:value r)))
    (.flush outputStream)
    (when close?
      (.close outputStream))))

(defn filename->path-and-filename [^String filename]
  (let [f (File. filename)]
    {:path      (.toString (.getParentFile f))
     :file-name (.getName f)}))

(defn partition-and-write-to-file [partition-size filename records]
  (let [{:keys [path file-name]} (when filename (filename->path-and-filename filename))]
    (loop [parts (partition-all partition-size records)
           i 0]
      (let [start-index (* partition-size i)
            end-index (min (+ (* partition-size i) (dec (count (first parts))))
                           (- (* partition-size (inc i)) 1))
            partition-filename (when filename
                                 (format "%s/%s_%s_%s" path start-index end-index file-name))]
        (if (seq (first parts))
          (write-to-file partition-filename (first parts)))
        (if (seq (rest parts))
          (recur (rest parts) (inc i)))))))

(defn store!
  "When filename is not provided then output is System.out"
  [records opts]
  (let [filename (-> opts :sink :filename)]
    (if-let [partition-size (-> opts :sink :partition-size)]
      (partition-and-write-to-file partition-size filename records)
      (write-to-file filename records))))

(comment
  (sink.file/store!
    [{:value "line1"}
     {:value "line2"}]
    {:sink {}})

  (sink.file/store!
    [{:value "line1"}
     {:value "line2"}]
    {:sink {:filename "test.ndjson"}})

  (sink.file/store!
    [{:value "line1"}
     {:value "line2"}]
    {:sink {:filename "foo/test.ndjson"}})

  (sink.file/store!
    (map (fn [val] {:value val}) (range 17))
    {:sink {:filename       "test.ndjson"
            :partition-size 10}})

  (sink.file/store!
    (map (fn [val] {:value val}) (range 17))
    {:sink {:filename       "foo/test.ndjson"
            :partition-size 10}})

  (sink.file/store!
    (map (fn [val] {:value val}) (range 17))
    {:sink {:filename       "/tmp/test.ndjson"
            :partition-size 10}})

  (sink.file/store!
    (map (fn [val] {:value val}) (range 17))
    {:sink {:partition-size 10}}))
