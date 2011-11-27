(ns s3ftp.util.test
  (:use clojure.test)
  (:require [clojure.contrib [string :as string]]))

(defmacro is=
  "Use inside (deftest) to do an equality test."
  ([form-left form-right] `(is= ~form-left ~form-right nil))
  ([form-left form-right msg] `(try-expr ~msg (= ~form-left ~form-right))))

; for debugging
(defn pfr
  ([msg x] (prn msg x) (flush) x)
  ([x] (pfr "" x)))

(defn causal-msgs [e]
  (let [es (take-while (complement nil?) (iterate #(.getCause %) e))
        msgs (map #(str "[" % "]") es)]
    (string/join " caused by " msgs)))
