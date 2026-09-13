package com.rgds.ultimate.shell;

/** Stable launch reason numbers; UI and exported diagnostics use the same mapping. */
final class LaunchFailure extends java.io.IOException {
    static final int FILE_CHANGED=66, TIMEOUT=67, CANCELLED=68, ENVIRONMENT_CHANGED=69, CHECK_FAILED=70;
    final int reason;
    LaunchFailure(int reason){super("launch_reason_"+reason);this.reason=reason;}
    static int reason(Exception e){
        if(e instanceof LaunchFailure)return ((LaunchFailure)e).reason;
        if(e instanceof DrasticLauncher.Failure)return ((DrasticLauncher.Failure)e).message;
        if(e instanceof android.os.OperationCanceledException||e instanceof java.io.InterruptedIOException)return CANCELLED;
        if(e instanceof android.content.pm.PackageManager.NameNotFoundException)return 40;
        if(e instanceof android.content.ActivityNotFoundException)return 41;
        if(e instanceof java.io.FileNotFoundException)return FILE_CHANGED;
        if(e instanceof java.io.IOException||e instanceof SecurityException)return 42;
        return CHECK_FAILED;
    }
    static String state(int reason){return reason==TIMEOUT?"PREPARATION_TIMEOUT":reason==CANCELLED?"CANCELLED":reason==52?"SYSTEM_REJECTED":"PRECONDITION_FAILED";}
    static int message(int reason){return reason==36?50:reason==39||reason==49?49:reason==44?51:reason;}
}
