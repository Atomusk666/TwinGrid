package com.rgds.ultimate.shell;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.os.SystemClock;
import android.widget.TextView;
import java.util.WeakHashMap;

/** Every app text role uses the same pixel family; native InputConnections remain intact. */
final class DsTypography {
    static final String UI="ui_pixel",TITLE="game_title_pixel",BODY="reading_pixel",TECHNICAL="technical_pixel";
    static final int TEXT_PX=20,GRID_PX=2;
    static final String HOME_PRIMARY="home_primary_30px",HOME_SECONDARY="home_secondary_20px";
    static final String SELECTED_TITLE="selected_game_title_30px";
    static final String FACE="Fusion Pixel 10px Proportional SC / 2x native grid";
    private static Typeface pixel;
    private static final WeakHashMap<TextView,String> roles=new WeakHashMap<>();
    static long loadMillis;
    static synchronized Typeface pixel(Context c){
        if(pixel==null){long start=SystemClock.uptimeMillis();pixel=c.getResources().getFont(R.font.fusion_pixel_10);loadMillis=SystemClock.uptimeMillis()-start;}
        return pixel;
    }
    static void paint(Context c,Paint p,int size){
        p.setTypeface(pixel(c));p.setTextSize(size);p.setTextScaleX(1);p.setFakeBoldText(false);
        // Audited 100-unit contours in a 1000-UPM font map to 2px cells at 20px.
        p.setAntiAlias(false);p.setSubpixelText(false);p.setHinting(Paint.HINTING_OFF);
    }
    private static void apply(TextView t,String role){
        int size=size(role);roles.put(t,role);t.setTypeface(pixel(t.getContext()));t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
        t.setTextScaleX(1);t.setIncludeFontPadding(false);t.setLetterSpacing(0);t.setFontFeatureSettings("'kern' 0, 'liga' 0");
        if(android.os.Build.VERSION.SDK_INT>=28)t.setFallbackLineSpacing(false);
        paint(t.getContext(),t.getPaint(),size);
    }
    static void ui(TextView t){apply(t,UI);}
    static void title(TextView t,int ignored){apply(t,TITLE);}
    static void selectedTitle(TextView t){apply(t,SELECTED_TITLE);}
    static void body(TextView t,int ignored){apply(t,BODY);}
    static void technical(TextView t,int ignored){apply(t,TECHNICAL);}
    private static int size(String role){return HOME_PRIMARY.equals(role)||SELECTED_TITLE.equals(role)?30:TEXT_PX;}
    static void home(TextView t,boolean primary){apply(t,primary?HOME_PRIMARY:HOME_SECONDARY);}
    static void ensure(TextView t){String role=roles.containsKey(t)?roles.get(t):BODY;if(!roles.containsKey(t)||t.getTextSize()!=size(role)||t.getTypeface()!=pixel(t.getContext())||t.getTextScaleX()!=1)apply(t,role);}
    static String role(TextView t){String r=roles.get(t);return r==null?"unregistered_application_text":r;}
    static String face(TextView t){return roles.containsKey(t)?(size(roles.get(t))==30?"Fusion Pixel 10px Proportional SC / 3x native grid":FACE):"UNREGISTERED";}
}
