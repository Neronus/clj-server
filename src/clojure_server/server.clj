;   Copyright (c) Christian von Essen. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure-server.server
  (:refer-clojure)
  (:use clojure.contrib.server-socket
		clojure.contrib.shell-out
		clojure-server.main))

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

(defn read-to-null [reader]
  (loop [result []]
	(let [c (.read reader)]
	  (cond
		(= c 0)
		(apply str result)
		(= c 1)
		(throw Exception "Wrong argument count: Reader died prematurely.")
		true
		(recur (conj result (char c)))))))

(defn- read-command-line-parms [reader n]
  (loop [result [], i 0]
	(if (< i n)
	  (recur (conj result (str (read-to-null reader))) (+ i 1))
	  result)))

(defn- set-property! [key value]
  (let [properties (System/getProperties)]
	(.setProperty properties (str key) (str value))))



(defn- get-property [key]
  (let [properties (System/getProperties)]
	(.getProperty properties (str key))))

(defn- get-property-names []
  (let [properties (System/getProperties)]
	(let [nameEnum (.propertyNames properties)]
	  (letfn [(iter [] 
					(if (.hasMoreElements nameEnum)
					  (lazy-seq (cons (.nextElement nameEnum) (iter)))))]
		(iter)))))

(defn- is-relative? [path]
  (not (= (first path) \/)))

(defn- file-mask [path]
  (let [stat (sh "/usr/bin/stat" "-L" "-c" "%A" path :return-map true)]
	(if (not (= (stat :exit) 0))
	  (throw (java.lang.RuntimeException. (stat :err)))
	  (re-seq #"[r-][w-][x-]" (.substring (stat :out) 1)))))

(defn auth [in out]
  (letfn ((dont-accept [] (.write out (int 0)) (.flush out) false)
		  (accept [] (.write out (int 1)) (.flush out) true)
		  (check-path [path]
			(loop [file-seq (lazy-slurp (java.io.FileReader. path)), sock-seq (lazy-slurp in)]
			  (cond (= () file-seq) true, (= (first file-seq) (first sock-seq)) (recur (rest file-seq) (rest sock-seq)), true false))))
	(let [path (read-to-null in)]
	  (if (is-relative? path)
		(dont-accept)
		(try
		 (let [mask (file-mask path)]
		   (if (not (and (re-find #"r" (nth mask 0)) (= "---" (nth mask 1)) (= "---" (nth mask 2))))
			 (dont-accept)
			 (do
			   (accept)
			   (if (not (check-path path))
				 (dont-accept)
				 (accept)))))
		 (catch Exception e (dont-accept)))))))


(defn- reciever [input output]
  (and (auth input output)
	   (let [pwd (read-to-null input)
			 n-args (Integer/parseInt (read-to-null input))
			 args (seq (read-command-line-parms input n-args))]
		 (binding [*in* input
				   *out* output
				   *err* output]
		   (set-property! "user.dir" pwd)
		   ;; This binding is not used, as clojure's core doesn't provide an exit function
		   ;; by default
		   ;;(binding [clojure.core/exit (fn [& args] (.stop (Thread/currentThread)))]
		   (clojure.contrib.trace/trace 'recieved)
			 (apply clojure-server.main/server-main args)))))

(defn start-server [port]
  (create-server port (fn [in out]
						(with-open
							[in (clojure.lang.LineNumberingPushbackReader. (java.io.InputStreamReader. in))
							 out (java.io.PrintWriter. out)]
						  (reciever in out)))))
;				 10
;				 (java.net.InetAddress/getByAddress
;				  (into-array (Byte/TYPE) [(byte 127) (byte 0) (byte 0) (byte 1)]))))

(defn stop-server [server]
  (close-server server))