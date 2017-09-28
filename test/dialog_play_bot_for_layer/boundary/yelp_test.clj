(ns dialog-play-bot-for-layer.boundary.yelp-test
  (:require [dialog-play-bot-for-layer.boundary.yelp :refer :all]
            [dialog-play-bot-for-layer.component.token-manager :as token-manager]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]
            [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def test-yelp (map->Yelp {:url "https://api.yelp.com"
                           :client-id "xxxxxxxxxxxxxxxxxxxxxx"
                           :client-secret "xxxxxxxxxxxxxxxxxxxxxx"
                           :token-manager (dialog-play-bot-for-layer.component.token-manager/map->TokenManager {})}))

(deftest search-restaurants-test
  (testing "OK"
    (let [token "" ;; (get-access-token test-yelp)
          businesses []
          ;; (search-restaurants test-yelp token {:unix-time 1506017690
          ;;                                      :radius 1000
          ;;                                      :term "ハンバーガー"})
          {:keys [coordinates phone is_closed name image_url
                  categories id url distance transactions display_phone
                  location price review_count rating]} (first businesses)]
      ;; (is (not (nil? businesses)))
      ;; (is (not (nil? coordinates)))
      ;; (is (not (nil? phone)))
      ;; (is (not (nil? is_closed)))
      ;; (is (not (nil? name)))
      ;; (is (not (nil? image_url)))
      ;; (is (not (nil? categories)))
      ;; (is (not (nil? id)))
      ;; (is (not (nil? url)))
      ;; (is (not (nil? distance)))
      ;; (is (not (nil? transactions)))
      ;; (is (not (nil? display_phone)))
      ;; (is (not (nil? location)))
      ;; (is (not (nil? price)))
      ;; (is (not (nil? review_count)))
      ;; (is (not (nil? rating)))
      )))
