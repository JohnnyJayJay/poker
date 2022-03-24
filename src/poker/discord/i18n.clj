(ns poker.discord.i18n
  (:require [edn-bundle.core :as bnd]
            [edn-bundle.format :as fmt])
  (:import (java.util Locale)))

(def resource-bundle-name "messages")

(defn guild-locale
  "Retrieves the locale set for a guild."
  [guild-id]
  Locale/ENGLISH)

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
