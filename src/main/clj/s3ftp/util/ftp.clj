(ns s3ftp.util.ftp
  (:require [clojure.contrib [logging :as logging]])
  (:import [org.apache.commons.net.ftp FTP FTPClient FTPReply]
           [java.net URL])
  (:use [clojure.contrib.str-utils :as str]))

;; simple wrappers for jakarta's ftp client
;; Based on code Kyle Burton: https://github.com/kyleburton/sandbox/blob/master/clojure-utils/kburton-clojure-utils/src/main/clj/com/github/kyleburton/sandbox/ftp.clj

(defmulti open "Open an ftp connection." class)

(defmethod open String [s]
  (open (java.net.URL. s)))

(defmethod open URL [url]
  (let [client (FTPClient.)]
    (.connect client
              (.getHost url)
              (if (= -1 (.getPort url))
                (.getDefaultPort url)
                (.getPort url)))
    client))

(defn mk-client [url]
  (let [u# (URL. url)
        client# (open u#)
        ]
    (if (.getUserInfo u#)
      (let [[uname# pass#] (.split (.getUserInfo u#) ":" 2)]
        (.login client# uname# pass#)))
    (.changeWorkingDirectory client# (.getPath u#))
    (.setFileType client# FTP/BINARY_FILE_TYPE)
    client#
    ))

;(def client (memoize mk-client))

;;;

(defn size [^FTPClient client path]
  (.sendCommand client "SIZE" path)
  [(.getReplyCode client)
   (-> client .getReplyString (.split "\\s+") second Long/parseLong)])

;;; Working Directory

(defn change-dir [^FTPClient client req-wd]
  (.changeWorkingDirectory client req-wd))

(defn pwd [^FTPClient client]
  (.printWorkingDirectory client))

;;; list

; TODO alt:  FTPFile[] 	listFiles(String pathname)
(defn ls-names
  ([^FTPClient client] (map #(.getName %) (.listFiles client)))
  ([^FTPClient client pathname] (map #(.getName %) (.listFiles client pathname))))

(defn ls-beans [^FTPClient client & [pathname]]
  (map #(bean %) (.listFiles client pathname)))

;;; Download

;(defn download-as-stream [^FTPClient client path]
;  (.retrieveFileStream client path))

(defn with-download-stream [^FTPClient client path f]
  (let [in (.retrieveFileStream client path)
        rc (.getReplyCode client)]
    (if (FTPReply/isPositivePreliminary rc)
        (let [result (f in)]
          (.close in)
          (if (.completePendingCommand client)
              result
              (logging/error "File transfer failed.")))
        (do (logging/error (str "File transfer failed with rc = " rc))
            rc))))

;;; Wrapped ftp calls, each block uses a new connection
;DISABLED FOR NOW! Since (list-all), etc are written to be used inside with-ftp and not passed a client object.
;                  Ideally, these would be multimethdos and would decide between a Url/String impl, and a FtpClient impl.
;
;(defmacro with-ftp [[client url & extra-bindings] & body]
;  `(let [u# (URL. ~url)
;         ~client (open u#)
;         res# (atom nil)
;         ~@extra-bindings]
;     (if (.getUserInfo u#)
;       (let [[uname# pass#] (.split (.getUserInfo u#) ":" 2)]
;         (.login ~client uname# pass#)))
;     (.changeWorkingDirectory ~client (.getPath u#))
;     (.setFileType ~client FTP/BINARY_FILE_TYPE)
;     (reset! res#
;            (do
;              ~@body))
;     (.disconnect ~client)
;     @res#))
;
;(defn list-all [url]
;  (with-ftp [client url]
;    (map #(.getName %) (.listFiles client))))
;
;(defn list-files [url]
;  (with-ftp [client url]
;    (map #(.getName %) (filter #(.isFile %) (.listFiles client)))))
;
;(defn list-directories [url]
;  (with-ftp [client url]
;    (map #(.getName %) (filter #(.isDirectory %) (.listFiles client)))))
;
;(defn retrieve-file [url fname & [local-file]]
;  (with-ftp [client url]
;    (if local-file
;      (with-open [outstream (java.io.FileOutputStream. local-file)]
;        (.retrieveFile client fname outstream))
;      (.retrieveFile client fname))))
;
;;(defn change-dir [url path]
;;  (with-ftp [client url]
;;    (.changeWorkingDirectory client path)))
;
;;; (retrieve-file "ftp://anonymous:user%40host.com@ftp2.census.gov/geo/tiger/TIGER2008/42_PENNSYLVANIA/" "tl_2008_42_place.zip" "/home/mortis/tl_2008_42_place.zip")
;;; (list-files "ftp://anonymous:user%40host.com@ftp2.census.gov/geo/tiger/TIGER2008/42_PENNSYLVANIA/")
