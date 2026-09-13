package com.rgds.ultimate.shell;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.FrameLayout;
import org.json.*;
import java.util.WeakHashMap;

/** Screen-local grid boundaries and layout subdivision. Two cached backgrounds, no animation. */
final class DsGrid {
    static final Spec TOP=new Spec(true,0,0,40,4,195),BOTTOM=new Spec(false,80,60,120,4,170);
    static final int EDGE=2,INSET=12,FOCUS_OUTSET=4,FOCUS_LENGTH=20;
    static final class Spec {
        final boolean top;final int originX,originY,main,unit,gray;Bitmap bitmap;
        Spec(boolean t,int x,int y,int m,int u,int g){top=t;originX=x;originY=y;main=m;unit=u;gray=g;}
        int snapX(int x){return originX+Math.round((x-originX)/(float)unit)*unit;}
        int snapY(int y){return originY+Math.round((y-originY)/(float)unit)*unit;}
        synchronized Bitmap bitmap(){if(bitmap==null){
            bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);Paint p=new Paint();c.drawColor(Color.rgb(251,251,251));
            // Static background only; five device pixels represent two reference rows.
            p.setColor(Color.rgb(227,227,227));for(int y=3;y<480;y+=5)c.drawRect(0,y,640,y+2,p);
            p.setColor(Color.rgb(gray,gray,gray));
            for(int x=originX%main;x<=640;x+=main)c.drawRect(x-EDGE,0,x,480,p);
            for(int y=originY%main;y<=480;y+=main)c.drawRect(0,y-EDGE,640,y,p);
        }return bitmap;}
        JSONObject json()throws JSONException{return new JSONObject().put("screen",top?"upper":"lower").put("originX",originX).put("originY",originY).put("mainPitch",main).put("layoutSubdivision",unit).put("lineWidth",EDGE).put("lineGray",gray).put("fineHorizontalPitch",5).put("fineHorizontalGray",227).put("panelInset",INSET).put("focusOutset",FOCUS_OUTSET).put("boundaryRule","Main lines occupy two pixels before the boundary. Control boundaries snap to subdivision lines.");}
    }
    private static final WeakHashMap<View,Spec> roots=new WeakHashMap<>();
    static void register(View root,boolean top){roots.put(root,top?TOP:BOTTOM);}
    static void register(View root,Spec spec){roots.put(root,spec);}
    static Spec of(View view){View next=view;while(next!=null){Spec found=roots.get(next);if(found!=null)return found;next=next.getParent() instanceof View?(View)next.getParent():null;}return null;}
    static int[] align(FrameLayout parent,int x,int y,int w,int h){
        Spec spec=of(parent);if(spec==null||w<0||h<0)return new int[]{x,y,w,h};
        int ox=0,oy=0;View p=parent;while(p!=null&&!roots.containsKey(p)){ViewGroup.LayoutParams raw=p.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){ox+=((FrameLayout.LayoutParams)raw).leftMargin;oy+=((FrameLayout.LayoutParams)raw).topMargin;}p=p.getParent() instanceof View?(View)p.getParent():null;}
        int left=spec.snapX(ox+x),top=spec.snapY(oy+y),right=spec.snapX(ox+x+w),bottom=spec.snapY(oy+y+h);
        return new int[]{left-ox,top-oy,Math.max(4,right-left),Math.max(4,bottom-top)};
    }
    static Drawable background(boolean top,int x,int y){return new Background(top?TOP:BOTTOM,x,y);}
    static final class Background extends Drawable {
        final Spec spec;final int x,y;Background(Spec s,int x,int y){spec=s;this.x=x;this.y=y;}
        public void draw(Canvas c){c.drawBitmap(spec.bitmap(),-x,-y,null);}
        public void setAlpha(int n){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    /** Only the separate review runner attaches this noninteractive overlay. */
    static final class Guides extends View {
        final Spec spec;final Paint p=new Paint();Guides(android.content.Context c,boolean top){super(c);spec=top?TOP:BOTTOM;setClickable(false);setFocusable(false);}
        protected void onDraw(Canvas c){p.setColor(0x559900BB);for(int x=0;x<getWidth();x+=spec.unit)c.drawRect(x,0,x+1,getHeight(),p);for(int y=0;y<getHeight();y+=spec.unit)c.drawRect(0,y,getWidth(),y+1,p);p.setColor(0xBB008050);for(int x=spec.originX%spec.main;x<getWidth();x+=spec.main)c.drawRect(x,0,x+2,getHeight(),p);for(int y=spec.originY%spec.main;y<getHeight();y+=spec.main)c.drawRect(0,y,getWidth(),y+2,p);}
    }
}
