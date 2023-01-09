;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main.ui.viewer.inspect.code
  (:require
   ["js-beautify" :as beautify]
   [app.common.data.macros :as dm]
   [app.common.geom.shapes :as gsh]
   [app.common.uuid :as uuid]
   [app.main.data.events :as ev]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.components.code-block :refer [code-block]]
   [app.main.ui.components.copy-button :refer [copy-button]]
   [app.main.ui.hooks :as hooks]
   [app.main.ui.icons :as i]
   [app.util.code-gen :as cg]
   [app.util.dom :as dom]
   [cuerdas.core :as str]
   [potok.core :as ptk]
   [rumext.v2 :as mf]))

(defn generate-markup-code [_type shapes from]
  (let [frame (if (= from :workspace)
                (dom/query js/document (dm/str "#shape-" uuid/zero))
                (dom/query js/document "#svg-frame"))
        markup-shape
        (fn [shape]
          (let [selector (str "#shape-" (:id shape) (when (= :text (:type shape)) " .root"))]
            (when-let [el (and frame (dom/query frame selector))]
              (str
               (str/fmt "<!-- %s -->" (:name shape))
               (.-outerHTML el)))))]
    (->> shapes
         (map markup-shape )
         (remove nil?)
         (str/join "\n\n"))))

(defn format-code [code type]
  (let [code (-> code
                 (str/replace "<defs></defs>" "")
                 (str/replace "><" ">\n<"))]
    (cond-> code
      (= type "svg") (beautify/html #js {"indent_size" 2}))))

(defn get-flex-elements [page-id shapes from]
  (let [ids (mapv :id shapes)
        ids (hooks/use-equal-memo ids)
        get-layout-children-refs (mf/use-memo (mf/deps ids page-id from) #(if (= from :workspace)
                                                                            (refs/workspace-get-flex-child ids)
                                                                            (refs/get-flex-child-viewer ids page-id)))]

    (mf/deref get-layout-children-refs)))

(mf/defc code
  [{:keys [shapes frame on-expand from]}]
  (let [style-type  (mf/use-state "css")
        markup-type (mf/use-state "svg")
        shapes      (->> shapes
                         (map #(gsh/translate-to-frame % frame)))
        route      (mf/deref refs/route)
        page-id    (:page-id (:query-params route))
        flex-items (get-flex-elements page-id shapes from)
        shapes     (map #(assoc % :flex-items flex-items) shapes)
        style-code (-> (cg/generate-style-code @style-type shapes)
                       (format-code "css"))

        markup-code (-> (mf/use-memo (mf/deps shapes) #(generate-markup-code @markup-type shapes from))
                        (format-code "svg"))

        on-markup-copied
        (mf/use-callback
         (mf/deps @markup-type)
         (fn []
           (st/emit! (ptk/event ::ev/event
                                {::ev/name "copy-inspect-code"
                                 :type @markup-type}))))

        on-style-copied
        (mf/use-callback
         (mf/deps @style-type)
         (fn []
           (st/emit! (ptk/event ::ev/event
                                {::ev/name "copy-inspect-style"
                                 :type @style-type}))))]

    [:div.element-options
     [:div.code-block
      [:div.code-row-lang "CSS"

       [:button.expand-button
        {:on-click on-expand}
        i/full-screen]

       [:& copy-button {:data style-code
                        :on-copied on-style-copied}]]

      [:div.code-row-display
       [:& code-block {:type @style-type
                       :code style-code}]]]

     [:div.code-block
      [:div.code-row-lang "SVG"

       [:button.expand-button
        {:on-click on-expand}
        i/full-screen]

       [:& copy-button {:data markup-code
                        :on-copied on-markup-copied}]]
      [:div.code-row-display
       [:& code-block {:type @markup-type
                       :code markup-code}]]]]))