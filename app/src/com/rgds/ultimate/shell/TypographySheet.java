package com.rgds.ultimate.shell;
import android.app.*;import android.widget.*;import android.view.*;

/** Readable credits and script samples, reached from About > Sources. */
final class TypographySheet {
    static void show(Activity a){
        ScrollView scroll=new ScrollView(a);LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(16,8,16,12);scroll.addView(box);
        sample(a,box,"font_sample_ui_en","Tasks  Genres  Favorites  Recent\nSearch  Settings  0/O  1/I/l\n0123456789",DsTypography.UI,20);
        sample(a,box,"font_sample_ui_zh","全部游戏  类型  文件夹  收藏  最近\n资料补齐  中文汉化修正版",DsTypography.UI,20);
        sample(a,box,"font_sample_title","中文汉化修正版 (v1.5) / Professor Layton and the Unwound Future",DsTypography.TITLE,24);
        sample(a,box,"font_sample_body","Pokémon / Zoë / かな / (v1.5)\n简体中文 / 繁體中文：「龍與劍」。\nPixel body text wraps at its real width. 正文按实际宽度换行，保留完整资料入口。",DsTypography.BODY,19);
        sample(a,box,"font_sample_technical",ClassicScreen.version(a)+"\n0123456789  abcDEF  0/O  1/I/l",DsTypography.TECHNICAL,16);
        LinearLayout icons=new LinearLayout(a);icons.setPadding(0,10,0,10);for(int i=0;i<16;i++){View v=new View(a);v.setBackground(new DsIcons(i,16));v.setTag("font_sheet_icon_"+i);icons.addView(v,new LinearLayout.LayoutParams(32,32));}box.addView(icons);
        sample(a,box,"font_credits",UiStrings.msg("ui_d5000000000d"),DsTypography.BODY,17);
        AlertDialog d=new ShellDialogBuilder(a).setTitle(UiStrings.msg("ui_d5000000000c")).setView(scroll).setPositiveButton(UiStrings.msg("ui_572cf45ba436"),null).setNeutralButton("OFL 1.1",(v,n)->license(a)).create();d.show();
    }
    private static void sample(Activity a,LinearLayout box,String id,String value,String role,int size){TextView t=ClassicUi.text(a,value,size);t.setTag(id);t.setPadding(0,8,0,8);if(role.equals(DsTypography.UI))DsTypography.ui(t);else if(role.equals(DsTypography.TITLE))DsTypography.title(t,size);else if(role.equals(DsTypography.TECHNICAL))DsTypography.technical(t,size);t.setGravity(Gravity.TOP);box.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private static void license(Activity a){try(java.io.InputStream in=a.getResources().openRawResource(R.raw.fusion_pixel_license)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);UiDialogs.text(a,"Fusion Pixel · OFL 1.1",out.toString("UTF-8"));}catch(java.io.IOException e){UiDialogs.text(a,"OFL 1.1",e.getClass().getSimpleName());}}
}
