(ns dialog-play-bot-for-layer.handler.verify
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response]
            [integrant.core :as ig]))

(defmethod ig/init-key :dialog-play-bot-for-layer.handler/verify [_ options]
  (fn [{[] :ataraxy/result :as req}]
    (let [{:keys [verification_challenge]} (:params req)]
      (if verification_challenge
        [::response/ok verification_challenge]
        [::response/ok ""]))))
