package com.rgds.ultimate.shell;
import android.content.Context;import android.graphics.*;import android.os.*;import android.text.*;import android.util.LruCache;import android.view.View;import java.util.concurrent.*;
/** Layout keyed by text and real geometry; only the latest pending selection is retained. */
final class DescriptionView extends View {
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final LruCache<String,StaticLayout> cache=new LruCache<>(96);
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"overview-layout"));
    private final Handler main=new Handler(Looper.getMainLooper());private final Object lock=new Object();
    private StaticLayout layout;private String bound="",gameId="",currentText="",drawn="",shortSource="source_excerpt";private long requestedAt;private long generation;private Request pending;private boolean pumping;
    private static final class Request {String text,key;int width,height;long generation;TextPaint paint;Request(String t,String k,long g,int w,int h,TextPaint p){text=t;key=k;generation=g;width=w;height=h;paint=p;}}
    DescriptionView(Context context){super(context);setPadding(12,12,12,12);DsTypography.paint(context,paint,20);paint.setColor(ClassicUi.INK);}
    void bind(String id,String text){gameId=id;currentText=text;text=UiStrings.display(text);String draft=MetadataManager.get(getContext()).overviewDisplay(id,text);shortSource=draft.equals(text)?"source_excerpt":"reviewed_draft_v1";text=draft;int width=Math.max(1,(getWidth()>0?getWidth():326)-getPaddingLeft()-getPaddingRight()),height=Math.max(1,(getHeight()>0?getHeight():222)-getPaddingTop()-getPaddingBottom());String cacheKey="overview"+OverviewLayout.VERSION+":"+LocaleSettings.ui()+":"+LocaleSettings.revision()+":"+System.identityHashCode(paint.getTypeface())+":"+paint.getTextScaleX()+":"+paint.getTextSkewX()+":"+paint.getFlags()+":"+width+":"+height+":"+paint.getTextSize()+":"+text;String key=id+"\n"+MetadataManager.hash(UiStrings.display(currentText))+"\n"+cacheKey;if(key.equals(bound))return;bound=key;requestedAt=SystemClock.uptimeMillis();long version=++generation;setContentDescription(text);
        StaticLayout hit=cache.get(cacheKey);layout=hit;invalidate();if(hit!=null||text.isEmpty()){synchronized(lock){pending=null;}return;}
        synchronized(lock){pending=new Request(text,cacheKey,version,width,height,new TextPaint(paint));if(pumping)return;pumping=true;worker.execute(this::pump);}
    }
    private void pump(){while(true){Request r;synchronized(lock){r=pending;pending=null;if(r==null){pumping=false;return;}}
        StaticLayout next=cache.get(r.key);if(next==null){next=fit(r);cache.put(r.key,next);}
        final StaticLayout ready=next;main.post(()->{if(generation==r.generation){layout=ready;invalidate();}});
    }}
    private static StaticLayout fit(Request r){return OverviewLayout.fit(r.text,r.width,r.height,r.paint);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(w!=ow||h!=oh){bound="";bind(gameId,currentText);}}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(layout!=null){int save=c.save();c.clipRect(getPaddingLeft(),getPaddingTop(),getWidth()-getPaddingRight(),getHeight()-getPaddingBottom());c.translate(getPaddingLeft(),getPaddingTop());layout.draw(c);c.restoreToCount(save);if(!drawn.equals(bound)){drawn=bound;PerfTrace.event("OVERVIEW_DRAW gameId="+gameId+" uptime="+SystemClock.uptimeMillis()+" requestMs="+(SystemClock.uptimeMillis()-requestedAt)+" lines="+layout.getLineCount()+" width="+getWidth()+" height="+layout.getHeight());}}}
    org.json.JSONObject geometry()throws org.json.JSONException{
        org.json.JSONObject o=new org.json.JSONObject().put("role",DsTypography.BODY).put("typeface",DsTypography.FACE).put("fontPx",paint.getTextSize()).put("availableWidth",getWidth()-getPaddingLeft()-getPaddingRight()).put("availableHeight",getHeight()-getPaddingTop()-getPaddingBottom()).put("contentInsets",new org.json.JSONArray(new int[]{getPaddingLeft(),getPaddingTop(),getPaddingRight(),getPaddingBottom()})).put("layoutReady",layout!=null);
        if(layout!=null){org.json.JSONArray lines=new org.json.JSONArray();for(int i=0;i<layout.getLineCount();i++)lines.put(new org.json.JSONObject().put("baseline",layout.getLineBaseline(i)).put("width",layout.getLineWidth(i)).put("start",layout.getLineStart(i)).put("end",layout.getLineEnd(i)));o.put("lines",lines).put("layoutHeight",layout.getHeight()).put("exceedsHeight",layout.getHeight()>getHeight()-getPaddingTop()-getPaddingBottom()).put("uncoveredByPixelFont",PixelCoverage.missing(layout.getText())).put("displayedText",layout.getText().toString()).put("sourceTextLength",UiStrings.display(currentText).length()).put("sourceText",UiStrings.display(currentText))
            .put("lineCount",layout.getLineCount()).put("shortTextSource",shortSource)
            .put("truncated",layout.getText().toString().endsWith("…")).put("condensed",shortSource.equals("reviewed_draft_v1"))
            .put("boundGameId",gameId).put("layoutGeneration",generation).put("fullDetailsReachable",true);}return o;
    }
    private void release(){generation++;synchronized(lock){pending=null;}bound="";layout=null;}
    private void restore(){if(!gameId.isEmpty())bind(gameId,currentText);}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(lock!=null){if(!isShown())release();else restore();}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(lock!=null){if(visibility!=VISIBLE)release();else restore();}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();restore();}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
}
