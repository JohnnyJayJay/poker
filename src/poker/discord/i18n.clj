(ns poker.discord.i18n
  (:require [edn-bundle.core :as bnd]
            [edn-bundle.format :as fmt]
            [poker.discord.state :refer [db-conn]]
            [datalevin.core :as d])
  (:import (java.util Locale)))

(def resource-bundle-name "messages")

(defn guild-locale
  "Retrieves the locale set for a guild."
  [guild-id]
  (Locale/forLanguageTag (or (d/get-value db-conn "language" guild-id) "en")))

(defn set-guild-locale!
  "Sets the locale to be used for a guild"
  [guild-id ^String locale]
  (d/transact-kv db-conn [[:put "language" guild-id locale]]))

(defn guild-bundle
  "Retrieves the resource bundle for a guild"
  [guild-id]
  (bnd/get-bundle resource-bundle-name :locale (guild-locale guild-id) :control bnd/edn-control))

(defn loc-msg
  "Returns a localised message for the given guild, formatted with the given args."
  [guild-id-or-bundle key & args]
  (let [bundle (cond-> guild-id-or-bundle (not (instance? java.util.ResourceBundle guild-id-or-bundle)) guild-bundle)
        message-pattern (bnd/get-object bundle key)]
    (apply fmt/format message-pattern args)))
