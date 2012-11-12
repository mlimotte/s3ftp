(ns s3ftp.aws.s3
  (:use s3ftp.aws.aws
        s3ftp.pallet.thread-expr)
  (:require [clojure [string :as string]]
            [clojure.tools.trace :as trace]
            [clojure.tools.logging :as log]
            )
  (:import com.amazonaws.services.s3.AmazonS3Client
           [com.amazonaws.services.s3.model GetObjectRequest
                                            ListObjectsRequest
                                            ObjectMetadata
                                            S3Object]))

;;; Service

(def max-list-results 2048)

;(def s3-client (memoize (fn [creds] (AmazonS3Client. creds))))
(trace/deftrace s3-client [creds] (AmazonS3Client. creds))


;;; Helpers

(defn chop [s]
  (cond
    (= s "") ""
    (nil? s) nil
    :else    (subs s 0 (-> s count dec))))

(defn leading-slash? [s] (= (subs s 0 1) "/"))

(defn trailing-slash? [s] (= (last s) \/))

(defn ensure-leading-slash [s]
  (if (leading-slash? s) s (str "/" s)))

(defn ensure-no-leading-slash [s]
  (if (leading-slash? s) (subs s 1) s))

(defn ensure-trailing-slash [s]
  (if (trailing-slash? s) s (str s "/")))

(defn ensure-no-trailing-slash [s]
  (if (trailing-slash? s) (chop s) s))


;;; Download

;The underlying HTTP connection cannot be closed until the user finishes reading the data and closes the stream. Callers should therefore:
;    * Use the data from the input stream in Amazon S3 object as soon as possible,
;    * Close the input stream in Amazon S3 object as soon as possible.
;Throws:
;    AmazonClientException - If any errors are encountered in the client while making the request or handling the response.
;    AmazonServiceException - If any errors occurred in Amazon S3 while processing the request.
(defn get-stream [bucket path client]
  (let [s3obj (.getObject client (GetObjectRequest. bucket path))]
    (if s3obj (.getObjectContent s3obj))))

;;; Meta-data

;throws AmazonClientException,
;       AmazonServiceException
(defn get-meta-data [bucket path client]
  (.getObjectMetadata client bucket path))

;TODO multi-methods for ^S3Object
(defn size [^ObjectMetadata s3ObjMeta] (.getContentLength s3ObjMeta))

(defn last-mod [^ObjectMetadata s3ObjMeta] (.getLastModified s3ObjMeta))

;;; List

;Always check the ObjectListing.isTruncated() method to see if the returned listing is complete or if additional calls are needed to get more results. Alternatively, use the listNextBatchOfObjects(ObjectListing) method as an easy way to get the next page of object listings.

;;dir of "test/" - "/test/foo/" as a common prefix, and "test/ymdh.txt as key of first S3ObjectSummary
;;dir with no trailing slash - yields "dir/" as a common prefix
;;bad bucket - AmazonS3Exception 
;;non-existent key - empty results
;;bad secret - AmazonS3Exception
;;bad access-id - AmazonS3Exception

;(defnk ls [bucket path client :use-trailing-slash true]
;  (let [path (ensure-no-leading-slash path)
;        path (if use-trailing-slash (ensure-trailing-slash path)
;                 path)
;	      req (ListObjectsRequest. bucket path nil "/" max-list-results)]
;    (.listObjects client req)))

(trace/deftrace ls [bucket path client & {:keys [use-trailing-slash] :or [true]}]
  (let [
;        path (ensure-no-leading-slash path)
;        path (if use-trailing-slash
;               (ensure-trailing-slash path)
;               path)
        path (-> path
               ensure-no-leading-slash
               (if-> use-trailing-slash
                 ensure-trailing-slash
                 identity)
        )
	      _ (log/debug (format "ListObjectsRequest. %s %s %s %s %s" bucket path nil "/" max-list-results))
        req
          (try
            (ListObjectsRequest. bucket path nil "/" (Integer. max-list-results))
            (catch Exception e (log/debug "MARC" e)))
        ]
    (log/debug (str "ls path3 " path))
    (log/debug (str "ls req " req))
    (.listObjects client req)))

(trace/deftrace strip-prefixes [obj-listing coll]
  (-> obj-listing
    .getPrefix
    ;re-pattern -- these are all literal strings, not regexes
    (arg-> [prefix-pattern] #(string/replace-first % prefix-pattern ""))
    (map coll)))

;; a/b/c.txt
;; a/d.txt
;; (files ... "a") -> d.txt
;; (files ... "a/") -> d.txt
;; (dirs ... "a" -> "b"
;; (dirs ... "a/" -> "b"

(trace/deftrace files
  ([obj-listing]
     (let [summaries (.getObjectSummaries obj-listing)
	   keys (map #(.getKey %) summaries)]
       (strip-prefixes obj-listing keys))))

(trace/deftrace dirs
  ([obj-listing]
     (->> obj-listing
          .getCommonPrefixes
          (map chop)
          (strip-prefixes obj-listing))))

(defn truncated? [obj-listing] (.isTruncated obj-listing))

(trace/deftrace dir-exists? [bucket path client]
  (let [obj-listing (ls bucket path client)]
    (or (seq (dirs obj-listing))
        (seq (files obj-listing)))))


