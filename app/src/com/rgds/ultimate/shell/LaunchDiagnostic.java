package com.rgds.ultimate.shell;
import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
/** Eight requests, eight events each. No filenames, paths, game bytes or system logs. */
final class LaunchDiagnostic {
    private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("launch_diagnostic_v115",0);}
    static synchronized String begin(Context c,GameEntry game){return begin(c,game,"SELECTED_ROM",-1,-1);}
    static synchronized String begin(Context c,GameEntry game,String source,int display,int task){
        String id=java.util.UUID.randomUUID().toString();try{
            JSONObject old=read(c),o=new JSONObject().put("schema",2).put("requestId",id).put("previousRequestId",old.optString("requestId"))
                .put("at",System.currentTimeMillis()).put("action","START_SELECTED_ROM").put("actionSource",source)
                .put("sourceDisplay",display).put("sourceTask",task).put("gameId",game.gameId)
                .put("uriScheme",game.uri.getScheme()).put("provider",game.uri.getAuthority())
                .put("uriHash",hash(game.uriString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .put("scannedBytes",game.romBytes).put("scannedModifiedAt",game.romModifiedAt)
                .put("shellReadCheck",false).put("outcome","REQUESTED").put("loadObservation","UNKNOWN")
                .put("receiverSessionObservation","UNKNOWN").put("sdk",android.os.Build.VERSION.SDK_INT)
                .put("androidVersion",android.os.Build.VERSION.RELEASE).put("fingerprint",android.os.Build.FINGERPRINT)
                .put("model",android.os.Build.MODEL).put("abis",new JSONArray(java.util.Arrays.asList(android.os.Build.SUPPORTED_ABIS)))
                .put("appUser",android.os.Process.myUid()/100000);
            android.content.pm.PackageInfo shell=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            o.put("productName",ProductIdentity.NAME).put("packageName",c.getPackageName()).put("versionName",shell.versionName).put("versionCode",shell.versionCode).put("shellVersion",shell.versionName).put("shellCode",shell.versionCode);write(c,o);
        }catch(Exception ignored){}return id;
    }
    private static JSONArray history(Context c){try{return new JSONArray(prefs(c).getString("history","[]"));}catch(Exception e){return new JSONArray();}}
    private static void write(Context c,JSONObject o){
        JSONArray old=history(c),next=new JSONArray();String id=o.optString("requestId");
        if(old.length()==0){JSONObject last=read(c);if(!last.optString("requestId").isEmpty())old.put(last);}
        for(int i=0;i<old.length();i++){JSONObject x=old.optJSONObject(i);if(x!=null&&!id.equals(x.optString("requestId")))next.put(x);}
        if(!id.isEmpty())next.put(o);
        JSONArray bounded=new JSONArray();for(int i=Math.max(0,next.length()-8);i<next.length();i++)bounded.put(next.optJSONObject(i));
        prefs(c).edit().putString("last",o.toString()).putString("history",bounded.toString()).commit();
    }
    private static boolean terminal(JSONObject o){String state=o.optString("outcome");return !state.isEmpty()&&!state.equals("REQUESTED")&&!state.equals("PREPARING")&&!state.equals("READY");}
    static synchronized boolean active(Context c,String id){JSONObject o=read(c);return id.equals(o.optString("requestId"))&&!terminal(o);}
    static synchronized void save(Context c,JSONObject o){JSONObject current=read(c);if(terminal(current))return;if(o.optString("requestId").isEmpty()||!o.optString("requestId").equals(current.optString("requestId")))return;
        try{if(current.has("events"))o.put("events",current.get("events"));if(current.has("returnPending"))o.put("returnPending",current.get("returnPending"));else o.remove("returnPending");}catch(Exception ignored){}write(c,o);}
    static synchronized JSONObject read(Context c){try{return new JSONObject(prefs(c).getString("last","{\"action\":\"NO_GAME_REQUEST\",\"loadObservation\":\"UNKNOWN\"}"));}catch(Exception e){return new JSONObject();}}
    static synchronized JSONObject export(Context c){try{return ProductIdentity.diagnostics(c).put("schema",2).put("exportedAt",System.currentTimeMillis()).put("requests",history(c)).put("last",read(c)).put("directoryAccess",DirectoryHealth.get(c).snapshot()).put("privacy","No ROM, save, filename, absolute path or full system log");}catch(Exception e){return new JSONObject();}}
    private static void event(JSONObject o,String action)throws Exception{JSONArray old=o.optJSONArray("events"),events=new JSONArray();if(old!=null)for(int i=Math.max(0,old.length()-7);i<old.length();i++)events.put(old.get(i));events.put(new JSONObject().put("action",action).put("at",System.currentTimeMillis()).put("parentRequestId",o.optString("requestId")).put("loadObservation","UNKNOWN"));o.put("events",events);}
    static synchronized void manual(Context c,String action){JSONObject o=read(c);try{event(o,action);write(c,o);}catch(Exception ignored){}}
    static synchronized void returned(Context c){JSONObject o=read(c);if(!o.optBoolean("returnPending"))return;try{event(o,"RETURN_TO_SHELL_LOAD_UNCONFIRMED");o.remove("returnPending");write(c,o);}catch(Exception ignored){}}
    static synchronized void outcome(Context c,String id,String state){JSONObject o=read(c);if(!active(c,id))return;try{if(state.startsWith("CANCELLED"))o.put("reason",LaunchFailure.CANCELLED);else if(state.equals("PREPARATION_TIMEOUT"))o.put("reason",LaunchFailure.TIMEOUT);else if(state.equals("SYSTEM_REJECTED"))o.put("reason",52);o.put("outcome",state).put("observedAt",System.currentTimeMillis());if(state.equals("DISPATCHED_UNKNOWN")){o.put("returnPending",true);prefs(c).edit().putBoolean("sessionMayExist",true).commit();}}catch(Exception ignored){}write(c,o);}
    static synchronized void failure(Context c,String id,int reason){JSONObject o=read(c);if(!active(c,id))return;try{o.put("outcome",LaunchFailure.state(reason)).put("reason",reason).put("observedAt",System.currentTimeMillis());}catch(Exception ignored){}write(c,o);}
    static synchronized void failedFile(Context c,String id){failure(c,id,42);}
    static boolean sessionMayExist(Context c){return prefs(c).getBoolean("sessionMayExist",true);}
    static synchronized void userObservation(Context c,String id,String state){JSONObject o=read(c);if(!id.equals(o.optString("requestId")))return;try{o.put("userObservation",state).put("userObservedAt",System.currentTimeMillis());event(o,"USER_REPORT_"+state);if(state.equals("NORMAL_EXIT_REPORTED"))prefs(c).edit().putBoolean("sessionMayExist",false).commit();write(c,o);}catch(Exception ignored){}}
    static String help(Context c){JSONObject o=read(c);String state=o.optString("outcome");int reason=o.optInt("reason");
        if(state.startsWith("CANCELLED"))reason=LaunchFailure.CANCELLED;
        else if(state.equals("PREPARATION_TIMEOUT"))reason=LaunchFailure.TIMEOUT;
        else if(state.equals("SYSTEM_REJECTED"))reason=52;
        return JourneyGuide.s(reason==0?36:LaunchFailure.message(reason));}
    static String hash(byte[] bytes)throws Exception{return hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    static String fileHash(java.io.File file)throws Exception{java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");try(java.io.InputStream in=new java.io.FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))>0){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();digest.update(buffer,0,n);}}return hex(digest.digest());}
}
