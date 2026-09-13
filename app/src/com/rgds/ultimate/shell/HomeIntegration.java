package com.rgds.ultimate.shell;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.provider.Settings;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Optional integration. No boot receiver, privilege escalation, key hook or implicit enable. */
final class HomeIntegration {
    static final int REQUEST_HOME=2077,REQUEST_SETTINGS=2078;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->new Thread(r,"home-recovery"));
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<Activity,Boolean> systemVisits=new WeakHashMap<>();
    static void paused(Activity a){} // Focus transitions across two displays are not system-dialog completion.
    static void resumed(Activity a){if(a.hasWindowFocus()&&Boolean.TRUE.equals(systemVisits.get(a))){systemVisits.remove(a);ShellCoordinator.get().pickerFinished();}}
    static String home(Context c){ResolveInfo r=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);return r==null||r.activityInfo==null?"":new ComponentName(r.activityInfo.packageName,r.activityInfo.name).flattenToString();}
    static JSONObject snapshot(Context c){JSONObject o=new JSONObject();try{
        RoleManager role=Build.VERSION.SDK_INT>=29?c.getSystemService(RoleManager.class):null;
        String actual=home(c);
        o.put("uid",android.os.Process.myUid()).put("roleAvailable",role!=null&&role.isRoleAvailable(RoleManager.ROLE_HOME)).put("roleHeld",role!=null&&role.isRoleHeld(RoleManager.ROLE_HOME))
            .put("home",actual).put("isDefaultHome",actual.equals(new ComponentName(c,BottomHomeActivity.class).flattenToString()))
            .put("defaultEnabled",false);
    }catch(Exception e){try{o.put("error",e.getClass().getSimpleName());}catch(Exception ignored){}}return o;}
    static void showHome(Activity a){JSONObject facts=snapshot(a);
        GapUi.menu(a,UxStrings.s(40),UxStrings.s(46)+"\n"+facts.optString("home")+"\n\n"+UxStrings.s(41),new String[]{UxStrings.s(42),UxStrings.s(43),UxStrings.s(44),UxStrings.s(45)},n->{
            if(n==0)requestHome(a);else if(n==1)openSystem(a,new Intent(Settings.ACTION_HOME_SETTINGS));else if(n==2)openSystem(a,new Intent(Settings.ACTION_SETTINGS));else originalLauncher(a);
        });
    }
    static void requestHome(Activity a){
        JSONObject facts=snapshot(a);if(facts.has("error")){UiDialogs.text(a,UxStrings.s(40),UxStrings.s(62));return;}
        if(facts.optBoolean("isDefaultHome")){UiDialogs.text(a,UxStrings.s(40),UxStrings.s(70));return;}
        IO.execute(()->{try{write(a,"home-request",new JSONObject().put("before",facts).put("requestedAt",System.currentTimeMillis()).put("state","SYSTEM_CONSENT_PENDING"));
            MAIN.post(()->{if(a.isFinishing())return;RoleManager r=Build.VERSION.SDK_INT>=29?a.getSystemService(RoleManager.class):null;
                openSystem(a,r!=null&&r.isRoleAvailable(RoleManager.ROLE_HOME)&&!r.isRoleHeld(RoleManager.ROLE_HOME)?r.createRequestRoleIntent(RoleManager.ROLE_HOME):new Intent(Settings.ACTION_HOME_SETTINGS));});
        }catch(Exception e){MAIN.post(()->UiDialogs.text(a,UxStrings.s(40),UxStrings.s(62)));}});
    }
    static void openSystem(Activity a,Intent i){try{FirstRun.requested(a);systemVisits.put(a,false);ShellCoordinator.get().pickerStarted();a.startActivityForResult(i,REQUEST_HOME);}catch(Exception e){systemVisits.remove(a);ShellCoordinator.get().pickerFinished();UiDialogs.text(a,UxStrings.s(40),e.getClass().getSimpleName());}}
    static boolean result(Activity a,int request){if(request!=REQUEST_HOME&&request!=REQUEST_SETTINGS)return false;systemVisits.put(a,true);resumed(a);JSONObject facts=snapshot(a);FirstRun.result(a,facts);IO.execute(()->{try{write(a,"home-last-result",facts.put("at",System.currentTimeMillis()));}catch(Exception ignored){}});MetadataManager.get(a).notifyTaskUi();return true;}
    private static void originalLauncher(Activity a){
        String saved=read(a,"home-request").optJSONObject("before")==null?"":read(a,"home-request").optJSONObject("before").optString("home");
        ComponentName component=ComponentName.unflattenFromString(saved);
        if(component==null||component.getPackageName().equals(a.getPackageName()))component=new ComponentName("com.android.launcher3","com.android.launcher3.uioverrides.QuickstepLauncher");
        ShellCoordinator.get().exitToDesktop(a,component);
    }
    private static AtomicFile file(Context c,String name){return new AtomicFile(new File(c.getFilesDir(),"integration_v77/"+name+".json"));}
    private static JSONObject read(Context c,String name){try{return new JSONObject(new String(file(c,name).readFully(),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    private static void write(Context c,String name,JSONObject data)throws IOException{
        AtomicFile f=file(c,name);File dir=f.getBaseFile().getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Recovery directory unavailable");FileOutputStream out=null;
        try{out=f.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}
    }
}
