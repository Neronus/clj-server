;   Copyright (c) Christian von Essen. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure-server.server
  (:refer-clojure)
  (:use	clojure-server.main clojure.contrib.trace)
  (:import (posix File)
		   (clojure_server ChunkOutputStream)
		   (java.io InputStreamReader OutputStreamWriter)
		   (java.net ServerSocket InetAddress)
		   (java.util.concurrent ThreadPoolExecutor TimeUnit LinkedBlockingQueue)))

(defn- lazy-slurp
  "Read lazyly from anything supporting a .read method (e.g. Streams),
which return -1 on error, and something coerceable into a character
otherwise"
  [in] (letfn [(this [] (lazy-seq
						  (try
						   (let [c (.read in)]
							 (if (= c -1)
							   (do (.close in) nil)
							   (cons (char c) (this))))
						   (catch Exception e (.close in) (throw)))))]
		 (this)))

(defn read-to-null
  "read to the next null byte from the reader."
  [reader]
  (loop [result []]
	(let [c (.read reader)]
	  (cond
		(= c 0)
		(apply str result)
		(= c 1)
		(throw Exception "Wrong argument count: Reader died prematurely.")
		true
		(recur (conj result (char c)))))))

(defn- read-command-line-parms
  "Reads n null-terminated strings from the reader."
  [reader n]
  (loop [result [], i 0]
	(if (< i n)
	  (recur (conj result (str (read-to-null reader))) (+ i 1))
	  result)))

(defn- set-property!
  "Set the system property"
  [key value]
  (let [properties (System/getProperties)]
	(.setProperty properties (str key) (str value))))

(defn- is-relative?
  "Returns false iff the first character of the sequence given as path
is equals to /"
  [path]
  (not (= (first path) \/)))

(defn- check-modflags
  "Returns false iff anyone but the user can do anything with the file.
Otherwise true is returned"
  [path]
  (let [f (File. path)
		mode (-> f .getStat .mode)]
	(=
	 ;; only the lowest 6 bits are interesting, and they shouldn't be set
	 (bit-and 0077 mode)
	 0)))

(defn auth
  "Works the authorization procedure using in and out for communication, which
should be Reader and Writer instannces.
If authorization was successful, true is returned, false otherwise."
  [in out]
  (letfn ((dont-accept [] (.write out (int 0)) (.flush out) false)
		  (accept [] (.write out (int 1)) (.flush out) true)
		  (check-path [path]
			(loop [file-seq (lazy-slurp (java.io.FileReader. path)), sock-seq (lazy-slurp in)]
			  (cond (= () file-seq) true, (= (first file-seq) (first sock-seq)) (recur (rest file-seq) (rest sock-seq)), true false))))
	(let [path (read-to-null in)]
	  (if (is-relative? path)
		(dont-accept)
		(try
		 (if-not (check-modflags path)
		   (dont-accept)
		   (do
			 (accept)
			 (if (not (check-path path))
			   (dont-accept)
			   (accept))))
		 (catch Exception e (dont-accept)))))))


(defn- reciever
  "Called by the server main loop. This instance calls the authorizatoin procedure
and, if successful, starts the main repl on the given input and output streams."
  [input output]
  (when (auth input output)
	(let [pwd (read-to-null input)
		  n-args (Integer/parseInt (read-to-null input))
		  args (seq (read-command-line-parms input n-args))]
	  (binding [*in* (clojure.lang.LineNumberingPushbackReader.
					  (InputStreamReader. input))
				*out* (OutputStreamWriter. (ChunkOutputStream. output 1))
				*err* (OutputStreamWriter. (ChunkOutputStream. output 2))]
		(set-property! "user.dir" pwd)
		;; This binding is not used, as clojure's core doesn't provide an exit function
		;; by default
		;;(binding [clojure.core/exit (fn [& args] (.stop (Thread/currentThread)))]
		(apply clojure-server.main/server-main args)))))

(defn create-server
  "Creates a server socket on the given port with the given backlog, which defaults
to 10. The returned socket will listen on 127.0.0.1."
  ([port]
	 (create-server port 10))
  ([port backlog]
	 (let [socket (ServerSocket. port backlog
								 (InetAddress/getByAddress (into-array (Byte/TYPE) [(byte 127) (byte 0) (byte 0) (byte 1)])))]
	   socket)))


(defn server-loop
  "accepts connections on the socket, and launches the repl on incoming
connections. Doesn't return."
  [socket minThreads maxThreads]
  (let [exec (ThreadPoolExecutor. minThreads maxThreads 5 TimeUnit/MINUTES
								  (LinkedBlockingQueue.))]
	(loop []
		(let [csocket (.accept socket)
			  gensym-ns *gensym-ns*]
		  (.submit exec #^Callable #(with-open [csocket csocket]
									  (binding [*gensym-ns* gensym-ns]
										(reciever (.getInputStream csocket) (.getOutputStream csocket))))))
		(recur))))
