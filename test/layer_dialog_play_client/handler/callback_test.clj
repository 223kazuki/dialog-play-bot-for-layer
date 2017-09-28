(ns layer-dialog-play-client.handler.callback-test
  (:require [layer-dialog-play-client.handler.callback :refer :all]
            [layer-dialog-play-client.boundary.dialog-play :refer [IDialogPlay]]
            [layer-dialog-play-client.boundary.layer :refer [ILayer]]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]
            [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [shrubbery.core :refer :all]
            [layer-dialog-play-client.boundary.layer :as layer]
            [layer-dialog-play-client.boundary.dialog-play :as dialog-play]))

(defn dialog-play-stub [opts]
  (mock IDialogPlay
        {:create-channel "06a85100-bd9d-499e-8cec-097f9561a429"
         :post-message ["message1" "message2"]}))

(defmethod ig/init-key ::dialog-play [_ opts]
  (let [app-token "69635388-0bed-4d65-bff5-312354a842c2"]
    (dialog-play-stub opts)))

(defn layer-stub [opts]
  (mock ILayer
        {:get-bot-user-id "a27b9da1-d609-47ed-92d4-9d3771fceee5"
         :create-conversation "e88f732b-36c1-409d-894d-e6738cb653c1"
         :post-message {}}))

(defmethod ig/init-key ::layer [_ {:keys [url] :as opts}]
  (let [layer-app-id "c4962943-de2d-40e9-bfce-800d571e5bb9"
        layer-api-token "480be27d-a2b2-40f2-af82-72cfa76a585a"
        layer-bot-user-id "a27b9da1-d609-47ed-92d4-9d3771fceee5"]
    (layer-stub {:url url
                 :app-id layer-app-id
                 :api-token layer-api-token
                 :bot-user-id layer-bot-user-id})))

(def config
  {:layer-dialog-play-client.handler/callback
   {:dialog-play (ig/ref ::dialog-play)
    :layer (ig/ref ::layer)
    :token-manager (ig/ref :layer-dialog-play-client.component/token-manager)
    :sync true}
   ::dialog-play
   {:url "https"}
   ::layer
   {:url "https"}
   :layer-dialog-play-client.component/token-manager
   {}})

(defn create-system [] (ig/init config))

(def mock-request-body
  (json/read-str "
{
  \"message\": {
      \"id\": \"layer:///messages/940de862-3c96-11e4-baad-164230d1df67\",
    \"url\": \"https://api.layer.com/apps/082d4684-0992-11e5-a6c0-1697f925ec7b/messages/940de862-3c96-11e4-baad-164230d1df67\",
      \"conversation\": {
        \"id\": \"layer:///conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f\",
        \"url\": \"https://api.layer.com/apps/082d4684-0992-11e5-a6c0-1697f925ec7b/conversations/e67b5da2-95ca-40c4-bfc5-a2a8baaeb50f\"
      },
      \"parts\": [
          {
            \"id\": \"layer:///messages/940de862-3c96-11e4-baad-164230d1df67/parts/0\",
              \"mime_type\": \"text/plain\",
              \"body\": \"This is the message.\",
              \"size\": 20
          }
      ],
      \"sent_at\": \"2014-09-09T04:44:47+00:00\",
      \"received_at\": \"2014-09-16T19:54:39+00:00\",
      \"sender\": {
      \"id\": \"12345\",
      \"name\": \"t-bone\"
      },
      \"recipient_status\": {
          \"12345\": \"read\",
          \"999\": \"sent\",
          \"111\": \"sent\"
      }
  },
  \"target_config\" : {
    \"key1\" : \"value1\",
    \"key2\" : \"value2\"
  }
  }" :key-fn keyword))

(deftest callback
  (let [{:keys [layer-dialog-play-client.handler/callback
                layer-dialog-play-client.handler.callback-test/layer
                layer-dialog-play-client.handler.callback-test/dialog-play]} (create-system)]
    (testing "OK"
      (let [[status body]
            (callback
             {:body (io/input-stream
                     (.getBytes (json/write-str mock-request-body)))})]
        (is (received? dialog-play dialog-play/create-channel))
        (is (received? dialog-play dialog-play/post-message))
        (is (received? layer layer/post-message))
        (is (= status :ataraxy.response/ok))
        (is (= body "OK"))))
    (testing "Bot message"
      (let [[status body]
            (callback
             {:body (io/input-stream
                     (.getBytes (-> mock-request-body
                                    (assoc-in [:message :sender :id] "a27b9da1-d609-47ed-92d4-9d3771fceee5")
                                    json/write-str)))})]
        (is (= status :ataraxy.response/ok))
        (is (= body "OK"))))))
