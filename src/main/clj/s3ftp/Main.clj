(ns s3ftp.Main
  (:gen-class)
  (:use [s3ftp.s3_ftplet :only [the-ftplet]]
        [s3ftp.core :only [ifp]])
  (:require
   clojure.java.io
   [s3ftp [sdb_user_manager :as um]])
  (:import
    [org.apache.ftpserver FtpServerFactory ConnectionConfigFactory]
    org.apache.ftpserver.listener.ListenerFactory
    org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
    net.fileservice.S3Ftplet))

(defn connection-config-factory []
  (doto (ConnectionConfigFactory.)
    (.setAnonymousLoginEnabled false)
    (.setMaxAnonymousLogins 20)
    (.setMaxLogins 20)))

(defn connection-config []
  (.createConnectionConfig (connection-config-factory)))

(defn properties-user-manager-factory [fname]
  (doto (PropertiesUserManagerFactory.)
        (.setFile (clojure.java.io/file fname))))

(defn properties-user-manager [& args]
  (.createUserManager (apply properties-user-manager-factory args)))

(defn listener-factory [port]
  (let [ssl nil]
    ;; define SSL configuration
    ;SslConfigurationFactory ssl = new SslConfigurationFactory();
    ;ssl.setKeystoreFile(new File("src/test/resources/ftpserver.jks"));
    ;ssl.setKeystorePassword("password");
    ;; set the SSL configuration for the listener
    ;factory.setSslConfiguration(ssl.createSslConfiguration());
    ;??? factory.setImplicitSsl(true);
    (doto (ListenerFactory.)
          (.setPort port)
          (.setIdleTimeout 60))))

(defn listener [& args]
  (.createListener (apply listener-factory args)))

(defn server-factory [lstnr user-mgr conn-config ftplets]
  (doto (FtpServerFactory.)
        (.addListener "default" lstnr)
        (.setUserManager user-mgr)
        (.setConnectionConfig conn-config)
        (.setFtplets ftplets)))

(defn server [& args]
  (.createServer (apply server-factory args)))

;; TODO limit: ... org.apache.ftpserver.listener.nio.FtpLoggingFilter - SENT: 211-Extensions supported

;;; Main
(defn -main [& args]
  (let [;user-mgr (properties-user-manager "users.properties")
        user-mgr um/sdb-user-manager
        lstnr (listener 2221)
        conn-config (connection-config)
        ; ftplets must be a Java HashMap, so that the ftpserver can clear() it at shutdown
        ftplets (doto (java.util.HashMap.) (.putAll {"s3" the-ftplet}))
        srvr (server lstnr user-mgr conn-config ftplets)]
    (binding [*assert* true]
      (.start srvr))
    ; return the server in case this method is called from somewhere else that might also want to stop the server (e.g. unit tests)
    srvr))
