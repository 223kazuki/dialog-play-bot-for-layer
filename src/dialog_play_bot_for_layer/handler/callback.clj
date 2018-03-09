(ns dialog-play-bot-for-layer.handler.callback
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [clojure.data.json :as json]
            [taoensso.timbre :refer [info debug]]
            [clj-http.client :as client]
            [clojure.string :as str]
            [dialog-play-bot-for-layer.boundary.layer :as layer]
            [dialog-play-bot-for-layer.boundary.dialog-play :as dialog-play]
            [dialog-play-bot-for-layer.boundary.yelp :as yelp]
            [dialog-play-bot-for-layer.component.token-manager :as token-manager]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]))

(def DATE_FORMATTER (f/formatter "yyyy/MM/dd HH:mm:ss"))
(def CUSTOM_RESPONSE_PREFIX "CUSTOM_RESPONSE: ")
(def CUSTOM_RESPONSE_PREFIX_REGEX (re-pattern CUSTOM_RESPONSE_PREFIX))

;; Custom handler for dialog play response.
(defmulti custome-behavier :type)
(defmethod custome-behavier "poll" [params {:keys [layer token-manager
                                                   conversation-id] :as opts}]
  (let [{:keys [body]} params
        _ (info params)]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.text-poll+json"
                                               :body (json/write-str body)})
    "OK"))
(defmethod custome-behavier :default [params opts] "OK")

(defn dialog-play-to-layer
  [{:keys [dialog-play layer token-manager] :as opts} conversation-id mime-type message]
  (let [channel-uuid (or (token-manager/get-dialog-play-channel-id token-manager conversation-id)
                         (let [uuid (dialog-play/create-channel dialog-play)]
                           (token-manager/new-conversation token-manager conversation-id uuid)
                           uuid))
        _ (info "raw message: " message)
        _ (info "mime-type: " mime-type)
        message (case mime-type
                  message)
        _ (info "parsed message: " message)
        dialog-play-messages (dialog-play/post-message dialog-play message channel-uuid)]
    (when dialog-play-messages
      (doall
       (for [message dialog-play-messages]
         (do
           (info "Dialog Play response: " message)
           (cond
             (str/starts-with? message CUSTOM_RESPONSE_PREFIX)
             (as-> message m
               (str/split m CUSTOM_RESPONSE_PREFIX_REGEX)
               (rest m)
               (apply str m)
               (json/read-str m :key-fn keyword)
               (custome-behavier m (assoc opts :conversation-id conversation-id)))

             :else
             (let [result (layer/post-message layer conversation-id {:mime_type "text/plain"
                                                                     :body message})]
               "OK"))))))))

(defmulti  handle-webhook (fn [req opts] (get-in req [:headers "layer-webhook-event-type"])))
(defmethod handle-webhook "Message.created" [req {:keys [dialog-play layer token-manager sync] :as opts}]
  (let [{:keys [body mime_type params]} req]
    (when body
      (let [body (some-> body slurp (json/read-str :key-fn keyword))
            part (some-> body
                         (get-in [:message :parts])
                         first)
            mime-type (:mime_type part)
            message (:body part)
            conversation-id (some-> body
                                    (get-in [:message :conversation :id])
                                    (str/split #"/")
                                    last)
            sender-id (some-> body
                              (get-in [:message :sender :id]))]
        (info "Message created: " conversation-id sender-id message mime-type)
        (when (not= sender-id (layer/get-bot-user-id layer))
          (if sync
            (dialog-play-to-layer opts conversation-id mime-type message)
            (letfn [(f [] (dialog-play-to-layer opts conversation-id mime-type message))]
              (.start (Thread. f)))))))))
(defmethod handle-webhook "Conversation.created" [req {:keys [dialog-play layer token-manager sync] :as opts}]
  (let [{:keys [body params]} req]
    (when body
      (let [body (some-> body slurp (json/read-str :key-fn keyword))
            _ (info "body: " body)
            conversation-id (some-> body
                                    (get-in [:conversation :id])
                                    (str/split #"/")
                                    last)]
        (info "Conversation created: " conversation-id)
        (when-let [welcome-message (:welcome-message env)]
          (letfn [(f [] (layer/post-message layer conversation-id
                                            {:body welcome-message}))]
            (.start (Thread. f))))))))
(defmethod handle-webhook :default [req opts])

(defmethod ig/init-key :dialog-play-bot-for-layer.handler/callback [_ opts]
  (fn [{[] :ataraxy/result :as req}]
    (handle-webhook req opts)
    [::response/ok "OK"]))
