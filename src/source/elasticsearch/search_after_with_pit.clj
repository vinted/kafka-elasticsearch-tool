(ns source.elasticsearch.search-after-with-pit
  (:require [clojure.tools.logging :as log]
            [scroll.pit :as pit]
            [scroll :as scroll]))

(defn records [es-host index-name query max-docs keep-alive]
  (let [opts {:keep-alive keep-alive}
        pit (pit/init es-host index-name opts)
        ; mutable state is needed because PIT ID might change between calls
        latest-pit-id (atom (:id pit))
        pit-with-keep-alive (assoc pit :keep_alive keep-alive)]
    (lazy-cat
      (let [hits (scroll/hits
                   {:es-host    es-host
                    :index-name index-name
                    :query      (assoc query :pit pit-with-keep-alive)
                    :opts       {:strategy      :search-after
                                 ; expects an atom
                                 ; the contents of an atom will be a string with PIT ID
                                 :latest-pit-id latest-pit-id}})]
        (if max-docs (take max-docs hits) hits))
      ; last element of the lazy-sequence is the output of `do` macro
      ; and inside the `do` we terminate the PIT and return nil
      ; that last nil will not be in the sequence because `lazy-cat` terminates if nil
      (do
        (log/debugf "PIT terminated with: %s"
                    (pit/terminate es-host {:id @latest-pit-id}))
        nil))))

; TODO: support other options such as keywordize?
(defn fetch [opts]
  (let [max-docs (-> opts :max_docs)
        es-host (or (-> opts :source :remote :host) "http://localhost:9200")
        index-name (or (-> opts :source :index)
                       (-> opts :source :remote :index)
                       "*")
        query (or (-> opts :source :query) {:query {:match_all {}}})
        keep-alive (or (-> opts :source :remote :connect_timeout) "30s")]
    (records es-host index-name query max-docs keep-alive)))

(comment
  (source.elasticsearch.search-after-with-pit/fetch
    {:max_docs 12
     :source   {:remote {:host "http://localhost:9200"}
                :index  "index_name"}}))
