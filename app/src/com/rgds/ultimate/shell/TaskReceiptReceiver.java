package com.rgds.ultimate.shell;
import android.content.*;
/** Non-exported destination of this app's notification swipe intent. */
public final class TaskReceiptReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){if(intent.getData()==null)return;String key=intent.getData().getSchemeSpecificPart();c.getSharedPreferences("task_receipts_v1",0).edit().putLong("dismissed:"+key,System.currentTimeMillis()).apply();MetadataManager.get(c).notifyTaskUi();}
}
