package com.rgds.ultimate.shell;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** One interactive owner. Page events queue work; focus/readiness only drain it. */
final class JourneyGuide {
    static String s(int n){return UiStrings.msg(String.format(java.util.Locale.ROOT,"ui_115%05x",n));}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("journey_v115",0);}
    private static boolean taskRequested;
    private static final java.util.WeakHashMap<BottomHomeActivity,String> observed=new java.util.WeakHashMap<>();
    private static void event(Context c,String action){SharedPreferences p=prefs(c);try{org.json.JSONArray old=new org.json.JSONArray(p.getString("events","[]")),events=new org.json.JSONArray();for(int i=Math.max(0,old.length()-23);i<old.length();i++)events.put(old.get(i));events.put(new org.json.JSONObject().put("action",action).put("revision",p.getLong("revision",0)).put("at",System.currentTimeMillis()).put("save",p.getString("save","")).put("game",p.getString("game","")));p.edit().putString("events",events.toString()).apply();}catch(Exception ignored){}PerfTrace.event("GUIDE_EVENT "+action+" save="+p.getString("save",""));}
    static void observe(BottomHomeActivity a,ShellStateRepository.Snapshot state){
        if(!state.stateReady)return;
        FirstRun.observe(a,state.page);
        SharedPreferences p=prefs(a);
        if(!p.contains("schema")){
            p.edit().putInt("schema",1).putString("game",state.romTreeUri==null?"NEW":"DONE")
                .putString("save",state.backupTreeUri==null?"NEW":"DONE").putBoolean("firstRomCommitted",state.romTreeUri!=null).commit();
        }
        if(state.romTreeUri!=null&&!DirectoryRequest.get(a).active()&&!"DONE".equals(p.getString("game","")))p.edit().putString("game","DONE").putBoolean("firstRomCommitted",true).apply();
        if(state.backupTreeUri!=null&&!DirectoryRequest.get(a).active()&&"PENDING".equals(p.getString("save","")))p.edit().putString("save","DONE").apply();
        if(!p.contains("firstRomCommitted"))p.edit().putBoolean("firstRomCommitted",state.romTreeUri!=null).commit();
        if(state.page!=ShellStateRepository.Page.TASKS)taskRequested=false;
        // This post belongs to a bound page, not a guessed lifecycle delay.
        String key=state.page+"|"+(state.romTreeUri!=null);if(!key.equals(observed.put(a,key)))a.scheduleFirstRun();
    }
    static boolean pendingLibrary(Context c){SharedPreferences p=prefs(c);String save=p.getString("save","");return save.equals("PENDING")||save.equals("SHOWN")||save.equals("INTERRUPTED")||save.equals("FAILURE_PENDING")||waiting(c);}
    static void activeTasks(BottomHomeActivity a){
        if(SetupJourney.exists(a)&&!SetupJourney.snapshot(a).optBoolean("acknowledged")&&ShellStateRepository.get(a).page()!=ShellStateRepository.Page.PREPARING){SetupJourney.reopen(a);return;}
        if("DONE".equals(prefs(a).getString("tasks",""))&&ShellStateRepository.get(a).page()==ShellStateRepository.Page.TASKS){GapUi.history(a);return;}
        ShellStateRepository.get(a).openTasks();taskRequested=true;
        a.getWindow().getDecorView().postOnAnimation(()->drain(a));
    }
    static void drain(BottomHomeActivity a){
        if(a.isFinishing()||a.isDestroyed()||!ShellCoordinator.get().owns(a))return;
        String reason=!ShellCoordinator.get().canInteract()?"EXTERNAL_OR_PICKER":!a.hasWindowFocus()?"WINDOW":ShellDialogBuilder.hasOpenDialog()||a.editingSearch()?"DIALOG_OR_EDITOR":"";
        if(!reason.isEmpty()){if(pendingLibrary(a)&&!reason.equals(prefs(a).getString("deferred",""))){prefs(a).edit().putString("deferred",reason).apply();event(a,"DEFER_"+reason);}return;}
        if(DirectoryAccess.busy()){DirectoryAccess.showSaveNotice(a);return;}
        ShellStateRepository.Snapshot state=ShellStateRepository.get(a).snapshot();if(!state.stateReady)return;
        SharedPreferences p=prefs(a);
        if(state.page==ShellStateRepository.Page.TASKS&&taskRequested&&!"DONE".equals(p.getString("tasks",""))){
            taskRequested=false;showTasks(a,true);return;
        }
        DirectoryRequest flow=DirectoryRequest.get(a);
        if(flow.phase()==DirectoryRequest.Phase.OFFERED){flow.resumeOffer(a);return;}
        if((state.page!=ShellStateRepository.Page.LIBRARY&&state.page!=ShellStateRepository.Page.SETTINGS&&state.page!=ShellStateRepository.Page.PREPARING)||waiting(a))return;
        if(flow.active())return;
        if(state.page==ShellStateRepository.Page.LIBRARY&&state.romTreeUri==null&&"NEW".equals(p.getString("game","NEW")))
            flow.request(a,false,"AUTO_LIBRARY",true);
        else if(state.romTreeUri!=null&&saveDue(a)&&(state.page!=ShellStateRepository.Page.PREPARING||SetupJourney.presented(a)))
            flow.request(a,true,"AUTO_SAVE_NEXT",true);
    }
    static boolean saveDue(Context c){SharedPreferences p=prefs(c);String stage=p.getString("save","");return p.getBoolean("saveObligation",false)&&(stage.equals("PENDING")||stage.equals("INTERRUPTED")||stage.equals("SHOWN"));}
    static void directoryOffered(Context c,boolean saves){prefs(c).edit().putString(saves?"save":"game","SHOWN").apply();event(c,saves?"SAVE_VISIBLE":"GAME_VISIBLE");}
    static void directoryPicker(Context c,boolean saves,long id){prefs(c).edit().putString("waiting",saves?"save":"game").putString(saves?"save":"game","PICKER").putLong("revision",id).commit();event(c,saves?"SAVE_PICKER":"GAME_PICKER");}
    static void directoryValidating(Context c,boolean saves){prefs(c).edit().putString("waiting",saves?"save":"game").putString(saves?"save":"game","VALIDATING").apply();event(c,saves?"SAVE_VALIDATING":"GAME_VALIDATING");}
    static boolean waiting(Context c){DirectoryRequest.Phase phase=DirectoryRequest.get(c).phase();return phase==DirectoryRequest.Phase.PICKER||phase==DirectoryRequest.Phase.VALIDATING||phase==DirectoryRequest.Phase.COMMITTING;}
    static void directoryEnded(Context c,boolean saves,String phase,String reason){prefs(c).edit().putString("waiting","").putString(saves?"save":"game",phase).putString(saves?"saveEnd":"gameEnd",reason).commit();ActionKeyLatch.reset();event(c,(saves?"SAVE_":"GAME_")+reason);}
    static void directoryAccepted(Context c,boolean saves,Uri uri){
        SharedPreferences p=prefs(c);boolean first=!p.getBoolean("firstRomCommitted",false);
        SharedPreferences.Editor edit=p.edit().putString("waiting","").putString(saves?"save":"game","DONE");
        if(!saves){edit.putBoolean("firstRomCommitted",true);if(first&&"NEW".equals(p.getString("save","NEW"))&&ShellStateRepository.get(c).snapshot().backupTreeUri==null)edit.putString("save","PENDING").putBoolean("saveObligation",true);}
        edit.commit();if(!saves&&first)SetupJourney.begin(c,DirectoryRequest.get(c).id());event(c,saves?"SAVE_COMMITTED":"ROM_COMMITTED");ShellCoordinator.get().guideReady();
    }
    static void restored(Context c,boolean savedActivity){
        // Migration from pre-hotfix offers. The transaction owns all subsequent recovery.
        if(!c.getSharedPreferences("directory_request_v116",0).contains("request")){
            SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit().putString("waiting","");
            for(String key:new String[]{"game","save"}){String stage=p.getString(key,"");if(stage.equals("SHOWN"))e.putString(key,key.equals("save")?"PENDING":"NEW");if(stage.equals("PICKER")||stage.equals("VALIDATING"))e.putString(key,"INTERRUPTED");}e.commit();
        }
        SharedPreferences p=prefs(c);
        if(!p.contains("saveObligation")){
            String stage=p.getString("save","");boolean auto=false;
            try{org.json.JSONObject request=new org.json.JSONObject(c.getSharedPreferences("directory_request_v116",0).getString("request","{}"));auto=request.optString("source").startsWith("AUTO_SAVE")&&request.optBoolean("saves");}catch(Exception ignored){}
            p.edit().putBoolean("saveObligation",stage.equals("PENDING")||auto&&(stage.equals("INTERRUPTED")||stage.equals("SHOWN")||stage.equals("PICKER")||stage.equals("VALIDATING"))).commit();
        }
        event(c,savedActivity?"ACTIVITY_RESTORED":"PROCESS_ENTERED");
    }
    static void regainedFocus(Context c){}
    static void saveAccess(Context c,Uri uri,boolean readable){if(uri==null||!uri.equals(ShellStateRepository.get(c).snapshot().backupTreeUri))return;SharedPreferences p=prefs(c);String stage=p.getString("save","");if(!readable&&stage.equals("DONE")){p.edit().putString("save","INVALID_ACCESS").apply();event(c,"SAVE_ACCESS_UNAVAILABLE");}else if(readable&&stage.equals("INVALID_ACCESS")){p.edit().putString("save","DONE").apply();event(c,"SAVE_ACCESS_RESTORED");}}
    static void showTasks(BottomHomeActivity a,boolean first){
        AlertDialog d=new ShellDialogBuilder(a).setTitle(s(9)).setMessage(s(10))
            .setPositiveButton(s(11),(x,w)->{if(first)prefs(a).edit().putString("tasks","DONE").apply();})
            .setOnCancelListener(x->{if(first)prefs(a).edit().putString("tasks","DONE").apply();})
            .setNeutralButton(s(12),(x,w)->{if(first)prefs(a).edit().putString("tasks","DONE").apply();FirstRun.activeSettings(a);}).show();
        if(first&&d.isShowing()){prefs(a).edit().putString("tasks","SHOWN").apply();PerfTrace.event("GUIDE_TASKS_VISIBLE");}
    }
    static org.json.JSONObject snapshot(Context c){org.json.JSONObject o=new org.json.JSONObject(prefs(c).getAll());try{o.put("events",new org.json.JSONArray(prefs(c).getString("events","[]")));}catch(Exception ignored){}return o;}
}
