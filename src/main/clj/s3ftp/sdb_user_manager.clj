(ns s3ftp.sdb_user_manager
  (:use [s3ftp.util.test :only [pfr]])
  (:require
    [s3ftp.aws [aws :as aws]]
    [clojure.tools.logging :as log])
  (:import
    [org.apache.ftpserver.ftplet User
                                 UserManager
                                 AuthenticationFailedException]
    [org.apache.ftpserver.usermanager AnonymousAuthentication
                                      UsernamePasswordAuthentication
                                      Md5PasswordEncryptor]
    [org.apache.ftpserver.usermanager.impl BaseUser
                                           WritePermission
                                           ConcurrentLoginPermission
                                           TransferRatePermission]))


;;; Constants

(def max-login-number 2)
(def max-login-per-ip 2)

;;; Authentication

(defn auth-anonymous? [auth] (instance? AnonymousAuthentication auth))

(defn auth-creds [auth]
  {:pre (instance? UsernamePasswordAuthentication auth)}
  [(.getUsername auth) (.getPassword auth)])

;;; Password

(def pw-encryptor (Md5PasswordEncryptor.))

(defn encrypt [pw] (.encrypt pw-encryptor pw))

(defn pw-matches? [check-pw stored-pw] (.matches pw-encryptor check-pw stored-pw))

;;; User

(defn create-user [{:keys [username password home-dir enabled]}]
  (let [authorities [(WritePermission.)
                     (ConcurrentLoginPermission. max-login-number max-login-per-ip)
                     (TransferRatePermission. 0 0)]]
    (doto (BaseUser.)
          (.setName username)
          (.setPassword password)
          (.setHomeDirectory home-dir)
          (.setEnabled enabled)
          ; Use the ftp default MaxIdleTimeout (not per-user)
          (.setAuthorities authorities))))

;;; User Manager

(defn get-user [username]
  ; TODO SDB lookup
  ; TODO get home dir - the same as root?  (*** Can I stuff arbitrary user data in here???)
  ; TODO ftplet needs S3 creds, buckets and root-prefix
  {:username username
   :password "test"
   :enabled true


   ; TODO this is ignored -- what should it mean in conjunction with root-prefix?  Maybe use home-dir instead of root-prefix?
   :home-dir "/does-this string mean anything?"


   :bucket "allcitysoftware.file-service"
   :root-prefix "/test"
   :access-id aws/macs-access-id
   :secret-key aws/macs-secret-key})

(defn authenticate [user pw]
  ; TODO use pw-encryptor
  (= (:password user) pw))

(defn -authenticate [auth]
  (log/trace "AUTHETICATE " auth)
  (if (auth-anonymous? auth)
      (throw (AuthenticationFailedException. "Anonymous access is not allowed.")))
  (let [[usern pw] (auth-creds auth)
        user (get-user usern)
        authed? (authenticate user pw)]
    (if authed?
        (create-user user)
        (AuthenticationFailedException. "Username or password not recognized."))))

(defn -does-exist [username]
  (not (nil? (get-user username))))

(def sdb-user-manager
  (reify UserManager
    (authenticate [this auth] (-authenticate auth))
    (doesExist [this username] (-does-exist username))
    (getAdminName [this] "admin")
    (isAdmin [this username] (= "admin" username))
    (getUserByName [this username] (-> username get-user (dissoc :password) create-user))))


