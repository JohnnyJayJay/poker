(ns poker.discord.component)

(defmulti handle-component-interaction
  (fn [interaction] (-> interaction :data :custom-id)))
