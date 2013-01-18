(in-ns 'symbolicweb.core)


;;; Represents a browser window or tab within a single browser Session
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; TODO: Instead of using an Agent, this code could probably do the same thing DO-MTX (database_common.clj) does.



(defn mk-Viewport [request ^Ref session ^WidgetBase root-widget & args]
  "This will instantiate a new Viewport and also 'register' it as a part of SESSION and the server via -VIEWPORTS-."
  (assert (= :lifetime-root @(.parent (.lifetime root-widget))))
  (let [viewport-id (str "sw-" (generate-uid))
        viewport (ref (apply assoc {}
                             :type ::Viewport
                             :id viewport-id
                             :last-activity-time (atom (System/currentTimeMillis))
                             :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}

                             ;; Comet.
                             :response-str (StringBuilder.)
                             :response-sched-fn (atom nil)
                             :response-agent (agent nil)

                             ;; Resources.
                             :rest-css-entries (ref [])
                             :rest-js-entries (ref [])

                             :session session
                             :root-element root-widget ;; TODO: Rename...
                             :widgets {(.id root-widget) root-widget} ;; Viewport --> Widget  (DOM events.)
                             args))]
    (when-not (:one-shot? @session)
      (swap! -viewports- #(assoc % viewport-id viewport)))
    (dosync
     (alter session update-in [:viewports] assoc viewport-id viewport)
     ;; Widget --> Viewport.
     (vm-set (.viewport root-widget) viewport)
     (do-lifetime-activation (.lifetime root-widget)))
    viewport))



(defn add-rest-css [^Ref viewport rest-css-entry]
  (alter (:rest-css-entries @viewport)
         conj rest-css-entry))



(defn add-rest-js [^Ref viewport rest-js-entry]
  (alter (:rest-js-entries @viewport)
         conj rest-js-entry))



(defn add-response-chunk-agent-fn [viewport viewport-m ^String new-chunk]
  (with-errors-logged
    (locking viewport
      (let [response-sched-fn ^Atom (:response-sched-fn viewport-m)]
        (.append ^StringBuilder (:response-str viewport-m) new-chunk)
        (when @response-sched-fn
          (.run ^java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask
                (.job ^overtone.at_at.ScheduledJob @response-sched-fn)))))))



(defn add-response-chunk ^String [^String new-chunk widget]
  "WIDGET: A WidgetBase or Viewport instance."
  (when-not *with-js?*
    (if (viewport? widget)
      (let [viewport widget
            viewport-m @widget]
        (send (:response-agent viewport-m)
              (fn [_] (add-response-chunk-agent-fn viewport viewport-m new-chunk))))
      (letfn [(do-it []
                (let [viewport (viewport-of widget)
                      viewport-m @viewport]
                  ;; TODO: M-m-m-mega hack. Why isn't REMOVE-VIEW called vs. TemplateElements and it seems some other widgets
                  ;; held in HTMLContainers in some cases?
                  ;; UPDATE 10/15/2012: This doesn't seem to happen any more, but I'm leaving it here a while longer.
                  ;; Are children not added to HTMLTemplate?
                  (if (< (* -viewport-timeout- 3) ;; NOTE: Times 3!
                         (- (System/currentTimeMillis) @(:last-activity-time viewport-m)))
                    (do
                      ;; Ok, it seems :VIEWPORT is set and :PARENT is not set to :DEAD so so this means ENSURE-NON-VISIBLE
                      ;; has _not_ been called yet -- which is strange.
                      ;;(remove-view (:model @widget) widget) ;; Ok, we might still leak; what about children of parent?
                      ;;(dbg-prin1 [(:type @widget) (:id @widget)])
                      (when (not= :dead (parent-of widget))
                        ;; This one is interesting; uncomment when working on this.
                        (println "ADD-RESPONSE-CHUNK: Found stale widget:" (.id widget))
                        (detach-branch widget)
                        (def -lost-widget- widget)))
                    (send (:response-agent viewport-m)
                          (fn [_]
                            (add-response-chunk-agent-fn viewport viewport-m new-chunk))))))]
        (if (viewport-of widget) ;; Visible?
          (do-it)
          (when-not (= :deactivated (lifetime-state-of (.lifetime widget)))
            (add-lifetime-activation-fn (.lifetime widget) (fn [_] (do-it))))))))
  new-chunk)
