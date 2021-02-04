(ns polyglot.sci
  (:require [sci.core :as sci]
            [core.json :as json])
  (:import (java.util UUID Date)))

(defn sci-compile [^String script]
  (sci/eval-string script
                   {:classes {'UUID UUID
                              'Date Date}}))

(defn script->transform-fn [script]
  (let [transform-fn (sci-compile script)]
    (fn [^String value]
      (json/encode (transform-fn (json/decode value))))))

(defn apply-sci-transform [m script]
  (let [transform-f (sci-compile script)]
    (transform-f m)))

(defn string->string [s transform-script]
  (json/encode (apply-sci-transform (json/decode s) transform-script)))

; specializations for multi argument functions
(defn script->transform-fn-for-boost [script]
  (let [transform-fn (sci-compile script)]
    (fn [& args]
      (apply transform-fn args))))

(comment
  ((polyglot.sci/script->transform-fn-for-boost "(fn [& args] args)") 1 2 3)

  ((polyglot.sci/script->transform-fn-for-boost "(fn [& args] (Date.))") 1 2 3))
