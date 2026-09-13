package com.rgds.ultimate.shell;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Shell-only presentation preference. Capacity still comes from Android's battery broadcast.
 * Listener registration and changes are owned by the UI thread, like attached View lifecycles. */
final class BatterySettings {
    private static final WeakHashMap<Runnable,Boolean> listeners=new WeakHashMap<>();
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("battery_display_v1",0);}
    static boolean numbers(Context c){return "numbers".equals(prefs(c).getString("mode","icon"));}
    static String label(Context c){return UiStrings.msg(numbers(c)?"ui_ba77000004":"ui_ba77000003");}
    static void addListener(Runnable listener){listeners.put(listener,true);}
    static void removeListener(Runnable listener){listeners.remove(listener);}
    private static void notifyListeners(){for(Runnable listener:new ArrayList<>(listeners.keySet()))listener.run();}
    static boolean setNumbers(Context c,boolean numbers){
        if(numbers(c)==numbers)return true;
        SharedPreferences preference=prefs(c);String previous=preference.getString("mode","icon"),next=numbers?"numbers":"icon";
        if(!preference.edit().putString("mode",next).commit()||!next.equals(preference.getString("mode","icon"))){
            // Android updates memory even if disk commit fails. Restore that memory too,
            // so a failed selection cannot silently change the next attached view's style.
            preference.edit().putString("mode",previous).commit();
            if(!previous.equals(preference.getString("mode","icon")))notifyListeners();
            return false;
        }
        notifyListeners();
        return true;
    }
    static void choose(Activity activity){
        new ShellDialogBuilder(activity).setTitle(UiStrings.msg("ui_ba77000001"))
            .setSingleChoiceItems(new String[]{UiStrings.msg("ui_ba77000003"),UiStrings.msg("ui_ba77000004")},numbers(activity)?1:0,(dialog,which)->{
                if(!setNumbers(activity,which==1)){UiDialogs.text(activity,UiStrings.msg("ui_ba77000001"),UiStrings.msg("ui_ba77000005"));return;}
                dialog.dismiss();MetadataManager.get(activity).notifyTaskUi();
            }).setNegativeButton(UiStrings.msg("ui_2cd0f3be8738"),null).show();
    }
}
