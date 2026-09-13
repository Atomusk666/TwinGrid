package com.rgds.ultimate.shell;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import java.lang.ref.WeakReference;

/** Explicit two-window handoff; focus loss never initiates a launch. Main thread only. */
final class ShellCoordinator {
    enum Phase { BROWSING, PICKER, LAUNCHING, EXTERNAL, RETURNING, HOME_SUSPENDED, EXITED }
    private static final ShellCoordinator INSTANCE = new ShellCoordinator();
    static ShellCoordinator get() { return INSTANCE; }
    private WeakReference<BottomHomeActivity> bottom = new WeakReference<>(null);
    private WeakReference<TopDisplayActivity> top = new WeakReference<>(null);
    private WeakReference<BottomHomeActivity> topOwner = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Phase phase = Phase.BROWSING; // Runtime only: no stale external flag after process death.
    private boolean topPending;
    private boolean bottomDeparted;
    private long launchEpoch;
    private int attempts;

    void attach(BottomHomeActivity activity) {
        // Android separates HOME and standard tasks even for a singleTask activity.
        // Retire the previous surface while retaining the shared repository and session.
        BottomHomeActivity previous=bottom.get();
        bottom = new WeakReference<>(activity);
        if(previous!=null&&previous!=activity&&topOwner.get()==previous)hideTop();
        if(previous!=null&&previous!=activity&&!previous.isFinishing())previous.finish();
        if (phase == Phase.EXITED) phase = Phase.BROWSING;
        if(phase==Phase.PICKER&&!DirectoryRequest.get(activity).picker())phase=Phase.BROWSING;
        if(phase==Phase.LAUNCHING){phase=Phase.BROWSING;ShellStateRepository.get(activity).cancelLaunchRecord();}
        if(activity.getSharedPreferences("home_session_v77",0).getBoolean("externalOpen",false)){
            // A persisted dispatch marker means only that an external return remains unobserved.
            phase=Phase.EXTERNAL;bottomDeparted=true;
        }
        { if(PerfTrace.isEnabled()) Log.i("TwinGrid", "BOTTOM_ATTACH task=" + activity.getTaskId()); }
    }
    void attach(TopDisplayActivity activity) {
        top = new WeakReference<>(activity); topOwner = new WeakReference<>(bottom.get()); topPending = false;
        { if(PerfTrace.isEnabled()) Log.i("TwinGrid", "TOP_ATTACH task=" + activity.getTaskId()); }
        if (phase != Phase.BROWSING && phase != Phase.RETURNING && phase != Phase.LAUNCHING) activity.finishAndRemoveTask();
    }
    void detach(Activity activity) {
        if (top.get() == activity) { top.clear(); topOwner.clear(); topPending = false; }
        if (bottom.get() == activity) bottom.clear();
    }
    void bottomPaused() {
        if (phase == Phase.LAUNCHING || phase == Phase.EXTERNAL) bottomDeparted = true;
        log("BOTTOM_PAUSE");
    }
    void bottomStopped(BottomHomeActivity owner){
        if(bottom.get()==owner&&phase==Phase.BROWSING){hideTop();log("BOTTOM_BACKGROUND_HIDE_TOP");}
    }
    void bottomResumed() {
        log("BOTTOM_RESUME");
        if (phase == Phase.EXTERNAL && bottomDeparted) {
            phase = Phase.RETURNING;
            bottomDeparted = false;
            log("EXTERNAL_RETURN_OBSERVED");
            phase = Phase.BROWSING;
            BottomHomeActivity owner=bottom.get();
            if(owner!=null) {
                owner.getSharedPreferences("home_session_v77",0).edit().putBoolean("externalOpen",false).apply();
                ShellStateRepository.get(owner).externalReturned();LaunchDiagnostic.returned(owner);
                RomLibrary.get(owner).refreshSaves();
            }
        }
        if (phase == Phase.BROWSING) ensureTop();
    }
    void pickerStarted() {
        phase = Phase.PICKER; hideTop(); log("PICKER_START");
    }
    void pickerFinished() {
        phase = Phase.BROWSING; ensureTop(); log("PICKER_FINISH");
    }
    void launchStarted() {
        // A read check must not remove the focused Shell window. Android returns
        // focus asynchronously; a fast check otherwise cancels its own request.
        ++launchEpoch;phase = Phase.LAUNCHING; bottomDeparted = false; log("LAUNCH_START");
    }
    boolean launchWindowReady(BottomHomeActivity owner) {
        if (owner == null || bottom.get() != owner || owner.isFinishing()) return false;
        TopDisplayActivity upper = top.get();
        return owner.hasWindowFocus() || (topOwner.get() == owner && upper != null
                && !upper.isFinishing() && upper.surfaceHasWindowFocus());
    }
    void beginExternalHandoff() {
        if (phase != Phase.LAUNCHING) throw new IllegalStateException("No pending launch");
        hideTop();
    }
    void launchAccepted() { phase = Phase.EXTERNAL;BottomHomeActivity a=bottom.get();if(a!=null)a.getSharedPreferences("home_session_v77",0).edit().putBoolean("externalOpen",true).apply();log("EXTERNAL_START_ACCEPTED"); }
    void homeIntent(Intent intent){
        if(intent==null||!intent.hasCategory(Intent.CATEGORY_HOME))return;
        BottomHomeActivity a=bottom.get();
        if(phase==Phase.LAUNCHING&&a!=null)a.cancelPendingLaunch("CANCELLED_HOME_RETURN");
        if(phase==Phase.PICKER&&(a==null||!DirectoryRequest.get(a).picker())){phase=Phase.BROWSING;log("HOME_RETURN_FROM_SYSTEM");}
        if(phase==Phase.EXTERNAL){
            // HOME selects the Shell surface, not an implicit resume/exit of the external game.
            // The same return path keeps the receiver session observation UNKNOWN.
            bottomDeparted=true;bottomResumed();log("HOME_RETURN_SESSION_UNKNOWN");
        }
    }
    void launchFailed() {
        BottomHomeActivity owner=bottom.get();
        if(owner!=null)ShellStateRepository.get(owner).cancelLaunchRecord();
        if(owner!=null)owner.getSharedPreferences("home_session_v77",0).edit().putBoolean("externalOpen",false).apply();
        phase = Phase.BROWSING; bottomDeparted = false; ensureTop(); log("LAUNCH_FAILURE");
    }
    boolean owns(BottomHomeActivity a){return bottom.get()==a;}
    void guideReady(){BottomHomeActivity a=bottom.get();if(a!=null&&!a.isFinishing())a.scheduleFirstRun();}
    boolean canInteract() { return phase == Phase.BROWSING; }
    boolean launchPending() { return phase == Phase.LAUNCHING; }
    long launchEpoch(){return launchEpoch;}
    String phaseName(){return phase.name();}
    void backFromEitherScreen(){BottomHomeActivity activity=bottom.get();if(activity!=null&&(canInteract()||launchPending()))activity.onBackPressed();}
    void openSettingsActively(){BottomHomeActivity a=bottom.get();if(a!=null&&canInteract())FirstRun.activeSettings(a);}
    void openTasksActively(){BottomHomeActivity a=bottom.get();if(a!=null&&canInteract())a.openTasksActively();}
    void chooseRomDirectory(){chooseRomDirectory("SHARED");}
    void chooseRomDirectory(String source){BottomHomeActivity activity=bottom.get();if(activity!=null)activity.chooseRomDirectory(source);}
    void launchSelectedFromEitherScreen() {
        BottomHomeActivity activity = bottom.get();
        if (activity != null && canInteract()) activity.launchSelectedGame();
    }
    void performSettingFromEitherScreen(int index) {
        BottomHomeActivity activity=bottom.get();
        if(activity!=null&&canInteract())activity.performSetting(index);
    }
    void openSearch(){BottomHomeActivity a=bottom.get();if(a!=null&&canInteract()){
        Intent i=new Intent(a,BottomHomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);ActivityOptions options=ActivityOptions.makeBasic();options.setLaunchDisplayId(0);a.startActivity(i,options.toBundle());
        handler.post(a::onLocalSearchRequested);
    }}
    void endTextEditing(){BottomHomeActivity a=bottom.get();if(a!=null)a.endTextEditing();}
    void refreshLocale(){BottomHomeActivity a=bottom.get();TopDisplayActivity b=top.get();if(a!=null)a.refreshLocale();if(b!=null)b.refreshLocale();ShellDialogBuilder.refreshLocale();}
    void chooseCover(String id){BottomHomeActivity a=bottom.get();if(a!=null&&canInteract())a.chooseCover(id);}
    private void hideTop() {
        TopDisplayActivity activity = top.get();
        top.clear(); topOwner.clear(); topPending = false;
        if (activity != null) {activity.closeSurface();activity.finishAndRemoveTask();}
    }
    void ensureTop() {
        BottomHomeActivity owner = bottom.get();
        if (owner == null || owner.isFinishing() || phase != Phase.BROWSING ||
                topPending || (top.get() != null && !top.get().isFinishing())) return;
        PowerManager power = (PowerManager) owner.getSystemService(Context.POWER_SERVICE);
        if (power != null && !power.isInteractive()) return;
        DisplayManager dm = (DisplayManager) owner.getSystemService(Context.DISPLAY_SERVICE);
        Display display = dm == null ? null : dm.getDisplay(2);
        if (display == null || !display.isValid()) {
            ShellStateRepository.get(owner).markTopLaunchFailed("Display 2 unavailable"); return;
        }
        if (attempts >= 3) { log("TOP_RETRY_LIMIT"); return; }
        topPending = true; attempts++;
        Intent intent = new Intent(owner, TopDisplayActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(2);
        try {
            owner.startActivity(intent, options.toBundle());
            log("TOP_LAUNCH_REQUEST");
        } catch (RuntimeException error) {
            topPending = false;
            ShellStateRepository.get(owner).markTopLaunchFailed(error.toString());
            Log.e("TwinGrid", "TOP_LAUNCH_FAILURE", error);
        }
    }
    void topReady() { attempts = 0; }
    void exit(Activity source) {exitToDesktop(source,new ComponentName("com.android.launcher3","com.android.launcher3.uioverrides.QuickstepLauncher"));}
    void exitToDesktop(Activity source,ComponentName target) {
        phase = Phase.EXITED; log("USER_EXIT");
        ShellStateRepository.get(source).flush();
        ShellStateRepository.get(source).cancelLaunchRecord();
        hideTop();
        BottomHomeActivity owner = bottom.get();
        bottom.clear();
        if (owner != null) owner.finishAndRemoveTask();
        Intent desktop = new Intent(Intent.ACTION_MAIN);
        desktop.setComponent(target);
        desktop.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityOptions options = ActivityOptions.makeBasic(); options.setLaunchDisplayId(0);
        try { source.startActivity(desktop, options.toBundle()); }
        catch (RuntimeException error) { Log.e("TwinGrid", "DESKTOP_OPEN_FAILURE", error); }
    }
    private void log(String event) {
        BottomHomeActivity a=bottom.get();if(a!=null)MetadataManager.get(a).suspend(phase!=Phase.BROWSING&&phase!=Phase.RETURNING&&phase!=Phase.PICKER);
        { if(PerfTrace.isEnabled()) Log.i("TwinGrid", event + " phase=" + phase); }
    }
}
