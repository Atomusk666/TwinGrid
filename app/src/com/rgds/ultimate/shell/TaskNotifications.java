package com.rgds.ultimate.shell;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;

/** Singleton owner, silent terminal receipts only. No service, wake lock, or executable notification actions. */
final class TaskNotifications {
    static final String VIEW="com.rgds.ultimate.shell.VIEW_TASK_RESULT",CHANNEL="cover_results";
    static final int REQUEST=2005,ID=801;
    private final Context context; private final SharedPreferences prefs; private final NotificationManager manager;
    private String observed="";
    TaskNotifications(Context c){context=c;prefs=c.getSharedPreferences("metadata_v4",0);manager=c.getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,UiStrings.display(UiStrings.msg("ui_aaf6ceaacbd2")),NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);manager.createNotificationChannel(channel);manager.cancel(ID);
    }
    static boolean permitted(Context c){return Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    static String status(Context c){
        SharedPreferences p=c.getSharedPreferences("metadata_v4",0);if(!p.getBoolean("completion_notifications",false))return UiStrings.msg("ui_3fd47edce45b");
        NotificationManager n=c.getSystemService(NotificationManager.class);NotificationChannel channel=n.getNotificationChannel(CHANNEL);
        return !permitted(c)||!n.areNotificationsEnabled()?UiStrings.msg("ui_26cd8ef77a6d"):channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE?UiStrings.msg("ui_550330de23f8"):UiStrings.msg("ui_8da97ddda990");
    }
    static void toggle(Activity a){
        SharedPreferences p=a.getSharedPreferences("metadata_v4",0);
        if(p.getBoolean("completion_notifications",false)){p.edit().putBoolean("completion_notifications",false).apply();a.getSystemService(NotificationManager.class).cancel(ID);MetadataManager.get(a).notifyTaskUi();return;}
        if(!permitted(a)){a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQUEST);return;}
        p.edit().putBoolean("completion_notifications",true).apply();MetadataManager.get(a).notifyTaskUi();
    }
    static void permissionResult(Activity a,int[] results){
        a.getSharedPreferences("metadata_v4",0).edit().putBoolean("completion_notifications",results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED).apply();MetadataManager.get(a).notifyTaskUi();
    }
    void cancel(String key){if(observed.equals(key))manager.cancel(ID);}
    void update(CoverTask.Snapshot s){
        MetadataManager m=MetadataManager.get(context);TaskReceipts receipts=m.receipts;
        if(receipts==null||!TaskReceipts.result(s))return;String key=TaskReceipts.key(s);
        if(!observed.equals(key)){manager.cancel(ID);observed=key;}
        if(receipts.find(key)==null||receipts.notified(key)||!status(context).equals(UiStrings.msg("ui_8da97ddda990")))return;
        Intent view=new Intent(context,BottomHomeActivity.class).setAction(VIEW).setData(Uri.parse("rgds-task:"+key)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent intent=PendingIntent.getActivity(context,ID,view,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent clear=new Intent(context,TaskReceiptReceiver.class).setData(Uri.parse("rgds-task:"+key));
        PendingIntent deleted=PendingIntent.getBroadcast(context,ID,clear,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle(UiStrings.display(s.title())).setContentText(UiStrings.display(s.counts()).replace('\n',' ')).setContentIntent(intent).setDeleteIntent(deleted).setOnlyAlertOnce(true).setAutoCancel(true).setLocalOnly(true).setShowWhen(true).setWhen(s.ended>0?s.ended:s.changed).build();
        try{manager.notify(ID,n);receipts.notifiedNow(key);}catch(SecurityException ignored){}
    }
    static void open(Activity a,Intent intent){if(!VIEW.equals(intent.getAction()))return;String key=intent.getData()==null?"":intent.getData().getSchemeSpecificPart();GapUi.results(a,key);}
}
