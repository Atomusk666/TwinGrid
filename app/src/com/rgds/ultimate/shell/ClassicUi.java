package com.rgds.ultimate.shell;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import android.util.LruCache;
import android.os.Handler;
import android.os.Looper;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Native-pixel DS Classic primitives: rectangular controls, cached grid and low-rate clock. */
final class ClassicUi {
    static final int INK=Color.rgb(40,40,40),MUTED=Color.rgb(105,105,105),ACCENT=Color.rgb(65,97,113);
    static final int PAPER=Color.rgb(243,243,243),LINE=Color.rgb(121,121,121),SELECT=Color.rgb(227,227,227);
    static final Handler MAIN=new Handler(Looper.getMainLooper());
    static final ExecutorService ICON_WORKER=Executors.newSingleThreadExecutor(r->new Thread(r,"shell-icons"));
    static final LruCache<String,Bitmap> ICONS=new LruCache<>(128);
    private static Bitmap grid;
    static TextView text(Context c,String value,int size){
        TextView t=new ShellTextView(c);t.setText(value);t.setTextColor(INK);
        DsTypography.body(t,size);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("'kern' 0, 'liga' 0");t.setFocusable(false);return t;
    }
    static void text(TextView t,String value){if(!value.contentEquals(t.getText()))t.setText(value);}
    static TextView label(Context c,String value,int size,int color){
        TextView t=text(c,value,size);t.setTextColor(color);return t;
    }
    static void readingText(TextView view,String value){
        if(value.contentEquals(view.getText()))return;
        android.text.SpannableString text=new android.text.SpannableString(value);
        int start=0;boolean first=true;
        while(start<value.length()){
            int end=value.indexOf('\n',start);if(end<0)end=value.length();String line=value.substring(start,end);
            boolean paragraph=start==0||start>1&&value.charAt(start-1)=='\n'&&value.charAt(start-2)=='\n';
            if(!line.isEmpty()&&(first||paragraph&&line.length()<=34&&!line.startsWith("http")&&!line.endsWith("。"))){
                // Pixel outlines keep their original stroke width; color and spacing carry hierarchy.
                text.setSpan(new android.text.style.ForegroundColorSpan(first?INK:ACCENT),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if(first)text.setSpan(new android.text.style.AbsoluteSizeSpan(DsTypography.TEXT_PX),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if(!line.isEmpty())first=false;start=end+1;
        }
        view.setText(text);
    }
    static void place(FrameLayout root,View v,int x,int y,int width,int height){
        
        if(v.getBackground() instanceof Edge||v.getBackground() instanceof TabFace||v.getBackground() instanceof InputEdge){int[] r=DsGrid.align(root,x,y,width,height);x=r[0];y=r[1];width=r[2];height=r[3];}
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;
        if(v.getTag()==null)v.setTag("at_"+x+"_"+y+"_"+width+"_"+height);root.addView(v,p);
    }
    static FrameLayout page(Context c){FrameLayout p=new FrameLayout(c);p.setVisibility(View.GONE);return p;}
    static TextView button(FrameLayout parent,Context c,String value,int x,int y,int w,int h,Runnable action){
        TextView t=text(c,value,20);DsTypography.ui(t);t.setGravity(Gravity.CENTER);t.setBackground(new Edge(false));
        // TextView clips to its padded content, not just the outer view. Reserve
        // the complete font line box even in the 32px toolbar/scope controls.
        int vertical=Math.min(6,Math.max(0,(h-t.getLineHeight())/2));
        t.setPadding(8,vertical,8,vertical);t.setMaxLines(h>=52?2:1);if(h<52)t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);
        t.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{MUTED,INK}));
        t.setOnClickListener(v->action.run());place(parent,t,x,y,w,h);return t;
    }
    static TextView toolbarButton(FrameLayout parent,Context c,String value,int x,int y,int w,Runnable action){
        TextView t=new ToolbarTextView(c,value);t.setGravity(Gravity.CENTER);t.setTextColor(INK);t.setPadding(8,2,8,2);t.setBackground(new Edge(false));t.setOnClickListener(v->action.run());place(parent,t,x,y,w,32);return t;
    }
    static void lines(TextView t,int count){t.setMaxLines(count);t.setEllipsize(TextUtils.TruncateAt.END);if(android.os.Build.VERSION.SDK_INT>=28)t.setFallbackLineSpacing(false);}
    static void focus(View v,boolean selected){
        if(v.isSelected()==selected)return;v.setSelected(selected);
        if(v.getBackground() instanceof Edge)((Edge)v.getBackground()).select(selected);
    }
    static class Edge extends Drawable {
        final Paint p=new Paint();boolean selected,enabled=true,pressed,keyboardFocus,busy,stateSelected;final boolean list;private Shader surface,pressedSurface;
        private android.widget.ListView cursorOwner;
        void cursorOwner(android.widget.ListView owner){cursorOwner=owner;}
        Edge(boolean selected){this(selected,false);}Edge(boolean s,boolean list){selected=s;this.list=list;}
        @Override protected void onBoundsChange(Rect r){surface=new LinearGradient(0,r.top,0,r.bottom,new int[]{0xFFF3F3F3,0xFFE3E3E3,0xFFCBCBCB},new float[]{0,.55f,1},Shader.TileMode.CLAMP);pressedSurface=new LinearGradient(0,r.top,0,r.bottom,0xFFC3C3C3,0xFFE3E3E3,Shader.TileMode.CLAMP);}
        void select(boolean s){selected=s;invalidateSelf();}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] states){boolean e=false,t=false,k=false,b=false,s=false;for(int state:states){if(state==android.R.attr.state_enabled)e=true;if(state==android.R.attr.state_pressed)t=true;if(state==android.R.attr.state_focused)k=true;if(state==android.R.attr.state_activated)b=true;if(state==android.R.attr.state_selected)s=true;}if(e==enabled&&t==pressed&&k==keyboardFocus&&b==busy&&s==stateSelected)return false;enabled=e;pressed=t;keyboardFocus=k;busy=b;stateSelected=s;invalidateSelf();return true;}
        private void box(Canvas c,int l,int t,int r,int b,int color){p.setColor(color);c.drawRect(l,t,r,b,p);}
        @Override public void draw(Canvas c){Rect r=getBounds();int edge=DsGrid.EDGE;
            {
                // Reference button grayscale transition, cached for this control's fixed bounds.
                p.setShader(enabled?(pressed?pressedSurface:surface):null);p.setColor(0xFFD3D3D3);c.drawRect(r,p);p.setShader(null);
                box(c,r.left,r.top,r.right,r.top+edge,INK);box(c,r.left,r.top,r.left+edge,r.bottom,INK);box(c,r.left,r.bottom-edge,r.right,r.bottom,INK);box(c,r.right-edge,r.top,r.right,r.bottom,INK);
                int light=pressed?LINE:Color.rgb(251,251,251),shadow=pressed?Color.rgb(251,251,251):Color.rgb(170,170,170);
                box(c,r.left+edge,r.top+edge,r.right-edge,r.top+edge*2,light);box(c,r.left+edge,r.top+edge,r.left+edge*2,r.bottom-edge,light);
                box(c,r.left+edge,r.bottom-edge*2,r.right-edge,r.bottom-edge,shadow);box(c,r.right-edge*2,r.top+edge,r.right-edge,r.bottom-edge,shadow);
            }
            if(enabled&&(selected||keyboardFocus||stateSelected)&&(cursorOwner==null||cursorOwner.hasFocus())){
                int o=-2; /* Internal focus safety area; siblings cannot cover these pixels. */int[][] corners={{r.left-o,r.top-o,1,1},{r.right+o,r.top-o,-1,1},{r.left-o,r.bottom+o,1,-1},{r.right+o,r.bottom+o,-1,-1}};
                for(int[] a:corners)for(int layer=0;layer<2;layer++){int len=DsGrid.FOCUS_LENGTH-layer*2,thick=layer==0?6:2,offset=layer==0?0:2;int x=a[0]+a[2]*offset,y=a[1]+a[3]*offset;p.setColor(layer==0?Color.rgb(251,251,251):ACCENT);c.drawRect(Math.min(x,x+a[2]*len),Math.min(y,y+a[3]*thick),Math.max(x,x+a[2]*len),Math.max(y,y+a[3]*thick),p);c.drawRect(Math.min(x,x+a[2]*thick),Math.min(y,y+a[3]*len),Math.max(x,x+a[2]*thick),Math.max(y,y+a[3]*len),p);}
            }
            if(busy)box(c,r.right-12,r.top+8,r.right-8,r.top+12,ACCENT);
        }
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    static final class Band extends Drawable {
        private final Paint p=new Paint();private Shader shade;
        @Override protected void onBoundsChange(Rect r){shade=new LinearGradient(0,r.top,0,r.bottom,Color.rgb(170,195,211),Color.rgb(97,130,154),Shader.TileMode.CLAMP);}
        @Override public void draw(Canvas c){Rect r=getBounds();p.setShader(shade);c.drawRect(r,p);p.setShader(null);p.setColor(Color.WHITE);c.drawRect(r.left,r.top,r.right,r.top+1,p);p.setColor(ACCENT);c.drawRect(r.left,r.bottom-2,r.right,r.bottom,p);}
        @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    /** Inset editing surface uses the same cached grayscale and border recipe. */
    static final class InputEdge extends Edge {
        InputEdge(){super(false);}
        @Override public void draw(Canvas canvas){boolean old=pressed;pressed=enabled;super.draw(canvas);pressed=old;}
    }
    /** Navigation shares the approved setting/button surface, including its focus owner. */
    static final class TabFace extends Edge {
        TabFace(){super(false);}
        @Override protected boolean onStateChange(int[] states){
            boolean changed=super.onStateChange(states),next=false;
            for(int state:states)if(state==android.R.attr.state_selected)next=true;
            if(next!=selected){select(next);changed=true;}return changed;
        }
    }
    static FrameLayout panel(Context c){FrameLayout p=new FrameLayout(c);p.setBackground(new Edge(false));return p;}
    static Drawable progress(){android.graphics.drawable.LayerDrawable d=new android.graphics.drawable.LayerDrawable(new Drawable[]{new android.graphics.drawable.ColorDrawable(0xFFC3C3C3),new android.graphics.drawable.ClipDrawable(new android.graphics.drawable.ColorDrawable(ACCENT),Gravity.LEFT,android.graphics.drawable.ClipDrawable.HORIZONTAL)});d.setId(0,android.R.id.background);d.setId(1,android.R.id.progress);return d;}
    static final class SeekThumb extends Edge {SeekThumb(){super(false);setBounds(0,0,20,28);}@Override public int getIntrinsicWidth(){return 20;}@Override public int getIntrinsicHeight(){return 28;}}
    static final class CheckFace extends Drawable {
        final Paint p=new Paint();final Edge face=new Edge(false);final DsIcons mark=new DsIcons(DsIcons.CHECK,16);boolean checked,enabled=true;
        @Override public int getIntrinsicWidth(){return 24;}@Override public int getIntrinsicHeight(){return 24;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] states){boolean c=false,e=false;for(int s:states){if(s==android.R.attr.state_checked)c=true;if(s==android.R.attr.state_enabled)e=true;}if(c==checked&&e==enabled)return false;checked=c;enabled=e;invalidateSelf();return true;}
        public void draw(Canvas c){Rect b=getBounds();int x=b.centerX()-12,y=b.centerY()-12;face.setBounds(x,y,x+24,y+24);face.setState(getState());face.draw(c);if(checked){mark.setBounds(x+4,y+4,x+20,y+20);mark.draw(c);}}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    static final class Icon extends View {
        enum Kind { SYMBOL, GAME, FOLDER }
        final Paint paint=new Paint();Bitmap bitmap;private String key="",boundId="",bitmapKey="";
        private Kind kind=Kind.SYMBOL;private DsIcons glyph;private long generation;
        private java.util.concurrent.Future<?> request;
        private String drawnMode="NOT_DRAWN",drawnId="";private long drawnGeneration=-1;
        Icon(Context c,int symbol){super(c);glyph=new DsIcons(symbol,32);key="SYMBOL:"+symbol;}
        void bindFolder(String id){bindKind(Kind.FOLDER,id,"FOLDER:"+id,DsIcons.FOLDER);}
        private boolean bindKind(Kind next,String id,String nextKey,int symbol){
            if(key.equals(nextKey)&&kind==next)return false;
            if(request!=null){request.cancel(false);request=null;}
            generation++;key=nextKey;kind=next;boundId=id;bitmap=null;bitmapKey="";
            glyph=new DsIcons(symbol,32);invalidate();return true;
        }
        void bind(GameEntry game){
            String id=game==null?"":game.gameId,next="GAME:"+id+":"+(game==null?0:game.romModifiedAt);
            if(!bindKind(Kind.GAME,id,next,DsIcons.GAME)||game==null)return;
            Bitmap cached=ICONS.get(next);if(cached!=null){bitmap=cached;bitmapKey=next;invalidate();return;}
            final long ticket=generation;
            request=ICON_WORKER.submit(()->{
                Bitmap b=ICONS.get(next);if(b==null){b=game.icon();if(b!=null)ICONS.put(next,b);}
                final Bitmap ready=b;MAIN.post(()->accept(ticket,next,ready));
            });
        }
        private void accept(long ticket,String expected,Bitmap ready){
            if(generation!=ticket||kind!=Kind.GAME||!key.equals(expected)||!isAttachedToWindow())return;
            bitmap=ready;bitmapKey=ready==null?"":expected;request=null;invalidate();
        }
        @Override protected void onDraw(Canvas c){
            drawnId=boundId;drawnGeneration=generation;
            if(bitmap!=null){
                drawnMode="ROM_BITMAP";int scale=Math.max(1,Math.min(getWidth()/32,getHeight()/32));int size=32*scale;
                int x=(getWidth()-size)/2,y=(getHeight()-size)/2;paint.setFilterBitmap(false);
                c.drawBitmap(bitmap,null,new Rect(x,y,x+size,y+size),paint);return;
            }
            drawnMode="GLYPH";glyph.setBounds(0,0,getWidth(),getHeight());glyph.draw(c);
        }
        org.json.JSONObject identity()throws org.json.JSONException{
            org.json.JSONObject o=new org.json.JSONObject().put("renderMode",drawnMode)
                .put("actualGlyphSymbol",glyph.symbol).put("boundItemKind",kind.name()).put("boundItemId",boundId)
                .put("bindingKey",key).put("bindingGeneration",generation).put("drawnGeneration",drawnGeneration)
                .put("drawnItemId",drawnId).put("bitmapBindingKey",bitmapKey);
            o.put("bitmapIdentity",bitmap==null?org.json.JSONObject.NULL:new org.json.JSONObject()
                .put("generationId",bitmap.getGenerationId()).put("object",Integer.toHexString(System.identityHashCode(bitmap)))
                .put("width",bitmap.getWidth()).put("height",bitmap.getHeight()));return o;
        }
        @Override protected void onDetachedFromWindow(){generation++;if(request!=null)request.cancel(false);request=null;key="";super.onDetachedFromWindow();}
    }

    static final class ClockFace extends View {
        Bitmap face;final Paint p=new Paint();final Calendar time=Calendar.getInstance();
        private final Runnable secondTick=new Runnable(){public void run(){
            if(!isAttachedToWindow()||!isShown())return;
            refresh();postDelayed(this,1000L-System.currentTimeMillis()%1000L);
        }};
        ClockFace(Context c){super(c);DsTypography.paint(c,p,20);setContentDescription("Current system time");}
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();removeCallbacks(secondTick);if(isShown())secondTick.run();}
        @Override public void onVisibilityAggregated(boolean visible){super.onVisibilityAggregated(visible);removeCallbacks(secondTick);if(visible&&isAttachedToWindow())secondTick.run();}
        @Override protected void onDetachedFromWindow(){removeCallbacks(secondTick);super.onDetachedFromWindow();}
        void refresh(){time.setTimeInMillis(System.currentTimeMillis());invalidate(20,20,getWidth()-20,getHeight()-20);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            if(w<=0||h<=0)return;
            face=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(face);
            c.drawColor(PAPER);p.setColor(Color.rgb(227,227,227));
            for(int x=0;x<w;x+=HomeLayout.TOP_GRID.main)c.drawRect(x,0,x+1,h,p);for(int y=0;y<h;y+=HomeLayout.TOP_GRID.main)c.drawRect(0,y,w,y+1,p);
            p.setColor(INK);c.drawRect(0,0,w,2,p);c.drawRect(0,0,2,h,p);c.drawRect(w-2,0,w,h,p);c.drawRect(0,h-2,w,h,p);
            float cx=w/2f,cy=h/2f;
            p.setColor(Color.rgb(121,121,121));int[][] marks={{1,0},{2,1},{2,3},{1,4},{-1,4},{-2,3},{-2,1},{-1,0}};
            for(int[] mark:marks){float x=cx+mark[0]*(w-48)/4f,y=24+mark[1]*(h-48)/4f;c.drawRect(Math.round(x)-3,Math.round(y)-3,Math.round(x)+3,Math.round(y)+3,p);}
            p.setColor(Color.rgb(170,170,170));
            numeral(c,"1",(int)cx-30,10,6);numeral(c,"2",(int)cx,10,6);
            numeral(c,"6",(int)cx-15,h-52,6);numeral(c,"9",10,(int)cy-21,6);numeral(c,"3",w-40,(int)cy-21,6);
        }
        private void numeral(Canvas c,String digit,int x,int y,int scale){
            String[] rows;
            switch(digit){
                case "1":rows=new String[]{"01100","11100","01100","01100","01100","01100","01100"};break;
                case "2":rows=new String[]{"11111","00011","00011","11111","11000","11000","11111"};break;
                case "3":rows=new String[]{"11111","00011","00011","11111","00011","00011","11111"};break;
                case "6":rows=new String[]{"11111","11000","11000","11111","11011","11011","11111"};break;
                default:rows=new String[]{"11111","11011","11011","11111","00011","00011","11111"};
            }
            for(int row=0;row<7;row++)for(int col=0;col<5;col++)if(rows[row].charAt(col)=='1')c.drawRect(x+col*scale,y+row*scale,x+(col+1)*scale,y+(row+1)*scale,p);
        }
        @Override protected void onDraw(Canvas c){
            if(face!=null)c.drawBitmap(face,0,0,null);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-27;
            int second=time.get(Calendar.SECOND);double minute=time.get(Calendar.MINUTE)+second/60.0,hour=(time.get(Calendar.HOUR)+minute/60.0)*Math.PI/6;
            hand(c,cx,cy,hour,r*.58f,4,INK);hand(c,cx,cy,minute*Math.PI/30,r*.92f,3,ACCENT);
            hand(c,cx,cy,second*Math.PI/30,r*.96f,2,Color.rgb(191,101,123));
            p.setColor(INK);c.drawRect(cx-4,cy-4,cx+4,cy+4,p);
        }
        void hand(Canvas c,float x,float y,double a,float length,float width,int color){
            // Integer staircase hands on a square dial, not a smoothed phone clock.
            p.setColor(color);int endX=Math.round(x+(float)Math.sin(a)*length),endY=Math.round(y-(float)Math.cos(a)*length);
            int steps=Math.max(Math.abs(endX-(int)x),Math.abs(endY-(int)y));
            for(int i=0;i<=steps;i++){int px=Math.round(x+(endX-x)*i/Math.max(1,steps)),py=Math.round(y+(endY-y)*i/Math.max(1,steps));c.drawRect(px-(int)width/2,py-(int)width/2,px+(int)width/2+1,py+(int)width/2+1,p);}
        }
    }
    static final class Month extends View {
        final Paint p=new Paint();final Calendar today=Calendar.getInstance();long localeRevision=-1;
        Month(Context c){super(c);DsTypography.paint(c,p,20);}
        void refresh(){
            int old=today.get(Calendar.DAY_OF_YEAR),year=today.get(Calendar.YEAR);
            today.setTimeInMillis(System.currentTimeMillis());
            if(old!=today.get(Calendar.DAY_OF_YEAR)||year!=today.get(Calendar.YEAR)||localeRevision!=LocaleSettings.revision()){localeRevision=LocaleSettings.revision();invalidate();}
        }
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();p.setColor(PAPER);c.drawRect(0,0,w,h,p);
            p.setColor(INK);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRect(1,1,w-1,h-1,p);p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(20);p.setColor(INK);
            String[] days=LocaleSettings.ui().equals("zh")?new String[]{"日","一","二","三","四","五","六"}:new String[]{"Su","Mo","Tu","We","Th","Fr","Sa"};
            Calendar first=(Calendar)today.clone();first.set(Calendar.DAY_OF_MONTH,1);
            int offset=first.get(Calendar.DAY_OF_WEEK)-1,max=today.getActualMaximum(Calendar.DAY_OF_MONTH);
            int weeks=(offset+max+6)/7;
            float cell=(w-4)/7f,step=(h-34)/(float)weeks;
            for(int i=0;i<7;i++){
                int x=Math.round(2+cell*i),right=Math.round(2+cell*(i+1));
                p.setColor(i==0?Color.rgb(244,216,229):i==6?Color.rgb(209,226,245):PAPER);c.drawRect(x,2,right,h-2,p);
                p.setColor(i==0?Color.rgb(163,76,109):i==6?Color.rgb(63,102,145):Color.rgb(103,113,118));c.drawRect(x,2,right,32,p);
                p.setColor(Color.WHITE);c.drawText(days[i],Math.round(2+cell*(i+.5f)),25,p);
                p.setColor(LINE);c.drawRect(x,2,x+1,h-2,p);
            }
            for(int row=0;row<=weeks;row++){int y=Math.round(32+row*step);p.setColor(LINE);c.drawRect(2,y,w-2,y+1,p);}
            p.setTextSize(20);
            for(int d=1;d<=max;d++){
                int slot=offset+d-1;float x=2+cell*(slot%7+.5f),y=32+(slot/7)*step+step/2+7;
                boolean active=d==today.get(Calendar.DAY_OF_MONTH);
                if(active){p.setColor(ACCENT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawRect(Math.round(x-cell/2+3),Math.round(32+(slot/7)*step+3),Math.round(x+cell/2-3),Math.round(32+(slot/7+1)*step-3),p);p.setStyle(Paint.Style.FILL);}
                p.setColor(slot%7==0?Color.rgb(163,76,109):slot%7==6?Color.rgb(63,102,145):INK);
                c.drawText(String.valueOf(d),Math.round(x),Math.round(y),p);
            }
        }
    }
}
