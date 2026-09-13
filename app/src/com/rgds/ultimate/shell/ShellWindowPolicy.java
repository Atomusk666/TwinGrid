package com.rgds.ultimate.shell;

import android.view.*;
import android.os.Build;
import org.json.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** One owner per Window. Event driven; no timer can outlive editing or an external handoff. */
final class ShellWindowPolicy {
    static Window window(android.app.Activity a){return a instanceof TopDisplayActivity?((TopDisplayActivity)a).surfaceWindow():a.getWindow();}
    enum Mode { BROWSING, MODAL, EDITING, EXTERNAL }
    private static final WeakHashMap<Window,Entry> entries=new WeakHashMap<>();
    private static final ArrayDeque<JSONObject> events=new ArrayDeque<>();
    private static final class Entry {
        Mode mode=Mode.BROWSING;String owner="";boolean active=true,focused,fits,wakePending;long generation;
        android.os.CancellationSignal control;
    }
    private static Entry entry(Window w){
        Entry e=entries.get(w);if(e!=null)return e;
        e=new Entry();entries.put(w,e);final Entry state=e;WeakReference<Window> ref=new WeakReference<>(w);
        View decor=w.getDecorView();state.focused=decor.hasWindowFocus();
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){Window x=ref.get();if(x!=null){state.generation++;apply(x,state,"attached");}}
            public void onViewDetachedFromWindow(View v){cancel(state);state.generation++;state.focused=false;}
        });
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(f->{Window x=ref.get();if(x==null)return;
            if(state.focused!=f){state.focused=f;cancel(state);state.generation++;record(x,state,"focus:"+f);}
            if(f)apply(x,state,"focus_acquired");
        });
        return e;
    }
    static void set(Window w,Mode mode,String owner){
        Entry e=entry(w);if(e.mode==mode&&e.owner.equals(owner))return;
        cancel(e);e.mode=mode;e.owner=owner;e.generation++;apply(w,e,"mode");
    }
    static void resumed(Window w,boolean active){
        Entry e=entry(w);if(e.active==active)return;cancel(e);e.active=active;e.generation++;
        if(active)apply(w,e,"resumed");else record(w,e,"paused");
    }
    static void refresh(Window w,String reason){Entry e=entry(w);cancel(e);e.generation++;apply(w,e,reason);}
    static void screenOn(Window w){Entry e=entry(w);cancel(e);e.wakePending=true;e.generation++;apply(w,e,"screen_on");}
    private static void cancel(Entry e){if(e.control!=null){android.os.CancellationSignal c=e.control;e.control=null;c.cancel();}}
    static void forget(Window w){Entry e=entries.remove(w);if(e!=null)cancel(e);}
    private static void apply(Window w,Entry e,String reason){
        if(!e.active||e.mode==Mode.EXTERNAL){record(w,e,reason+":suspended");return;}
        boolean editing=e.mode==Mode.EDITING;
        // FLAG_FULLSCREEN and decor fitting are part of this same policy, never a second caller.
        if(editing)w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        else w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        int soft=w.getAttributes().softInputMode;
        int next=(soft&~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                |(editing?WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE:WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        if(soft!=next)w.setSoftInputMode(next);
        e.fits=editing;
        if(Build.VERSION.SDK_INT>=30){
            w.setDecorFitsSystemWindows(editing);
            WindowInsetsController controller=w.getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(editing)controller.show(WindowInsets.Type.systemBars());else controller.hide(WindowInsets.Type.systemBars());
            }
        }else w.getDecorView().setSystemUiVisibility(editing?View.SYSTEM_UI_FLAG_VISIBLE:
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        record(w,e,reason);
        if(!editing&&e.wakePending&&w.getDecorView().hasWindowFocus()&&Build.VERSION.SDK_INT>=30)settleWakeSurface(w,e);
    }
    /** One public-API end-state transaction after wake, even if logical visibility was already false.
     * V1.18 can retain a visible status-bar leash while reporting hidden Insets. No polling/hide timer.
     * The callback loses authority immediately on editing, focus loss, pause or window replacement. */
    private static void settleWakeSurface(Window w,Entry e){
        WindowInsetsController controller=w.getInsetsController();if(controller==null)return;
        e.wakePending=false;final long generation=e.generation;final android.os.CancellationSignal signal=new android.os.CancellationSignal();e.control=signal;
        record(w,e,"wake_surface_request");
        controller.controlWindowInsetsAnimation(WindowInsets.Type.systemBars(),0,null,signal,new WindowInsetsAnimationControlListener(){
            public void onReady(WindowInsetsAnimationController animation,int types){
                if(entries.get(w)!=e||e.generation!=generation||!e.active||!w.getDecorView().hasWindowFocus()
                        ||e.mode==Mode.EDITING||e.mode==Mode.EXTERNAL){signal.cancel();return;}
                animation.setInsetsAndAlpha(animation.getHiddenStateInsets(),0f,1f);animation.finish(false);
                record(w,e,"wake_surface_hidden");
            }
            public void onFinished(WindowInsetsAnimationController animation){if(e.control==signal)e.control=null;record(w,e,"wake_surface_finished");}
            public void onCancelled(WindowInsetsAnimationController animation){if(e.control==signal)e.control=null;record(w,e,"wake_surface_cancelled");}
        });
    }
    private static JSONObject state(Window w,Entry e)throws JSONException{
        View v=w.getDecorView();Display d=v.getDisplay();
        return new JSONObject().put("windowIdentity",Integer.toHexString(System.identityHashCode(w)))
                .put("display",d==null?-1:d.getDisplayId()).put("owner",e.owner).put("mode",e.mode.name())
                .put("generation",e.generation).put("active",e.active).put("focus",v.hasWindowFocus())
                .put("decorFitsSystemWindows",e.fits).put("flags",w.getAttributes().flags)
                .put("softInputMode",w.getAttributes().softInputMode).put("pendingPolicyTimers",0);
    }
    private static void record(Window w,Entry e,String reason){try{
        JSONObject row=state(w,e).put("event",reason).put("uptime",android.os.SystemClock.uptimeMillis());
        if(events.size()==64)events.removeFirst();events.addLast(row);
    }catch(JSONException ignored){}}
    static JSONObject diagnostics(Window w)throws JSONException{
        Entry e=entries.get(w);JSONObject out=e==null?new JSONObject():state(w,e);
        JSONArray timeline=new JSONArray();for(JSONObject row:events)timeline.put(row);
        return out.put("timeline",timeline).put("observationLimit","Window requests; compositor/framebuffer must be checked independently");
    }
}
