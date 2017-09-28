(ns layer-dialog-play-client.boundary.layer
  (:require [integrant.core :as ig]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.string :as str]))

(defprotocol ILayer
  (get-bot-user-id [this])
  (create-conversation [this user-ids])
  (post-message [this conversation-id message]))

(defrecord Layer [url app-id api-token bot-user-id]
  ILayer
  (get-bot-user-id [this] bot-user-id)
  (create-conversation [this user-ids]
    (let [{:keys [status body]}
          (client/post (str url "/apps/" app-id "/conversations")
                       {:content-type :json
                        :headers {"Accept" "application/vnd.layer+json; version=2.0"
                                  "Authorization" (str "Bearer " api-token)}
                        :body (json/write-str {:participants (map #(str "layer:///identities/" %) user-ids)
                                               :distinct false
                                               :metadata {:conversationName "TestTest"
                                                          :background_color "#3c3c3c"}})})
          conversation-id
          (-> body
              (json/read-str :key-fn keyword)
              :id
              (str/split #"/")
              last)]
      conversation-id))
  (post-message [this conversation-id {:keys [mime_type body]}]
    (let [{:keys [status body]}
          (client/post (str url "/apps/" app-id
                            "/conversations/" conversation-id "/messages")
                       {:content-type :json
                        :headers {"Accept" "application/vnd.layer+json; version=2.0"
                                  "Authorization" (str "Bearer " api-token)}
                        :body (json/write-str {:sender_id (str "layer:///identities/" bot-user-id)
                                               :parts [{:body body
                                                        :mime_type (or mime_type
                                                                       "text/plain")}]})})]
      body)))

(defmethod ig/init-key :layer-dialog-play-client.boundary/layer [_ {:keys [url] :as opts}]
  (let [{:keys [layer-app-id layer-api-token layer-bot-user-id]} env]
    (map->Layer {:url url
                 :app-id layer-app-id
                 :api-token layer-api-token
                 :bot-user-id layer-bot-user-id})))
