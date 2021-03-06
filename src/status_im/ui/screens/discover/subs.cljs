(ns status-im.ui.screens.discover.subs
  (:require [re-frame.core :refer [reg-sub]]
            [status-im.utils.datetime :as time]))

(defn- calculate-priority [{:keys [chats current-public-key]
                            :contacts/keys [contacts]}
                           {:keys [whisper-id created-at]}]
  (let [contact               (get contacts whisper-id)
        chat                  (get chats whisper-id)
        seen-online-recently? (< (- (time/now-ms) (get contact :last-online))
                                 time/hour)
        me?                   (= current-public-key whisper-id)]
    (+ created-at                                           ;; message is newer => priority is higher
       (if (or me? contact) time/day 0)                     ;; user exists in contact list => increase priority
       (if (or me? chat) time/day 0)                        ;; chat with this user exists => increase priority
       (if (or me? seen-online-recently?) time/hour 0))))      ;; the user was online recently => increase priority


(defn- get-discoveries-by-tags [discoveries current-tag tags]
  (let [tags' (or tags [current-tag])]
    (filter #(some (->> (:tags %)
                        (map :name)
                        (into (hash-set)))
                   tags')
            (vals discoveries))))

(reg-sub :get-popular-discoveries
  (fn [db [_ limit tags]]
    (let [discoveries (:discoveries db)
          current-tag (:current-tag db)
          search-tags (:discover-search-tags db)]
      (let [discoveries (->> (get-discoveries-by-tags discoveries current-tag (or tags search-tags))
                             (map #(assoc % :priority (calculate-priority db %)))
                             (sort-by :priority >))]
        {:discoveries (take limit discoveries)
         :total       (count discoveries)}))))

(reg-sub :get-top-discovery-per-tag
  (fn [{:keys [discoveries tags]} [_ limit]]
    (let [tag-names (map :name (take limit tags))]
     (for [tag tag-names]
       (let [results (get-discoveries-by-tags discoveries tag nil)]
          [tag {:discovery (first results)
                :total     (count results)}])))))

(reg-sub :get-recent-discoveries
  (fn [db]
    (sort-by :created-at > (vals (:discoveries db)))))

(reg-sub :get-popular-tags
  (fn [db [_ limit]]
    (take limit (:tags db))))

(reg-sub :get-discover-search-results
  (fn [db]
    (let [discoveries (:discoveries db)
          current-tag (:current-tag db)
          tags        (:discover-search-tags db)]
      (get-discoveries-by-tags discoveries current-tag tags))))

(reg-sub :get-all-dapps
  (fn [db]
    (let [dapp? (->> (get-in db [:group/contact-groups "dapps" :contacts])
                     (map :identity)
                     set)]
      (->> (:contacts/contacts db)
           (filter #(-> % key dapp?))
           (into {})))))
