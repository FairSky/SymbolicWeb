(in-ns 'symbolicweb.core)

;; TODO:
;; * Input parsing should be or happen on the Model end so data flowing from back-ends (DBs) can benefit from
;;   it too.

;; * Better and more flexible parameter handling; static-attributes/CSS etc..

;; * Error handling and feedback to user.


(derive ::TextInput ::HTMLElement)
(defn make-TextInput [model & attributes]
  (with1 (apply make-HTMLElement "input" model
                :type ::TextInput
                :static-attributes {:type "text"}
                :handle-model-event-fn (fn [widget new-value]
                                         (jqVal widget new-value))
                attributes)
    (set-event-handler "change" it
                       (fn [& {:keys [new-value]}]
                         (set-value model (if-let [input-parsing-fn (:input-parsing-fn @it)]
                                            (input-parsing-fn new-value)
                                            new-value)))
                       :callback-data {:new-value "' + $(this).val() + '"})))


(derive ::IntInput ::TextInput)
(defn make-IntInput [model & attributes]
  (apply make-TextInput model
         :type ::IntInput
         :input-parsing-fn #(Integer/parseInt %)
         attributes))
