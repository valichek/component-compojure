#Component-compojure
![Clojars Project](http://clojars.org/valichek/component-compojure/latest-version.svg)

This is a little helpful macro to create [Stuart Sierra's component](https://github.com/stuartsierra/component) with [compojure](https://github.com/weavejester/compojure) Ring handlers. It is based on [Ievgenii Nikolchev's](https://github.com/ggenikus) [library](https://github.com/ggenikus/comcomp) with changes proposed by [Andreas Klein](https://github.com/Kungi) to fix this [issue](https://github.com/ggenikus/comcomp/issues/1).

##Installation
Add the following dependencies to your `project.clj` file:

    [com.stuartsierra/component "0.2.2"]
    [compojure "1.3.1"]
    [ring "1.3.2"]
    [valichek/component-compojure "0.1.1-SNAPSHOT"]

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
(defn get-word [db]
  (:word (:connection db)))

(defn get-count [db]
  (str (:count (:connection db))))

(ccompojure/defroutes ServerRoutes [db]
  (compojure/GET "/word" [] (get-word db))
  (compojure/GET "/count" [] (get-count db)))

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
