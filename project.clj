(defproject s3-ftplet "1.0.0-SNAPSHOT"
  :description        "FTP to S3 Gateway"

  :source-path        "src/main/clj"
  :test-path          "src/test/clj"
  :resources-path     "src/main/resources"

  ;:warn-on-reflection true

  :javac-debug        "true"
  :java-options       {:debug "true"}
  :java-source-path   "src/main/java"
  :java-fork          "true"

  :main               s3ftp.Main

  :repositories {"sonatype" "http://oss.sonatype.org/content/repositories/releases"}

  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]

                 [org.apache.mina/mina-core "2.0.3"]

                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [log4j/log4j "1.2.14"]

                 [org.apache.ftpserver/ftplet-api "1.0.5"]
                 [org.apache.ftpserver/ftpserver-core "1.0.5"]

                 ; TODO upgrade AWS sdk
                 [com.amazonaws/aws-java-sdk "1.1.7.1" :exclusions [javax.mail/mail]]
                 [org.cloudhoist/thread-expr "1.0.0"]
                 [clj-time "0.3.0"]

                 [slamhound "1.1.1"]
                 ]

  :dev-dependencies [[slamhound "1.1.1"]
                     [lein-difftest "1.3.1"]
                     [commons-net "2.2"]
		                 ]

  ;:aot [s3ftp.util.AbortException]
  )

