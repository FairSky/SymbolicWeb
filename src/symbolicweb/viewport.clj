(in-ns 'symbolicweb.core)

(declare make-ContainerView make-ContainerModel add-branch)
(defn make-Viewport [request application root-widget & args]
  "This will instantiate a new Viewport and also 'register' it as a part of APPLICATION and the server via -VIEWPORTS-."
  (let [viewport-id (cl-format false "~36R" (generate-uid))
        viewport (ref (apply assoc {}
                             :type ::Viewport
                             :id viewport-id
                             :last-activity-time (atom (System/currentTimeMillis))
                             :aux-callbacks {} ;; {:name {:fit-fn .. :handler-fn ..}}
                             :response-str (StringBuilder.)
                             :response-sched-fn (atom nil)
                             :response-agent (agent nil)
                             args))]
    (dosync
     (vm-set (:viewport @root-widget) viewport)
     (alter viewport assoc
            :application application
            :root-element root-widget
            :widgets {(:id @root-widget) root-widget})
     (add-branch viewport root-widget)
     (alter application update-in [:viewports] assoc viewport-id viewport))
    (when (:session? @application)
      (swap! -viewports- #(assoc % viewport-id viewport)))
    viewport))


(declare add-on-visible-fn)
#_(defn add-on-visible-fn [widget fn]
  "FN is code to execute when WIDGET is added to the client/DOM end."
  (alter (:on-visible-fns @widget) conj fn))


#_(defn add-on-non-visible-fn [widget fn]
  "FN is code to execute when WIDGET is removed from the client/DOM end."
  (alter (:on-non-visible-fns @widget) conj fn))


(defn add-response-chunk-agent-fn [viewport viewport-m ^String new-chunk]
  (with-errors-logged
    (locking viewport
      (let [response-sched-fn ^Atom (:response-sched-fn viewport-m)]
        (.append ^StringBuilder (:response-str viewport-m) new-chunk)
        (when @response-sched-fn
          (.run ^java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask
                (.job @response-sched-fn)))))))


(defn add-response-chunk [^String new-chunk widget]
  (when-not *with-js?*
    (if (= ::Viewport (:type @widget))
      (let [viewport widget
            viewport-m @widget]
        (send (:response-agent viewport-m)
              (fn [_] (add-response-chunk-agent-fn viewport viewport-m new-chunk))))
      (letfn [(do-it []
                (let [viewport (viewport-of widget)
                      viewport-m @viewport]
                  ;; TODO: M-m-m-mega hack. Why isn't REMOVE-VIEW called vs. TemplateElements and it seems some other widgets
                  ;; held in HTMLContainers in some cases?
                  ;; Are children not added to HTMLTemplate?
                  (if (< (* -viewport-timeout- 3) ;; NOTE: Times 3!
                         (- (System/currentTimeMillis) @(:last-activity-time viewport-m)))
                    (do
                      ;; Ok, it seems :VIEWPORT is set and :PARENT is not set to :DEAD so so this means ENSURE-NON-VISIBLE
                      ;; has _not_ been called yet -- which is strange.
                      ;;(remove-view (:model @widget) widget) ;; Ok, we might still leak; what about children of parent?
                      ;;(dbg-prin1 [(:type @widget) (:id @widget)])
                      (when (not= :dead (:parent @widget))
                        (dbg-prin1 [(:type @widget) (:id @widget)]) ;; This one is interesting; uncomment when working on this.
                        (remove-branch widget)
                        (def -lost-widget- widget)))
                    (send (:response-agent viewport-m)
                          (fn [_]
                            (add-response-chunk-agent-fn viewport viewport-m new-chunk))))))]
        (if (viewport-of widget) ;; Visible?
          (do-it)
          (add-on-visible-fn widget do-it)))))
  new-chunk)


(defn handle-widget-event [widget event-name]
  (let [w @widget]
    (if-let [handler-fn (get (:callbacks w) event-name)]
      (handler-fn w)
      (println (str "HANDLE-WIDGET-EVENT: No HANDLER-FN found for event '" event-name "' for Widget '" (:id w) "'"
                    " in Viewport '" (:id (viewport-of widget)) "'.")))))
