package com.rgds.ultimate.shell;
import android.app.Activity;
import android.net.Uri;
import android.view.*;
import android.widget.*;
final class QuickStart {
    static String s(int n){return UiStrings.msg(String.format(java.util.Locale.ROOT,"ui_93%06x",n));}
    static View body(Activity a,String value){
        FrameLayout inset=new FrameLayout(a);inset.setPadding(12,8,12,8);
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int w,int h){
            int limit=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?260:Math.min(260,MeasureSpec.getSize(h));
            super.onMeasure(w,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }};
        scroll.setFillViewport(false);scroll.setClipToPadding(true);scroll.setBackgroundColor(ClassicUi.PAPER);
        TextView text=ClassicUi.text(a,value,20);text.setGravity(Gravity.TOP);text.setPadding(16,12,16,16);text.setLineSpacing(5,1);
        scroll.addView(text,new ScrollView.LayoutParams(-1,-2));inset.addView(scroll,new FrameLayout.LayoutParams(-1,-2));return inset;
    }
    static void show(Activity a,int page){show(a,page,null);}
    static void show(Activity a,int page,Runnable next){
        ShellDialogBuilder b=new ShellDialogBuilder(a);b.setTitle(s(1)+"  "+(page+1)+" / 4").setView(body(a,s(7+page)));
        b.setPositiveButton(page==3?(next==null?s(6):s(2)):s(4),(d,w)->{if(page<3)show(a,page+1,next);else if(next!=null)next.run();});
        b.setNegativeButton(s(3),null);if(page>0)b.setNeutralButton(s(5),(d,w)->show(a,page-1,next));android.app.AlertDialog dialog=b.show();ShellDialogBuilder.help(dialog,s(7+page));
    }
    static void welcome(BottomHomeActivity a){
        android.app.AlertDialog dialog=new ShellDialogBuilder(a).setTitle(s(1)).setView(body(a,s(12)+"\n\n"+s(19)))
            .setPositiveButton(s(2),(d,w)->a.chooseRomDirectoryDirect())
            .setNeutralButton(s(0),(d,w)->show(a,0,a::chooseRomDirectoryDirect)).setNegativeButton(s(3),null).show();ShellDialogBuilder.help(dialog,s(12));
    }
    static String directoryText(BottomHomeActivity a,boolean saves){
        ShellStateRepository.Snapshot state=ShellStateRepository.get(a).snapshot();Uri uri=saves?state.backupTreeUri:state.romTreeUri;
        return s(saves?13:12)+"\n\n"+s(14)+" "+DirectoryAccess.label(a,uri,saves)+(saves?"":"\n\n"+s(19));
    }
    static void directory(BottomHomeActivity a,boolean saves,Runnable unused){DirectoryRequest.get(a).request(a,saves,"MANUAL_HELP",false);}
}
