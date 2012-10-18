(in-ns 'symbolicweb.core)


(defprotocol IWidgetBase
  (viewport-of [widget-base])
  (parent-of [widget-base])

  (attach-branch [widget-base child-widget-base])
  (detach-branch [widget-base])
  (empty-branch [widget-base]))



(deftype WidgetBase [^String id
                     ^Lifetime lifetime
                     ^clojure.lang.Fn render-fn
                     ^clojure.lang.Ref parent ;; WidgetBase
                     ^ValueModel viewport ;; Viewport
                     ^clojure.lang.Ref callbacks ;; {CB-NAME -> [HANDLER-FN CALLBACK-DATA], ...}   (DOM events)
                     ^Boolean escape-html?]
  IWidgetBase
  (viewport-of [_]
    @viewport)


  (parent-of [_]
    (ensure parent))


  (attach-branch [parent child]
    (assert (not (parent-of child))
            (str "ATTACH-BRANCH: CHILD already has a parent assigned for it: " (parent-of child)
                 ", EXISTING-PARENT-ID: " (.id (parent-of child))
                 ", CHILD-ID: " (.id child)))
    ;; CHILD -> PARENT.
    (ref-set (.parent child) parent)
    (attach-lifetime (.lifetime parent) (.lifetime child)))


  (detach-branch [widget]
    (detach-lifetime (.lifetime widget)))


  (empty-branch [widget]
    (doseq [^Lifetime child-lifetime (.children (.lifetime widget))]
      (detach-lifetime child-lifetime))))



(defn ^WidgetBase make-WidgetBase
  ([^clojure.lang.Fn render-fn]
     (make-WidgetBase render-fn {}))

  ([^clojure.lang.Fn render-fn args]
     (with1 (WidgetBase. (or (:id args)
                             (str "sw-" (generate-uid))) ;; ID
                         (if (:root-widget? args) ;; LIFETIME
                           (mk-LifetimeRoot)
                           (mk-Lifetime))
                         render-fn ;; RENDER-FN
                         (ref nil) ;; PARENT
                         (vm nil) ;; VIEWPORT
                         (ref {}) ;; CALLBACKS
                         (:escape-html? args)) ;; ESCAPE-HTML?


       (when-not (:root-widget? args)
         (add-lifetime-activation-fn (.lifetime it)
                                     (fn [^Lifetime lifetime]
                                       (let [parent-viewport (viewport-of (parent-of it))]
                                         ;; Viewport --> Widget (DOM events).
                                         (alter parent-viewport update-in [:widgets]
                                                assoc (.id it) it)
                                         ;; Widget --> Viewport.
                                         (vm-set (.viewport it) parent-viewport)))))

       (add-lifetime-deactivation-fn (.lifetime it)
                                     (fn [^Lifetime lifetime]
                                       (let [viewport (viewport-of it)]
                                         ;; Viewport -/-> Widget (DOM events).
                                         (alter viewport update-in [:widgets]
                                                dissoc (.id it) it)
                                         ;; Widget -/-> Viewport.
                                         (vm-set (.viewport it) nil)))))))



(defn ^String render-event [^WidgetBase widget
                            ^String event-type
                            & {:keys [js-before callback-data js-after]
                               :or {js-before "return(true);"
                                    callback-data ""
                                    js-after ""}}]
  (str "$('#" (.id widget) "').bind('" event-type "', "
       "function(event){"
       "swMsg('" (.id widget) "', '" event-type "', function(){" js-before "}, '"
       (reduce (fn [acc key_val] (str acc (url-encode-component (str (key key_val))) "=" (val key_val) "&"))
               ""
               callback-data)
       "', function(){" js-after "});"
       "});"))



(defn ^String render-html [^WidgetBase widget]
  "Return HTML structure which will be the basis for further initialization."
  ((.render-fn widget) widget))



(defn ^String sw [^WidgetBase widget]
  "Render WIDGET as part of a HTMLContainer; WITH-HTML-CONTAINER."
  ;;(println "SW:" (.id widget))
  (attach-branch *in-html-container?* widget)
  (render-html widget))



(defn ^WidgetBase set-event-handler [^String event-type
                                     ^WidgetBase widget
                                     ^clojure.lang.Fn callback-fn
                                     & {:keys [callback-data]}]
  "Set an event handler for WIDGET.
Returns WIDGET."
  ;; TODO: Check if EVENT-TYPE is already bound? Think about this ..
  (alter (.callbacks widget) assoc event-type [callback-fn callback-data])
  (add-response-chunk (render-event widget event-type :callback-data callback-data)
                      widget)
  widget)
