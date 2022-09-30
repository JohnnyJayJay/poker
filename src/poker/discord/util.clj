(ns poker.discord.util
  (:require [discljord.messaging :as dr]
            [poker.discord.state :refer [rest-conn]]))

(defn respond-interaction [id token {:keys [type data]}]
  (dr/create-interaction-response! rest-conn id token type :data data))
