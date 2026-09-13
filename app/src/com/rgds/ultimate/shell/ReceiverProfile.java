package com.rgds.ultimate.shell;
import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.File;
import org.json.JSONObject;

/** Android APK contracts; never infer a bridge to the separate Nano native runner. */
final class ReceiverProfile {
    static final String STOCK_SHA="4c9bd23fc7a3366f73bbcba4aef2858bfa3a3568b376160792322ed431180189";
    final String key,apk,signature,name;final long update;final int targetSdk;final boolean trial,displayZero;
    final ActivityInfo activity;
    private ReceiverProfile(String k,String a,String s,String n,long u,int sdk,boolean t,boolean d,ActivityInfo info){key=k;apk=a;signature=s;name=n;update=u;targetSdk=sdk;trial=t;displayZero=d;activity=info;}
    @SuppressWarnings("deprecation") static ReceiverProfile inspect(Context c)throws Exception {
        PackageManager pm=c.getPackageManager();PackageInfo p=pm.getPackageInfo(DrasticLauncher.PACKAGE,PackageManager.GET_SIGNATURES);
        ActivityInfo a=pm.getActivityInfo(DrasticLauncher.ENTRY,0);
        if(!a.exported||!a.enabled||!p.applicationInfo.enabled||a.permission!=null)throw new DrasticLauncher.Failure(41);
        if(!DrasticLauncher.PACKAGE.equals(a.taskAffinity))throw new DrasticLauncher.Failure(41);
        java.util.ArrayList<String> signs=new java.util.ArrayList<>();if(p.signatures!=null)for(Signature s:p.signatures)signs.add(LaunchDiagnostic.hash(s.toByteArray()));java.util.Collections.sort(signs);
        if(signs.isEmpty())throw new DrasticLauncher.Failure(41);
        String sig=signs.toString();File file=new File(p.applicationInfo.sourceDir);
        String stamp=p.lastUpdateTime+"|"+p.versionCode+"|"+p.versionName+"|"+sig+"|"+file.length()+"|"+file.lastModified()+"|"+file.getPath();
        android.content.SharedPreferences cache=c.getSharedPreferences("receiver_identity_v120",0);
        String hash=stamp.equals(cache.getString("stamp",""))?cache.getString("apk",""):"";
        if(hash.isEmpty()){hash=LaunchDiagnostic.fileHash(file);cache.edit().putString("stamp",stamp).putString("apk",hash).commit();}
        boolean stock=STOCK_SHA.equals(hash),physical=stock&&Build.VERSION.SDK_INT==34&&Build.MODEL.equals("RG DS")&&Build.DISPLAY.equals("V1.18")
            &&Build.FINGERPRINT.equals("Anbernic/rk3568_u/rk3568_u:14/UQ1A.240205.004.B1/eng.builde.20260518.174052:userdebug/release-keys");
        String key=LaunchDiagnostic.hash(("profile2|"+stamp+"|"+hash+"|"+Build.FINGERPRINT+"|"+Build.VERSION.SDK_INT+"|"+a.launchMode+"|"+a.taskAffinity).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new ReceiverProfile(key,hash,sig,stock?"STOCK_GETPATH_REPLACE_V2":"ANDROID_GETPATH_CONTROLLED_TRIAL_V2",p.lastUpdateTime,p.applicationInfo.targetSdkVersion,!stock,physical,a);
    }
    void describe(Context c,JSONObject d)throws Exception {
        PackageInfo p=c.getPackageManager().getPackageInfo(DrasticLauncher.PACKAGE,0);
        boolean read=c.getPackageManager().checkPermission("android.permission.READ_EXTERNAL_STORAGE",DrasticLauncher.PACKAGE)==PackageManager.PERMISSION_GRANTED;
        d.put("backend","ANDROID_DRASTIC_APK").put("profile",name).put("environmentKey",key).put("emulatorAPK",apk).put("emulatorSignature",signature)
            .put("emulatorVersion",p.versionName).put("emulatorCode",p.versionCode).put("emulatorUpdatedAt",update).put("targetSdk",targetSdk)
            .put("launchMode",activity.launchMode).put("taskAffinity",activity.taskAffinity).put("receiverReadPermission",read)
            .put("contractEvidence",trial?"PUBLIC_FRONTEND_PRECEDENT_USER_TRIAL":"EXACT_APK_RECEIVER_REVIEW")
            .put("environmentValidation",displayZero?"RGDS_V118_API34_PHYSICAL_ABA":"UNTESTED_PLAYER_RETEST")
            .put("receiverFileReadObservation","UNKNOWN").put("targetDisplay",displayZero?"0":"SYSTEM_DEFAULT");
        if(targetSdk<=29&&!read)throw new DrasticLauncher.Failure(36);
    }
    boolean trialAccepted(Context c){return key.equals(c.getSharedPreferences("receiver_identity_v120",0).getString("acceptedTrial",""));}
    void acceptTrial(Context c){c.getSharedPreferences("receiver_identity_v120",0).edit().putString("acceptedTrial",key).commit();}
}
