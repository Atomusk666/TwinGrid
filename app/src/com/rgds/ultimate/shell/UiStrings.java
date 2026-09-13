package com.rgds.ultimate.shell;
import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Stable resource keys in worker snapshots; resolve only at the rendering boundary. */
final class UiStrings {
    private static Context app;private static String language="";private static Resources resources;
    private static final Map<String,String> cache=new ConcurrentHashMap<>();
    private static final Map<TextView,CharSequence> raw=new WeakHashMap<>();
    private static final Map<TextView,String> rendered=new WeakHashMap<>();
    private static boolean refreshing;
    private static final Pattern KEY=Pattern.compile("\uE100(ui_[a-f0-9]+)\uE101");
    static void init(Context context){app=context.getApplicationContext();LocaleSettings.init(app);}
    static String msg(String key){return "\uE100"+key+"\uE101";}
    private static synchronized String resolve(String key){String next=LocaleSettings.ui();if(resources==null||!language.equals(next)){resources=LocaleSettings.localized(app).getResources();language=next;cache.clear();}String found=cache.get(key);if(found!=null)return found;int id=resources.getIdentifier(key,"string",app.getPackageName());String value=id==0?"[missing: "+key+"]":resources.getString(id);cache.put(key,value);return value;}
    static String display(String value){return render(value).toString();}
    static CharSequence render(CharSequence value){if(value==null)return "";if(value.toString().indexOf('\uE100')<0)return value;Matcher matcher=KEY.matcher(value);SpannableStringBuilder out=new SpannableStringBuilder(value);int delta=0;while(matcher.find()){String translated=resolve(matcher.group(1));out.replace(matcher.start()+delta,matcher.end()+delta,translated);delta+=translated.length()-(matcher.end()-matcher.start());}return out;}
    static CharSequence bind(TextView view,CharSequence value){
        // TextView may call setText again while changing typeface, line mode or spans.
        // Preserve the resource token when that internal call only repeats its rendered text.
        CharSequence previous=raw.get(view);
        boolean repeated=previous!=null&&previous.toString().indexOf('\uE100')>=0
                &&value!=null&&value.toString().indexOf('\uE100')<0
                &&render(previous).toString().contentEquals(value);
        if(!refreshing&&!repeated)raw.put(view,value);CharSequence result=render(value);rendered.put(view,result.toString());return result;
    }
    static CharSequence[] array(CharSequence[] values){CharSequence[] out=new CharSequence[values.length];for(int n=0;n<values.length;n++)out[n]=render(values[n]);return out;}
    static String[] array(String[] values){String[] out=new String[values.length];for(int n=0;n<values.length;n++)out[n]=display(values[n]);return out;}
    /** A recycled view has no text ownership until its adapter binds the current item. */
    static void beginRowBind(View view){if(view instanceof TextView&&!(view instanceof EditText)){raw.remove((TextView)view);rendered.remove((TextView)view);}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)beginRowBind(group.getChildAt(n));}}
    static void refreshTree(View view){if(view instanceof TextView&&!(view instanceof EditText)){TextView text=(TextView)view;CharSequence current=text.getText(),previous=raw.get(text);String shown=rendered.get(text);if(previous==null||current.toString().indexOf('\uE100')>=0||shown==null||!shown.contentEquals(current))raw.put(text,current);DsTypography.ensure(text);CharSequence translated=render(raw.get(text));refreshing=true;try{if(!translated.toString().contentEquals(text.getText()))text.setText(translated);rendered.put(text,translated.toString());}finally{refreshing=false;}}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)refreshTree(group.getChildAt(n));}}
    static void refresh(Activity a){refreshTree(a.getWindow().getDecorView());}
}
