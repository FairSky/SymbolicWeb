(in-ns 'symbolicweb.core)

(def -db-timeout-ms- 5000)


;;; Macros and dynamic variables.

;; html_container.clj
(def ^:dynamic *in-html-container?* false)
(def ^:dynamic *with-js?* false)

;; model.clj
(def ^:dynamic *observed-vms-ctx* false)
(def ^:dynamic *observed-vms-active-body-fns* #{})




(defmacro do1 [x & body]
  `(let [x# ~x]
     ~@body
     x#))



(defmacro with-js [& body]
  `(binding [*with-js?* true]
     ~@body))



;; Doesn't belong here, but can't find a way to bootstrap this crap proper.
(defmacro dbg-prin1 [form]
  `(let [res# ~form]
     (println '~form "=>" res#)
     res#))



(defn %with-errors-logged [f]
  (try
    (f)
    (catch Throwable e ;; TODO: Throwable is "wrong", but it also actually works.
      (try
        ;; TODO: Send this stuff to a ValueModel which'll print to STDOUT and present it to the user and what not.
        (clojure.stacktrace/print-stack-trace e 100)
        (catch Throwable e
          (println "%WITH-ERRORS-LOGGED: Dodge überfail... :(")
          (Thread/sleep 1000)))))) ;; Make sure we aren't flooded in case some loop gets stuck.


(defmacro with-errors-logged [& body]
  `(%with-errors-logged (fn [] ~@body)))



(defmacro with [form & body]
  `(let [~'it ~form]
     ~@body))



(defmacro with1 [form & body]
  `(with ~form
     ~@body
     ~'it))



(defmacro with-object [object & body]
  "Used by OBJ."
  `(let [~'%with-object ~object]
     ~@body))

(defmacro obj [signature]
  `(~signature ~'%with-object))



(defn check-type [obj type]
  (assert (isa? (class obj) type)
          (str "CHECK-TYPE: Expected " type ", but got: "
               (if obj
                 (str "<" (class obj) ": " obj ">")
                 "NIL"))))



(defmacro do1 [x & body]
  "As PROG1 from Common Lisp."
  `(let [x# ~x]
     ~@body
     x#))



(declare do-2pctx)
(defmacro swsync [& body]
  "BODY executes within a 2PCTX; a combined MTX and DBTX."
  `(do-2pctx (fn [] ~@body)))
