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
(def CONFIRMATION_RESPONSE_PREFIX "以下の内容でよろしいですか?")
(def CONFIRMATION_RESPONSE_PREFIX_REGEX (re-pattern CONFIRMATION_RESPONSE_PREFIX))

(def EXAMPLE_FLIGHTS
  [{:id "9b8810c8-18e8-4c8f-99ce-2a96915a21ab"
    :selectable true
    :date "12/28(木)"
    :routes
    [{:seats "○"
      :flightName "TL002"
      :depart {:airport "HND", :airportJapanese "羽田", :dateTime "19:45"}
      :arrival
      {:airport "SFO", :airportJapanese "サンフランシスコ", :dateTime "12:00"}}]
    :price "￥156,000"
    :tax "￥17,320"
    :amount "￥173,320"
    :time "9時間 15分"
    :milage "5130マイル"}
   {:id "90ddd38a-0ab1-4e44-bcbf-699fc51d7381"
    :selectable true
    :date "12/28(木)"
    :routes
    [{:seats "△"
      :flightName "TL012"
      :depart {:airport "NRT", :airportJapanese "成田", :dateTime "11:50"}
      :arrival
      {:airport "DFW"
       :airportJapanese "ダラス・フォートワース"
       :dateTime "08:05"}}
     {:seats "7"
      :flightName "TL7577"
      :depart
      {:airport "DFW"
       :airportJapanese "ダラス・フォートワース"
       :dateTime "11:10"}
      :arrival
      {:airport "SFO", :airportJapanese "サンフランシスコ", :dateTime "13:14"}}]
    :price "￥166,500"
    :tax "￥17,320"
    :amount "￥183,820"
    :time "18時間 24分"
    :milage "7904マイル"}
   {:id "9f06f070-d434-4a20-bfec-05a71902ad4f"
    :selectable true
    :date "12/28(木)"
    :routes
    [{:seats "7"
      :flightName "TL7016"
      :depart {:airport "NRT", :airportJapanese "成田", :dateTime "18:45"}
      :arrival
      {:airport "LAX", :airportJapanese "ロサンゼルス", :dateTime "11:45"}}
     {:seats "7"
      :flightName "TL7556"
      :depart
      {:airport "LAX", :airportJapanese "ロサンゼルス", :dateTime "14:00"}
      :arrival
      {:airport "SFO", :airportJapanese "サンフランシスコ", :dateTime "15:30"}}]
    :price "￥199,000"
    :tax "￥17,320"
    :amount "￥216,320"
    :time "13時間 45分"
    :milage "5797マイル"}])

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
        flights (->> EXAMPLE_FLIGHTS
                     (map #(assoc % :date dateDepart)))]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.flight.ticket.list+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :items flights})})
    "OK"))
(defmethod custome-behavier "airline/confirm" [params {:keys [layer yelp token-manager
                                                              conversation-id] :as opts}]
  (let [{:keys [flightId dateDepart]} params
        _ (println params) ;; log
        flights (->> EXAMPLE_FLIGHTS
                     (filter #(= (:id %) flightId))
                     (map #(assoc % :date dateDepart))
                     (map #(assoc % :selectable false)))]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.flight.ticket.list+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :items flights})})
    "OK"))
(defmethod custome-behavier "airline/seats" [params {:keys [layer yelp token-manager
                                                            conversation-id] :as opts}]
  (let [{:keys [flightId]} params
        _ (println params) ;; log
        seats []]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.flight.seat+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :seats seats})})
    "OK"))
(defmethod custome-behavier "airline/purchase" [params {:keys [layer yelp token-manager
                                                               conversation-id] :as opts}]
  (let [{:keys [flightId dateDepart]} params
        _ (println params) ;; log
        flight (->> EXAMPLE_FLIGHTS
                    (filter #(= (:id %) flightId))
                    first)
        order {:price (:price flight)
               :tax (:tax flight)
               :amount (:amount flight)
               :date dateDepart
               :confirmed false}]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.flight.ticket.purchase+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :order order})})
    "OK"))
(defmethod custome-behavier "airline/pdf" [params {:keys [layer yelp token-manager
                                                          conversation-id] :as opts}]
  (let [{:keys [file title]} params
        _ (println params) ;; log
        doc {:file file
             :title title}]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.pdf+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :doc doc})})
    "OK"))
(defmethod custome-behavier "profile/input" [params {:keys [layer yelp token-manager
                                                            conversation-id] :as opts}]
  (let [{:keys []} params]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.text-profile+json"
                                               :body (json/write-str
                                                      {})})
    "OK"))
(defmethod custome-behavier "airline/confirmation" [params {:keys [layer yelp token-manager
                                                                   conversation-id] :as opts}]
  (let [{:keys [message]} params
        _ (println params) ;; log
        parsed (as-> message m
                 (str/split m #"\n")
                 (rest m)
                 (map #(str/split % #":") m)
                 (map #(assoc {} :name (first %) :value (second %)) m))
        _ (println parsed) ;; log
        data {:title "フライト予約内容"
              :data parsed}]
    (layer/post-message layer conversation-id {:mime_type "application/x.card.confirmation+json"
                                               :body (json/write-str
                                                      {:title ""
                                                       :subtitle ""
                                                       :selection_mode "none"
                                                       :data data})})
    "OK"))
(defmethod custome-behavier :default [params opts] "OK")

(defn dialog-play-to-layer
  [{:keys [dialog-play layer token-manager] :as opts} conversation-id mime-type message]
  (let [channel-uuid (or (token-manager/get-dialog-play-channel-id token-manager conversation-id)
                         (let [uuid (dialog-play/create-channel dialog-play)]
                           (token-manager/new-conversation token-manager conversation-id uuid)
                           uuid))
        _ (println "raw message: " message)
        _ (println "mime-type: " mime-type)
        message (case mime-type
                  "application/x.card.flight.ticket.list+json" (-> message
                                                                   (json/read-str :key-fn keyword)
                                                                   :items
                                                                   first
                                                                   :id)
                  "application/x.card.flight.seat+json" (as-> message m
                                                          (json/read-str m :key-fn keyword)
                                                          (:seats m)
                                                          (map :name m)
                                                          (str/join "," m))
                  "application/x.card.flight.ticket.purchase+json" "購入完了"
                  "application/x.card-response+json" (some-> message
                                                             (json/read-str :key-fn keyword)
                                                             :data)
                  message)
        _ (println "parsed message: " message)
        dialog-play-messages (dialog-play/post-message dialog-play message channel-uuid)]
    (when dialog-play-messages
      (doall
       (for [message dialog-play-messages]
         (do
           (println "Dialog Play response: " message)
           (cond
             (str/starts-with? message CUSTOM_RESPONSE_PREFIX)
             (as-> message m
               (str/split m CUSTOM_RESPONSE_PREFIX_REGEX)
               (rest m)
               (apply str m)
               (json/read-str m :key-fn keyword)
               (custome-behavier m (assoc opts :conversation-id conversation-id)))

             (str/starts-with? message CONFIRMATION_RESPONSE_PREFIX)
             (as-> message m
               (assoc {} :type "confirmation" :message m)
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
                              (get-in [:message :sender :id])
                              (str/split #"/")
                              last)]
        (println "Message created: " conversation-id sender-id message mime-type)
        (when (not= sender-id (layer/get-bot-user-id layer))
          (if sync
            (dialog-play-to-layer opts conversation-id mime-type message)
            (letfn [(f [] (dialog-play-to-layer opts conversation-id mime-type message))]
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
