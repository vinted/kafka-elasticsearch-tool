(ns core.pprint
  (:require [clojure.pprint :as pprint]
            [clojure.string :as str]))

(defn keys-in
  "Returns a sequence of all key paths in a given map using DFS walk."
  [m]
  (letfn [(children [node]
            (let [v (get-in m node)]
              (if (map? v)
                (map (fn [x] (conj node x)) (keys v))
                [])))
          (branch? [node] (-> (children node) seq boolean))]
    (->> (keys m)
         (map vector)
         (mapcat #(tree-seq branch? children %)))))

(defn kv-string
  ([m] (kv-string m {}))
  ([m opts]
   (let [kseqs (keys-in m)
         kseqs (if-let [max-depth (:depth opts)]
                 (remove (fn [kseq] (< max-depth (count kseq))) kseqs)
                 kseqs)]
     (with-out-str
       (pprint/print-table
         (->> kseqs
              (map (fn [kseq]
                     {:k (keyword (str/join "." (map name kseq)))
                      :v (get-in m kseq)}))
              (remove (fn [m] (coll? (:v m))))
              (sort-by :k)))))))

(defn paths-to-vals [m]
  (let [paths (keys-in m)]
    (filter (fn [path] (not (map? (get-in m path)))) paths)))

(defn conf-merge
  "Merges two maps not overriding values"
  [conf-a conf-b]
  (let [paths (paths-to-vals conf-b)]
    (reduce (fn [acc path]
              (let [value (get-in conf-b path)]
                (assoc-in acc path value))) conf-a paths)))
