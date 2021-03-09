(ns polyglot.js
  (:require [core.json :as json])
  (:import (org.graalvm.polyglot Value Context HostAccess)
           (java.util Map)
           (org.graalvm.polyglot.proxy ProxyObject)))

(defn transform-map-js [m function-source-code]
  (let [^Context ctx (-> (Context/newBuilder (into-array String ["js"]))
                         (.allowHostAccess HostAccess/ALL)
                         (.build))
        initial-proxy (ProxyObject/fromMap m)
        ^Value f (.eval ctx "js" function-source-code)]
    (.as ^Value (.execute ^Value f (object-array [initial-proxy])) ^Class Map)))

(defn string->string [s transformation-source-code]
  (let [^Context ctx (-> (Context/newBuilder (into-array String ["js"]))
                         (.allowHostAccess HostAccess/ALL)
                         (.build))
        _ (.putMember (.getBindings ctx "js") "m" s)
        ^Value f (.eval ctx "js" (format "JSON.stringify((%s)(JSON.parse(m)))" transformation-source-code))]
    (.asString ^Value f)))

; https://www.graalvm.org/reference-manual/embed-languages/
(defn script->transform-fn
  "Given a JavaScript source code snippet creates a function that expects
  string that is going to be passed into that function as an argument.
  Returns a string."
  [^String script]
  (let [^Context ctx (.build (Context/newBuilder (into-array String ["js"])))
        ^String wrapped-script (format "(query) => JSON.stringify((%s)(JSON.parse(query)))" script)
        ^Value f (.eval ctx "js" wrapped-script)]
    (fn [^String value]
      ; Turns out that can't be accesses by multiple threads
      ; TODO: every thread should create its own Context
      ; https://medium.com/graalvm/multi-threaded-java-javascript-language-interoperability-in-graalvm-2f19c1f9c37b
      (locking ctx
        (.asString (.execute f (object-array [value])))))))

(defn script->transform-fn-vals
  "Given a JavaScript source code snippet creates a function that expects
  string that is going to be passed into that function as an argument.
  Returns a string."
  [^String script]
  (let [^Context ctx (.build (Context/newBuilder (into-array String ["js"])))
        ^String wrapped-script (format "(...args) => { args[0] = JSON.parse(args[0]); return JSON.stringify((%s).apply(null, args));}" script)
        ^Value f (.eval ctx "js" wrapped-script)]
    (fn [& vals]
      (locking ctx
        (.asString (.execute f (object-array vals)))))))

(comment
  (time
    (let [tf (time (polyglot.js/script->transform-fn
                "(request) => {const deepEqual=(e,t)=>{const r=Object.keys(e),n=Object.keys(t);if(r.length!==n.length)return!1;for(const n of r){const r=e[n],s=t[n],a=isObject(r)&&isObject(s);if(a&&!deepEqual(r,s)||!a&&r!==s)return!1}return!0},isObject=e=>null!==e&&\"object\"==typeof e,remove=[...request.query.bool.filter].filter(e=>e.range).filter(e=>Object.keys(e.range).includes(\"created_at\")||Object.keys(e.range).includes(\"user_updated_at\")).filter(e=>{const t=e.range.created_at&&1===Object.keys(e.range.created_at).length&&Object.keys(e.range.created_at).includes(\"lte\"),r=e.range.user_updated_at&&1===Object.keys(e.range.user_updated_at).length&&Object.keys(e.range.user_updated_at).includes(\"lte\");return t||r}),transform=[...remove].map(e=>{const t=Object.keys(e.range)[0];return{range:{[t]:{gt:e.range[t].lte}}}});request.query.bool.filter=request.query.bool.filter.filter(e=>!remove.some(t=>deepEqual(e,t))),request.query.bool.must_not.push(...transform);return request;}"))
          data "{\"_source\":false,\"stats\":[\"regular_items_lookup\"],\"query\":{\"bool\":{\"must\":[],\"filter\":[{\"range\":{\"created_at\":{\"lte\":\"2020-12-23T15:55:00+01:00\",\"gte\":\"2020-12-14T15:55:00+01:00\"}}},{\"range\":{\"user_updated_at\":{\"lte\":\"2020-12-23T15:55:00+01:00\"}}},{\"terms\":{\"country_id\":[7,10,16,18,19,20]}},{\"terms\":{\"catalog_tree_ids\":[1653]}}],\"must_not\":[{\"exists\":{\"field\":\"stress_test\"}},{\"exists\":{\"field\":\"user_shadow_banned\"}},{\"bool\":{\"must_not\":{\"term\":{\"country_id\":16}},\"filter\":{\"range\":{\"international_visibility_enabled_from\":{\"gt\":\"2020-12-23T15:55:00+01:00\"}}}}}],\"should\":[{\"bool\":{\"filter\":{\"range\":{\"user_updated_at\":{\"gte\":\"2020-11-23\"}}},\"must\":[{\"distance_feature\":{\"field\":\"user_updated_at\",\"origin\":\"2020-12-23T15:55:00+01:00\",\"pivot\":\"7d\",\"boost\":1.25}}]}},{\"constant_score\":{\"filter\":{\"range\":{\"promoted_until\":{\"gte\":\"2020-12-23T15:55:00+01:00\"}}},\"boost\":1.8}},{\"bool\":{\"must_not\":{\"term\":{\"country_id\":16}},\"must\":{\"range\":{\"international_visibility_enabled_from\":{\"boost\":1.0,\"lte\":\"2020-12-23T15:55:00+01:00\",\"gte\":\"2020-12-23T07:55:00+01:00\"}}}}}]}},\"from\":0,\"size\":24,\"rescore\":[]}"]
      (dotimes [i 10000]
        (tf data))))

  ; The cool trick can be that you encode many values in a string, and in your function parse it out.
  (let [tf (polyglot.js/script->transform-fn-vals
             "(x, y) => {return [x, y]}")]
    (tf (json/encode  {:a "1"}) "2" 3 4)))
