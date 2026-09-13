package com.rgds.ultimate.shell;
import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.util.Log;
import android.view.*;
import java.util.*;
/** Optional per-window measurements, buffered outside rendering; not photon timing. */
final class PerfTrace {
    static volatile boolean enabled;
    private static boolean initialized;
    static synchronized void init(Context context){if(!initialized){enabled=context.getSharedPreferences("rgds_diagnostics_v8",0).getBoolean("enabled",false);initialized=true;}}
    static boolean isEnabled(){return enabled;}
    static void setEnabled(Context context,boolean value){initialized=true;enabled=value;context.getSharedPreferences("rgds_diagnostics_v8",0).edit().putBoolean("enabled",value).apply();}
    private static final HandlerThread THREAD=new HandlerThread("shell-frame-metrics");
    static {THREAD.start();}
    private static final Handler HANDLER=new Handler(THREAD.getLooper());
    private final Window window;private final Window.OnFrameMetricsAvailableListener listener;
    private final ArrayDeque<String> frames=new ArrayDeque<>();private boolean flushPending;private int droppedBuffer;
    static void event(String text){if(enabled)Log.i("TwinGridPerf",text);}
    PerfTrace(Activity a,int display){
        init(a);window=ShellWindowPolicy.window(a);
        listener=(w,f,dropped)->{
            if(!enabled)return;
            String row="display="+display+" uptime="+SystemClock.uptimeMillis()+" intendedNs="+f.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)+" totalNs="+f.getMetric(FrameMetrics.TOTAL_DURATION)+" layoutNs="+f.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)+" drawNs="+f.getMetric(FrameMetrics.DRAW_DURATION)+" syncNs="+f.getMetric(FrameMetrics.SYNC_DURATION)+" commandNs="+f.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)+" gpuNs="+(Build.VERSION.SDK_INT>=31?f.getMetric(FrameMetrics.GPU_DURATION):-1)+" dropped="+dropped;
            if(frames.size()==512){frames.removeFirst();droppedBuffer++;}frames.add(row);
            if(!flushPending){flushPending=true;HANDLER.postDelayed(this::flush,750);}
        };window.addOnFrameMetricsAvailableListener(listener,HANDLER);
    }
    private void flush(){flushPending=false;if(!enabled){frames.clear();droppedBuffer=0;return;}boolean any=!frames.isEmpty();while(!frames.isEmpty())Log.i("TwinGridFrame",frames.removeFirst());if(droppedBuffer>0){event("FRAME_BUFFER_DROPPED count="+droppedBuffer);droppedBuffer=0;}if(any&&enabled)event("GC_SNAPSHOT uptime="+SystemClock.uptimeMillis()+" count="+Debug.getRuntimeStat("art.gc.gc-count")+" gcMs="+Debug.getRuntimeStat("art.gc.gc-time")+" allocated="+Debug.getRuntimeStat("art.gc.bytes-allocated")+" freed="+Debug.getRuntimeStat("art.gc.bytes-freed"));}
    void close(){window.removeOnFrameMetricsAvailableListener(listener);HANDLER.post(this::flush);}
}
