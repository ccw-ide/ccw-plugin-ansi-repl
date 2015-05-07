# ccw-plugin-ansi-repl

This project is a Counterclockwise User Plugin which enables ANSI colors to be displayed in the REPL when the ANSI Eclipse plugin is available.

This plugin's state is stable.

NOTE: this plugin will only work with a version of Counterclockwise that is yet to be released (as of 2015/5/7). You can use continuous integration-based CCW version to test it, in the mean time http://updatesite.ccw-ide.org/branch/master/ )

## Pre-requisite

The ANSI EConsole Eclipse Plugin ( http://marketplace.eclipse.org/content/ansi-escape-console ) must be installed and enabled.

## Install

The `~/.ccw/` folder is where Counterclockwise searches for User Plugins.

It is recommended to layout User Plugins inside this folder by mirroring Github's namespacing. So if you clone laurentpetit/ccw-plugin-ansi-repl, you should do the following:

- Create a folder named `~/.ccw/laurentpetit/`
- Clone this project from `~/.ccw/laurentpetit/`

        mkdir -p ~/.ccw/laurentpetit
        cd ~/.ccw/laurentpetit
        git clone https://github.com/laurentpetit/ccw-plugin-ansi-repl.git

- If you have already installed ccw-plugin-manager (https://github.com/laurentpetit/ccw-plugin-manager.git), then type `Alt+U S` to re[S]tart User Plugins (and thus ccw-plugin-ansi-repl will be found and loaded)
- If you have not already installed ccw-plugin-manager, restart your Eclipse / Counterclockwise/Standalone instance.

## Usage

This plugin enables ANSI characters to be printed accordingly in a REPL log area.

The plugin also adds an icon to REPLs which allows to disable ANSI EConsole styling.


## Uninstall

To uninstall a User plugin, simply remove its directory. At the next Eclipse/Counterclockwise restart, it'll be gone.


## License

Copyright © 2009-2015 Laurent Petit
Distributed under the Eclipse Public License, the same as Clojure.

Original code by François Rey at https://gist.github.com/fmjrey/9889500 under the MIT License


