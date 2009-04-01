all: clj-client clojure-server.jar

clj-client: clj-client.c
	$(CC) -Wall clj-client.c -o clj-client

clojure-server.jar: clojure_server/main.clj clojure_server/server.clj
	jar c clojure_server/*.clj > clojure-server.jar

clean:
	rm -f clj-client
	rm -f clojure-server.jar
