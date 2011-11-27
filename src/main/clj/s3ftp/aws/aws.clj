(ns s3ftp.aws.aws
  (:import
   [com.amazonaws.auth BasicAWSCredentials]))

;; AWS Credentials for mlimotte@allcitysoftware.com
(def *macs-access-id* "AKIAIVMJVOB74JOIC2SA")
(def *macs-secret-key* "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")

(def creds (memoize (fn [access-id secret] (BasicAWSCredentials. access-id secret))))

;(defn creds [] (BasicAWSCredentials. *access-key* *secret-key*))

;(defn creds [] (creds# *access-key* *secret-key*))

(def *macs-creds* (creds *macs-access-id* *macs-secret-key*))
