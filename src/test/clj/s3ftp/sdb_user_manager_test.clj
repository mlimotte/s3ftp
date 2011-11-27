(ns s3ftp.sdb_user_manager_test
  (:use s3ftp.s3_ftplet
        clojure.test
        s3ftp.util.test
        clojure.contrib.mock.test-adapter)
  (:require [s3ftp.Main :as main]
            [s3ftp.util [ftp :as ftp] [io :as io]])
  (:import org.apache.commons.net.ftp.FTPConnectionClosedException))

(def *start-server* true)
;(def *start-server* false)

(def *ftp-url* "ftp://test1:test@localhost:2221")

;;; Fixtures and Mocks

(defn start-server [f]
  (let [srvr (if *start-server* (main/-main))]
    (f)
    (if srvr (.stop srvr))))

(use-fixtures :once start-server)


;;; The Tests

; Test max logins for one user
(deftest max-logins-test
  (let [c1 (ftp/mk-client *ftp-url*)
        c2 (ftp/mk-client *ftp-url*)]
    (is (thrown-with-msg? FTPConnectionClosedException #"421" (ftp/mk-client *ftp-url*)))
    (.disconnect c1)
    (.disconnect c2)))

