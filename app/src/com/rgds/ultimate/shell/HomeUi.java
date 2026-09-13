package com.rgds.ultimate.shell;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import static com.rgds.ultimate.shell.ClassicUi.*;

/** Local HOME surfaces. No Edge, library, dialog or background-grid changes. */
final class HomeUi {
    static final int SLOT=0,PAIR=1,RECENT=3,SETTINGS=4,TASKS=5;
    static void box(Canvas c,Paint p,int l,int t,int r,int b,int color){p.setColor(color);c.drawRect(l,t,r,b,p);}
    static void focus(Canvas c,int w,int h){
        Paint p=new Paint();
        // Entirely inside the control; painted after children, never under siblings.
        for(int[] a:new int[][]{{2,2,1,1},{w-2,2,-1,1},{2,h-2,1,-1},{w-2,h-2,-1,-1}}){
            for(int layer=0;layer<2;layer++){
                int inset=layer==0?0:2,len=layer==0?26:22,thick=layer==0?8:4;
                int x=a[0]+a[2]*inset,y=a[1]+a[3]*inset;
                int color=layer==0?0xFFFBFBFB:ACCENT;
                box(c,p,Math.min(x,x+a[2]*len),Math.min(y,y+a[3]*thick),Math.max(x,x+a[2]*len),Math.max(y,y+a[3]*thick),color);
                box(c,p,Math.min(x,x+a[2]*thick),Math.min(y,y+a[3]*len),Math.max(x,x+a[2]*thick),Math.max(y,y+a[3]*len),color);
            }
        }
    }
    static final class Panel extends FrameLayout {
        Panel(Context c,int kind){super(c);setBackground(new Surface(kind));}
        @Override protected void dispatchDraw(Canvas c){super.dispatchDraw(c);if(isSelected()||isFocused())HomeUi.focus(c,getWidth(),getHeight());}
    }
    static final class Surface extends Drawable {
        final int kind;final Paint p=new Paint();boolean pressed,selected,active;
        Surface(int kind){this.kind=kind;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] states){
            boolean pr=false,se=false,ac=false;for(int s:states){pr|=s==android.R.attr.state_pressed;se|=s==android.R.attr.state_selected||s==android.R.attr.state_focused;ac|=s==android.R.attr.state_activated;}
            if(pr==pressed&&se==selected&&ac==active)return false;pressed=pr;selected=se;active=ac;invalidateSelf();return true;
        }
        @Override public void draw(Canvas c){
            Rect b=getBounds();c.save();c.translate(b.left,b.top);int w=b.width(),h=b.height();
            boolean tool=kind>=SETTINGS;int fill=pressed?0xFFD3DEE3:0xFFFBFBFB;
            box(c,p,0,0,w,h,INK);box(c,p,2,2,w-2,h-2,fill);
            box(c,p,2,2,w-2,4,pressed?LINE:0xFFFFFFFF);box(c,p,2,2,4,h-2,pressed?LINE:0xFFFFFFFF);
            box(c,p,4,h-4,w-2,h-2,0xFFAAAAAA);box(c,p,w-4,4,w-2,h-2,0xFFAAAAAA);
            if(kind==SLOT||kind==RECENT){
                box(c,p,4,4,112,h-4,pressed?0xFFC3CED3:0xFFE3E3E3);
                box(c,p,4,4,112,6,LINE);box(c,p,4,4,6,h-4,LINE);
                box(c,p,110,4,112,h-4,LINE);box(c,p,112,4,114,h-4,0xFFFFFFFF);
                for(int y=6;y<h-4;y+=4)box(c,p,114,y,w-4,y+1,pressed?0xFFC7D3DA:0xFFEDEDED);
            }else if(kind==PAIR){
                box(c,p,4,50,w-4,h-4,pressed?0xFFC3CED3:0xFFE3E3E3);
                box(c,p,4,48,w-4,49,0xFFB3B3B3);box(c,p,4,49,w-4,50,0xFFFFFFFF);
            }else if(tool){
                box(c,p,4,4,w-4,h-4,pressed?0xFFC3CED3:0xFFF3F3F3);
                DsIcons icon=new DsIcons(kind==SETTINGS?DsIcons.SETTINGS:DsIcons.TASKS,32);icon.setBounds((w-32)/2,(h-32)/2,(w+32)/2,(h+32)/2);icon.draw(c);
                if(active)box(c,p,w-10,6,w-6,10,ACCENT);
                if(selected)HomeUi.focus(c,w,h);
            }
            c.restore();
        }
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    /** Original menu-only symbols; ROM banner rendering remains in ClassicUi.Icon. */
    static final class Symbol extends android.view.View {
        final int symbol;final Paint p=new Paint();
        Symbol(Context c,int symbol){super(c);this.symbol=symbol;}
        @Override protected void onDraw(Canvas c){
            if(symbol!=DsIcons.LIBRARY){DsIcons d=new DsIcons(symbol,getWidth());d.setBounds(0,0,getWidth(),getHeight());d.draw(c);return;}
            int scale=Math.max(1,Math.min(getWidth(),getHeight())/24);c.save();c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);
            // Two offset game cartridges with labels and contact fingers, rather than bars.
            for(int[] a:new int[][]{{3,2},{10,7}}){int x=a[0],y=a[1];box(c,p,x,y,x+11,y+15,INK);box(c,p,x+1,y+1,x+10,y+14,0xFFFBFBFB);box(c,p,x+2,y+2,x+9,y+9,0xFFAAAAAA);box(c,p,x+3,y+3,x+8,y+7,0xFFE3E3E3);for(int n=2;n<9;n+=2)box(c,p,x+n,y+11,x+n+1,y+14,INK);}
            c.restore();
        }
    }
    private HomeUi(){}
}
