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
   [com.degel.re-frame-firebase :as firebase]))

(s/check-asserts true)

(defn private-fb-path
  ([path]
   (private-fb-path path nil))
  ([path for-uid]
   (if-let [uid (or for-uid
                    (<sub [:uid]))]
     (into [:private uid] path))))

(defn public-fb-path [path]
  (if-let [uid (<sub [:uid])]
    (into [:public] path)))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :page
 (fn [db [_ page]]
   (assoc db :page page)))

(re-frame/reg-event-db
 :form-state
 (fn [db [_ form form-component value]]
   (assoc-in db `[:forms ~form ~@form-component] value)))

(re-frame/reg-event-fx
 :set-user
 (fn [{db :db} [_ user]]
   (into {:db (assoc db :user user)}
         (when user
           {:firebase/write {:path       (private-fb-path [:user-details] (:uid user))
                             :value      (select-keys user [:display-name :email :photo-url])
                             :on-success #(console :log "Logged in:" (:display-name user))
                             :on-failure #(console :error "Failure: " %)}}))))

(re-frame/reg-event-fx
 :sign-in
 (fn [_ _] {:firebase/google-sign-in nil}))

(re-frame/reg-event-fx
 :sign-out
 (fn [_ _] {:firebase/sign-out nil}))

(re-frame/reg-event-fx
 :firebase-error
 (fn [_ [_ error]]
   (js/console.error (str "FIREBASE ERROR:\n" error))))


(re-frame/reg-event-fx
 :db-write-public
 (fn [{db :db} [_ {:keys [path value on-success on-failure] :as args}]]
   (if-let [path (public-fb-path path)]
     {:firebase/write (assoc args :path path)}

     ;; [TODO] Need to use pending Sodium generalization of :dispatch that takes a fn too.
     ((if on-failure (re-utils/event->fn on-failure) js/alert)
      (str "Can't write to Firebase, because not logged in:/n " path ": " value)))))


(defn logged-in? [db]
  (some? (get-in db [:user :uid])))


(defn fb-event [& {:keys [db path value on-success on-failure public? effect-type for-multi?] :as args}]
  (if (logged-in? db)
    (let [path-fn (if public? public-fb-path private-fb-path)
          path (path-fn path)
          effect-args (assoc (select-keys args [:value :on-success :on-failure])
                             :path path)]
      (if for-multi?
        [effect-type effect-args]
        {effect-type effect-args}))

    ;; [TODO] Need to use pending Sodium generalization of :dispatch that takes a fn too.
    ((if on-failure
       (re-utils/event->fn on-failure)
       js/alert)
     (str "Can't write to Firebase, because not logged in:/n " path ": " value))))


(defn new-keys [keys]
  (let [old-keys (vals (<sub [:firebase/on-value {:path [:public :keywords]}]))]
    (into [] (clojure.set/difference (set keys) (set old-keys)))))

(re-frame/reg-event-fx
 :commit-lystro
 (fn [{db :db} [_ form-key]]
   (let [form-path [:forms form-key]
         {:keys [selected-keys url text]} (get-in db form-path)]
     {:firebase/multi (into (mapv #(fb-event :for-multi? true
                                             :effect-type :firebase/push
                                             :db db
                                             :public? true
                                             :path [:keywords]
                                             :value %)
                                  (new-keys selected-keys))
                            [(fb-event :for-multi? true
                                       :effect-type :firebase/push
                                       :db db
                                       :path [:items]
                                       :value {:keys selected-keys :url url :text text})])
      :db (assoc-in db form-path nil)})))


(defn set-conj [set new]
  (conj (or set #{}) new))

(re-frame/reg-event-db
 :add-new-key
 (fn [db [_ form-key]]
   (let [form-path [:forms form-key]
         new-key (get-in db (conj form-path :new-key))]
     (-> db
         (update    :new-keys                       set-conj new-key)
         (update-in (conj form-path :selected-keys) set-conj new-key)
         (assoc-in  (conj form-path :new-key)       "")))))




(comment
  (re-frame/reg-event-fx
   :fb-test
   (fn [_ [_ & {:keys [event-type path value on-success on-failure]}]]
     {event-type {:path path
                  :value value
                  :on-success on-success
                  :on-failure on-failure}}))

  (re-frame/reg-event-db
   :got-read
   (fn [_ [_ val]] (prn "READ: " val)))

  (re-utils/>evt [:fb-test
                  :event-type :firebase/write
                  :path [:public :new]
                  :value 123
                  :on-success #(prn "GOOD")
                  :on-failure #(prn "BAD" %)])

  (re-utils/>evt [:fb-test
                  :event-type :firebase/write
                  :path [:not-public]
                  :value 123
                  :on-success #(prn "GOOD")
                  :on-failure #(prn "BAD" %)])

  (re-utils/>evt [:fb-test
                  :event-type :firebase/read-once
                  :path [:public :new]
                  :on-success [:got-read]
                  :on-failure #(prn "BAD" %)])
  )
