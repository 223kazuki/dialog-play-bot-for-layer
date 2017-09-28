(ns dialog-play-bot-for-layer.middleware
  "Logging request as map."
  (:require [integrant.core :as ig]
            [clojure.pprint :refer [pprint]]))

(defn wrap-log-requests
  [handler]
  (fn [request]
    (println (pr-str request))
    (handler request)))

(defmethod ig/init-key ::log-requests [_ _]
  #(wrap-log-requests %))
