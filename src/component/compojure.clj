(ns component.compojure
  (:require
   [com.stuartsierra.component :as component]
   [compojure.core :as compojure]))

(defn- wrapp-with-dep [f deps]
  (fn [req]
    (f (merge req [:system-deps deps]))))

(defn make-handler [routes deps]
  (-> routes
      (wrapp-with-dep deps)))

(defmacro defroutes [rec-name deps & routes]
  `(defrecord ~rec-name [~@deps]
     component/Lifecycle
     (start [this#]
       (let [keys# (map keyword '~deps)
             dep-map# (zipmap keys# ~deps)
             routes# (compojure/routes ~@routes)]
         (assoc this# :routes (make-handler routes# dep-map#))))
     (stop [this#] this#)))
