(ns layer-dialog-play-client.boundary.dialog-play
  (:require [integrant.core :as ig]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clj-http.client :as client]))

(defprotocol IDialogPlay
  (create-channel [this])
  (post-message [this message channel-uuid]))

(defrecord DialogPlay [url app-token]
  IDialogPlay
  (create-channel [this]
    (let [{:keys [status body]}
          (client/post (str url "/channels/connect")
                       {:content-type :json
                        :body (json/write-str {:application_token app-token})})
          channel-uuid (-> body (json/read-str :key-fn keyword) :channel_uuid)]
      (when (and (== status 200) channel-uuid)
        channel-uuid)))
  (post-message [this message channel-uuid]
    (let [{:keys [status body]}
          (client/post (str url "/channels/" channel-uuid "/messages")
                       {:content-type :json
                        :body (json/write-str {:type "text"
                                               :content {:text message}
                                               :sync true})})]
      (when (and (== status 200) body)
        (->> (json/read-str body :key-fn keyword)
             (map :content)
             (map :text))))))

(defmethod ig/init-key :layer-dialog-play-client.boundary/dialog-play [_ opts]
  (let [app-token (:dialog-play-app-token env)]
    (map->DialogPlay (assoc opts :app-token app-token))))
