(ns ansi-repl
  (:require [ccw.bundle :as b]
             [ccw.core.factories :as k]
             [ccw.e4.dsl  :refer :all]
             [ccw.eclipse :as e]
             [ccw.repl.view-helpers :as r])
  (:import
    ccw.CCWPlugin 
    [ccw.repl REPLView]
    [org.eclipse.ui PlatformUI IPartListener]
    [org.eclipse.swt.custom StyledText ST]
    ))

;; logging to eclipse log
(defn log [& m]
  ;; comment out the line below when logging is no longer necessary
  (CCWPlugin/log (str "ANSI-REPL: " (apply str m)))
  )

;; Plugin state that survives plugin reload.
;; Used to make sure we're not adding new listeners when one
;; is already present, otherwise reloading the plugin keeps
;; adding new listeners.
;; No need for an atom if only accessed from the UI thread.
(declare state)
(if-not (bound? #'state)
  (def state {:style-listeners {} :part-listener nil}))
(log state)

;; The following methods are using reflection because clojure
;; compilation will fail without the ansi console plugin.
(def ansi-console-bundle (b/bundle "net.mihai-nita.ansicon.plugin"))
(def make-ansi-listener
  (k/make-factory ansi-console-bundle "mnita.ansiconsole.participants.AnsiConsoleStyleListener" []))


;; StyleListener functions
(defn remove-style-listener [^REPLView rv]
  (log (str "removing ansi listener for " (.getPartName rv)))
  (let [^StyledText st (.logPanel rv)]
    (if-let [sl (get-in state [:style-listeners rv])]
      (do
        (if-not (. st (isDisposed)) (.removeLineStyleListener st sl))
        (def state (update-in state [:style-listeners] dissoc rv))
        (log (str "removed ansi listener for " (.getPartName rv)))))))
(defn remove-style-listener-async [^REPLView rv]
  (r/ui-async (remove-style-listener rv)))
(defn add-style-listener [^REPLView rv]
  (let [^StyledText st (.logPanel rv)]
    (if (b/available? ansi-console-bundle)
      (do
        (if (get-in state [:style-listeners rv])
	        (do
            (log (str "replacing existing ansi listener for " (.getPartName rv)))
            (remove-style-listener rv)))
        (let [sl (make-ansi-listener)]
          (log (str "adding ansi listener to " (.getPartName rv)))
          (.addLineStyleListener st sl)
          (def state (assoc-in state [:style-listeners rv] sl))))
    (log "Cannot add style listener: ANSI Console plugin no longer available?"))))
(defn add-style-listener-async [^REPLView rv]
  (r/ui-async (add-style-listener rv)))

;; PartListener functions
;; Note: SWT listener methods are invoked by the UI thread.
(defn make-part-listener []
  (log "creating part listener")
  (reify
    IPartListener
    (partOpened [this p] (if (instance? REPLView p) (add-style-listener p)))
    (partClosed [this p] (if (instance? REPLView p) (remove-style-listener p)))
    (partActivated [this p] ())
    (partBroughtToTop [this p] ())
    (partDeactivated [this p] ())))
(defn add-part-listener []
  (if (b/available? ansi-console-bundle)
     (do
       (if-let [^IPartListener pl (:part-listener state)]
			   (do
           (log (str "replacing part listener"))
           (.. PlatformUI
		         getWorkbench
		         getActiveWorkbenchWindow
		         getPartService
		         (removePartListener pl))
	         (def state (dissoc state :part-listener))))
	     ;; adding part listener
       (let [^IPartListener pl (make-part-listener)]
           (log (str "adding part listener"))
		       (.. PlatformUI
		         getWorkbench
		         getActiveWorkbenchWindow
		         getPartService
		         (addPartListener pl))
	         (def state (assoc state :part-listener pl))))
     (log "Cannot add part listener: ANSI Console plugin no longer available?")))
(defn add-part-listener-async []
  (r/ui-async (add-part-listener)))

;; Install listeners
(defn install []
  (log "enabling ANSI REPL")
  ;; install a part listener for handling future REPLs
  (add-part-listener-async)
  ;; handle existing REPLs
  (->> (CCWPlugin/getREPLViews)
    (filter #(if-let [l (.getLaunch %)] (not (.isTerminated l))))
    (map #(add-style-listener-async %))
    (doall)))

(if (b/available? ansi-console-bundle)
  (install))


;; Status check action
(defn check-msg []
  (if (b/available? ansi-console-bundle)
    (str "ANSI support added to these REPLs: \n" 
      (apply str (map #(str (. % (getPartName)) "\n") (keys (:style-listeners state)))))
    (if ansi-console-bundle
      (str "ANSI support disabled: ANSI Console bundle in state " (.getState ansi-console-bundle))
      "ANSI support disabled: ANSI Console bundle not found!")))

(defn display-status [context]
  (e/info-dialog "ANSI REPL" (check-msg))
  (log state))

(defcommand ansi-repl-check "ANSI REPL: display status")
(defhandler ansi-repl-check display-status)
(defkeybinding ansi-repl-check "Alt+U A")