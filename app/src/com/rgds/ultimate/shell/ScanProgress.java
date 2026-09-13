package com.rgds.ultimate.shell;
/** Actual counters, never an invented percentage. Runtime-only active state. */
final class ScanProgress {
    final String phase;final int session,folders,discovered,available,errors,opened,parsed,cached;final long changedAt;
    ScanProgress(String p,int s,int f,int d,int a,int e){this(p,s,f,d,a,e,0,0,0);}
    ScanProgress(String p,int s,int f,int d,int a,int e,int o,int r,int c){phase=p;session=s;folders=f;discovered=d;available=a;errors=e;opened=o;parsed=r;cached=c;changedAt=android.os.SystemClock.uptimeMillis();}
    boolean active(){return phase.equals("VALIDATING")||phase.equals("QUEUED")||phase.equals("DISCOVERING")||phase.equals("READING")||phase.equals("SAVES");}
    boolean attention(){return active()||phase.equals("FAILED")||phase.equals("PARTIAL")||phase.equals("CANCELED");}
    String title(){int n=phase.equals("VALIDATING")?13:phase.equals("QUEUED")?14:phase.equals("DISCOVERING")?15:phase.equals("READING")?16:phase.equals("COMPLETE")?17:phase.equals("PARTIAL")?18:phase.equals("FAILED")?19:phase.equals("CANCELED")?20:phase.equals("SAVES")?30:31;return JourneyGuide.s(n);}
    String counts(){boolean zh=LocaleSettings.ui().equals("zh");return (zh?"发现文件 ":"Files found ")+discovered+(zh?" · 打开 ":" · Opened ")+opened+"\n"+(zh?"解析 ":"Parsed ")+parsed+(zh?" · 缓存复用 ":" · Cached ")+cached+(zh?" · 错误 ":" · Errors ")+errors+"\n"+(zh?"索引保留 ":"Indexed ")+available+(zh?" · 本次未验证 ":" · Unverified this scan ")+Math.max(0,available-parsed-cached);}
    String compact(){int n=phase.equals("DISCOVERING")?55:phase.equals("READING")?56:phase.equals("SAVES")?57:phase.equals("VALIDATING")?58:59;return active()?JourneyGuide.s(n)+" · "+JourneyGuide.s(22)+discovered+" / "+JourneyGuide.s(23)+available+(errors>0?" · !"+errors:""):title();}
    String panel(){return title()+"\n\n"+counts()+"\n\n"+(phase.equals("FAILED")||phase.equals("PARTIAL")?(LocaleSettings.ui().equals("zh")?"查看读取原因，可重新实际读取。":"View read details and read the files again."):JourneyGuide.s(active()?25:28));}
    org.json.JSONObject json(){org.json.JSONObject o=new org.json.JSONObject();try{o.put("phase",phase).put("session",session).put("folders",folders).put("discovered",discovered).put("available",available).put("errors",errors).put("opened",opened).put("parsed",parsed).put("cacheHits",cached).put("retainedUnverified",Math.max(0,available-parsed-cached)).put("changedAt",changedAt).put("active",active());}catch(Exception ignored){}return o;}
}
