(ns s3ftp.aws.s3
  (:use s3ftp.aws.aws
        [clojure.contrib.def :only [defnk]])
  (:require [clojure.contrib [string :as string]])
  (:import com.amazonaws.services.s3.AmazonS3Client
           [com.amazonaws.services.s3.model GetObjectRequest
                                            ListObjectsRequest
                                            ObjectMetadata
                                            S3Object
                                            ]
   ))

;;; Service

(def *max-list-results* 2048)

(def s3-client (memoize (fn [creds] (AmazonS3Client. creds))))


;;; Helpers

(defn- test-apply [test-f apply-f s]
  (if (test-f s) s (apply-f s)))

(defn leading-slash-test [s] (= (subs s 0 1) "/"))

(defn ensure-leading-slash [s]
  (test-apply leading-slash-test #(str "/" %) s))
     
(defn ensure-no-leading-slash [s]
  (test-apply (complement leading-slash-test) #(string/drop 1 %) s))

(defn trailing-slash-test [s]
  (= (last s) \/))

(defn ensure-trailing-slash [s]
  (test-apply trailing-slash-test #(str % "/") s))

(defn ensure-no-trailing-slash [s]
  (test-apply (complement trailing-slash-test) string/chop s))


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

(defnk ls [bucket path client :use-trailing-slash true]
  (let [path (ensure-no-leading-slash path)
        path (if use-trailing-slash (ensure-trailing-slash path)
                 path)
	      req (ListObjectsRequest. bucket path nil "/" *max-list-results*)]
    (.listObjects client req)))

(defn strip-prefixes [obj-listing coll]
     (let [prefix-pattern (re-pattern (.getPrefix obj-listing))]
       (map #(string/replace-first-re prefix-pattern "" %) coll)))

;; a/b/c.txt
;; a/d.txt
;; (files ... "a") -> d.txt
;; (files ... "a/") -> d.txt
;; (dirs ... "a" -> "b"
;; (dirs ... "a/" -> "b"

(defn files
  ([obj-listing]
     (let [summaries (.getObjectSummaries obj-listing)
	   keys (map #(.getKey %) summaries)]
       (strip-prefixes obj-listing keys))))

(defn dirs
  ([obj-listing]
     (->> obj-listing
          .getCommonPrefixes
          (map #(string/butlast 1 %))
          (strip-prefixes obj-listing))))

(defn truncated? [obj-listing] (.isTruncated obj-listing))

(defn dir-exists? [bucket path client]
  (let [obj-listing (ls bucket path client)
        not-empty? (complement empty?)]
    (or (not-empty? (dirs obj-listing))
        (not-empty? (files obj-listing)))))


