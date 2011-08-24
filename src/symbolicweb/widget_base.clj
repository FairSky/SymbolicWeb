(in-ns 'symbolicweb.core)


(defn set-parent! [child new-parent]
  (alter child assoc :parent new-parent))


(defn set-children! [parent & children]
  (alter parent assoc :children (flatten children)))


(defn render-event [widget event-type & {:keys [js-before callback-data js-after]
                                         :or {js-before "return(true);"
                                              callback-data ""
                                              js-after ""}}]
  (str "$('#" (:id widget) "').bind('" event-type "', "
       "function(event){"
       "swMsg('" (:id widget) "', '" event-type "', function(){" js-before "}, '"
       (reduce (fn [acc key_val] (str acc (url-encode (str (key key_val))) "=" (val key_val) "&"))
               ""
               callback-data)
       "', function(){" js-after "});"
       "});"))


(defn render-events [widget]
  "Returns JS which will set up client/DOM side events."
  (when (:callbacks widget)
    (with-out-str
      (loop [callbacks (:callbacks widget)]
        (when-first [[event-type [callback-fn callback-data]] callbacks]
          (print (render-event widget event-type :callback-data callback-data))
          (recur (rest callbacks)))))))


(defn render-aux-js [widget]
  "Return JS which will initialize WIDGET."
  (when-let [render-aux-js-fn (:render-aux-js-fn widget)]
    (render-aux-js-fn widget)))


(defn render-aux-html [widget]
  "Return \"aux\" HTML for WIDGET."
  (when-let [render-aux-html-fn (:render-aux-html-fn widget)]
    (render-aux-html-fn widget)))


(declare ensure-visible)
(declare add-branch)
(defn render-html [widget]
  "Return HTML structure which will be the basis for further initialization via RENDER-AUX-JS.
The return value of RENDER-AUX-JS will be inlined within this structure."
  (let [widget-m (if (ref? widget)
                 @widget
                 widget)
        widget-type (:type widget-m)]
    (cond
     (isa? widget-type ::Widget)
     (do
       (when (and *in-html-container?* (not (:parent widget-m))) ;; TODO: The :parent test here?
         (add-branch *in-html-container?* widget))
       (if (isa? widget-type ::HTMLContainer)
         (binding [*in-html-container?* widget]
           ((:render-html-fn widget-m) widget-m))
         ((:render-html-fn widget-m) widget-m)))

     true
     (throw (Exception. (str "Can't render: " widget-m))))))


(defn sw [widget]
  (render-html widget))


(defn render-static-attributes [widget]
  (when-let [render-static-attributes-fn (:render-static-attributes-fn widget)]
    (render-static-attributes-fn)))


(defn set-event-handler [event-type widget callback-fn & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  (alter widget update-in [:callbacks] assoc event-type [callback-fn callback-data])
  widget)


(defn set-model! [widget model]
  {:pre (ref? widget)}
  (dosync
   (alter widget #(with1 (assoc %
                           :model model
                           :watchers ((:set-model-fn %) widget model))
                    (ref-set model @model))))) ;; Trigger initial update.


(defn make-ID
  ([] (make-ID {}))
  ([m] (assoc m :id (generate-aid))))


(defn make-WidgetBase [& key_vals]
  (let [widget-m (apply assoc (make-ID)
                        :type ::WidgetBase
                        :set-model-fn (fn [new-model] [])
                        :on-visible-fns []
                        :children [] ;; Note that order etc. doesn't matter here; this is only used to track
                                     ;; visibility on the client-end.
                        :callbacks {} ;; event-name -> [handler-fn callback-data]
                        :render-html-fn #(throw (Exception. (str "No :RENDER-HTML-FN defined for this widget (ID: " (:id %) ").")))
                        :parse-callback-data-handler #'default-parse-callback-data-handler
                        key_vals)]
    (ref widget-m)
    #_(let [widget-ref (ref widget-m)]
      (when *request*
        ;; There's no way we'll create a Widget in the context of one Viewport and use it or "send" it to another Viewport anyway.
        (alter *viewport* update-in [:widgets] assoc (:id widget-m) widget-ref))
      widget-ref)))


(derive ::Widget ::WidgetBase)
(defn make-Widget [& key_vals]
  (apply make-WidgetBase key_vals))


(derive ::HTMLElement ::Widget)
(defn make-HTMLElement
  ([element-type_element-attributes] (make-HTMLElement element-type_element-attributes (ref "")))
  ([element-type_element-attributes element-content]
  "HTML-CONTENT will be evaluated as TEXT on the client-end by default.
Set ESCAPE-HTML? to FALSE to change this."
  (let [[element-type & element-attributes] (if (vector? element-type_element-attributes)
                                              element-type_element-attributes
                                              (vector element-type_element-attributes))
        element-content (ensure-model element-content)
        html-element (apply make-Widget
                            :type ::HTMLElement
                            :html-element-type element-type
                            :set-model-fn (fn [widget model]
                                            (let [watch-key (generate-uid)]
                                              (add-watch model watch-key
                                                         (fn [_ _ _ new-value]
                                                           (jqHTML widget new-value)))
                                              watch-key))
                            :render-html-fn #(str "<" (:html-element-type %)
                                                  " id='" (:id %) "'" (render-static-attributes %) ">"
                                                  (render-aux-html %)
                                                  (let [script (str (render-aux-js %) (render-events %))]
                                                    (when (seq script)
                                                      (str "<script type='text/javascript'>" script "</script>")))
                                                  "</" (:html-element-type %) ">")
                            element-attributes)]
    (set-model! html-element element-content)
    html-element)))



(derive ::Button ::HTMLElement)
(defn make-Button [element-content]
  (make-HTMLElement ["button" :type ::Button]
                    element-content))
