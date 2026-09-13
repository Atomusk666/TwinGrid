package com.rgds.ultimate.shell;

/** Activity-owned actions that need an Android UI or external launch context. */
interface ShellActionHandler {
    void onLaunchRequested();

    void onBackRequested();

    void onDetailsRequested();

    void onFilterRequested();

    void onLocalSearchRequested();
}
