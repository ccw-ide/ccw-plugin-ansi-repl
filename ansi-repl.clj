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
    org.osgi.framework.Bundle
    ))

(def ansi-console-bundle (b/bundle "net.mihai-nita.ansicon.plugin"))

(defn- repl-view? [p]
  (instance? REPLView p))

(defn log [m]
  (CCWPlugin/log m))

(def make-ansi-listener (k/make-factory ansi-console-bundle "mnita.ansiconsole.participants.AnsiConsoleStyleListener" []))

(defn add-style-listener [^REPLView p]
  (log (str "Adding listener to " p))
  (if (b/available? ansi-console-bundle)
    (do
      (.. p
        logPanel
        (addLineStyleListener (make-ansi-listener))))
    (e/info-dialog "ANSI REPL" "Cannot add listener: ANSI Console plugin not available.")))

(defn repl-listener []
  (reify
    IPartListener
    (partOpened [this p] (if (repl-view? p) (add-style-listener p)))
    (partClosed [this p] ())
    (partActivated [this p] ())
    (partBroughtToTop [this p] ())
    (partDeactivated [this p] ())))

(defn install []
  (if (b/available? ansi-console-bundle)
    (do
      (log "Enabling ANSI REPL")
      (r/ui-async 
        ;PlatformUI.getWorkbench().getActiveWorkbenchWindow().getPartService().addPartListener(partListener)
        (log "Adding part listener")
        (.. PlatformUI
          getWorkbench
          getActiveWorkbenchWindow
          getPartService
          (addPartListener (repl-listener)))
        (log "Processing existing REPLs")
        (->> (CCWPlugin/getREPLViews)
          (filter #(if-let [l (.getLaunch %)] (not (.isTerminated l))))
          (map #(add-style-listener %))
          (doall))))))

(install)

(def msg (if (b/available? ansi-console-bundle)
           "ANSI Console bundle active"
           (if ansi-console-bundle
             (str "ANSI Console bundle in state " (.getState ansi-console-bundle))
             "ANSI Console bundle not found!")))

(defn greet [context]                                ; <1>
  (e/info-dialog "ANSI REPL" msg))

(defcommand greeter "ANSI REPL check")                ; <2>
(defhandler greeter greet)
(defkeybinding greeter "Alt+U A")