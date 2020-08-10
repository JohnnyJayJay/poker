(ns poker.discord.command
  (:require [clojure.tools.cli :as cli]))

(defn- parse-int [^String s]
  (try
    (Integer/valueOf s)
    (catch NumberFormatException _ nil)))

(defn poker-options [default-wait-time default-timeout]
  [[nil "--buy-in VALUE" "Buy-in amount"
    :id :buy-in
    :parse-fn parse-int
    :validate [pos? "Buy-in must be a positive number"]]
   [nil "--big-blind VALUE" "Big blind amount"
    :id :small-blind
    :parse-fn parse-int
    :validate [pos? "Big blind must be a positive number"]]
   [nil "--small-blind VALUE" "Small blind amount"
    :id :big-blind
    :parse-fn parse-int
    :validate [pos? "Small blind must be a positive number"]]
   [nil "--wait-time MILLISECONDS" "Time to wait before match start"
    :id :wait-time
    :parse-fn parse-int
    :validate [some? "Wait time must be a number"
               #(< 0 % 120000) "Wait time must be more than 0 and less than 120000 ms (2 minutes)"]
    :default default-wait-time]
   [nil "--timeout MILLISECONDS" "Inactive time until a player folds automatically"
    :id :timeout
    :parse-fn parse-int
    :validate [some? "Timeout must be a number"
               #(< 0 % 360000) "Timeout must be more than 0 and less than 360000 ms (6 minutes)"]
    :default default-timeout]])

(defn compute-buy-in
  [{[buy-in-arg] :arguments :keys [buy-in] :as opt-map} default]
  (assoc opt-map :buy-in (or buy-in (some-> buy-in-arg parse-int) default)))

(defn compute-big-blind
  [{:keys [big-blind buy-in] :as opt-map}]
  (assoc opt-map :big-blind (or big-blind (max 1 (quot buy-in 100)))))

(defn compute-small-blind
  [{:keys [small-blind big-blind] :as opt-map}]
  (assoc opt-map :small-blind (or small-blind (max 1 (quot big-blind 2)))))

(defn parse-command [args {:keys [default-wait-time default-timeout default-buy-in] :as config}]
  (-> args
      (cli/parse-opts (poker-options default-wait-time default-timeout))
      (compute-buy-in default-buy-in)
      compute-big-blind
      compute-small-blind))


