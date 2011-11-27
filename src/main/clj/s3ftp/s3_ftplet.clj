(ns s3ftp.s3_ftplet
  (:use [s3ftp.util.test :only [pfr]])
  (:require
   [clojure.contrib [io :as io]
                    [string :as string]
                    [logging :as logging]]
   [s3ftp.util [io :as util-io] [test :as test]]
   [s3ftp.aws [s3 :as s3] [aws :as aws]]
   [clj-time [core :as time] [format :as timef] [coerce :as timec]])
  (:import [org.apache.ftpserver.ftplet DefaultFtpReply
                                        FtpletResult
                                        FtpException]
           java.io.IOException
           [com.amazonaws AmazonClientException
                          AmazonServiceException]))

;;; User

(def user-data {
  :bucket "allcitysoftware.file-service"
  :root-prefix "/test"
  :access-id aws/*macs-access-id*
  :secret-key aws/*macs-secret-key*
  })

;;; Path Translation and Management

;; Rules
;; - (user-data :root-prefix) always starts with "/" and ends with no "/"
;; - session "pwd" always starts and ends with "/"

(defn abs-with-trail-slash? [path] (re-find #"^/.*/$|^/$" path))
(defn abs-path? [path] (re-find #"^/" path))

(defn process-up-dir [path]
  {:pre [(abs-path? path)]}
  (let [s (string/replace-re #"[^/]*/\.\.(?:$|/)" "" path)]
    (if (string/blank? s) "/" s)))

(defn normalize-ftp-path [pwd path]
  {:pre [(abs-with-trail-slash? pwd)]}
  ; path can be relative or absolute
  (let [path (if (string/blank? path) "/" path)
        path (if (s3/leading-slash-test path) path (str pwd path))
        path (process-up-dir path)]
    path))

(defn as-s3-path [session ftp-path]
  {:pre [(abs-path? ftp-path)]}
  (let [path (str (user-data :root-prefix) ftp-path)
        path (s3/ensure-no-leading-slash path)]
    (logging/debug (str "as-s3-path " ftp-path " => " path))
    path))

(defn get-pwd [session]
  (or (.getAttribute session "pwd") "/"))

(defn set-pwd [session pwd]
  (.setAttribute session "pwd" pwd))

(defn req-arg-as-s3-path [session request-arg]
  (->> request-arg (normalize-ftp-path (get-pwd session)) (as-s3-path session)))


;;; Service

(defn creds [user-data]
  (aws/creds (:access-id user-data) (:secret-key user-data)))

(defn s3-client [user-data] (s3/s3-client (creds user-data)))

;;; FTP Protocol

(defn reply [code & msg] (DefaultFtpReply. code (apply str msg)))

(defn write-reply
  ([session code & msg]
   (.write session (apply reply code msg)))
  ([session args] (apply write-reply session args)))

(defn open-data-connection [session]
  (logging/debug (str "Opening data connection for " session))
  (.. session getDataConnection openConnection))

(defn close-data-connection [session]
  (try (-> session .getDataConnection .closeDataConnection)
    (catch Exception e)))

;;ftp reply codes
;502 Command not implemented.
;550 Requested action not taken. File unavailable, not found, not accessible
;451 Requested action aborted: local error in processing.

(defn xfer [session request data-f prelim-reply complete-reply xfer-fail-reply]
  (try
    (let [data (data-f) ; Can be a string or a stream
          _ (write-reply session prelim-reply)
          out (open-data-connection session)]
      (try
         (.transferToClient out session data)
         (write-reply session complete-reply)
         (catch Exception e
           (write-reply session (concat xfer-fail-reply [" " (test/causal-msgs e)]))
           (logging/error "xfer failed" e))
         (finally
           (if (instance? java.io.InputStream data)
               (.close data))
           (close-data-connection session))))
    (catch AmazonServiceException e
      ;file (status code 404) / bucket (status code 403) not found
      (write-reply session 550 (test/causal-msgs e))
      (logging/error "xfer failed" e))
    (catch AmazonClientException e
      ; network connection not working
      (write-reply session 451 (test/causal-msgs e))
      (logging/error "xfer failed" e))
    (catch IOException e
      ; Other FTP exceptions
      (write-reply session 425 "Cannot open data connection: " (test/causal-msgs e))
      (logging/error "xfer failed" e))
    (catch Exception e
      ; catch-all
      (write-reply session 425 "Unknown error: " (test/causal-msgs e))
      (logging/error (str (-> e .getName .getClass) ": " (test/causal-msgs e)))
      (logging/error "xfer failure." e)
      (throw e))))


;;; Download

; TODO maybe instead of (s3-client user-data), use (.getAttribute session "s3client")

; TODO is mget (and mput) implemented with a client side loop, or do I need server-side support?

(defn get-s3-stream [session path]
  (s3/get-stream (user-data :bucket) path (s3-client user-data)))

(defn open-download-stream [session request]
  (get-s3-stream session (req-arg-as-s3-path session (.getArgument request))))

(defn do-download [session request]
  (xfer session request
        (partial open-download-stream session request)
        [150 "Getting data connection."]
        [226 "Data transfer okay."]
        [551 "Data transfer failed."]))


;;; Upload

;[ INFO] 2011-05-05 00:57:43,988 [admin] [0:0:0:0:0:0:0:1%0] RECEIVED: STOR another.small.txt
;[ INFO] 2011-05-05 00:57:43,994 [admin] [0:0:0:0:0:0:0:1%0] File uploaded /another.small.txt
;[ INFO] 2011-05-05 00:57:43,995 [admin] [0:0:0:0:0:0:0:1%0] SENT: 150 File status okay; about to open data connection.
;[ INFO] 2011-05-05 00:57:43,995 [admin] [0:0:0:0:0:0:0:1%0] SENT: 226 Transfer complete.

; TODO:  STOR
;This command causes the server-DTP to accept the data transferred via the data connection and to store the data as a file at the server site. If the file specified in the pathname exists at the server site, then its contents shall be replaced by the data being transferred. A new file is created at the server site if the file specified in the pathname does not already exist.
;Server Replies
;501 Syntax error.
;550 Invalid path.
;550 Permission denied.
;150 Opening data connection.
;425 Cannot open the data connection.
;426 Data connection error.
;551 Error on output file.
;226 Transfer complete.


;;; Rename

;RNFR
;This command specifies the old pathname of the file which is to be renamed. This command must be immediately followed by a RNTO command specifying the new file pathname.
;Server Replies
;501 Syntax error.
;550 File unavailable.
;350 Requested file action pending further information.

(defn do-rename-from [session request]
  (write-reply session 502 "Command not implemented."))

;RNTO
;This command specifies the new pathname of the file specified in the immediately preceding RNFR command. Together the two commands cause a file to be renamed.
;Server Replies
;501 Syntax error.
;503 Cannot find the file which has to be renamed.
;553 Not a valid file name.
;553 No permission.
;250 Requested file action okay, file renamed.
;553 Cannot rename file.

(def do-rename-to do-rename-from)


;;; Meta-data

(defn do-meta-data [session request f]
  (try
    (let [path (req-arg-as-s3-path session (.getArgument request))
          meta (s3/get-meta-data (user-data :bucket) path (s3-client user-data))
          result (f meta)]
      (write-reply session 213 result))
    (catch AmazonServiceException e
      ;status code: 404  (file not found)
      ;status code: 403  (for bad bucket name)
      (write-reply session 550 (test/causal-msgs e))
      (logging/error "do-meta-data Failure" e))
    (catch AmazonClientException e
      ; network connection not working
      (write-reply session 451 (test/causal-msgs e))
      (logging/error "do-meta-data Failure" e))))

(defn do-size [session request] (do-meta-data session request s3/size))

(def *mdtm-datetime-format* (timef/formatter "yyyyMMddHHmmss.SSS"))
(defn do-last-modified [session request]
  (do-meta-data session request
                #(->> % s3/last-mod timec/from-date (timef/unparse *mdtm-datetime-format*))))


;;; Directories

(defn do-cwd [session request]
  (let [requested-wd (.getArgument request)
        newpwd (normalize-ftp-path (get-pwd session) requested-wd)
        newpwd (s3/ensure-trailing-slash newpwd)]
    (logging/debug (str "do-cwd " newpwd))
    (if (= (get-pwd session) newpwd)
      (write-reply session 250 "Command okay.")
      (if (s3/dir-exists? (user-data :bucket)
                          (as-s3-path session newpwd)
                          (s3-client user-data))
        (do (set-pwd session newpwd)
          (write-reply session 250 "Command okay."))
        (write-reply session 550 "No such directory.")))))

(defn do-pwd [session request]
  ;; home-dir works, but it is a String
  ;; TODO maybe I can make a new interface with get Data, and extend BasicUser to implement it
  (prn " PWD homedir " (.. session getUser getHomeDirectory))
  (write-reply session 257 "\"" (get-pwd session) "\" is current directory."))


; TODO mk dir
;[ INFO] 2011-05-05 00:57:19,793 [admin] [0:0:0:0:0:0:0:1%0] RECEIVED: MKD testdir
;[ INFO] 2011-05-05 00:57:19,794 [admin] [0:0:0:0:0:0:0:1%0] Directory create : admin - /testdir
;[ INFO] 2011-05-05 00:57:19,795 [admin] [0:0:0:0:0:0:0:1%0] SENT: 257 "/testdir" created.
; TODO how does S3hub do it in S3?  Simplest for me, would be to create a dummy file inside mkdir foo -> create foo/.cloud_file (and strip .cloud file from listings)
; s3hub creates this, for example: s3://allcitysoftware.file-service/foo2/subdir_$folder$


; TODO rm dir
; does the dir need to be empty first?


;;; Delete

; TODO delete file


;;; List

;(def *ls-datetime-format* (timef/formatter "MMM dd yyyy HH:mm:ss"))
(def *ls-datetime-format* (timef/formatter "MMM dd yyyy"))
(defn date-for-ls [d]
  (timef/unparse *ls-datetime-format* (timec/from-date d)))

;150 Here comes the directory listing.
;drwxrwsr-x    2 0        4009         4096 Nov 26  2008 42133_York_County
;-rw-rw-r--    1 8984     4009       709473 Nov 26  2008 tl_2008_42133_addr.zip
;;perm num-links owner group bytes date filename
;226 Directory send OK.
(defn get-listings [session request]
  (let [req-arg (.getArgument request)
        req-file (if (string/blank? req-arg) (get-pwd session) req-arg)
        possible-file-request? (and (-> req-arg string/blank? not)
                                    (-> req-arg last (not= \/)))
        path (req-arg-as-s3-path session req-file)
        obj-list (s3/ls (user-data :bucket) path (s3-client user-data))
        found? (-> obj-list .getObjectSummaries count pos?)
        ;; when count is 0, means empty directory, or ls on a file
        obj-list (if (and (not found?) possible-file-request?)
                     (s3/ls (user-data :bucket) path (s3-client user-data)
                            :use-trailing-slash false)
                     obj-list)
        files (.getObjectSummaries obj-list)
        file-f (juxt #(-> % .getOwner .getDisplayName)
                     #(-> % .getOwner .getDisplayName) ; group
                     #(.getSize %)
                     #(-> % .getLastModified date-for-ls)
                     #(string/replace-first-re #"^.*/" "" (.getKey %)))
        file-records (map file-f files)
        file-fmt-str "-rw-rw----    1 %-11s %-11s %12d %s %s\r\n"
        file-listings (map #(apply format file-fmt-str %) file-records)
        dirs (s3/dirs obj-list)
        dir-fmt-str (str "drwxrwx---   2 0          0                   0 "
                         (date-for-ls (java.util.Date.)) " %s\r\n")
        dir-listings (map #(format dir-fmt-str %) dirs)
        listings (concat dir-listings file-listings)]
    (if (-> listings count pos?)
        (apply str listings)
        (throw (AmazonServiceException. "File/dir not found.")))))

(defn do-ls [session request]
  (xfer session request
        (partial get-listings session request)
        [150 "Here comes the directory listing."]
        [226 "Directory send OK."]
        [551 "Directory listing failed."]))


;;; The FTPlet

(defn -beforeCommand [session request]
  (let [commands {:cwd do-cwd
                  :list do-ls
                  :mdtm do-last-modified
                  :pwd do-pwd
                  :retr do-download
                  :size do-size
                  :rnfr do-rename-from
                  :rnto do-rename-to
                  }
        command (-> request .getCommand .toLowerCase keyword commands)]
    (if command
        (do (command session request) (FtpletResult/SKIP)))))

(defn -afterCommand [session request reply]
  (let [commands {}
        command (-> request .getCommand .toLowerCase keyword commands)]
    (if command (command session request reply))))

(defn -init [ftpletContext] nil)

(defn -destroy [] nil)

(defn -onConnect [session] nil)

(defn -onDisconnect [session] nil)

(def the-ftplet
  (reify org.apache.ftpserver.ftplet.Ftplet
    (beforeCommand [this session request] (-beforeCommand session request))
    (afterCommand [this session request reply] (-afterCommand session request reply))
    (init [this ftpletContext] (-init ftpletContext))
    (destroy [this] (-destroy))
    (onConnect [this session] (-onConnect session))
    (onDisconnect [this session] (-onDisconnect session))
    ))

