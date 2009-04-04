all: clj-client clojure-server.jar

clj-client: clj-client.c
	$(CC) -ggdb -Wall clj-client.c -o clj-client

clojure-server.jar: clojure_server/main.clj clojure_server/server.clj clojure_server/*.java
	javac clojure_server/*.java
	jar c clojure_server/*.clj clojure_server/*.class > clojure-server.jar

clean:
	rm -f clj-client
	rm -f clojure-server.jar
