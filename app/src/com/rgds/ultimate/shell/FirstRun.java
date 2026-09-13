package com.rgds.ultimate.shell;

import android.app.Activity;
import android.content.*;
import org.json.*;

/** Durable one-time invitation, serialized with ordinary language and system dialogs. */
final class FirstRun {
    static String s(int n){return UiStrings.msg(String.format(java.util.Locale.ROOT,"ui_88%06x",n));}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("first_run_v88",0);}
    static void init(Context c){
        SharedPreferences p=prefs(c);if(p.contains("schema"))return;
        boolean existing=!c.getSharedPreferences("rgds_shell_user_v3",0).getAll().isEmpty();
        p.edit().putInt("schema",1).putBoolean("upgradedUser",existing).putBoolean("languageDone",existing)
            .putBoolean("setupEligible",!existing).putInt("invitationVersion",existing?1:0).putString("choice",existing?"UPGRADE_PRESERVED":"NEW").commit();
    }
    private static android.app.AlertDialog invitation;
    private static boolean handled(SharedPreferences p){String choice=p.getString("choice","");return choice.equals("SET_NOW")||choice.equals("NOT_NOW")||choice.equals("USER_CANCEL")||choice.equals("ALREADY_ACTUAL_HOME");}
    static void activeSettings(BottomHomeActivity a){
        ShellStateRepository.get(a).openSettings();
        SharedPreferences p=prefs(a);p.edit().putBoolean("activeVisit",true).putLong("visit",p.getLong("visit",0)+1).commit();
        PerfTrace.event("HOME_INVITATION_ACTIVE_SETTINGS");a.scheduleFirstRun();
    }
    static void observe(Context c,ShellStateRepository.Page page){if(page!=ShellStateRepository.Page.SETTINGS&&prefs(c).getBoolean("activeVisit",false))prefs(c).edit().putBoolean("activeVisit",false).commit();}
    static void decide(Context c,String choice){prefs(c).edit().putString("choice",choice).putInt("invitationVersion",2).putBoolean("activeVisit",false).commit();}
    static void maybeShow(BottomHomeActivity a){
        SharedPreferences p=prefs(a);
        if(!p.getBoolean("activeVisit",false)||handled(p)||invitation!=null)return;
        if(a.isFinishing()||a.isDestroyed()||!ShellCoordinator.get().owns(a)||!a.hasWindowFocus()||!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog()||a.editingSearch())return;
        ShellStateRepository.Snapshot state=ShellStateRepository.get(a).snapshot();
        if(!state.stateReady||state.page!=ShellStateRepository.Page.SETTINGS)return;
        if(DirectoryRequest.get(a).active()||JourneyGuide.saveDue(a))return;
        JSONObject home=HomeIntegration.snapshot(a);
        if(home.optBoolean("isDefaultHome")){decide(a,"ALREADY_ACTUAL_HOME");return;}
        final long visit=p.getLong("visit",0);
        String body=s(2)+"\n\n"+SettingsModel.homePath()+"\n\n"+s(3);
        android.app.AlertDialog d=new ShellDialogBuilder(a).setTitle(s(1)).setMessage(body)
            .setPositiveButton(s(4),(dialog,w)->{if(p.getLong("visit",0)==visit){decide(a,"SET_NOW");HomeIntegration.requestHome(a);}})
            .setNegativeButton(s(5),(dialog,w)->{if(p.getLong("visit",0)==visit)decide(a,"NOT_NOW");})
            .setOnCancelListener(dialog->{if(!a.isChangingConfigurations()&&!a.isFinishing()&&p.getLong("visit",0)==visit)decide(a,"USER_CANCEL");}).create();
        invitation=d;ShellDialogBuilder.onDismissed(d,()->{if(invitation==d){invitation=null;if(!handled(p))p.edit().putString("choice","INTERRUPTED").apply();a.scheduleFirstRun();}});
        d.show();if(d.isShowing())p.edit().putString("choice","SHOWN").putLong("shownAt",System.currentTimeMillis()).apply();
    }
    static boolean setupPending(Context c){SharedPreferences p=prefs(c);return p.getBoolean("setupEligible",false)&&p.getBoolean("directoryAccepted",false);}
    static void directoryAccepted(Context c){prefs(c).edit().putBoolean("directoryAccepted",true).commit();}
    static void setupStarted(Context c,String id){SetupJourney.taskStarted(c,id);prefs(c).edit().putString("setupSession",id).putBoolean("setupEligible",false).commit();}
    static void requested(Context c){prefs(c).edit().putBoolean("systemPending",true).putLong("requestedAt",System.currentTimeMillis()).commit();}
    static void result(Context c,JSONObject facts){prefs(c).edit().putBoolean("systemPending",false).putString("lastActualResult",facts.toString()).commit();}
    static JSONObject snapshot(Context c){return new JSONObject(prefs(c).getAll());}
    static void catalog(Activity a){
        MetadataManager m=MetadataManager.get(a);
        new ShellDialogBuilder(a).setTitle(s(6)).setMessage(m.catalog.brief()+"\n\n"+s(7))
            .setPositiveButton(UiStrings.msg("ui_572cf45ba436"),null)
            .setNeutralButton(s(8),(d,w)->{m.updateChinese();ShellStateRepository.get(a).openTasks();})
            .setNegativeButton(UiStrings.msg("ui_70174055607c"),(d,w)->UiDialogs.sources(a)).show();
    }
}
