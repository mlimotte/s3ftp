(ns s3ftp.util.AbortException
  (:gen-class
    :extends RuntimeException
    :init init
    :constructors {[String] [String]
                   [String Throwable] [String Throwable]
                   }))

(defn -init
  ; result: [ [super-constructor-args] state ]
  ([msg] [[msg] nil])
  ([msg cause] [[(or msg (.getMessage cause)) cause] nil]))

