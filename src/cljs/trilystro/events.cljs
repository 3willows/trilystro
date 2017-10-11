;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns trilystro.events
  (:require
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [re-frame.loggers :refer [console]]
   [sodium.chrome-utils :as chrome]
   [sodium.re-utils :as re-utils :refer [<sub]]
   [trilystro.db :as db]
   [trilystro.firebase :as fb]
   [trilystro.fsm :as fsm]))

(s/check-asserts true)

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   (fsm/page db/default-db :initialize-db nil)))

(re-frame/reg-event-db
 :form-state
 (fn [db [_ form-name form-component value]]
   (if form-component
     (assoc-in db `[:forms ~form-name ~@form-component] value)
     (assoc-in db `[:forms ~form-name] value))))

(re-frame/reg-event-fx
 :set-user
 (fn [{db :db} [_ user]]
   (into {:db (fsm/page (assoc db :user user)
                        (if user :login-confirmed :logout)
                        nil)}
         (when user
           {:firebase/write {:path       (fb/my-shared-fb-path [:user-details] (:uid user))
                             :value      (select-keys user [:display-name :email :photo-url])
                             :on-success #(console :log "Logged in:" (:display-name user))
                             :on-failure #(console :error "Login failure: " %)}}))))

(defn new-tags [tags]
  (let [old-tags (<sub [:all-tags])]
    (into [] (clojure.set/difference (set tags) (set old-tags)))))

(re-frame/reg-event-fx
 :commit-lystro
 (fn [{db :db} [_ form-key]]
   (let [form-path [:forms form-key]
         form-vals (get-in db form-path)
         {:keys [tags url text public?]} form-vals]
     {:firebase/multi (conj (mapv #(fb/fb-event {:for-multi? true
                                                 :effect-type :firebase/write
                                                 :db db
                                                 :access :public
                                                 :path [:tags %]
                                                 :value true
                                                 :on-failure (fn [x] (console :log "Collision? " % " already tagged"))})
                                  (new-tags tags))
                            (let [options {:for-multi? true
                                           :db db
                                           :access (if public? :shared :private)
                                           :value {:tags tags
                                                   :url url
                                                   :text text
                                                   :owner (<sub [:uid])
                                                   :public? public?}}]
                              (if-let [old-id (:firebase-id form-vals)]
                                (fb/fb-event (assoc options
                                                    :effect-type :firebase/write
                                                    :path [:items old-id]))
                                (fb/fb-event (assoc options
                                                    :effect-type :firebase/push
                                                    :path [:items])))))
      :db (assoc-in db form-path nil)})))

(re-frame/reg-event-fx
 :clear-lystro
 (fn [{db :db} [_ {:keys [firebase-id owner public?]} :as lystro]]
   (let [mine? (= owner (<sub [:uid]))]
     (when mine?
       (fb/fb-event {:for-multi? false
                     :effect-type :firebase/write
                     :db db
                     :access (if public? :shared :private)
                     :path [:items firebase-id]
                     :value nil})))))


(defn set-conj [set new]
  (conj (or set #{}) new))

(re-frame/reg-event-db
 :add-new-tag
 (fn [db [_ form-key]]
   (let [form-path [:forms form-key]
         new-tag (get-in db (conj form-path :new-tag))]
     (if (empty? new-tag)
       db
       (-> db
           (update-in (conj form-path :tags)    set-conj new-tag)
           (assoc-in  (conj form-path :new-tag) ""))))))

