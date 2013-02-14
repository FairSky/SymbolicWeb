(in-ns 'symbolicweb.core)

;; TODO:
;; * Input parsing should be or happen on the Model end so data flowing from back-ends (DBs) can benefit from
;;   it too. Uhm, I think.

;; * Better and more flexible parameter handling; static-attributes/CSS etc..

;; * Error handling and feedback to user.



(defn ^WidgetBase mk-TextInput [^ValueModel value-model & args]
  "<input type='text' ..> type widget."
  (let [args (apply hash-map args)]
    (with1 (mk-WidgetBase (fn [^WidgetBase widget]
                            (str "<input type='text' id='" (.id widget) "'>"))
                          args)

      (vm-observe value-model (.lifetime it) true
                  (fn [_ _ new-value]
                    (jqVal it new-value)))

      (set-event-handler "change" it
                         (fn [& {:keys [new-value]}]
                           (let [new-value (if-let [f (:input-parsing-fn args)]
                                             (try
                                               (f new-value)
                                               (catch Throwable e
                                                 (if-let [f (:input-parsing-error-fn args)]
                                                   (f new-value)
                                                   (throw (ex-info (str "mk-TextInput: Input parsing error for widget: " (.id it))
                                                                   {:widget it :model value-model :new-value new-value}
                                                                   e)))))
                                             new-value)]
                             (vm-set value-model new-value)))
                         :callback-data {:new-value "' + encodeURIComponent($(this).val()) + '"}))))



(defn ^WidgetBase mk-LongInput [^ValueModel value-model & args]
  (apply mk-TextInput value-model
         (concat [:input-parsing-fn #(if (number? %)
                                       (long %)
                                       (Long/parseLong %))]
                 args)))



;; TODO: Switch to something like bcrypt or scrypt. http://crackstation.net/hashing-security.htm
#_(defn mk-HashedInput [model salt & attributes]
  "<input type='password' ..> type widget using SHA256 hashing on the client and server end. It is salted on the server end.
Note that the client-side hash halve is still transferred in clear text form from the client to the server. This is what happens:

  (sha (str salt (sha hash)))"
  (with1 (apply mk-HTMLElement "input" model
                :static-attributes {:type "password"}
                :handle-model-event-fn (fn [_ _ _])
                :input-parsing-fn (fn [input-str]
                                    (sha (str salt input-str))) ;; Salt then hash a second time on server end.
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-value]}]
                         (dosync
                          (vm-set model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                          (input-parsing-fn new-value)
                                          new-value))))
                       :callback-data
                       {:new-value "' + encodeURIComponent($.sha256($(this).val())) + '"}))) ;; Hash once on client end.



#_(defn mk-TextArea [model & attributes]
  (with1 (apply mk-HTMLElement "textarea" model
                :type ::TextArea
                :handle-model-event-fn (fn [widget _ new-value]
                                         (jqVal widget new-value))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-value]}]
                         (dosync
                          (vm-set model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                          (input-parsing-fn new-value)
                                          new-value))))
                       :callback-data {:new-value "' + encodeURIComponent($(this).val()) + '"})))



#_(defn mk-CKEditor [model & attributes]
  (apply mk-TextArea model
         :type ::CKEditor
         :render-aux-js-fn (fn [widget]
                             (let [w-m @widget
                                   id (:id w-m)]
                               (str "CKEDITOR.replace('" id "');"
                                    "CKEDITOR.instances['" id "'].on('blur', function(e){"
                                    "  if(CKEDITOR.instances['" id "'].checkDirty()){"
                                    "    CKEDITOR.instances['" id "'].updateElement();"
                                    "    $('#" id "').trigger('change');"
                                    "  }"
                                    "  CKEDITOR.instances['" id "'].resetDirty();"
                                    "});")))
         attributes))
