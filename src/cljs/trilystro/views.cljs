;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns trilystro.views
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [com.degel.re-frame-firebase]
   [re-frame.core :as re-frame]
   [re-frame.loggers :refer [console]]
   [sodium.core :as na]
   [sodium.extensions :as nax]
   [sodium.utils :as utils]
   [sodium.re-utils :refer [<sub >evt]]
   [trilystro.config :as config]
   [trilystro.events :as events]
   [trilystro.firebase :as fb]
   [trilystro.fsm :as fsm]
   [trilystro.modal :as modal]
   [trilystro.temp-utils :as tmp-utils]))


(defn lystro-search-grid
  "Grid to show Lystro elements.
  [TODO] Currently used only for the search box. Cleanup may be due"
  [params tags url text]
  (let [row-params {:color (or (:color params) "grey")}
        label-params {:width 3 :text-align "right"}
        value-params {:width 13}]
    [na/grid {:celled? true}
     [na/grid-row row-params
       [na/grid-column label-params "Tags:"]
       [na/grid-column value-params tags]]
      [na/grid-row row-params
       [na/grid-column label-params "URL:"]
       [na/grid-column value-params (if (string? url) (tmp-utils/link-to url) url)]]
      [na/grid-row row-params
       [na/grid-column label-params "Text:"]
       [na/grid-column value-params text]]]))


(defn mini-button
  "Icon-only button, good for standard actions"
  [icon options]
  [na/button (into {:icon icon
                    :floated "right"
                    :color "brown"
                    :size "mini"}
                   options)])


(defn search-panel
  "Component to specify Lystro search terms"
  []
  (let [getter ::fsm/page-param-val
        setter ::fsm/update-page-param-val]
    [lystro-search-grid {:color "brown"}
     [na/container {}
      [na/dropdown {:inline? true
                    :value (<sub [getter :tags-mode] :any-of)
                    :on-change (na/>event [setter :tags-mode] :any-of keyword)
                    :options (na/dropdown-list [[:all-of "All of"] [:any-of "Any of"]] first second)}]
      [nax/tag-selector {:all-tags-sub            [:all-tags]
                         :selected-tags-sub       [getter :tags]
                         :set-selected-tags-event [setter :tags]}]]
     [na/input {:type "url"
                :placeholder "Website..."
                :value     (<sub      [getter :url] "")
                :on-change (na/>event [setter :url])}]
     (let [corner (fn [icon side field]
                    (let [value (<sub [getter field])]
                      [na/label {:icon icon
                                 :corner side
                                 :size "mini"
                                 :class-name "clickable"
                                 :color (if value "orange" "brown")
                                 :on-click (na/>event [setter field (not value)])}]))]
       [:div
        (corner "tags" "left" :tags-as-text?)
        (corner "linkify" "right" :url-as-text?)
        [na/text-area {:rows 3
                       :placeholder "Description..."
                       :value     (<sub      [getter :text] "")
                       :on-change (na/>event [setter :text])}]])]))


(defn lystro-results-panel
  "Render one Lystro"
  [{:keys [tags text url owner public?] :as lystro}]
  (let [mine? (= owner (<sub [:uid]))]
    [na/segment {:secondary? (not mine?)
                 :tertiary? (not public?)
                 :class-name "lystro-result"}
     (when mine? (mini-button "delete"
                              {:on-click (na/>event (modal/goto :modal-confirm-delete lystro))}))
     (when mine? (mini-button "write"
                              {:on-click (na/>event (modal/goto :modal-edit-lystro lystro))}))
     [nax/draw-tags {:selected-tags-sub       [::fsm/page-param-val :tags]
                     :set-selected-tags-event [::fsm/update-page-param-val :tags]
                     :class-of-tag-sub        [:tag-class-by-frequency]}
      tags]
     [:div {:on-click #(when mine? (>evt (modal/goto :modal-edit-lystro lystro)))
            :class-name (str "text break-long-words "
                             (if mine? "editable-text" "frozen-text"))}
      text]
     [:div {:class-name "url"}
      (tmp-utils/link-to url)]
     (when (not mine?)
       [:div {:class "owner-sig"}
        (<sub [:user-pretty-name owner])])]))


;;; [TODO] Maybe move to utils, if this proves itself
(defn sort-by-alpha
  "Sort strings, ignoring case and non-alphabetic chars"
  [keyfn coll]
  (sort-by (comp #(apply str (re-seq #"[A-Z]" %))
                 str/upper-case
                 keyfn)
           coll))

(defn main-panel
  "The main screen"
  []
  [na/form {}
   [na/divider {:horizontal? true :section? true} "Search Lystros"]
   [search-panel]
   (let [selected-lystros
         (<sub [:lystros {:tags-mode     (<sub [::fsm/page-param-val :tags-mode])
                          :tags          (<sub [::fsm/page-param-val :tags])
                          :url           (<sub [::fsm/page-param-val :url])
                          :text          (<sub [::fsm/page-param-val :text])
                          :tags-as-text? (<sub [::fsm/page-param-val :tags-as-text?])
                          :url-as-text?  (<sub [::fsm/page-param-val :url-as-text?])}])]
     [:div
      [na/divider {:horizontal? true :section? true}
       (str "Results (" (count selected-lystros) ")")]
      `[:div {}
        ~@(mapv lystro-results-panel (sort-by-alpha :text selected-lystros))]
      [na/divider {:horizontal? true :section? true}]
      [na/container {}
       [na/button {:size "mini"
                   :icon "external share"
                   :content "export all"
                   :on-click #(>evt (modal/goto :modal-show-exports (<sub [:lystros])))}]
       [na/button {:size "mini"
                   :icon "share"
                   :content "export current"
                   :on-click #(>evt (modal/goto :modal-show-exports selected-lystros))}]]])])


(defn login-logout-control []
  (let [user (<sub [:user])]
    [na/menu-menu {:position "right"}
     [na/menu-item {:on-click (na/>event [(if user :sign-out :sign-in)])}
      (if user
        [na/label {:image true :circular? true}
         [na/image {:src (:photo-url user)}]
         (or (:display-name user) (:email user))]
        "login...")]
     (let [connected? (:firebase/connected? (<sub [:firebase/connection-state]))]
       [na/menu-item {:icon (if connected? "signal" "wait")
                      :content (if connected? "online" "offline")}])] ))

(defn top-bar []
  [na/menu {:fixed "top"}
   [na/menu-item {:header? true
                  :on-click (na/>event (modal/goto :modal-about))}
    [na/icon {:name "tasks" :size "big"}]
    (<sub [:name])]
   [na/menu-item {:name "Add"
                  :disabled? (not (<sub [::fsm/in-page? :logged-in]))
                  :on-click (na/>event (modal/goto :modal-new-lystro))}]
   [na/menu-item {:name "About"
                  :on-click (na/>event (modal/goto :modal-about))}]
   [login-logout-control]])



;;; We want to keep the Firebase ":on" subscriptions active, so need to mount them in the
;;; main panel. But, we don't want anything to show. We could use a display:none div, but
;;; this head-fake is more elegant, and seems to work works.
;;; [TODO] ^:export is probably not needed, but I've not tested removing it. See
;;;        discussion in Slack #clojurescript channel Sept 6-7 2017.
(defn ^:export null-op [x] "")

(defn app-view []
  [na/container {}
   (into [:div] (<sub [::modal/all-modal-views]))
   [top-bar]
   [na/container {:style {:margin-top "5em"}}
    [nax/google-ad
     :unit "half banner"
     :ad-client "ca-pub-7080962590442738"
     :ad-slot "5313065038"
     :test (when config/debug? "... ADVERT  HERE ...")]
    (when (<sub [::fsm/in-page? :logged-in])
      (let [open-state ;; Subs that should be held open for efficiency
            [(<sub [:firebase/on-value {:path (fb/private-fb-path [:lystros])}])
             (<sub [:firebase/on-value {:path (fb/private-fb-path [:user-settings])}])
             (<sub [:firebase/on-value {:path (fb/all-shared-fb-path [:lystros])}])
             (<sub [:firebase/on-value {:path (fb/all-shared-fb-path [:user-details])}]) ;; [TODO][ch94] rename
             ]]
        (null-op open-state)
        [main-panel]))]])
