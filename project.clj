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

  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-time "0.3.3"]

                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [log4j/log4j "1.2.14"]

                 [org.apache.mina/mina-core "2.0.3"]
                 [org.apache.ftpserver/ftplet-api "1.0.5"]
                 [org.apache.ftpserver/ftpserver-core "1.0.5"]

                 [com.amazonaws/aws-java-sdk "1.2.12" :exclusions [javax.mail/mail]]

                 ;Made a local copy because this one was not Clojure 1.3 compatible
                 ;[org.cloudhoist/thread-expr "1.1.0"]
                 [org.clojure/tools.macro "0.1.1"]

                 ]

  :dev-dependencies [[commons-net "2.2"]
                     [org.clojure/tools.trace "0.7.1"]]

  ;:aot [s3ftp.util.AbortException]
  )

