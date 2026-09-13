package com.rgds.ultimate.shell;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import org.json.JSONObject;
import java.io.File;

/** Display branding and read-only export identity; never a storage or package migration. */
final class ProductIdentity {
    static final String NAME="TwinGrid";
    private static String apkStamp="",apkDigest="";
    private static synchronized String installedHash(Context c)throws Exception {
        File apk=new File(c.getApplicationInfo().sourceDir);
        String stamp=apk.getPath()+"|"+apk.length()+"|"+apk.lastModified();
        if(!stamp.equals(apkStamp)){String hash=LaunchDiagnostic.fileHash(apk);apkStamp=stamp;apkDigest=hash;}
        return apkDigest;
    }
    static JSONObject diagnostics(Context c){
        JSONObject o=new JSONObject();
        try{
            PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            o.put("productName",NAME).put("packageName",c.getPackageName())
                .put("versionName",p.versionName).put("versionCode",Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)
                .put("firmware",Build.DISPLAY).put("fingerprint",Build.FINGERPRINT)
                .put("model",Build.MODEL).put("androidVersion",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT);
            try{o.put("apkSHA256",installedHash(c));}catch(Exception e){o.put("apkSHA256","UNKNOWN").put("apkIdentityError",e.getClass().getSimpleName());}
            JSONObject emulator=new JSONObject().put("packageName",DrasticLauncher.PACKAGE);
            try{ReceiverProfile.inspect(c).describe(c,emulator);}catch(Exception e){emulator.put("identityError",e.getClass().getSimpleName());}
            o.put("emulatorIdentity",emulator);
        }catch(Exception e){try{o.put("identityError",e.getClass().getSimpleName());}catch(Exception ignored){}}
        return o;
    }
}
