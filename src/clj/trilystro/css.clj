;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns trilystro.css
  (:require [garden.def :refer [defstyles]]))

(defstyles screen
  [:body
   {:background-color "PapayaWhip"
    :color "black"}]
  [:.hidden
   {:display "none"}]

  [:.errmsg
   {:color "red"}]

  [:.credits
   {:font-size "70%"}]

  [:.lystro-result
   [:.url
    {:margin "0 0 0.5rem 0"}]

   [:.text
    {:margin "0.5rem 0px 0.5rem 0"}]]
  [:.editable-text
   {:cursor "pointer"}]
  [:.frozen-text
   {:color "dark-grey"}]

  [:.break-long-words
   {:overflow-wrap "break-word"}]

  [:.literal-whitespace
   {:font-family "Monospace"
    :font-size "80%"
    :white-space "pre-wrap"}]

  [:.minor
   {:color "dark-gray"
    :font-size "60%"
    :font-style "italic"}]

  [:.owner-sig
   {:color "dark-gray"
    :font-style "italic"
    :text-align "right"}]

  [:.rare-tag :.average-tag :.common-tag
   {:cursor "pointer"
    :margin "0 0.1rem 0 0.1rem"
    :text-align "center"
    :border-radius "100px"}]
  [:.rare-tag
   {:font-weight "bold"
    :padding "0.14rem 0.7rem 0.28rem 0.7rem"}]
  [:.average-tag
   {:padding "0.12rem 0.6rem 0.24rem 0.6rem"}]
  [:.common-tag
   {:padding "0.1rem 0.5rem 0.2rem 0.5rem"}]
  [:.selected-tag
   {:background-color "orange"
    :color "black"}]
  [:.unselected-tag
   {:background-color "tan"
    :color "white"}])
