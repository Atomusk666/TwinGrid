package com.rgds.ultimate.shell;
import android.graphics.*;
import android.os.*;
import android.os.Process;
import android.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One native decoder, at most four pending resource keys, cancellable visible subscribers. */
final class CoverLoader {
    private final MetadataManager metadata;
    private final Object lock=new Object();
    private final IdentityHashMap<Object,Subscription> subscribers=new IdentityHashMap<>();
    private final LinkedHashMap<String,Task> pending=new LinkedHashMap<>();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->new Thread(()->{Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);r.run();},"cover-decode"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(5*1024*1024){protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    private boolean pumping,suspended;private Task running;private long generation,skipped,hits,misses,decoded;private int highWater,writes;
    private ScheduledFuture<?> diskWrite;
    private static final class Subscription {Object owner;String resource;long generation,at;Consumer<Bitmap> callback;}
    private static final class Task {String key,resource;int size;long queuedAt;File thumb;Task(String k,String r,int s){key=k;resource=r;size=s;queuedAt=SystemClock.uptimeMillis();}}
    CoverLoader(MetadataManager m){metadata=m;}
    private String resource(String key,int target){return metadata.coverVersion(key)+"/thumb-v1/"+target;}
    Bitmap cached(String key,int target){return memory.get(resource(key,target));}
    void request(Object owner,String key,int target,Consumer<Bitmap> callback){
        String resource=resource(key,target);Bitmap cached=memory.get(resource);release(owner);
        if(cached!=null){hits++;PerfTrace.event("COVER_HIT key="+key+" bytes="+cached.getAllocationByteCount()+" hits="+hits);callback.accept(cached);return;}
        misses++;Subscription s=new Subscription();s.owner=owner;s.resource=resource;s.generation=++generation;s.at=SystemClock.uptimeMillis();s.callback=callback;
        synchronized(lock){
            if(suspended)return;subscribers.put(owner,s);pruneLocked();
            if(running==null||!running.resource.equals(resource)){pending.remove(resource);pending.put(resource,new Task(key,resource,target));}
            while(pending.size()>4){String first=pending.keySet().iterator().next();pending.remove(first);removeResourceLocked(first);skipped++;}
            highWater=Math.max(highWater,pending.size());
            PerfTrace.event("COVER_QUEUE generation="+s.generation+" key="+key+" uptime="+s.at+" pending="+pending.size()+" highWater="+highWater+" skipped="+skipped);
            if(!pumping){pumping=true;worker.execute(this::pump);}
        }
    }
    void release(Object owner){synchronized(lock){generation++;subscribers.remove(owner);pruneLocked();}}
    boolean subscribed(Object owner){synchronized(lock){return subscribers.containsKey(owner);}}
    void suspend(boolean value){synchronized(lock){suspended=value;if(value){subscribers.clear();pruneLocked();}}}
    void evictAll(){memory.evictAll();}
    private boolean neededLocked(String resource){for(Subscription s:subscribers.values())if(s.resource.equals(resource))return true;return false;}
    private void removeResourceLocked(String resource){subscribers.values().removeIf(s->s.resource.equals(resource));}
    private void pruneLocked(){Iterator<Task> it=pending.values().iterator();while(it.hasNext())if(!neededLocked(it.next().resource)){it.remove();skipped++;}}
    private void pump(){
        while(true){Task t;
            synchronized(lock){pruneLocked();if(pending.isEmpty()||suspended){pumping=false;running=null;return;}String last=null;for(String key:pending.keySet())last=key;t=pending.remove(last);running=t;if(!neededLocked(t.resource)){skipped++;continue;}}
            long begin=SystemClock.uptimeMillis();Bitmap image=null;boolean needed;
            synchronized(lock){needed=neededLocked(t.resource)&&!suspended;}
            if(needed&&metadata.decodingAllowed())try{image=memory.get(t.resource);if(image==null)image=read(t);if(image!=null){memory.put(t.resource,image);decoded++;}}catch(Exception error){PerfTrace.event("COVER_FAILED key="+t.key+" reason="+error.getClass().getSimpleName());}
            else skipped++;
            long end=SystemClock.uptimeMillis();PerfTrace.event("COVER_DECODE key="+t.key+" queueMs="+(begin-t.queuedAt)+" decodeMs="+(end-begin)+" uptime="+end+" decoded="+decoded+" skipped="+skipped+" memoryBytes="+memory.size());
            final Bitmap result=image;main.post(()->{
                List<Subscription> deliver=new ArrayList<>();synchronized(lock){Iterator<Subscription> it=subscribers.values().iterator();while(it.hasNext()){Subscription s=it.next();if(s.resource.equals(t.resource)){deliver.add(s);it.remove();}}}
                for(Subscription s:deliver)s.callback.accept(result);
            });
            if(image!=null&&t.thumb!=null)scheduleThumbnail(t,image);
            synchronized(lock){running=null;}
        }
    }
    private Bitmap read(Task t)throws IOException {
        File original=metadata.coverFile(t.key);if(original==null)return null;
        File dir=new File(original.getParentFile().getParentFile(),"thumbs");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Thumbnail directory");
        File thumb=new File(dir,MetadataManager.hash(t.resource)+".png");
        if(thumb.isFile()){
            BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeFile(thumb.toString(),size);
            if(size.outWidth>0&&size.outHeight>0&&size.outWidth<=t.size&&size.outHeight<=t.size&&thumb.length()<2*1024*1024){BitmapFactory.Options config=new BitmapFactory.Options();config.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap b=BitmapFactory.decodeFile(thumb.toString(),config);if(b!=null)return b;}
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(original.toString(),bounds);
        if(bounds.outWidth<1||bounds.outHeight<1)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;options.inSampleSize=1;
        while(Math.max(bounds.outWidth,bounds.outHeight)/(options.inSampleSize*2)>=t.size)options.inSampleSize*=2;
        synchronized(lock){if(!neededLocked(t.resource)||suspended){skipped++;return null;}}
        Bitmap b=BitmapFactory.decodeFile(original.toString(),options);if(b==null)return null;
        float scale=Math.min(1f,(float)t.size/Math.max(b.getWidth(),b.getHeight()));
        if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);if(resized!=b)b.recycle();b=resized;}
        // Publish pixels first. One replaceable, delayed disk write never delays this image's callback.
        t.thumb=thumb;
        return b;
    }
    private void scheduleThumbnail(Task t,Bitmap bitmap){
        final long expected;synchronized(lock){expected=generation;if(diskWrite!=null)diskWrite.cancel(false);}
        diskWrite=worker.schedule(()->{
            synchronized(lock){if(suspended||expected!=generation||!pending.isEmpty())return;}
            if(!metadata.decodingAllowed())return;
            AtomicFile a=new AtomicFile(t.thumb);FileOutputStream out=null;
            try{out=a.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Thumbnail encode");a.finishWrite(out);}
            catch(IOException e){if(out!=null)a.failWrite(out);}
            if(++writes%32==0)trim(t.thumb.getParentFile());
        },75,TimeUnit.MILLISECONDS);
    }
    private void trim(File dir){File[] files=dir.listFiles((d,n)->n.matches("[a-f0-9]{64}\\.png"));if(files==null)return;long bytes=0;for(File f:files)bytes+=f.length();if(bytes<=64L*1024*1024)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(File f:files){if(bytes<=64L*1024*1024)break;long n=f.length();if(f.delete())bytes-=n;}}
}
