package com.rgds.ultimate.shell;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Bounded local evidence only; never changes scan acceptance, ROMs or user records. */
final class ScanReadEvidence {
    private final Context context;
    private final JSONObject data=new JSONObject(),counts=new JSONObject();
    private final JSONArray samples=new JSONArray();
    private final java.util.LinkedHashMap<String,JSONObject> diverse=new java.util.LinkedHashMap<>();
    private int cached,opened,parsed,failures,active,peak;
    private long lastPersist;
    ScanReadEvidence(Context c,int session,Uri tree){context=c;try{
        data.put("version",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName)
            .put("schema",2).put("versionCode",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode).put("pid",android.os.Process.myPid()).put("worker",Thread.currentThread().getName())
            .put("uid",android.os.Process.myUid()).put("session",session).put("started",System.currentTimeMillis()).put("terminal","RUNNING")
            .put("treeHash",hash(tree)).put("counts",counts).put("samples",samples);
    }catch(Exception ignored){}}
    static String hash(Uri uri)throws Exception{return LaunchDiagnostic.hash(String.valueOf(uri).getBytes(StandardCharsets.UTF_8));}
    synchronized void cached(){cached++;}
    synchronized void opened(){opened++;active++;peak=Math.max(peak,active);}
    synchronized void closed(){active=Math.max(0,active-1);}
    synchronized void parsed(){parsed++;}
    synchronized int cachedCount(){return cached;}
    synchronized int openedCount(){return opened;}
    synchronized int parsedCount(){return parsed;}
    synchronized void progress(int folders,int found,int available){try{data.put("folders",folders).put("discovered",found).put("availableIndex",available).put("retainedUnverified",Math.max(0,available-parsed-cached));}catch(Exception ignored){}}
    synchronized void failed(String stage,Uri uri,long size,long modified,Exception e,boolean cancelled){
        failures++;
        if(e instanceof NdsHeaderParser.ReadFailure)stage=((NdsHeaderParser.ReadFailure)e).stage;
        try{
            JSONObject detail=reason(stage,e,cancelled);stage=detail.optString("stage");
            String key=stage+":"+detail.optString("code")+":"+detail.optString("errnoName");counts.put(key,counts.optInt(key)+1);
            String doc;try{doc=android.provider.DocumentsContract.getDocumentId(uri);}catch(Exception ignored){doc="";}
            int depth=doc.isEmpty()?0:doc.split("/").length;String group=key+":depth="+depth;
            if(!diverse.containsKey(group)){
                JSONArray chain=new JSONArray();Throwable cause=e;
                for(int i=0;i<4&&cause!=null;i++,cause=cause.getCause()){
                    JSONObject item=new JSONObject().put("type",cause.getClass().getName());
                    // Exception messages can contain full user document paths. Keep only a correlation hash.
                    item.put("messageHash",LaunchDiagnostic.hash(String.valueOf(cause.getMessage()).getBytes(StandardCharsets.UTF_8)));
                    JSONArray frames=new JSONArray();for(StackTraceElement f:cause.getStackTrace())if(frames.length()<6)frames.put(f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber());
                    chain.put(item.put("frames",frames));
                }
                detail.put("documentHash",hash(uri)).put("size",size).put("metadataUnknown",size<=0).put("modifiedAt",modified).put("depth",depth).put("causes",chain);
                if(diverse.size()==12){String replace=null;for(String existing:diverse.keySet())if(!existing.startsWith(key+":"))replace=existing;if(replace!=null)diverse.remove(replace);}
                if(diverse.size()<12)diverse.put(group,detail);
            }
            long now=android.os.SystemClock.uptimeMillis();if(failures==1||now-lastPersist>=2000){lastPersist=now;persist(context,"scan_read_failure_v123.json",snapshot().toString().getBytes(StandardCharsets.UTF_8));}
        }catch(Exception ignored){}
    }
    synchronized JSONObject snapshot(){try{JSONArray rows=new JSONArray();for(JSONObject sample:diverse.values())rows.put(sample);return new JSONObject(data.toString()).put("samples",rows).put("cacheHits",cached).put("opened",opened).put("parsed",parsed).put("failures",failures).put("openResources",active).put("peakOpenResources",peak);}catch(Exception ignored){return new JSONObject();}}
    synchronized void finish(Context c,String terminal,boolean cancelled){try{
        data.put("terminal",terminal).put("cancelled",cancelled).put("ended",System.currentTimeMillis());
        byte[] bytes=snapshot().toString().getBytes(StandardCharsets.UTF_8);
        persist(c,"scan_read_last_v123.json",bytes);
        if(failures>0)persist(c,"scan_read_failure_v123.json",bytes);
    }catch(Exception ignored){}}
    static void persist(Context c,String name,byte[] bytes){AtomicFile f=new AtomicFile(new File(c.getFilesDir(),name));FileOutputStream out=null;try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);}}
    static JSONObject reason(String stage,Exception e,boolean cancelled){
        JSONObject result=new JSONObject();try{
            String code=cancelled?"CANCELLED":e instanceof SecurityException?"PERMISSION_DENIED":e instanceof java.io.FileNotFoundException?"DOCUMENT_NOT_FOUND":"IO_FAILURE";
            int errno=0;Throwable cause=e;
            for(int i=0;i<5&&cause!=null;i++,cause=cause.getCause()){
                if(cause instanceof android.system.ErrnoException)errno=((android.system.ErrnoException)cause).errno;
                if(cause instanceof NdsHeaderParser.ReadFailure){NdsHeaderParser.ReadFailure f=(NdsHeaderParser.ReadFailure)cause;stage=f.stage;code=f.code;result.put("expectedBytes",f.expected).put("actualBytes",f.actual).put("offset",f.offset).put("limit",f.limit);}
                if(cause instanceof LaunchFailure)code="LAUNCH_REASON_"+((LaunchFailure)cause).reason;
                String text=String.valueOf(cause.getMessage());for(String name:new String[]{"EACCES","EPERM","EMFILE","ENFILE","EIO","ENOENT","ESTALE","ESPIPE","ECANCELED","ENODEV"})if(java.util.regex.Pattern.compile("\\b"+name+"\\b").matcher(text).find()){result.put("errnoName",name);break;}
            }
            if(errno!=0)result.put("errno",errno).put("errnoName",android.system.OsConstants.errnoName(errno));
            result.put("stage",stage).put("code",code).put("exceptionClass",e.getClass().getName()).put("reason",description(code,result.optString("errnoName"),false)).put("cancelled",cancelled).put("threadInterrupted",Thread.currentThread().isInterrupted());
        }catch(Exception ignored){}return result;
    }
    static String description(String code,String errno,boolean zh){
        if(!errno.isEmpty())return (zh?"文件访问错误 ":"File access error ")+errno;
        if(code.contains("LENGTH_CONFLICT")||code.contains("LENGTH_CHANGED"))return zh?"文件长度信息矛盾或读取时发生变化":"File length conflicts or changed during the read";
        if(code.equals("RANDOM_ACCESS_UNSUPPORTED"))return zh?"提供方不支持随机读取":"Provider does not support random access";
        if(code.equals("SHORT_HEADER")||code.equals("UNEXPECTED_EOF"))return zh?"实际内容不足所需头部或区段":"Content ended before the required header or segment";
        if(code.equals("RANGE_OUT_OF_BOUNDS"))return zh?"区段超出实际文件范围":"Segment exceeds actual file bounds";
        if(code.equals("PERMISSION_DENIED"))return zh?"此文件访问权限不足":"Permission denied for this document";
        if(code.equals("DOCUMENT_NOT_FOUND")||code.equals("DOCUMENT_MISSING"))return zh?"原文件目前不可访问":"The original document is unavailable";
        if(code.equals("NO_READ_PROGRESS"))return zh?"提供方未返回读取内容":"Provider returned no read progress";
        if(code.equals("CANCELLED"))return zh?"读取已取消":"Read cancelled";
        if(code.startsWith("LAUNCH_REASON_"))return zh?"启动格式或文件身份检查拒绝":"Launch format or file identity check rejected";
        return zh?"读取失败；查看阶段与错误代码":"Read failed; inspect the stage and error code";
    }
    static JSONObject saved(Context c,String name){try{return new JSONObject(new String(new AtomicFile(new File(c.getFilesDir(),name)).readFully(),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
}
