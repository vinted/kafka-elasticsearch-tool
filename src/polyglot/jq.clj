(ns polyglot.jq
  (:require [clojure.string :as str]
            [core.json :as json]
            [jq.core :as jq]))

(defn script->transform-fn
  "Given a JQ script snippet creates a function that expects a JSON
  string that is going to be passed into that function as an argument.
  Returns a string."
  [^String script]
  (jq/processor script))

(defn script->transform-fn-vals
  "Given a JQ script snippet creates a function that accepts any number
  of arguments where first argument must be a JSON string and the rest
  of the arguments can be of any type. The output of is a JSON string."
  [^String script]
  (let [jq-fn (jq/processor script)]
    (fn [& args]
      (jq-fn (str "[" (str/join "," (concat [(first args)] (map json/encode-vanilla (rest args)))) "]")))))

(comment
  ;; Add an foo attribute with value bar to a map
  (let [tf (polyglot.jq/script->transform-fn ".foo = \"bar\"")]
    (tf "{}"))
  ;; => {"foo":"bar"}

  ;; String interpolation
  (let [tf (polyglot.jq/script->transform-fn ".a = \">>\\(.a+10)<<\"")]
    (tf "{\"a\": 12}"))
  ;; => {"a":">>22<<"}

  ;; Generate an array
  (let [tf (polyglot.jq/script->transform-fn "[(.a,.b)=range(3)]")]
    (tf "{\"a\": 12}"))
  ;; => [{"a":0,"b":0},{"a":1,"b":1},{"a":2,"b":2}]


  ;; Collect all the keys from an array of maps to an array
  (let [tf (polyglot.jq/script->transform-fn-vals "[.[] | keys] | add")]
    (tf "{\"a\": 1}" "{\"b\": 2}"))
  ;; => ["a","b"]

  ;; Work with an array of elements
  (let [tf (polyglot.jq/script->transform-fn-vals
             ".[0] as $first_arg | .[1] as $second_arg | {} | .\"1st\" = $first_arg | .\"2nd\" = $second_arg")]
    (tf "{\"a\": 1}" "{\"b\": 2}"))
  ;; => {"1st":{"a":1},"2nd":{"b":2}}

  ;; Example of destructuring an array
  (let [tf (polyglot.jq/script->transform-fn-vals
             ". as [$first_arg, $second_arg] | {} | .fa = $first_arg | .sa = $second_arg")]
    (tf "{\"a\": 1}" "{\"b\": 2}"))
  ;; => {"fa":{"a":1},"sa":{"b":2}}

  ;; Recursively collect all the 'a' attributes from an array of maps
  ;; and remove those records that value is null
  (let [tf (polyglot.jq/script->transform-fn-vals
             "[.. | .a?] | map(select(. != null))")]
    (tf "{\"a\": 1}" "{\"b\": 2}"))
  ;; => [1]

  ; Collect al; the paths from a nested map
  (let [tf (polyglot.jq/script->transform-fn-vals
             "select(objects)|=[.] | map( paths(scalars) ) | map( map(select(numbers)=\"[]\") | join(\".\")) | unique")]
    (tf "{\"a\": 1, \"b\": {\"c\": 3}}"))
  ;; => ["a","b.c"]

  ;; Collect all paths from an array
  (let [tf (polyglot.jq/script->transform-fn-vals
             "select(objects)|=[.] | map( paths(scalars) ) | map( map(select(numbers)=\"[]\") | join(\".\")) | unique")]
    (tf "[{\"a\": 1, \"b\": {\"c\": 3}}]"))
  ;; => ["[].a","[].b.c"]

  ;; With plain values
  (let [tf (polyglot.jq/script->transform-fn-vals
             ". as [$first_arg, $second_arg, $third_arg] | {} | .fa = $first_arg | .sa = $second_arg | .ta = $third_arg")]
    (tf "{\"a\": 1}" 11 "\"string\""))
  ;; => {"fa":{"a":1},"sa":11,"ta":"string"}

  ;; All types of values can be passed to the function as args
  (let [tf (polyglot.jq/script->transform-fn-vals
             ". as [$first_arg, $second_arg, $third_arg, $fourth_arg, $fifth_arg, $sixth_arg]
             | {}
             | .\"1st\" = $first_arg
             | .\"2dn\" = $second_arg
             | .\"3rd\" = $third_arg
             | .\"4th\" = $fourth_arg
             | .\"5th\" = $fifth_arg
             | .\"6th\" = $sixth_arg")]
    (tf "{\"a\": 1}" "my_string" 12 false nil {:b 10}))
  ;; => {"1st":{"a":1},"2dn":"my_string","3rd":12,"4th":false,"5th":null,"6th":{"b":10}}
  )
