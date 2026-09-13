package com.rgds.ultimate.shell;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

/** Aspect-preserving cover; bitmap changes invalidate drawing without remeasuring both screens. */
final class CoverView extends FrameLayout {
    private enum State {READY,LOADING,MISSING,FAILED}
    private final View image;private final ClassicUi.Icon fallback;private final TextView label;
    private final MetadataManager metadata;private Bitmap bitmap;private String bound="",drawn="";private State state=State.MISSING;
    private boolean topAligned;
    private long requestedAt;private Runnable deferred;private GameEntry currentGame;private boolean hasBinding;
    CoverView(Context c){
        super(c);metadata=MetadataManager.get(c);setBackground(new ClassicUi.Edge(false));
        image=new View(c){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF destination=new RectF();
            @Override protected void onDraw(Canvas canvas){
                Bitmap b=bitmap;if(b==null)return;float scale=Math.min((getWidth()-14f)/b.getWidth(),(getHeight()-14f)/b.getHeight());
                float w=b.getWidth()*scale,h=b.getHeight()*scale,y=topAligned?7:(getHeight()-h)/2;destination.set((getWidth()-w)/2,y,(getWidth()+w)/2,y+h);canvas.drawBitmap(b,null,destination,paint);
                if(!drawn.equals(bound)){drawn=bound;PerfTrace.event("COVER_DRAW uptime="+SystemClock.uptimeMillis()+" requestMs="+(SystemClock.uptimeMillis()-requestedAt)+" binding="+bound);}
            }};addView(image,new LayoutParams(-1,-1));
        fallback=new ClassicUi.Icon(c,0);addView(fallback,new LayoutParams(64,64,Gravity.CENTER));
        label=ClassicUi.label(c,"",16,ClassicUi.MUTED);label.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);label.setPadding(8,0,8,24);addView(label,new LayoutParams(-1,-1));
    }
    void setTopAligned(boolean value){if(topAligned!=value){topAligned=value;image.invalidate();}}
    void bind(GameEntry g){
        currentGame=g;hasBinding=true;
        String key=g==null?"":metadata.coverKey(g.gameId),next=(g==null?"":g.gameId)+":"+key;
        if(next.equals(bound)&&(state==State.READY||state==State.MISSING||deferred!=null||metadata.covers().subscribed(this)))return;cancel();bound=next;requestedAt=SystemClock.uptimeMillis();
        Bitmap hit=key.isEmpty()?null:metadata.covers().cached(key,288);
        if(hit!=null){metadata.covers().request(this,key,288,b->{if(bound.equals(next)&&b!=null)show(b);});return;}
        bitmap=null;image.invalidate();fallback.bind(g);fallback.setVisibility(VISIBLE);label.setVisibility(VISIBLE);
        state=key.isEmpty()?State.MISSING:State.LOADING;label.setText(g==null?UiStrings.msg("ui_8cca87194c0f"):key.isEmpty()?UiStrings.msg("ui_393dbfc51f2d"):UiStrings.msg("ui_f2a1d8f27455"));
        if(key.isEmpty())return;
        deferred=()->{deferred=null;if(bound.equals(next)&&isShown())metadata.covers().request(this,key,288,b->{if(bound.equals(next)&&isShown()){if(b!=null)show(b);else{state=State.FAILED;label.setText(UiStrings.msg("ui_c86bc011ec0a"));}}});};
        postDelayed(deferred,24);
    }
    private void show(Bitmap b){bitmap=b;state=State.READY;fallback.setVisibility(INVISIBLE);label.setVisibility(INVISIBLE);image.invalidate();}
    void emptyState(String text){if(bound.equals(":"))label.setText(text);}
    private void cancel(){if(deferred!=null){removeCallbacks(deferred);deferred=null;}metadata.covers().release(this);}
    private void releaseVisible(){cancel();bound="";bitmap=null;image.invalidate();}
    private void restore(){if(hasBinding)bind(currentGame);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(metadata!=null){if(visibility!=VISIBLE)releaseVisible();else restore();}}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(metadata!=null){if(!isShown())releaseVisible();else restore();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restore();}
    @Override protected void onDetachedFromWindow(){cancel();bound="";bitmap=null;super.onDetachedFromWindow();}
}
