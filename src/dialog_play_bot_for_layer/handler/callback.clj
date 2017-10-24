(ns dialog-play-bot-for-layer.handler.callback
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [clojure.data.json :as json]
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
(defmethod custome-behavier "restaurants/search" [params {:keys [layer yelp token-manager
                                                                 conversation-id] :as opts}]
  (let [{:keys [date distance keyword]} params
        unix-time (as-> date d
                    (f/parse DATE_FORMATTER d)
                    (c/to-long d)
                    (/ d 1000))
        token (or (token-manager/get-yelp-access-token token-manager)
                  (let [token (yelp/get-access-token yelp)]
                    (token-manager/register-yelp-access-token token-manager token)
                    token))
        result (yelp/search-restaurants yelp token {:unix-time unix-time
                                                    :radius distance
                                                    :term keyword})
        businesses (->> result
                        (shuffle)
                        (take 2)
                        (map #(yelp/parse-business yelp %)))]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.carousel+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :items businesses})}))
  "OK")
(defmethod custome-behavier "airline/search" [params {:keys [layer yelp token-manager
                                                             conversation-id] :as opts}]
  (let [{:keys [international numberPeople airportDepart airportArrival dateDepart dateReturn]} params
        _ (println params) ;; log
        flights [{:id "9b8810c8-18e8-4c8f-99ce-2a96915a21ab"
                  :selectable true
                  :date "12/30(月)"
                  :routes [{:seats "○"
                            :flightName "TL002"
                            :depart {:airport "HND"
                                     :airportJapanese "羽田"
                                     :dateTime "19:45"}
                            :arrival {:airport "SFO"
                                      :airportJapanese "サンフランシスコ"
                                      :dateTime "12:00"}}]
                  :price "￥98000"
                  :time "9時間 15分"
                  :milage "1539マイル"}]]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.flight.ticket.list+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :items flights})})
    "OK"))
(defmethod custome-behavier :default [params opts] "OK")

(defn dialog-play-to-layer
  [{:keys [dialog-play layer token-manager] :as opts} conversation-id message]
  (let [channel-uuid (or (token-manager/get-dialog-play-channel-id token-manager conversation-id)
                         (let [uuid (dialog-play/create-channel dialog-play)]
                           (token-manager/new-conversation token-manager conversation-id uuid)
                           uuid))
        dialog-play-messages (dialog-play/post-message dialog-play message channel-uuid)]
    (when dialog-play-messages
      (doall
       (for [message dialog-play-messages]
         (if (str/starts-with? message CUSTOM_RESPONSE_PREFIX)
           (as-> message m
             (str/split m CUSTOM_RESPONSE_PREFIX_REGEX)
             (rest m)
             (apply str m)
             (json/read-str m :key-fn keyword)
             (custome-behavier m (assoc opts :conversation-id conversation-id)))
           (let [result (layer/post-message layer conversation-id {:mime_type "text/plain"
                                                                   :body message})]
             "OK")))))))

(defmulti  handle-webhook (fn [req opts] (get-in req [:headers "layer-webhook-event-type"])))
(defmethod handle-webhook "Message.created" [req {:keys [dialog-play layer token-manager sync] :as opts}]
  (let [{:keys [body params]} req]
    (when body
      (let [body (some-> body slurp (json/read-str :key-fn keyword))
            _ (println "body: " body)
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
                              (get-in [:message :sender :id])
                              (str/split #"/")
                              last)]
        (println "Message created: " conversation-id sender-id message mime-type)
        (when (not= sender-id (layer/get-bot-user-id layer))
          (if sync
            (dialog-play-to-layer opts conversation-id message)
            (letfn [(f [] (dialog-play-to-layer opts conversation-id message))]
              (.start (Thread. f)))))))))
(defmethod handle-webhook "Conversation.created" [req {:keys [dialog-play layer token-manager sync] :as opts}]
  (let [{:keys [body params]} req]
    (when body
      (let [body (some-> body slurp (json/read-str :key-fn keyword))
            _ (println "body: " body)
            conversation-id (some-> body
                                    (get-in [:conversation :id])
                                    (str/split #"/")
                                    last)]
        (println "Conversation created: " conversation-id)
        (when-let [welcome-message (:welcome-message env)]
          (letfn [(f [] (layer/post-message layer conversation-id
                                            {:body welcome-message}))]
            (.start (Thread. f))))))))
(defmethod handle-webhook :default [req opts])

(defmethod ig/init-key :dialog-play-bot-for-layer.handler/callback [_ opts]
  (fn [{[] :ataraxy/result :as req}]
    (handle-webhook req opts)
    [::response/ok "OK"]))
