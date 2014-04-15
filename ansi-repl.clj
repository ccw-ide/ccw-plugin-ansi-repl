;; CounterClockWiwe plugin for adding ANSI escape code support to REPLs.
;; To enable its functionality install the ANSI console plugin found at:
;;  https://github.com/mihnita/ansi-econsole
;; See CCW user documentation for details on plugin installation:
;;  http://doc.ccw-ide.org/documentation.html#_user_plugins
;; See also:
;;  https://code.google.com/p/counterclockwise/issues/detail?id=624
;;  https://code.google.com/p/counterclockwise/issues/detail?id=629
;;
;; Latest version at https://gist.github.com/fmjrey/9889500
;;
;; Licensed under The MIT License (MIT)
;; http://opensource.org/licenses/MIT
;; Copyright (c) 2014 François Rey

(ns ansi-repl
  (:require [ccw.bundle :as bundle]
             [ccw.core.factories :as factories]
             [ccw.e4.dsl  :refer :all]
             [ccw.eclipse :as eclipse]
             [ccw.repl.view-helpers :as view-helpers])
  (:import
    ccw.CCWPlugin 
    [ccw.repl REPLView]
    org.eclipse.jface.action.Action
    org.eclipse.jface.resource.ImageDescriptor
    [org.eclipse.ui PlatformUI IPartListener]
    [org.eclipse.swt.custom StyledText ST]
    ))

;; logging to eclipse log
(defn log [& m]
  ;; Comment out the line below when logging is no longer necessary.
  ;; Logging is useful for developing, but can be annoying because
  ;; it brings to the foreground the Error Log view.
  ;(CCWPlugin/log (str "ANSI-REPL: " (apply str m)))
  )

;; The following methods use reflection otherwise clojure
;; compilation will fail without the ansi console plugin.
(def ansi-console-bundle (bundle/bundle "net.mihai-nita.ansicon.plugin"))
(declare make-ansi-listener)
(if (bundle/available? ansi-console-bundle)
  (def make-ansi-listener
      (factories/make-factory ansi-console-bundle "mnita.ansiconsole.participants.AnsiConsoleStyleListener" [])))

;; Plugin state that survives plugin reload.
;; Used to make sure we're not adding new listeners when one
;; is already present, otherwise reloading the plugin keeps
;; adding new listeners.
;; No need for an atom if only accessed from the UI thread.
(declare state)
(if-not (bound? #'state)
  (def state {:installed false :part-listener nil :repls {}}))
(defn installed? []
  (:installed state))
(defn flag-installed []
  (alter-var-root #'state assoc :installed true))
(defn flag-uninstalled []
  (alter-var-root #'state assoc :installed false))
(defn repl-names-where-installed []
  "Returns a possibly empty list of REPL names where ANSI support is installed."
  (doall (map #(.getPartName %) (keys (:repls state)))))
(defn status-msg []
  (if (bundle/available? ansi-console-bundle)
    (if (installed?)
      (if-let [rn (seq (repl-names-where-installed))]
        (str "ANSI support installed on these REPLs: \n"
          (apply str (map #(str " - " % \newline) rn)))
        (str "ANSI support installed, but there's no REPL."))
      "ANSI support disabled.")
    (if ansi-console-bundle
      (str "ANSI support disabled: ANSI Console bundle in state " (.getState ansi-console-bundle))
      "ANSI support disabled: ANSI Console bundle not found!")))


;; StyleListener functions
(defn redraw [^StyledText st]
  (if-not (. st (isDisposed))
    (.redrawRange st 0 (.getCharCount st) true)))
(defn remove-style-listener [^REPLView rv]
  ;(log (str "removing ansi listener for " (.getPartName rv)))
  (let [^StyledText st (.logPanel rv)
        sl (get-in state [:repls rv :listener])]
    (when sl
      (if-not (. st (isDisposed)) (.removeLineStyleListener st sl))
      (alter-var-root #'state update-in [:repls rv] dissoc :listener)
      (log (str "removed listener for " (.getPartName rv)))
      (view-helpers/ui-async (redraw st)))))
(defn add-style-listener [^REPLView rv]
  (let [^StyledText st (.logPanel rv)]
    (when (get-in state [:repls rv :listener])
	    (log (str "replacing existing listener for " (.getPartName rv)))
      (remove-style-listener rv))
    (let [sl (make-ansi-listener)]
      (log (str "adding listener to " (.getPartName rv)))
      (.addLineStyleListener st sl)
      (alter-var-root #'state assoc-in [:repls rv :listener] sl)
      (view-helpers/ui-async (redraw st)))))

;; REPL Toolbar toggle button
(when ansi-console-bundle
  (def icon-enabled-descriptor
    (ImageDescriptor/createFromURL (.getEntry ansi-console-bundle "/icons/ansiconsole.gif"))))
(def ansi-action-id "toggle-ansi-support")
(defn remove-toolbar-action [^REPLView rv]
  (when-let [action (get-in state [:repls rv :action])]
    (.. rv getViewSite getActionBars getToolBarManager (remove ansi-action-id))
    (alter-var-root #'state update-in [:repls rv] dissoc :action)
    (log (str "removed toolbar button from " (.getPartName rv)))))
(defn add-toolbar-action [^REPLView rv]
  (when (get-in state [:repls rv :action])
    (log (str "replacing toolbar button on " (.getPartName rv)))
    (remove-toolbar-action rv))
  (let [action (proxy [Action] ["Process ANSI escape code" Action/AS_CHECK_BOX]
                         (run []
                           (if (.isChecked (get-in state [:repls rv :action]))
                             (add-style-listener rv)
                             (remove-style-listener rv))))]
			(doto action
			  (.setToolTipText "Process ANSI escape code")
			  (.setImageDescriptor icon-enabled-descriptor)
        (.setId ansi-action-id))
      (log (str "adding toolbar button on " (.getPartName rv)))
			(.. rv getViewSite getActionBars getToolBarManager (add action))
			(.. rv getViewSite getActionBars getToolBarManager (update true))
			;; In a proxy there's no easy way to emulate the self-reference (this)
			;; so we keep a record of the action for a given repl.
			(alter-var-root #'state assoc-in [:repls rv :action] action)))

;; Install/uninstall on a given REPL
(defn install-on [^REPLView rv]
  (if (bundle/available? ansi-console-bundle)
    (if (installed?)
      (add-toolbar-action rv)
      (log (str "can't install on " (.getPartName rv) ":\n" (status-msg))))
    (log (str "can't install on " (.getPartName rv) ":\n" (status-msg)))))
(defn uninstall-from [^REPLView rv]
  (remove-toolbar-action rv)
  (remove-style-listener rv)
  (alter-var-root #'state update-in [:repls] dissoc rv))

;; PartListener functions
;; Note: SWT listener methods are invoked by the UI thread.
(defn make-part-listener []
  ;(log "creating part listener")
  (reify
    IPartListener
    (partOpened [this p] (if (instance? REPLView p) (install-on p)))
    (partClosed [this p] (if (instance? REPLView p) (uninstall-from p)))
    (partActivated [this p] ())
    (partBroughtToTop [this p] ())
    (partDeactivated [this p] ())))
(defn remove-part-listener []
  ;(log (str "removing part listener"))
  (when-let [^IPartListener pl (:part-listener state)]
    (log (str "removing part listener " pl))
    (.. PlatformUI
	     getWorkbench
	     getActiveWorkbenchWindow
	     getPartService
	     (removePartListener pl))
    (alter-var-root #'state dissoc :part-listener)))
(defn add-part-listener []
  (if (bundle/available? ansi-console-bundle)
     (do
       (remove-part-listener)
	     ;; adding part listener
       (let [^IPartListener pl (make-part-listener)]
           (log (str "adding part listener"))
		       (.. PlatformUI
		         getWorkbench
		         getActiveWorkbenchWindow
		         getPartService
		         (addPartListener pl))
	         (alter-var-root #'state assoc :part-listener pl)))
     (log "Cannot add part listener: ANSI Console plugin no longer available?")))

;; Install/Uninstall ansi support
(defn install []
  (log "installing ANSI REPL")
  (when (and (bundle/available? ansi-console-bundle) (not (installed?)))
    ;; install a part listener for handling future REPLs
	  (add-part-listener)
    (flag-installed)
	  ;; handle existing REPLs
	  (->> (CCWPlugin/getREPLViews)
      ;(map #(do (log (str "found " (.getPartName %))) %))
	    (filter #(if-let [rvl (.getLaunch %)] (not (.isTerminated rvl))))
	    (map #(install-on %))
	    (doall)))
  (log (status-msg)))
(defn uninstall []
  (log "disabling ANSI REPL")
  ;; install a part listener for handling future REPLs
  (remove-part-listener)
  ;; handle existing REPLs
  (doall (map #(remove-style-listener %) (keys (:repls state))))
  ;; mark as uninstalled
  (flag-uninstalled)
  (log (status-msg))
)

;; Define eclipse key binding
(defn display-status [context]
  (eclipse/info-dialog "ANSI REPL" (status-msg)))
(defcommand ansi-repl-status "ANSI REPL: display status")
(defhandler ansi-repl-status display-status)
(defkeybinding ansi-repl-status "Alt+U A")

;; Install upon starting the plugin
(view-helpers/ui-async (install))