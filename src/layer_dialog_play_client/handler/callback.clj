(ns layer-dialog-play-client.handler.callback
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.string :as str]
            [layer-dialog-play-client.boundary.layer :as layer]
            [layer-dialog-play-client.boundary.dialog-play :as dialog-play]
            [layer-dialog-play-client.boundary.yelp :as yelp]
            [layer-dialog-play-client.component.token-manager :as token-manager]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

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
                        (take 5)
                        (map #(yelp/parse-business yelp %)))]
    (layer/post-message layer conversation-id {:mime_type "custom/restaurants"
                                               :body (json/write-str businesses)}))
  "OK")
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

(defmethod ig/init-key :layer-dialog-play-client.handler/callback [_ {:keys [dialog-play layer token-manager sync] :as opts}]
  (fn [{[] :ataraxy/result :as req}]
    (let [{:keys [body params]} req]
      (when body
        (let [body (-> body slurp (json/read-str :key-fn keyword))
              part (-> body
                       (get-in [:message :parts])
                       first)
              mime-type (:mime_type part)
              message (:body part)
              conversation-id (-> body
                                  (get-in [:message :conversation :id])
                                  (str/split #"/")
                                  last)
              sender-id (-> body
                            (get-in [:message :sender :id])
                            (str/split #"/")
                            last)]
          (when (not= sender-id (layer/get-bot-user-id layer))
            (if sync
              (dialog-play-to-layer opts conversation-id message)
              (letfn [(f [] (dialog-play-to-layer opts conversation-id message))]
                (.start (Thread. f)))))))
      [::response/ok "OK"])))
