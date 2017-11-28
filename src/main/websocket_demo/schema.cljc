(ns websocket-demo.schema
  (:require
    [clojure.spec.alpha :as s]
    [om.next :as om]
    #?(:clj
    [clojure.future :refer :all])))

(s/def :db/id (s/or :tmpid om/tempid? :realid number?))
(s/def ::name string?)
(s/def ::message string?)
(s/def ::chat-room-message (s/keys :req [:db/id ::message ::name]))
(s/def ::chat-room-messages (s/every ::chat-room-message :kind vector?))
(s/def ::chat-room-title string?)
(s/def ::chat-room (s/keys :req [:db/id ::chat-room-messages ::chat-room-title]))
(s/def ::user (s/keys :req [:db/id ::name]))
(s/def ::users (s/every ::user :kind vector?))
(s/def ::next-message-id pos-int?)
(s/def ::next-user-id pos-int?)
(s/def ::database (s/keys :req [::users ::next-user-id ::next-message-id ::chat-room]))
(s/def ::push-verb #{:add-chat-message :user-entered-chat-room :user-left-chat-room})
(s/def ::push-message (s/or :user ::user :message ::chat-room-message))
