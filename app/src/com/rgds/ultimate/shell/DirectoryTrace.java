package com.rgds.ultimate.shell;

import android.view.*;
import org.json.*;

/** Bounded metadata only: no key text, coordinates, URIs, or game names. */
final class DirectoryTrace {
    static String lastInput="none";
    private static final java.util.ArrayDeque<JSONObject> inputs=new java.util.ArrayDeque<>();
    private static final java.util.WeakHashMap<Window,Boolean> watched=new java.util.WeakHashMap<>();
    static void watch(Window w,String surface){
        if(w==null||watched.containsKey(w))return;watched.put(w,true);final Window.Callback original=w.getCallback();
        w.setCallback((Window.Callback)java.lang.reflect.Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,args)->{
            if(args!=null&&args.length>0&&("dispatchKeyEvent".equals(method.getName())||"dispatchTouchEvent".equals(method.getName())))record(surface,args[0]);
            try{return method.invoke(original,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        }));
    }
    private static void record(String surface,Object value){try{
        JSONObject o=new JSONObject().put("surface",surface);String key;
        if(value instanceof KeyEvent){KeyEvent e=(KeyEvent)value;key="key:"+e.getDeviceId()+":"+e.getKeyCode()+":"+e.getDownTime();o.put("action",e.getAction()).put("repeat",e.getRepeatCount()).put("time",e.getEventTime()).put("source",e.getSource());}
        else{MotionEvent e=(MotionEvent)value;if(e.getActionMasked()!=MotionEvent.ACTION_DOWN&&e.getActionMasked()!=MotionEvent.ACTION_UP&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL)return;key="touch:"+e.getDeviceId()+":"+e.getDownTime();o.put("action",e.getActionMasked()).put("time",e.getEventTime()).put("source",e.getSource());}
        lastInput=key;o.put("id",key);if(inputs.size()==64)inputs.removeFirst();inputs.addLast(o);android.util.Log.i("TwinGridDirectoryInput",o.toString());
    }catch(Exception ignored){}}
    static JSONArray snapshot(){return new JSONArray(inputs);}
}
