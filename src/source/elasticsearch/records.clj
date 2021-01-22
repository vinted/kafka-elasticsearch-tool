(ns source.elasticsearch.records
  (:require [scroll :as scroll]))

(defn conf->scroll
  [conf default-conf]
  (let [source-host (or (-> conf :source :remote :host)
                        (-> default-conf :source :remote :host))
        source-index (or (-> conf :source :index)
                         (-> default-conf :source :index))
        query (or (-> conf :source :query)
                  (-> default-conf :source :query))
        scroll-keep-context (or (-> conf :source :remote :socket_timeout)
                                (-> default-conf :source :remote :socket_timeout)
                                "1m")
        keywordize? (first
                      (drop-while
                        nil?
                        (list (-> conf :source :keywordize?)
                              (-> default-conf :source :keywordize?)
                              true)))
        strategy (or (-> conf :source :strategy)
                     (-> default-conf :source :strategy) :search-after)]
    {:es-host    source-host
     :index-name source-index
     :query      query
     :opts       {:keep-context scroll-keep-context
                  :keywordize?  keywordize?
                  :strategy     strategy}}))

(defn fetch
  ([conf] (fetch conf {}))
  ([conf default-conf]
   (let [max-docs (-> conf :max_docs)
         es-records (scroll/hits (conf->scroll conf default-conf))]
     (if max-docs (take max-docs es-records) es-records))))

(comment
  (source.elasticsearch.records/fetch
    {:max_docs 10
     :source {:remote {:host "http://localhost:9200"}
              :index "index_name"
              :query {:size 1}}}))
