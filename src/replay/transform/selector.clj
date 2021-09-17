(ns replay.transform.selector)

(defn path->selector
  "path-to-data can be either a String or an array for get-in.
  When array then keywordize strings while integers should be preserved
  Returns an array that can be used as get-in ks param"
  [path-to-data]
  (if (string? path-to-data)
    [(keyword path-to-data)]
    (mapv (fn [selector]
            (if (string? selector)
              (keyword selector)
              selector)) path-to-data)))

(comment
  (replay.transform.selector/path->selector ["a" 1 "b"]))
