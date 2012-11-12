(ns s3ftp.s3_ftplet_test
  (:use s3ftp.s3_ftplet
        clojure.test
        s3ftp.util.test
        ;clojure.contrib.mock.test-adapter
        )
  (:require [s3ftp.Main :as main]
            [s3ftp.util [ftp :as ftp] [io :as io]]))

(def local-anon-ftp-url "ftp://anonymous:blank@localhost:2221")
(def local-marc-test-ftp-url "ftp://test1:test@localhost:2221")
(def ftp-url local-marc-test-ftp-url)

(def start-server? true)
;(def start-server? false)

;;; Fixtures and Mocks

(defn start-server [f]
  (let [srvr (if start-server? (main/-main))]
    (f)
    (if srvr (.stop srvr))))

(def ^:dynamic *client*)
(defn ftp-connect [f]
  (with-redefs [*client* (ftp/mk-client ftp-url)]
    (f)
    (.disconnect *client*)))

(use-fixtures :once start-server)

(use-fixtures :each ftp-connect)

(defn mock-request [arg]
  (reify org.apache.ftpserver.ftplet.FtpRequest
    (getArgument [this] arg)))

(defn mock-session [attrs]
  (let [state (atom attrs)]
    (reify org.apache.ftpserver.ftplet.FtpSession
      (getAttribute [this name] (@state name))
      (setAttribute [this name value] (reset! state (assoc @state name value))))))


;;; The Tests

;(deftest normalize-ftp-path-test
;  (is (normalize-ftp-path "/" "") "/")
;  (is (normalize-ftp-path "/" nil) "/")
;  (is (normalize-ftp-path "/foo/" "/") "/")
;  (is (normalize-ftp-path "/" "foo") "/foo")
;  (is (normalize-ftp-path "/foo/" "bar/baz") "/foo/bar/baz")
;  (is (normalize-ftp-path "/foo/" "/bar") "/bar")
;  (is (normalize-ftp-path "/foo/" "/bar/baz") "/bar/baz")
;  (is (normalize-ftp-path "/foo/" "/bar/baz/") "/bar/baz/")
;  (is (thrown? AssertionError (normalize-ftp-path "/foo" "bar")))
;  (is (thrown? AssertionError (normalize-ftp-path "foo/" "bar"))))
;
;(deftest process-up-dir-test
;  (is= (process-up-dir "/foo/../") "/")
;  (is= (process-up-dir "/../") "/")
;  (is= (process-up-dir "/foo/") "/foo/")
;  (is= (process-up-dir "/foo/bar/") "/foo/bar/")
;  (is= (process-up-dir "/foo/bar/../") "/foo/")
;  (is= (process-up-dir "/foo/bar/..") "/foo/")
;  (is= (process-up-dir "/foo/bar") "/foo/bar")
;  (is (thrown? AssertionError (process-up-dir "foo/bar/../"))))

(deftest do-cd-test
  (testing "cd"
    (ftp/change-dir *client* "/")
    (ftp/change-dir *client* "foo")
    (is= (ftp/pwd *client*) "/foo/")))

;(deftest do-pwd-test
;  (testing "pwd"
;    (is= (ftp/pwd client) "/")
;;    (is= (ftp/pwd (ftp/mk-client (str ftp-url "/foo"))) "/foo/")
;    ))

;(deftest do-download-test
;  ; with absolute path
;  (is= (ftp/with-download-stream *client* "/foo/small.txt" #(io/copy-to-string %))
;       "line1\nline2\n")
;  ; with relative path
;  (ftp/change-dir *client* "foo")
;  (is (not (empty? (ftp/with-download-stream *client* "bar" #(io/copy-to-string %)))))
;  ; Test failure condition (file does not exist)
;  (is (ftp/with-download-stream *client* "no.file" #(io/copy-to-string %))
;      550)
;  )
;
;(deftest do-size-test
;  ; This also tests the error catching in do-meta-data
;  ; positive tests
;  (expect [write-reply (has-args [(complement nil?) 213 1958] (times once))]
;    (do-size (mock-session {"pwd" "/"}) (mock-request "foo/bar")))
;  (expect [write-reply (has-args [(complement nil?) 213 1958] (times once))]
;    (do-size (mock-session {"pwd" "/foo/"}) (mock-request "bar")))
;  ; bad file name
;  (expect [write-reply (has-args [(complement nil?) 550 #(re-find #"404" %)] (times once))]
;    (do-size (mock-session {"pwd" "/"}) (mock-request "no.file")))
;  ; Bad bucket name
;  (with-bindings {#'s3ftp.s3_ftplet/user-data (assoc s3ftp.s3_ftplet/user-data :bucket "XXX")}
;    (expect [write-reply (has-args [(complement nil?) 550 #(re-find #"403" %)] (times once))]
;      (do-size (mock-session {"pwd" "/"}) (mock-request "foo/bar"))))
;  )
;
;(deftest ftp-do-size-test
;  (is= (ftp/size *client* "foo/bar") [213 1958]))
;
;(deftest do-last-modified-test
;  (expect [write-reply (has-args [(complement nil?) 213 "20110428184125.000"] (times once))]
;    (do-last-modified (mock-session {"pwd" "/"}) (mock-request "foo/bar")))
;  )
;
;(deftest do-ls-test
;  ; current directory
;  (is= (map (fn [m] [(:name m) (:size m) (:type m)]) (ftp/ls-beans *client*))
;       [["foo" 0 1] ["foo2" 0 1] ["ymdh.txt" 1958 0]])
;
;  ; abs path, directory
;  (is= (map (fn [m] [(:name m) (:size m) (:type m)]) (ftp/ls-beans *client* "/foo"))
;       [["bar" 1958 0] ["small.txt" 12 0]])
;
;  ; test relative path
;  (ftp/change-dir *client* "foo2")
;  (is= (map (fn [m] [(:name m) (:size m) (:type m)]) (ftp/ls-beans *client* "subdir"))
;       [["small2.txt" 12 0]])
;  (ftp/change-dir *client* "")
;
;  ; A explicit space in the filename arg is respected, and is not equivalent to the name w/out the space (as per std FTP)
;  (is= (map (fn [m] [(:name m) (:size m) (:type m)]) (ftp/ls-beans *client* " foo"))
;       [])
;
;  ; ls of a filename
;  (is= (map (fn [m] [(:name m) (:size m) (:type m)]) (ftp/ls-beans *client* "foo/bar"))
;    [["bar" 1958 0]])
;
;  ; test an error condition (not using client for this test, so we can check the reply directly)
;  (expect [write-reply (has-args [(complement nil?) 550] (times once))]
;    (do-ls (mock-session {"pwd" "/"}) (mock-request "no.file")))
;  )

