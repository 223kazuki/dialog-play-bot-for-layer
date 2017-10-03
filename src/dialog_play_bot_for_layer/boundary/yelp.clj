(ns dialog-play-bot-for-layer.boundary.yelp
  (:require [integrant.core :as ig]
            [environ.core :refer [env]]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.string :as str]))

(defn parse-business
  [this business]
  (let [{:keys [coordinates phone is_closed name image_url categories id
                url distance transactions display_phone location price
                review_count rating]} business]
    (json/write-str {:title name
                     :image_url image_url
                     :link url
                     :detail (str "Price: " price "\n"
                                  "Reviews: " review_count "\n"
                                  "Rating: " rating)})))

(defprotocol IYelp
  (get-access-token [this])
  (search-restaurants [this access-token conditions]))

(defrecord Yelp [url client-id client-secret]
  IYelp
  (get-access-token [this]
    (let [{:keys [status body]}
          (client/post (format "%s/oauth2/token?grant_type=token&client_id=%s&client_secret=%s"
                               url client-id client-secret))]
      (when (== status 200)
        (-> body
            (json/read-str :key-fn keyword)
            :access_token))))
  (search-restaurants [{:keys [token-manager] :as this} access-token conditions]
    (let [{:keys [unix-time radius term]} conditions
          _ (println "Unix-time:" unix-time)
          {:keys [status body]}
          (client/get (format "%s/v3/businesses/search?locale=ja_JP&term=%s&radius=%s&open_at=%s&latitude=%s&longitude=%s"
                              url term radius unix-time 37.332243 -121.894307)
                      {:headers {"Authorization" (str "Bearer " access-token)}})]
      (when (== status 200)
        (-> body
            (json/read-str :key-fn keyword)
            :businesses)))))

(defmethod ig/init-key :dialog-play-bot-for-layer.boundary/yelp [_ {:keys [url] :as opts}]
  (let [{:keys [yelp-client-id yelp-client-secret]} env]
    (map->Yelp {:url url
                :client-id yelp-client-id
                :client-secret yelp-client-secret})))
