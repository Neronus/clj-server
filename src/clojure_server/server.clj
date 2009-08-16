;   Copyright (c) Christian von Essen. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure-server.server
  (:refer-clojure)
  (:import (clojure_server ChunkOutputStream ExitException)
		   (java.io InputStreamReader PrintWriter)
		   (java.net ServerSocket InetAddress)
		   (java.util.concurrent ThreadPoolExecutor TimeUnit LinkedBlockingQueue))
  (:use clojure-server.main
		clojure.contrib.trace
		clojure.contrib.duck-streams))

(def MSG-STDOUT 0)
(def MSG-STDERR 1)
(def MSG-EXIT 2)
(def MSG-PATH 3)
(def MSG-OK 4)
(def MSG-ERR 5)

(def *security-file*)

(defmacro bytearray->int [n array]
  (let [array-sym (gensym "array")]
	(letfn [(inner [i]
				   (let [ai (- n i)]
					 (if (= 1 i)
					   `(bit-and (int (aget ~array-sym ~ai)) 255)
					   `(bit-or
						 (bit-shift-left (bit-and (int (aget ~array-sym ~ai)) 255) ~(* (- i 1) 8))
								   ~(inner (dec i))))))]
	  `(let [~array-sym ~array]
		 ~(inner n)))))

(defn read-byte
  [in]
  (.read in))

(defn read-int
  [in]
  (let [buffer (make-array Byte/TYPE 4)]
	(if (= (.read in buffer) 4)
	  (bytearray->int 4 buffer)
	  (throw (Exception. "Error while reading integer.")))))
		
(defn read-sized-data
  "Reads from in first an integer and then the number of bytes specified. Returns
the data as a byte array."
  ([in length]
	 (let [array  (make-array Byte/TYPE length)]
	   (loop [read 0]
		 (if (= read length)
		   array
		   (let [new (.read in array read (- length read))]
			 (if (= new -1)
			   (throw (Exception. "Error while reading from socket"))
			   (recur (+ new read))))))))
  ([in]
	 (let [length (read-int in)]
	   (read-sized-data in length))))

(defn read-sized-string
  ([in]
	 (let [array (read-sized-data in)]
	   (String. array)))
  ([in length]
	 (let [array (read-sized-data in length)]
	   (String. array))))

(defn- read-command-line-parms
  "Reads n null-terminated strings from the reader."
  [in n]
  (doall (for [i (range n)] (read-sized-string in))))

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

(defn- send-int
  "Send an 4 byte integer down the stream"
  [out int]
  (println int)
  (.write out (bit-shift-right int 24))
  (.write out (bit-shift-right int 16))
  (.write out (bit-shift-right int 8))
  (.write out int))

(defn send-message
  ([out message data]
	 (let [out (ChunkOutputStream. out message)]
	   (.write out data)
	   (.flush out)))
  ([out message]
	 (send-message out message (make-array Byte/TYPE 0))))

(defn auth
  "Works the authorization procedure using in and out for communication, which
should be Reader and Writer instannces.
If authorization was successful, true is returned, false otherwise."
  [in out]
  (letfn ((dont-accept [] (send-message out MSG-ERR) (.flush out) false)
		  (accept [] (send-message out MSG-OK) (.flush out) true)
		  (check-path [path]
			(send-message out MSG-PATH (.getBytes path))
			(let [content (.getBytes (slurp* path))
				  recieved-content (read-sized-data in (count content))]
			  (and (= (count content) (count recieved-content))
				   (loop [i 0]
					 (cond
					   (>= i (count content))
					   true
					   (= (aget content i) (aget recieved-content i))
					   (recur (unchecked-inc i))
					   :else
					   false))))))
	(let [path *security-file*]
	  (if (is-relative? path)
		(dont-accept)
		(try
		 (do
		   (if (not (check-path path))
			 (dont-accept)
			 (accept)))
		 (catch Exception e (dont-accept)))))))

(defn- reciever
  "Called by the server main loop. This instance calls the authorizatoin procedure
and, if successful, starts the main repl on the given input and output streams."
  [input output]
  (when (auth input output)
	(let [pwd (read-sized-string input)
		  n-args (read-int input)
		  args (seq (read-command-line-parms input n-args))]
	  (binding [*in* (clojure.lang.LineNumberingPushbackReader.
					  (InputStreamReader. input))
				*out* (PrintWriter. (ChunkOutputStream. output MSG-STDOUT))
				*err* (PrintWriter. (ChunkOutputStream. output MSG-STDERR))
				*exit* (ChunkOutputStream. output MSG-EXIT)
				*pwd* pwd]
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

(defn set-security-manager [manager]
  (System/setSecurityManager manager))

(defn get-security-manager []
  (System/getSecurityManager))

(defn build-security-manager []
  (proxy [SecurityManager] []
	  (checkExit [status]
		 (throw (ExitException. status)))
	  (checkAccept [host port])
	  (checkAccess [g])
	  (checkAwtEventQueueAccess [])
	  (checkConnect ([host port])
					([host port context]))
	  (checkCreateClassLoader [])
	  (checkDelete [file])
	  (checkExec [cmd])
	  (checkLink [lib])
	  (checkListen [port])
	  (checkMemberAccess [clazz which])
	  (checkMulticast ([maddr])
					  ([maddr ttl]))
	  (checkPackageAccess [pkg])
	  (checkPackageDefinition [pkg])
	  (checkPermission ([perm])
					   ([perm context]))
	  (checkPrintJobAccess [])
	  (checkPropertiesAccess ([])
							 ([key]))
	  (checkRead ([file])
				 ([file context]))
	  (checkSecurityAccess [target])
	  (checkSetFactory [])
	  (checkSystemClipboardAccess [])
	  (checkTopLevelWindow [window] true)
	  (checkWrite ([fd]))))

(defn set-exit-security-manager []
	 (set-security-manager (build-security-manager)))

(defn server-loop
  "accepts connections on the socket, and launches the repl on incoming
connections. Doesn't return."
  [socket minThreads maxThreads security-file]
  (let [exec (ThreadPoolExecutor. minThreads maxThreads
								  (* 60 5) TimeUnit/SECONDS
								  (LinkedBlockingQueue.))]
	(loop []
		(let [csocket (.accept socket)
			  gensym-ns *gensym-ns*]
		  (.submit exec #^Callable #(with-open [csocket csocket]
		  (with-open [csocket csocket]
									  (binding [*gensym-ns* gensym-ns
												*security-file* security-file]
										(reciever (.getInputStream csocket)
												  (.getOutputStream csocket)))))))
		(recur))))

