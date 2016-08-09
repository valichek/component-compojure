#Component-compojure
[![Clojars Project](https://img.shields.io/clojars/v/valichek/component-compojure.svg)](https://clojars.org/valichek/component-compojure)

This is a little helpful macro to create [Stuart Sierra's component](https://github.com/stuartsierra/component) with [compojure](https://github.com/weavejester/compojure) Ring handlers. It is based on [Ievgenii Nikolchev's](https://github.com/ggenikus) [library](https://github.com/ggenikus/comcomp) with changes proposed by [Andreas Klein](https://github.com/Kungi) to fix this [issue](https://github.com/ggenikus/comcomp/issues/1).

##Installation
Add the following dependencies to your `project.clj` file:

    [com.stuartsierra/component "0.2.2"]
    [compojure "1.3.1"]
    [ring "1.3.2"]
    [valichek/component-compojure "0.2"]

##How it works
Dependencies is the main feature of Component. The good way to provide compojure request handlers with Component dependencies is to merge dependencies with request map. That is what `component.compojure.core/defroutes` macros does.

The typical request map from provided example looks like:
```clojure
{:cookies {}, :remote-addr "127.0.0.1", :params {}, :flash nil, :route-params {}, :headers {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "accept-encoding" "gzip, deflate", "accept-language" "en-US,en;q=0.5", "connection" "keep-alive", "host" "127.0.0.1:9999", "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:35.0) Gecko/20100101 Firefox/35.0"}, :async-channel #/127.0.0.1:55106>, :server-port 9999, :content-length 0, :form-params {}, :websocket? false, :session/key nil, :query-params {}, :content-type nil,
:system-deps {:db #conch_example.core.Database{:host "0.0.0.0", :port 9900, :connection {:host "0.0.0.0", :port 9900, :word "Hello", :count 5}}},
:character-encoding "utf8", :uri "/help", :server-name "127.0.0.1", :query-string nil, :body nil, :multipart-params {}, :scheme :http, :request-method :get, :session {}}
```
Note that `:system-deps` contains `db` dependency:

    :system-deps {:db #conch_example.core.Database{:host "0.0.0.0", :port 9900, :connection {:host "0.0.0.0", :port 9900, :word "Hello", :count 5}}}

##Component dependencies and limitations for request destructuring
To take advantage from Component system when handling requests with Compojure we need to extract dependencies from request.

The most general case is to capture the dependencies from request map directly:

```clojure
(ccompojure/defroutes ServerRoutes [db]
  (GET "/" request (str (:db (:system-deps request))))
```

Note that `:db` keyword and `db` parameter should have the same names.

The good way to get dependencies is to use destructuring syntax:
```clojure
(ccompojure/defroutes ServerRoutes [db]
  (compojure/GET "/word"
                 [:as request
                  :as {deps :system-deps}
                  :as {{db :db} :system-deps}]
                 (str request deps db)))
```

##Usage
This small application demonstrates how to use the library to create Ring handlers for [http-kit server](https://github.com/http-kit/http-kit) in Stewart Sierra's component eco-system.

First, add dependency for http-kit:

    [http-kit "2.1.18"]

Then:
```clojure
(ns http-kit-component-example.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]
            [component.compojure :as ccompojure]
            [compojure.core :as compojure]
            [compojure.route :as compojure-route]
            [compojure.handler :as compojure-handler]))

;; create Database component with dummy data
(defn connect-to-database [host port]
  {:host host :port port :word "Hello" :count 5})

(defrecord Database [host port connection]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (println ";; Starting database")
    ;; In the 'start' method, initialize this component
    ;; and start it running. For example, connect to a
    ;; database, create thread pools, or initialize shared
    ;; state.
    (let [conn (connect-to-database host port)]
      ;; Return an updated version of the component with
      ;; the run-time state assoc'd in.
      (assoc component :connection conn)))

  (stop [component]
    (println ";; Stopping database")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    ;(.close connection)
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
    (assoc component :connection nil)))

;; create ServerRoutes component and pass Database to the request handlers
(defn get-word [request deps db]
  (:word (:connection db)))

(defn get-count [db]
  (str (:count (:connection db))))

(ccompojure/defroutes ServerRoutes [db]
  (compojure/GET "/help" request (str request))
  (compojure/GET "/count" request (get-count (:db (:system-deps request))))
  (compojure/GET "/word"
                 [:as request
                  :as {deps :system-deps}
                  :as {{db :db} :system-deps}]
                 (get-word request deps db))
  (compojure-route/not-found "The page could not be found"))

;; Create Server component to run http-kit server, use ring handlers created in ServerRoutes component
(defn get-routes [routes]
  (-> (compojure-handler/site routes)))

(defrecord Server [host port routes]
  ;; Implement the Lifecycle protocol
  component/Lifecycle

  (start [component]
    (println ";; Starting server")
    ;; In the 'start' method, initialize this component
    ;; and start it running. For example, connect to a
    ;; database, create thread pools, or initialize shared
    ;; state.
    (assoc component :server (httpkit/run-server
                                (get-routes (:routes routes))
                                {:ip host :port port :join? false})))

  (stop [component]
    (println ";; Stopping server")
    ;; In the 'stop' method, shut down the running
    ;; component and release any external resources it has
    ;; acquired.
    ((:server component) :timeout 100)
    ;; Return the component, optionally modified. Remember that if you
    ;; dissoc one of a record's base fields, you get a plain map.
    (assoc component :server nil)))

;; Define system map with component dependencies
(defn example-system [config-options]
  (let [{:keys [db-host db-port http-host http-port]} config-options]
    (component/system-map
      :db (map->Database {:host db-host :port db-port})
      :routes (component/using
               (map->ServerRoutes {})
               [:db])
      :server (component/using
               (map->Server {:host http-host :port http-port})
               [:routes]))))

;; Define system with config options
(def system (example-system {:db-host "0.0.0.0"
                             :db-port 9900
                             :http-host "0.0.0.0"
                             :http-port 9999}))
```
To start/stop the system:
```clojure
(alter-var-root #'system component/start)
;; Starting database
;; Starting server

(alter-var-root #'system component/stop)
;; Stopping server
;; Stopping database
```
The same but catching errors from components:
```clojure
(try
  (alter-var-root #'system component/start)
  (catch Throwable t
    (.getCause t)))
;; Starting database
;; Starting server

(try
  (alter-var-root #'system component/stop)
  (catch Throwable t
    (.getCause t)))
;; Stopping server
;; Stopping database
```

##License
Distributed under the Eclipse Public License either version 1.0 or any later version.
