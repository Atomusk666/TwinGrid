package com.rgds.ultimate.shell;
import android.app.*;
import android.content.*;
import android.content.res.*;
import java.util.*;

/** App/content preferences never mutate system, keyboard or ROM language. */
final class LocaleSettings {
    private static Context app;private static volatile long revision;private static volatile String ui="zh",content="zh";
    static void init(Context c){if(app!=null)return;app=c.getApplicationContext();SharedPreferences p=prefs();if(!p.contains("app"))p.edit().putString("app","system").putInt("schema",1).commit();load();}
    private static SharedPreferences prefs(){return app.getSharedPreferences("locales_v1",0);}
    private static void load(){String choice=prefs().getString("app","zh");ui=choice.equals("system")?(Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage().equals("zh")?"zh":"en"):choice;String body=prefs().getString("content","app");content=body.equals("app")?ui:body;}
    static String ui(){return ui;}
    static String content(){return content;}
    static void configurationChanged(Context c){if(app==null)return;if(android.os.Build.VERSION.SDK_INT>=33){android.os.LocaleList list=app.getSystemService(LocaleManager.class).getApplicationLocales();String selected=list.isEmpty()?"system":list.get(0).getLanguage().equals("zh")?"zh":"en";if(prefs().getBoolean("nativeApplied",false))prefs().edit().putString("app",selected).commit();}String before=ui+":"+content;load();if(!before.equals(ui+":"+content)){revision++;MetadataManager.get(c).localeChanged();ShellCoordinator.get().refreshLocale();}}
    static long revision(){return revision;}
    static Context localized(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(Locale.forLanguageTag(ui().equals("zh")?"zh-Hans":"en"));return c.createConfigurationContext(config);}
    static String field(String field){return field(field,content());}
    static String field(String field,String locale){return locale.equals("en")&&!field.endsWith("_en")?field+"_en":field;}
    static void firstChoice(Activity a,String language){prefs().edit().putString("app",language).commit();load();revision++;MetadataManager.get(a).localeChanged();ShellCoordinator.get().refreshLocale();}
    static void choose(Activity a,boolean data){String[] ids=data?new String[]{"app","zh","en"}:new String[]{"system","zh","en"};String[] labels=data?new String[]{UiStrings.msg("ui_2992d2200554"),UiStrings.msg("ui_9937e365aaf3"),"English"}:new String[]{UiStrings.msg("ui_1f57c28e086b"),UiStrings.msg("ui_9937e365aaf3"),"English"};GapUi.menu(a,data?UiStrings.msg("ui_98a28c9b4bbe"):UiStrings.msg("ui_127527c89c51"),data?UiStrings.msg("ui_4294829eb5ed"):UiStrings.msg("ui_e18a6fb0cfe2"),labels,i->{prefs().edit().putString(data?"content":"app",ids[i]).commit();load();revision++;if(!data&&android.os.Build.VERSION.SDK_INT>=33){prefs().edit().putBoolean("nativeApplied",true).commit();a.getSystemService(LocaleManager.class).setApplicationLocales(ids[i].equals("system")?android.os.LocaleList.getEmptyLocaleList():android.os.LocaleList.forLanguageTags(ids[i].equals("zh")?"zh-Hans":"en"));}MetadataManager.get(a).localeChanged();ShellCoordinator.get().refreshLocale();});}
}
