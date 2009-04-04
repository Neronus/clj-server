;; Copyright (c) Rich Hickey, Christian von Essen All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse Public
;; License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found
;; in the file epl-v10.html at the root of this distribution. By using this
;; software in any fashion, you are agreeing to be bound by the terms of
;; this license. You must not remove this notice, or any other, from this
;; software.

;; Originally contributed by Stephen C. Gilardi

;; Modified by Christian von Essen for clojure-server

(ns clojure-server.main
  (:import (clojure.lang
			Compiler Compiler$CompilerException))
  (:use clojure.main clojure.contrib.trace)
  (:require clojure.contrib.stacktrace))

(defn- make-absolute [path]
  "If the path is absolute, return it as it is. Otherwise,
prepend user.dir to it, and return the result"
  (if (= (first path) \/)
	path
	(str (System/getProperty "user.dir") \/ path)))

(defn- init-opt
  "Load a script"
  [path]
  (load-script (make-absolute path)))

(defn- eval-opt
  "Evals expressions in str, prints each non-nil result using prn"
  [str]
  (let [eof (Object.)]
    (with-in-str str
      (loop [input (read *in* false eof)]
        (when-not (= input eof)
          (let [value (eval input)]
            (when-not (nil? value)
              (prn value))
            (recur (read *in* false eof))))))))

(defn- init-dispatch
  "Returns the handler associated with an init opt"
  [opt]
  ({"-i"     init-opt
    "--init" init-opt
    "-e"     eval-opt
    "--eval" eval-opt} opt))

(defn- initialize
  "Common initialize routine for repl, script, and null opts"
  [ns-name args inits]
  (eval `(ns ~ns-name))
  (in-ns ns-name)
  (set! *command-line-args* args)
  (doseq [[opt arg] inits]
    ((init-dispatch opt) arg)))

(defn- repl-opt
  "Start a repl with args and inits. Print greeting if no eval options were
  present"
  [ns-name [_ & args] inits]
  (when-not (some #(= eval-opt (init-dispatch (first %))) inits)
    (println "Clojure"))
  (repl :init #(initialize ns-name args inits)
		:caught (fn [e]
				  (if (or (instance? java.lang.InterruptedException e) (and (instance? Compiler$CompilerException e) (instance? InterruptedException (.getCause e))))
					(throw e)
					(do
					  (if (instance? Compiler$CompilerException e)
						(.println *err* (clojure.contrib.stacktrace/root-cause e))
						(.println *err* e))
					  (.flush *err*)))))
  (prn)
  (locking repl-opt
	(.interrupt (Thread/currentThread))
	(.wait repl-opt)))


(defn- script-opt
  "Run a script from a file, resource, or standard in with args and inits"
  [ns-name [path & args] inits]
  (with-bindings
    (initialize ns-name args inits)
    (if (= path "-")
      (load-reader *in*)
      (load-script (make-absolute path)))))

(defn- null-opt
  "No repl or script opt present, just bind args and run inits"
  [ns-name args inits]
  (with-bindings
    (initialize ns-name args inits)))

(defn- help-opt
  "Print help text for main"
  [_ _ _]
  (println (:doc (meta (var main)))))

(defn- main-dispatch
  "Returns the handler associated with a main option"
  [opt]
  (or
   ({"-r"     repl-opt
     "--repl" repl-opt
     nil      null-opt
     "-h"     help-opt
     "--help" help-opt
     "-?"     help-opt} opt)
   script-opt))

(defn server-main
  "Usage: java -cp clojure.jar clojure.main [init-opt*] [main-opt] [arg*]

  With no options or args, runs an interactive Read-Eval-Print Loop

  init options:
    -i, --init path   Load a file or resource
    -e, --eval string Evaluate expressions in string; print non-nil values

  main options:
    -r, --repl        Run a repl
    path              Run a script from from a file or resource
    -                 Run a script from standard input
    -h, -?, --help    Print this help message and exit

  operation:

    - Establishes thread-local bindings for commonly set!-able vars
    - Enters the user namespace
    - Binds *command-line-args* to a seq of strings containing command line
      args that appear after any main option
    - Runs all init options in order
    - Runs a repl or script if requested

  The init options may be repeated and mixed freely, but must appear before
  any main option. The appearance of any eval option before running a repl
  suppresses the usual repl greeting message: \"Clojure\".

  Paths may be absolute or relative in the filesystem or relative to
  classpath. Classpath-relative paths have prefix of @ or @/"
  [& args]
  (let [ns-name (gensym "user")]
	(try
	 (if args
	   (loop [[opt arg & more :as args] args inits []]
		 (if (init-dispatch opt)
		   (recur more (conj inits [opt arg]))
		   ((main-dispatch opt) ns-name args inits)))
	   (repl-opt ns-name nil nil))
	 (catch Exception e
	   (when (not (or
				   (and (instance? Compiler$CompilerException e) (instance? InterruptedException (.getCause e)))
				   (instance? InterruptedException e)))
		 (binding [*out* *err*]
		   (clojure.contrib.stacktrace/print-stack-trace
			(clojure.contrib.stacktrace/root-cause e))
		   (flush))))
	 (finally (clojure.lang.Namespace/remove ns-name)))
	(flush)))