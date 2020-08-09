(ns poker.discord.command)

(defn- parse-int [^String s] (Integer/valueOf s))

(def poker-options
  [[nil "--buy-in VALUE" "Buy-in amount"
    :id :buy-in
    :parse-fn parse-int
    :validate [pos? "Buy-in must be a positive value"]
    :default 1000]
   [nil "--big-blind VALUE" "Big blind amount"
    :id :small-blind
    :parse-fn parse-int
    :validate [pos? "Big blind must be a positive value"]
    :default-fn (fn [{:keys [buy-in]}]
                  (max (quot buy-in 100) 1))]
   [nil "--small-blind VALUE" "Small blind amount"
    :id :big-blind
    :parse-fn parse-int
    :validate [pos? "Small blind must be a positive value"]
    :default-fn (fn [{:keys [small-blind]}]
                  (max (quot small-blind 2) 1))]
   [nil "--wait-time MILLISECONDS" "Time to wait before match start"
    :id :wait-time
    :parse-fn parse-int
    :validate [pos? "Wait time must be a positive value"]
    :default 25000]
   [nil "--timeout MILLISECONDS" "Inactive time until a player folds automatically"
    :id :timeout
    :parse-fn parse-int
    :validate [pos? "Timeout must be a positive value"]
    :default 180000]])

