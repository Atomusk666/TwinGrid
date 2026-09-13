package com.rgds.ultimate.shell;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Image-only transport. System resolver, normal TLS, no proxy override or host substitution. */
final class CoverTransport {
    static final String OFFICIAL="https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%20DS/";
    static final String RAW="https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_DS/";
    static final String JSDELIVR="https://cdn.jsdelivr.net/gh/libretro-thumbnails/Nintendo_-_Nintendo_DS@";
    static final String JSDMIRROR="https://cdn.jsdmirror.com/gh/libretro-thumbnails/Nintendo_-_Nintendo_DS@";
    static final int LIMIT=4*1024*1024;
    static final long ITEM_MS=24000,ROUTE_MS=10000;
    private final Set<Call> calls=new CopyOnWriteArraySet<>();
    private final Object callLock=new Object();private volatile long generation;
    private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"cover-deadline");t.setDaemon(true);return t;});
    private final Map<String,Long> backoff=new ConcurrentHashMap<>();
    private final Map<String,Long> circuit=new ConcurrentHashMap<>();
    private final Map<String,Integer> failures=new ConcurrentHashMap<>();
    private final Object hostLock=new Object();
    private final Map<String,Object> probes=new HashMap<>();
    private long networkEpoch;
    private static final class Permit {final long epoch;final Object probe;Permit(long epoch,Object probe){this.epoch=epoch;this.probe=probe;}}
    private final OkHttpClient client;
    private long nextRequest;
    CoverTransport(){
        Dispatcher dispatcher=new Dispatcher();dispatcher.setMaxRequests(2);dispatcher.setMaxRequestsPerHost(2);
        client=new OkHttpClient.Builder().dispatcher(dispatcher).dns(Dns.SYSTEM).cache(null)
            .connectTimeout(4,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    }
    /** Package-only dependency seam for synthetic host transport regression. */
    CoverTransport(OkHttpClient testClient){client=Objects.requireNonNull(testClient);}
    void cancel(){Call[] active;synchronized(callLock){generation++;active=calls.toArray(new Call[0]);}for(Call call:active)call.cancel();}
    void networkChanged(){synchronized(hostLock){networkEpoch++;circuit.clear();failures.clear();probes.clear();}} // Retry-After survives network changes.
    private Permit enterHost(String host,boolean isolated,long now)throws Failure{
        synchronized(hostLock){
            checkBackoff(host,now);
            Long until=circuit.get(host);Object probe=null;
            if(!isolated&&until!=null){
                if(now<until)throw new Failure("HOST_BACKOFF",until);
                // Keep the expired deadline until this single half-open attempt completes.
                // Peers cannot all probe the same failing route when its cooldown expires.
                if(probes.containsKey(host))throw new Failure("HOST_BACKOFF",now+1000);
                probe=new Object();probes.put(host,probe);
            }
            return new Permit(networkEpoch,probe);
        }
    }
    private void checkBackoff(String host,long now)throws Failure{
        synchronized(hostLock){Long until=backoff.get(host);if(until!=null){if(now<until)throw new Failure("HOST_BACKOFF",until);backoff.remove(host,until);}}
    }
    private void hostResult(String host,Permit permit,boolean success,boolean failure){
        if(permit==null)return;
        synchronized(hostLock){
            if(permit.epoch!=networkEpoch)return;
            if(success){failures.remove(host);circuit.remove(host);}
            else if(failure){int count=failures.merge(host,1,Integer::sum);if(permit.probe!=null||count>=2)circuit.put(host,System.currentTimeMillis()+60000);}
            if(permit.probe!=null)probes.remove(host,permit.probe);
            // A concurrent 429/503 may have arrived after this request started.
            // Success only resets connection health, never a live server Retry-After.
        }
    }
    static final class Failure extends IOException {
        final String kind;final long retryAt;
        Failure(String kind,long retryAt){super(kind);this.kind=kind;this.retryAt=retryAt;}
    }
    static final class Result {
        byte[] body;String url,route,sha256;int width,height;
    }
    static String pathUrl(String base,String path){return base+android.net.Uri.encode(path,"/");}
    static String digest(String algorithm,byte[] bytes)throws Exception{return hex(MessageDigest.getInstance(algorithm).digest(bytes));}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    static String blob(byte[] bytes)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-1");d.update(("blob "+bytes.length+"\0").getBytes(java.nio.charset.StandardCharsets.UTF_8));return hex(d.digest(bytes));}
    static void checkImage(Result r,String expectedBlob)throws Exception{
        if(r.body==null||r.body.length<16||!RemotePolicy.imageMagic(r.body))throw new Failure("INVALID_IMAGE_MAGIC",0);
        BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(r.body,0,r.body.length,b);
        if(b.outWidth<32||b.outHeight<32||b.outWidth>4096||b.outHeight>4096||(long)b.outWidth*b.outHeight>12000000)throw new Failure("INVALID_IMAGE_DIMENSIONS",0);
        BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=Math.max(1,Math.max(b.outWidth,b.outHeight)/512);
        Bitmap proof=BitmapFactory.decodeByteArray(r.body,0,r.body.length,decode);if(proof==null)throw new Failure("IMAGE_DECODE_FAILED",0);proof.recycle();
        if(!expectedBlob.isEmpty()&&!expectedBlob.equals(blob(r.body)))throw new Failure("INDEX_CONTENT_MISMATCH",0);
        r.width=b.outWidth;r.height=b.outHeight;r.sha256=digest("SHA-256",r.body);
    }
    private void check(BooleanSupplier allowed,long token)throws Failure{if(token!=generation||!allowed.getAsBoolean()||Thread.currentThread().isInterrupted())throw new Failure("CANCELLED",0);}
    Result fetch(String path,String revision,String expectedBlob,long deadline,BooleanSupplier allowed,Consumer<JSONObject> sink)throws Exception{
        if(!path.startsWith("Named_Boxarts/")||!path.endsWith(".png")||path.contains("..")||!revision.matches("[a-f0-9]{40}"))throw new Failure("INVALID_INDEX_RESOURCE",0);
        long token=generation;
        // Same content provider, two distribution routes; not independent artwork databases.
        String[] routes={"jsdmirror-pinned","jsdelivr-pinned"};String[] urls={pathUrl(JSDMIRROR+revision+"/",path),pathUrl(JSDELIVR+revision+"/",path)};
        boolean all404=true;Exception last=null;
        for(int i=0;i<urls.length;i++){
            check(allowed,token);if(SystemClock.elapsedRealtime()>=deadline)throw new Failure("ITEM_BUDGET_EXHAUSTED",0);
            try{return request(urls[i],routes[i],expectedBlob,deadline,allowed,sink,false,token);}
            catch(Failure e){if(e.kind.equals("CANCELLED"))throw e;if(!e.kind.equals("HTTP_404")){all404=false;last=e;}else if(last==null)last=e;}
            catch(Exception e){all404=false;last=e;}
        }
        if(all404)throw new Failure("ALL_ROUTES_404",System.currentTimeMillis()+7*86400000L);
        throw last==null?new Failure("NO_QUALIFIED_ROUTE",0):last;
    }
    // Preliminary route screening and final isolated real GETs use this exact production function.
    Result request(String url,String route,String expectedBlob,long deadline,BooleanSupplier allowed,Consumer<JSONObject> sink,boolean isolatedProbe)throws Exception{return request(url,route,expectedBlob,deadline,allowed,sink,isolatedProbe,generation);}
    private Result request(String url,String route,String expectedBlob,long deadline,BooleanSupplier allowed,Consumer<JSONObject> sink,boolean isolatedProbe,long token)throws Exception{
        check(allowed,token);HttpUrl initial=HttpUrl.get(url);validate(initial);
        long now=System.currentTimeMillis(),start=SystemClock.elapsedRealtime();final long routeDeadline=Math.min(deadline,start+ROUTE_MS);Trace trace=new Trace(url,route,start);Permit permit=null;
        try{
            permit=enterHost(initial.host(),isolatedProbe,now);
            long slot;synchronized(hostLock){slot=Math.max(SystemClock.elapsedRealtime(),nextRequest);nextRequest=slot+500;}
            while(SystemClock.elapsedRealtime()<slot){check(allowed,token);if(SystemClock.elapsedRealtime()>=routeDeadline)throw new Failure("ITEM_BUDGET_EXHAUSTED",0);Thread.sleep(Math.max(1,Math.min(50,slot-SystemClock.elapsedRealtime())));}
            HttpUrl current=initial;
            for(int redirect=0;redirect<=3;redirect++){
                check(allowed,token);validate(current);checkBackoff(current.host(),System.currentTimeMillis());long remaining=routeDeadline-SystemClock.elapsedRealtime();if(remaining<=0)throw new Failure("ITEM_BUDGET_EXHAUSTED",0);
                trace.add("url",current.toString());trace.add("finalHost",current.host());
                trace.startHop(current.toString());
                OkHttpClient measured=client.newBuilder().eventListener(trace).callTimeout(remaining,TimeUnit.MILLISECONDS).build();
                Request req=new Request.Builder().url(current).header("User-Agent","TwinGrid/0.9.0 (personal NDS artwork; bounded requests)").header("Accept","image/png,image/jpeg,image/webp").build();
                Call call=measured.newCall(req);synchronized(callLock){check(allowed,token);calls.add(call);}final ScheduledFuture<?> expiry=deadlines.schedule(call::cancel,remaining,TimeUnit.MILLISECONDS);final CountDownLatch done=new CountDownLatch(1);final Response[] response={null};final IOException[] error={null};final boolean[] abandoned={false};
                call.enqueue(new Callback(){public void onFailure(Call c,IOException e){synchronized(done){error[0]=e;done.countDown();}}public void onResponse(Call c,Response r){synchronized(done){if(abandoned[0])r.close();else response[0]=r;done.countDown();}}});
                try{
                    long callDeadline=SystemClock.elapsedRealtime()+remaining;
                    while(!done.await(50,TimeUnit.MILLISECONDS)){check(allowed,token);if(SystemClock.elapsedRealtime()>=callDeadline)throw new Failure("REQUEST_DEADLINE",0);}
                    check(allowed,token);if(error[0]!=null)throw error[0];Response res=response[0];if(res==null)throw new Failure("EMPTY_RESPONSE",0);
                    try(Response close=res){
                        int status=res.code();trace.add("httpStatus",status);trace.add("contentType",res.header("Content-Type",""));
                        if(status==301||status==302||status==303||status==307||status==308){String location=res.header("Location");HttpUrl next=location==null?null:current.resolve(location);if(next==null||redirect==3)throw new Failure("REDIRECT_REJECTED",0);validate(next);if(!next.equals(initial))throw new Failure("REDIRECT_RESOURCE_REJECTED",0);current=next;trace.add("redirects",redirect+1);continue;}
                        if(status!=200){long retry=RemotePolicy.retryAt(status,res.header("Retry-After"),System.currentTimeMillis());if(status==429||status==403||status>=500)synchronized(hostLock){backoff.merge(current.host(),retry,Math::max);}throw new Failure("HTTP_"+status,retry);}
                        ResponseBody body=res.body();if(body==null||body.contentLength()>LIMIT)throw new Failure("RESPONSE_SIZE",0);
                        String type=res.header("Content-Type","").split(";")[0].trim().toLowerCase(Locale.ROOT);if(!Arrays.asList("image/png","image/jpeg","image/webp","application/octet-stream").contains(type))throw new Failure("NON_IMAGE_CONTENT_TYPE",0);
                        ByteArrayOutputStream out=new ByteArrayOutputStream();InputStream in=body.byteStream();byte[] buffer=new byte[16384];int n;
                        while((n=in.read(buffer))!=-1){check(allowed,token);if(SystemClock.elapsedRealtime()>callDeadline)throw new Failure("REQUEST_DEADLINE",0);if(out.size()+n>LIMIT)throw new Failure("RESPONSE_SIZE",0);out.write(buffer,0,n);trace.add("bytes",out.size());}
                        Result result=new Result();result.body=out.toByteArray();result.url=current.toString();result.route=route;checkImage(result,expectedBlob);check(allowed,token);if(SystemClock.elapsedRealtime()>=deadline)throw new Failure("ITEM_BUDGET_EXHAUSTED",0);trace.add("sha256",result.sha256);trace.add("gitBlobSHA1",blob(result.body));trace.add("width",result.width);trace.add("height",result.height);trace.add("result","VERIFIED");hostResult(initial.host(),permit,true,false);permit=null;return result;
                    }
                }finally{expiry.cancel(false);synchronized(done){abandoned[0]=true;if(response[0]!=null)response[0].close();}call.cancel();calls.remove(call);}
            }
            throw new Failure("REDIRECT_LIMIT",0);
        }catch(Exception e){
            String kind=e instanceof Failure?((Failure)e).kind:e instanceof UnknownHostException?"DNS_FAILURE":e instanceof javax.net.ssl.SSLException?"TLS_FAILURE":e instanceof SocketTimeoutException||e instanceof InterruptedIOException?"TIMEOUT":e instanceof ConnectException?"CONNECT_FAILURE":e.getClass().getSimpleName();
            if(token!=generation||!allowed.getAsBoolean())kind="CANCELLED";
            else if(SystemClock.elapsedRealtime()>=routeDeadline&&!kind.equals("HOST_BACKOFF"))kind="REQUEST_DEADLINE";
            trace.add("result",kind);trace.add("errorClass",e.getClass().getSimpleName());trace.add("errorDetail",String.valueOf(e.getMessage()));
            boolean neutral=kind.equals("CANCELLED")||kind.equals("HOST_BACKOFF")||kind.equals("REQUEST_DEADLINE")||kind.equals("ITEM_BUDGET_EXHAUSTED");
            hostResult(initial.host(),permit,kind.equals("HTTP_404"),!neutral&&!kind.equals("HTTP_404"));permit=null;
            throw kind.equals("CANCELLED")||kind.equals("REQUEST_DEADLINE")?new Failure(kind,0):e instanceof Failure?e:new Failure(kind,System.currentTimeMillis()+60000);
        }finally{trace.add("elapsedMs",SystemClock.elapsedRealtime()-start);trace.add("uid",android.os.Process.myUid());trace.add("systemDNS",true);trace.add("isolatedProbe",isolatedProbe);sink.accept(trace.json());}
    }
    static void validate(HttpUrl url)throws Failure{
        String host=url.host(),path=url.encodedPath();boolean known=host.equals("thumbnails.libretro.com")&&path.startsWith("/Nintendo%20-%20Nintendo%20DS/Named_Boxarts/")||host.equals("raw.githubusercontent.com")&&path.matches("/libretro-thumbnails/Nintendo_-_Nintendo_DS/[a-f0-9]{40}/Named_Boxarts/[^/]+\\.png")||(host.equals("cdn.jsdelivr.net")||host.equals("cdn.jsdmirror.com"))&&path.matches("/gh/libretro-thumbnails/Nintendo_-_Nintendo_DS@[a-f0-9]{40}/Named_Boxarts/[^/]+\\.png");
        if(!url.isHttps()||!known||!url.username().isEmpty()||!url.password().isEmpty()||url.port()!=443||url.query()!=null||url.fragment()!=null||!path.endsWith(".png"))throw new Failure("SOURCE_POLICY_REJECTED",0);
    }
    private static final class Trace extends okhttp3.EventListener {
        final Map<String,Object> data=new LinkedHashMap<>();final List<Map<String,Object>> hops=new ArrayList<>();Map<String,Object> hop;final long start;long dns,connect,tls,headers,body;int httpRequests;
        Trace(String url,String route,long start){this.start=start;add("initialURL",url);add("route",route);add("provider","libretro-thumbnails");add("networkAttempted",false);add("callEnqueued",false);add("httpRequests",0);add("startedAtUTC",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.ROOT).format(new Date()));for(String key:new String[]{"dnsMs","connectMs","tlsMs","ttfbMs","transferMs"})add(key,JSONObject.NULL);}
        synchronized void startHop(String url){hop=new LinkedHashMap<>();hops.add(hop);add("url",url);add("callEnqueued",true);headers=body=dns=connect=tls=0;for(String key:new String[]{"dnsMs","connectMs","tlsMs","ttfbMs","transferMs"})add(key,JSONObject.NULL);}
        synchronized void add(String key,Object value){data.put(key,value);if(hop!=null)hop.put(key,value);}
        synchronized JSONObject json(){JSONObject result=new JSONObject(data);JSONArray list=new JSONArray();for(Map<String,Object> row:hops)list.put(new JSONObject(row));try{result.put("hops",list);}catch(JSONException ignored){}return result;}
        public void dnsStart(Call c,String host){dns=SystemClock.elapsedRealtime();add("networkAttempted",true);add("dnsHost",host);}
        public void dnsEnd(Call c,String host,List<InetAddress> ips){add("dnsMs",SystemClock.elapsedRealtime()-dns);List<String> addresses=new ArrayList<>();for(InetAddress ip:ips)addresses.add(ip.getHostAddress());add("resolvedAddresses",new JSONArray(addresses));}
        public void connectStart(Call c,InetSocketAddress address,Proxy proxy){connect=SystemClock.elapsedRealtime();add("networkAttempted",true);add("proxyType",proxy.type().toString());}
        public void secureConnectStart(Call c){tls=SystemClock.elapsedRealtime();}
        public void secureConnectEnd(Call c,Handshake h){add("tlsMs",SystemClock.elapsedRealtime()-tls);if(h!=null)add("tls",h.tlsVersion().javaName());}
        public void connectEnd(Call c,InetSocketAddress a,Proxy p,Protocol protocol){add("connectMs",SystemClock.elapsedRealtime()-connect);}
        public void requestHeadersEnd(Call c,Request r){headers=SystemClock.elapsedRealtime();add("networkAttempted",true);add("httpRequests",++httpRequests);}
        public void responseHeadersStart(Call c){if(headers>0)add("ttfbMs",SystemClock.elapsedRealtime()-headers);}
        public void responseBodyStart(Call c){body=SystemClock.elapsedRealtime();}
        public void responseBodyEnd(Call c,long bytes){add("transferMs",SystemClock.elapsedRealtime()-body);add("bytes",bytes);}
    }
}
