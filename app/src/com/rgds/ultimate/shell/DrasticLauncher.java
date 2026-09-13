package com.rgds.ultimate.shell;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Process;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.net.Uri;
import java.io.File;
import android.util.Log;

/** Only the public entry point observed in the installed package. No emulator private APIs. */
final class DrasticLauncher {
    static final String PACKAGE="com.dsemu.drastic";
    static final ComponentName ENTRY=new ComponentName(PACKAGE,PACKAGE+".DraSticActivity");
    static final class Failure extends Exception {final int message;Failure(int n){super("launch_reason_"+n);message=n;}}
    static final class Plan {final Intent intent;final String requestId;final ReceiverProfile profile;GameEntry game;LaunchFile file;boolean dispatched;
        Plan(Intent i,String id,ReceiverProfile p){intent=i;requestId=id;profile=p;}}
    static Plan prepare(android.content.Context owner,GameEntry game,String requestId)throws Exception {
        org.json.JSONObject diagnostic=LaunchDiagnostic.read(owner);
        if(!LaunchDiagnostic.active(owner,requestId))throw new LaunchFailure(LaunchFailure.CANCELLED);
        diagnostic.put("shellReadCheck",true).put("outcome","PREPARING");
        android.content.pm.PackageInfo shell=owner.getPackageManager().getPackageInfo(owner.getPackageName(),0);
        diagnostic.put("shellVersion",shell.versionName).put("shellCode",shell.versionCode);
        LaunchDiagnostic.save(owner,diagnostic);
        try {
        ReceiverProfile profile;
        try{profile=ReceiverProfile.inspect(owner);profile.describe(owner,diagnostic);}
        catch(android.content.pm.PackageManager.NameNotFoundException e){throw new Failure(40);}
        catch(Failure e){throw e;}
        catch(Exception e){throw new Failure(41);}
        LaunchDiagnostic.save(owner,diagnostic);
        if(!"content".equals(game.uri.getScheme())||!"com.android.externalstorage.documents".equals(game.uri.getAuthority()))throw new Failure(44);
        String id=DocumentsContract.getDocumentId(game.uri);
        int separator=id.indexOf(':');
        if(separator<1) throw new IllegalArgumentException("No storage volume in document ID");
        String volumeId=id.substring(0,separator),relative=id.substring(separator+1);
        if(relative.isEmpty() || relative.startsWith("/") || relative.contains("\\"))
            throw new IllegalArgumentException("Unsafe relative document path");
        for(String part:relative.split("/"))if(part.equals("..")||part.equals("."))
            throw new IllegalArgumentException("Unsafe relative document path");
        File root=null;
        StorageManager storage=owner.getSystemService(StorageManager.class);
        for(StorageVolume volume:storage.getStorageVolumes())
            if(("primary".equals(volumeId)&&volume.isPrimary()) || volumeId.equalsIgnoreCase(volume.getUuid())) {
                if(Build.VERSION.SDK_INT>=30)root=volume.getDirectory();
                else {
                    // Public API26-29 mapping through this app's actual external-files volumes.
                    for(File dir:owner.getExternalFilesDirs(null))if(dir!=null){
                        StorageVolume found=storage.getStorageVolume(dir);
                        if(found!=null&&((volume.isPrimary()&&found.isPrimary())||(volume.getUuid()!=null&&volume.getUuid().equalsIgnoreCase(found.getUuid())))){
                            String suffix=File.separator+"Android"+File.separator+"data"+File.separator+owner.getPackageName()+File.separator+"files";
                            if(dir.getPath().endsWith(suffix))root=new File(dir.getPath().substring(0,dir.getPath().length()-suffix.length()));
                        }
                    }
                }
                break;
            }
        if(root==null) throw new LaunchFailure(42);
        File target=new File(root,relative).getCanonicalFile();
        String allowed=root.getCanonicalPath()+File.separator;
        if(!target.getPath().startsWith(allowed))throw new SecurityException("ROM escaped its storage volume");
        String path=new File(root,relative).getPath();
        // Receiver d(): getData().getPath() -> File.exists(). Encode once via Builder.
        Uri data=new Uri.Builder().path(path).build();
        if(!path.equals(data.getPath())||data.getScheme()!=null||data.getQuery()!=null||data.getFragment()!=null)throw new Failure(44);
        Intent intent=new Intent(Intent.ACTION_VIEW,data).setComponent(ENTRY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        diagnostic.put("strategy",profile.name).put("component",ENTRY.flattenToShortString()).put("volumeKind","primary".equals(volumeId)?"INTERNAL":"REMOVABLE")
            .put("intentAction",Intent.ACTION_VIEW).put("dataScheme","none").put("mimeType",org.json.JSONObject.NULL)
            .put("flags",intent.getFlags()).put("grant","none; receiver uses direct File")
            .put("pathRoundTrip",true).put("mappedPathHash",LaunchDiagnostic.hash(path.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .put("shellRawPathReadable",target.canRead()).put("outcome","READY");
        diagnostic.put("preparedAt",System.currentTimeMillis());LaunchDiagnostic.save(owner,diagnostic);return new Plan(intent,requestId,profile);
        }catch(IllegalArgumentException e){throw new Failure(44);}
    }
    static void dispatch(Activity owner,Plan plan)throws Exception {
        ActivityOptions options=ActivityOptions.makeBasic();if(plan.profile.displayZero)options.setLaunchDisplayId(0);
        if(!LaunchDiagnostic.active(owner,plan.requestId))throw new LaunchFailure(LaunchFailure.CANCELLED);
        if(plan.dispatched)throw new IllegalStateException("Already dispatched");
        try{
            if(plan.profile.trial&&!plan.profile.trialAccepted(owner))throw new LaunchFailure(LaunchFailure.ENVIRONMENT_CHANGED);
            if(owner.getPackageManager().getPackageInfo(PACKAGE,0).lastUpdateTime!=plan.profile.update)throw new LaunchFailure(LaunchFailure.ENVIRONMENT_CHANGED);
        }catch(Exception e){LaunchDiagnostic.failure(owner,plan.requestId,LaunchFailure.ENVIRONMENT_CHANGED);throw new LaunchFailure(LaunchFailure.ENVIRONMENT_CHANGED);}
        plan.dispatched=true;
        try{owner.startActivity(plan.intent,options.toBundle());LaunchDiagnostic.outcome(owner,plan.requestId,"DISPATCHED_UNKNOWN");}
        catch(Exception e){LaunchDiagnostic.failure(owner,plan.requestId,52);throw new LaunchFailure(52);}
    }
}
