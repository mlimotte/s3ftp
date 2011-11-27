(ns s3ftp.aws.ses
  (:use s3ftp.aws.aws)
  (:import
   [com.amazonaws.services.simpleemail.model SendEmailRequest
                                             Destination
                                             Content
                                             Message
                                             Body]
   com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient))


;;; Simple Email Service (SES)

(def ses (memoize (fn [] (AmazonSimpleEmailServiceClient. (creds)))))

(defn mk-text-message [subj body]
  (Message. (Content. subj) (Body. (Content. body))))

(defn send-text-email [from to-coll subj body]
  (let [dest (Destination. to-coll)
        msg (mk-text-message subj body)
        req (SendEmailRequest. from dest msg)]
    (.sendEmail (ses) req)))

