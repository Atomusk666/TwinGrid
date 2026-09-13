package com.rgds.ultimate.shell;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

/** Presentation lifetime of the first committed library, independent of item/task lifetime. */
final class SetupJourney {
    static String text(String zh,String en){return LocaleSettings.ui().startsWith("zh")?zh:en;}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("first_setup_ui_v117",0);}
    private static final java.util.WeakHashMap<android.app.Activity,String> drawn=new java.util.WeakHashMap<>();
    static boolean presented(android.app.Activity a){return prefs(a).getString("session","").equals(drawn.get(a));}
    static void drawn(android.app.Activity a){if(!(a instanceof BottomHomeActivity)||presented(a))return;drawn.put(a,prefs(a).getString("session",""));event(a,"PREPARATION_DRAWN");ShellCoordinator.get().guideReady();}
    static boolean exists(Context c){return prefs(c).contains("session");}
    static boolean held(Context c){return exists(c)&&!prefs(c).getBoolean("left",false);}
    static void begin(Context c,long request){
        SharedPreferences p=prefs(c);if(p.contains("session"))return;
        p.edit().putString("session",java.util.UUID.randomUUID().toString()).putLong("romRequest",request)
            .putBoolean("left",false).putBoolean("acknowledged",false).putString("scan","{}").commit();
        event(c,"ROM_COMMIT_PREPARATION");ShellStateRepository.get(c).openPreparation();
    }
    static void saveRequest(Context c,long id){if(exists(c)){prefs(c).edit().putLong("saveRequest",id).apply();event(c,"SAVE_CHILD_REQUEST");}}
    static void taskStarted(Context c,String id){if(!exists(c))return;SharedPreferences p=prefs(c);if(!p.contains("taskSession")){p.edit().putString("taskSession",id).commit();event(c,"FIRST_SETUP_BOUND");}}
    static void scan(Context c,ScanProgress scan){
        if(!exists(c))return;SharedPreferences p=prefs(c);
        int bound=p.getInt("generation",-1);
        if(scan.phase.equals("QUEUED")&&(bound<0||p.getBoolean("resumeScan",false))){p.edit().putInt("generation",scan.session).putBoolean("resumeScan",false).commit();bound=scan.session;event(c,"SCAN_BOUND");}
        if(bound==scan.session){p.edit().putString("scan",scan.json().toString()).apply();}
    }
    static void task(Context c,CoverTask.Snapshot task){
        if(!exists(c)||!task.id.equals(prefs(c).getString("taskSession",""))||task.id.isEmpty())return;
        prefs(c).edit().putString("task",task.json().toString()).apply();
    }
    static JSONObject read(Context c,String key){try{return new JSONObject(prefs(c).getString(key,"{}"));}catch(Exception e){return new JSONObject();}}
    static void leave(Context c,boolean result){prefs(c).edit().putBoolean("left",true).putBoolean("acknowledged",result).commit();event(c,result?"RESULT_ACKNOWLEDGED":"USER_BROWSE_EARLY");ShellStateRepository.get(c).setCategory(ShellStateRepository.Category.NDS);}
    static void reopen(Context c){if(exists(c)){event(c,"USER_REOPEN_PREPARATION");ShellStateRepository.get(c).openPreparation();}}
    static void resume(Context c){prefs(c).edit().putBoolean("resumeScan",true).commit();event(c,"USER_RESUME_SCAN");RomLibrary.get(c).scan();}
    static String controlKey(Context c,int action){Model m=new Model(c);return prefs(c).getString("session","")+"|"+action+"|"+(action==0?(m.resumable?"RESUME":m.result?"RESULT":"BROWSE"):"NAV");}
    static String stateLabel(String state){
        switch(state){
            case "COMPLETE":return text("已完成","Complete");case "PARTIAL":return text("部分完成","Partly complete");case "EMPTY":return text("无条目","No entries");
            case "FAILED":case "ERROR":return text("失败，请检查","Failed; check details");case "CANCELED":return text("已取消","Canceled");
            case "NETWORK":return text("等待网络","Waiting for network");case "METERED":return text("等待非计费网络","Waiting for unmetered network");
            case "RETRY_WAIT":return text("等待重试","Waiting to retry");case "PAUSED":case "PAUSING":return text("已暂停","Paused");
            case "INTERRUPTED":return text("已中断，可在任务中继续","Interrupted; resume in Tasks");case "SLEEP":return text("设备休眠，稍后继续","Device asleep; will resume");
            case "GAME":return text("游戏运行中，稍后继续","Game running; will resume");default:return text("进行中","In progress");
        }
    }
    static final class Model {
        final String title,scan,local,cover,action;final boolean result,resumable;
        Model(Context c){
            MetadataManager m=MetadataManager.get(c);JSONObject s=read(c,"scan"),t=read(c,"task");
            String phase=s.optString("phase","QUEUED"),state=t.optString("state","");
            boolean scanActive=s.optBoolean("active",true),scanBad=phase.equals("FAILED")||phase.equals("PARTIAL")||phase.equals("CANCELED");
            resumable=scanActive&&!RomLibrary.get(c).progress().active()&&!RomLibrary.get(c).isScanning();
            boolean empty=!scanActive&&!scanBad&&s.optInt("available")==0;
            boolean terminal=state.equals("COMPLETE")||state.equals("PARTIAL")||state.equals("EMPTY")||state.equals("ERROR");
            boolean wait=state.equals("NETWORK")||state.equals("METERED")||state.equals("RETRY_WAIT")||state.equals("PAUSED")||state.equals("INTERRUPTED")||state.equals("SLEEP")||state.equals("GAME");
            boolean policy=t.length()==0&&(m.isPaused()||c.getSharedPreferences("metadata_v4",0).contains("auto")&&!m.enabled());
            result=!scanActive&&(scanBad||empty||terminal||wait||policy);
            title=resumable?text("准备已中断，可继续","Preparation interrupted"):
                scanBad?text("读取结果需要检查","Check the scan result"):empty?text("此目录没有可用游戏","No games in this folder"):
                result?terminal?text("本轮准备结果","Preparation result"):text("游戏可浏览，资料尚未完成","Browse now; details are pending"):text("首次准备","Preparing your library");
            scan=(scanActive?text("正在查找 / 读取游戏","Finding / reading games"):text("游戏读取：","Game scan: ")+stateLabel(phase))+"\n"+
                text("已发现 ","Found ")+s.optInt("discovered")+text(" · 可用 "," · Available ")+s.optInt("available")+text(" · 读取失败 "," · Unreadable ")+s.optInt("errors");
            local=empty?text("无需整理空目录","No entries to organize"):
                t.length()==0?policy?text("资料整理遵循已保存的暂停 / 关闭选择","Saved pause / automatic-off choice retained"):text("准备离线资料与本地封面中","Preparing offline details and local covers"):
                text("离线资料 / 本地图：已检查 ","Offline details / local art: checked ")+t.optInt("localProcessed")+" / "+t.optInt("localTotal")+
                    (t.optBoolean("localDone")?text(" · 已检查完"," · Check finished"):text(" · 进行中"," · In progress"));
            cover=t.length()==0?text("封面等待本地整理；缺资料不强行匹配","Covers follow local checks; uncertain matches stay open"):
                text("封面：","Covers: ")+stateLabel(state)+" · "+t.optInt("processed")+" / "+t.optInt("total")+"\n"+
                text("下载 ","Downloaded ")+t.optInt("downloads")+text(" · 复用 "," · Reused ")+t.optInt("reused")+
                text(" · 无图 / 待确认 / 失败 "," · Missing / identity / failed ")+t.optInt("missing")+" / "+t.optInt("identity")+" / "+t.optInt("failed");
            action=resumable?text("继续准备","Resume preparation"):result?(wait&&t.optBoolean("localDone")?text("进入游戏库，封面稍后补齐","Enter library; covers can wait"):text("进入游戏库","Enter game library")):text("先浏览游戏，后台继续","Browse games; continue in background");
        }
    }
    static void event(Context c,String reason){
        SharedPreferences p=prefs(c);try{JSONArray old=new JSONArray(p.getString("events","[]")),next=new JSONArray();for(int i=Math.max(0,old.length()-63);i<old.length();i++)next.put(old.get(i));next.put(new JSONObject().put("reason",reason).put("at",System.currentTimeMillis()).put("session",p.getString("session","")).put("romRequest",p.getLong("romRequest",0)).put("saveRequest",p.getLong("saveRequest",0)).put("generation",p.getInt("generation",-1)).put("taskSession",p.getString("taskSession","")).put("left",p.getBoolean("left",false)));p.edit().putString("events",next.toString()).apply();android.util.Log.i("TwinGridSetup",next.getJSONObject(next.length()-1).toString());}catch(Exception ignored){}
    }
    static JSONObject snapshot(Context c){JSONObject o=new JSONObject(prefs(c).getAll());try{o.put("scan",read(c,"scan")).put("task",read(c,"task")).put("events",new JSONArray(prefs(c).getString("events","[]")));}catch(Exception ignored){}return o;}
}
