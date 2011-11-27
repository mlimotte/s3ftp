(ns s3ftp.util.io
  (:require [clojure.contrib [io :as io]])
  (:import java.io.StringWriter)
  )

(defn resource-as-stream [rel-path]
  (.getResourceAsStream (clojure.lang.RT/baseLoader) rel-path))

(defn copy-to-string [in]
  (let [buf (StringWriter.)]
    (io/copy in buf)
    (.toString buf)))


