package com.rgds.ultimate.shell;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.Executors;

/** Independent same-process, same-UID read-only comparison worker. Never dispatches a game. */
final class RomReadAudit {
    private static RomReadAudit instance;
    private final Context context;
    private final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"rom-read-audit"));
    private JSONObject data=new JSONObject();
    private CancellationSignal cancellation=new CancellationSignal();
    private boolean running;
    private RomReadAudit(Context c){context=c.getApplicationContext();}
    static synchronized RomReadAudit get(Context c){if(instance==null)instance=new RomReadAudit(c);return instance;}
    synchronized JSONObject snapshot(boolean rows){try{JSONObject result=new JSONObject(data.toString());if(!rows)result.remove("files");return result;}catch(Exception e){return new JSONObject();}}
    synchronized void cancel(){cancellation.cancel();}
    synchronized void start(){
        if(running)return;
        Uri tree=ShellStateRepository.get(context).snapshot().romTreeUri;
        if(tree==null)return;
        running=true;cancellation=new CancellationSignal();CancellationSignal signal=cancellation;
        int generation=RomLibrary.get(context).generation();
        data=new JSONObject();try{android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);data.put("schema",1).put("state","QUEUED").put("started",System.currentTimeMillis()).put("pid",android.os.Process.myPid()).put("uid",android.os.Process.myUid()).put("versionName",info.versionName).put("versionCode",info.versionCode).put("scanGeneration",generation).put("treeHash",ScanReadEvidence.hash(tree)).put("cacheHits",0).put("worker","rom-read-audit").put("execution","READ_ONLY_NO_LAUNCH").put("perFileMaximumRequestedBytes",4162).put("maximumFiles",10000).put("cooperativeTimeoutMs",180000).put("timeoutSemantics","CancellationSignal and checks between bounded reads; provider I/O may return later; no replacement worker").put("files",new JSONArray());}catch(Exception ignored){}
        // A signal belongs only to this audit, and timeout never starts a replacement worker.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{synchronized(this){if(running&&cancellation==signal)signal.cancel();}},180000);
        worker.execute(()->run(tree,generation,signal));
    }
    private void check(Uri tree,int generation,CancellationSignal signal){signal.throwIfCanceled();if(!RomLibrary.get(context).generationIs(generation)||!tree.equals(ShellStateRepository.get(context).snapshot().romTreeUri))throw new OperationCanceledException();}
    private synchronized void update(String state,int folders,int found,int opened,int parsed,int accepted,int rejected,int failures,int warnings){try{data.put("state",state).put("folders",folders).put("discovered",found).put("opened",opened).put("parsed",parsed).put("preflightAccepted",accepted).put("preflightRejected",rejected).put("readFailures",failures).put("bannerWarnings",warnings).put("updated",System.currentTimeMillis());}catch(Exception ignored){}}
    private synchronized void record(JSONObject row){try{data.getJSONArray("files").put(row);}catch(Exception ignored){}}
    private void run(Uri tree,int generation,CancellationSignal signal){
        int folders=0,found=0,opened=0,parsed=0,accepted=0,rejected=0,failures=0,warnings=0;String terminal="COMPLETE";
        RomLibrary library=RomLibrary.get(context);long deadline=android.os.SystemClock.uptimeMillis()+180000;
        try{
            library.requireReadGrant(tree);ArrayDeque<String> queue=new ArrayDeque<>();HashSet<String> seen=new HashSet<>();queue.add(DocumentsContract.getTreeDocumentId(tree));
            while(!queue.isEmpty()){
                check(tree,generation,signal);String id=queue.removeFirst();if(!seen.add(id))continue;if(seen.size()>20000)throw RomReadFile.failure("CHILDREN_QUERY","DIRECTORY_LIMIT",20000,seen.size());
                java.util.List<RomLibrary.Document> children;
                try{children=library.children(tree,id,signal);folders++;}catch(OperationCanceledException e){throw e;}catch(Exception e){failures++;record(ScanReadEvidence.reason("CHILDREN_QUERY",e,signal.isCanceled()).put("documentHash",ScanReadEvidence.hash(DocumentsContract.buildDocumentUriUsingTree(tree,id))));continue;}
                for(RomLibrary.Document document:children){
                    check(tree,generation,signal);if(android.os.SystemClock.uptimeMillis()>deadline){terminal="TIMEOUT";signal.cancel();signal.throwIfCanceled();}
                    if(document.directory){queue.add(document.id);continue;}if(!document.name.toLowerCase(java.util.Locale.ROOT).endsWith(".nds"))continue;
                    if(found>=10000)throw RomReadFile.failure("ENUMERATION","FILE_LIMIT",10000,found+1);found++;
                    JSONObject row=new JSONObject().put("documentHash",ScanReadEvidence.hash(document.uri)).put("parentHash",ScanReadEvidence.hash(DocumentsContract.buildDocumentUriUsingTree(tree,id))).put("depth",document.id.split("/").length).put("enumeratedSize",document.size).put("enumeratedModified",document.modified).put("metadataUnknown",document.size<=0).put("uriConstruction","buildDocumentUriUsingTree").put("openedUriEqualsEnumerated",true).put("scanGeneration",generation).put("threadInterruptedBefore",Thread.currentThread().isInterrupted());
                    String stage="DOCUMENT_QUERY";
                    try{
                        long[] single=RomReadFile.query(context,document.uri,signal);row.put("queriedSize",single[0]).put("queriedModified",single[1]);
                        stage="FILE_OPEN";ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(document.uri,"r",signal);if(fd==null)throw new java.io.IOException("No file descriptor");opened++;row.put("opened",true);
                        NdsHeaderParser.Metadata metadata;RomReadFile identity;String headerHash;
                        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
                            stage="FILE_STAT";identity=RomReadFile.inspect(context,document.uri,single[0],single[1],fd,in.getChannel(),signal);row.put("length",identity.json()).put("enumerationMatchesQuery",(document.size<=0||single[0]<=0||document.size==single[0])&&(document.modified<=0||single[1]<=0||document.modified==single[1]));
                            stage="HEADER_PARSE";metadata=NdsHeaderParser.parse(in.getChannel(),identity.size,()->check(tree,generation,signal));
                            byte[] header=new byte[512];java.nio.ByteBuffer bytes=java.nio.ByteBuffer.wrap(header);in.getChannel().position(0);int empty=0;
                            while(bytes.hasRemaining()){check(tree,generation,signal);int n=in.getChannel().read(bytes);if(n<0)throw RomReadFile.failure("AUDIT_HEADER_READ","UNEXPECTED_EOF",512,bytes.position());if(n==0&&++empty>3)throw RomReadFile.failure("AUDIT_HEADER_READ","NO_READ_PROGRESS",512,bytes.position());if(n>0)empty=0;}
                            headerHash=LaunchDiagnostic.hash(header);
                        }
                        row.put("resourcesClosed",true).put("parsed",true).put("headerBytesRead",512).put("parserBytesRead",metadata.bytesRead).put("parserFirst512SHA256",headerHash).put("bannerWarning",metadata.warningCode);parsed++;if(!metadata.warningCode.isEmpty())warnings++;
                        GameEntry game=RomLibrary.entry(document,metadata,identity);stage="LAUNCH_PREFLIGHT";
                        try{LaunchFile file=LaunchFile.read(context,game,signal);boolean stable=headerHash.equals(file.headerHash);row.put("preflightIdentity",file.json()).put("headerStableAcrossReopen",stable);if(!stable)throw new LaunchFailure(LaunchFailure.FILE_CHANGED);row.put("preflight","ACCEPTED");accepted++;}
                        catch(OperationCanceledException e){throw e;}catch(Exception e){row.put("preflight","REJECTED").put("preflightFailure",ScanReadEvidence.reason(stage,e,signal.isCanceled()));rejected++;}
                    }catch(OperationCanceledException e){row.put("cancelled",true);record(row);throw e;}
                    catch(Exception e){row.put("readFailure",ScanReadEvidence.reason(stage,e,signal.isCanceled()));failures++;}
                    row.put("signalCancelledAfter",signal.isCanceled()).put("threadInterruptedAfter",Thread.currentThread().isInterrupted());record(row);update("READING",folders,found,opened,parsed,accepted,rejected,failures,warnings);
                }
            }
            if(failures>0)terminal="PARTIAL";
        }catch(OperationCanceledException e){if(!terminal.equals("TIMEOUT"))terminal=android.os.SystemClock.uptimeMillis()>=deadline?"TIMEOUT":"CANCELLED";}
        catch(Exception e){terminal="FAILED";failures++;record(ScanReadEvidence.reason("AUDIT",e,signal.isCanceled()));}
        finally{synchronized(this){running=false;update(terminal,folders,found,opened,parsed,accepted,rejected,failures,warnings);try{data.put("ended",System.currentTimeMillis()).put("signalCancelled",signal.isCanceled()).put("sameScanGenerationAtEnd",library.generationIs(generation));}catch(Exception ignored){}ScanReadEvidence.persist(context,"rom_read_audit_v124.json",data.toString().getBytes(StandardCharsets.UTF_8));}}
    }
}
