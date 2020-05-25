(ns poker.util
  "The poker.util namespace contains functions that are used in multiple namespaces to avoid cyclic references.")

(defn compare-desc
  "Compares the second with the first argument.
  The result of this is a comparator that compares in reverse order."
  [one two]
  (compare two one))

(defn highest
  "Returns a vector of the highest values in the given collection according to the given key function."
  [keyfn [first & rest]]
  (reduce (fn [[one :as result] two]
            (let [comparison (compare (keyfn one) (keyfn two))]
              (cond
                (zero? comparison) (conj result two)
                (pos? comparison) result
                (neg? comparison) [two])))
          [first]
          rest))

(defn all-in?
  "Returns whether the given player is all-in in the given game."
  [{:keys [budgets]} player-id]
  (zero? (budgets player-id)))

(defn highest-bet
  "Returns the highest bet so far in this round."
  [{:keys [round-bets]}]
  (reduce max 0 (vals round-bets)))