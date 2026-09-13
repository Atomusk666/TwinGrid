package com.rgds.ultimate.shell;

import android.util.Log;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Strict HTTPS, bounded calls, and an app-local DNS fallback. Never changes Android networking. */
final class SourceHttp {
    private final Set<Call> calls=new CopyOnWriteArraySet<>();
    private final Object callLock=new Object();
    private volatile long cancelGeneration;
    // OkHttp's synchronous DNS callback must retain the parent request's cancellation token.
    private final ThreadLocal<Long> activeGeneration=new ThreadLocal<>();
    private final OkHttpClient system=client(Dns.SYSTEM,22);
    private final OkHttpClient encrypted=client(this::resolve,50);
    private volatile boolean useEncrypted;
    private List<InetAddress> addresses;
    private long expires;
    private static OkHttpClient client(Dns dns,int seconds){return new OkHttpClient.Builder().dns(dns)
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(seconds,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(true).build();}
    void cancel(){Call[] active;synchronized(callLock){cancelGeneration++;active=calls.toArray(new Call[0]);}for(Call call:active)call.cancel();}
    long cancellationToken(){return cancelGeneration;}
    boolean isCurrent(long token){return !cancelled(token);}
    private boolean cancelled(long token){return token!=cancelGeneration||Thread.currentThread().isInterrupted();}
    private void checkCurrent(long token)throws IOException{if(cancelled(token))throw new IOException("Canceled");}
    static final class Result {
        int status;String type,retryAfter,route;byte[] body;
    }
    Result probe(String url)throws Exception{return probe(url,cancelGeneration);}
    Result publicText(String url)throws Exception{HttpUrl parsed=HttpUrl.get(url);if(!parsed.isHttps()||!Arrays.asList("en.wikipedia.org","zh.wikipedia.org").contains(parsed.host()))throw new IOException("Unsupported text source");return execute(system,new Request.Builder().url(url).header("User-Agent","TwinGrid/0.9.0 (personal NDS catalog; explicit user search)").header("Accept","application/json").build(),"system_dns",cancelGeneration);}
    Result probe(String url,long token)throws Exception{
        HttpUrl parsed=HttpUrl.get(url);if(!parsed.isHttps()||!Arrays.asList("zh.wikipedia.org","www.wikidata.org","api.screenscraper.fr","raw.githubusercontent.com").contains(parsed.host()))throw new IOException("Unsupported probe source");
        return execute(system,new Request.Builder().url(url).header("User-Agent","TwinGrid/0.9.0 (Android source capability check)").header("Accept","application/json").build(),"system_dns",token);
    }
    Result request(String path,JSONObject post)throws Exception{
        long token=cancelGeneration;
        Request.Builder b=new Request.Builder().url(BangumiSource.API+path)
            .header("User-Agent","TwinGrid/0.9.0 (Android; private personal library)")
            .header("Accept","application/json");
        if(post!=null)b.post(RequestBody.create(post.toString(),MediaType.get("application/json; charset=utf-8")));
        Request request=b.build();
        checkCurrent(token);
        if(!useEncrypted){try{return execute(system,request,"system_dns",token);}catch(IOException e){
            synchronized(callLock){checkCurrent(token);if(e.getMessage()!=null&&e.getMessage().equals("Canceled"))throw e;useEncrypted=true;}
            Log.w("TwinGridChinese","NETWORK_ROUTE system_dns failed="+e.getClass().getSimpleName());
        }}
        checkCurrent(token);return execute(encrypted,request,"encrypted_dns",token);
    }
    private Result execute(OkHttpClient client,Request request,String route,long token)throws IOException{
        checkCurrent(token);Call call=client.newCall(request);
        // Registration and cancellation share a lock; cancellation cannot miss a newly registered old request.
        synchronized(callLock){checkCurrent(token);calls.add(call);}
        Long previous=activeGeneration.get();activeGeneration.set(token);
        try{checkCurrent(token);try(Response response=call.execute()){
            Result r=new Result();r.status=response.code();r.type=response.header("Content-Type","");
            r.retryAfter=response.header("Retry-After","");r.route=route;
            ResponseBody body=response.body();if(body==null)throw new IOException("Empty HTTP response");
            if(body.contentLength()>2*1024*1024)throw new IOException("Response limit");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;
            try(InputStream in=body.byteStream()){while(true){checkCurrent(token);n=in.read(buffer);if(n==-1)break;if(bytes.size()+n>2*1024*1024)throw new IOException("Response limit");bytes.write(buffer,0,n);}}
            r.body=bytes.toByteArray();Handshake hs=response.handshake();
            { if(PerfTrace.isEnabled()) Log.i("TwinGridChinese","TLS host="+request.url().host()+" route="+route+" protocol="+(hs==null?"none":hs.tlsVersion().javaName())+" verified="+(hs!=null)); }
            checkCurrent(token);return r;
        }}catch(IOException e){if(cancelled(token))throw new IOException("Canceled",e);throw e;}
        finally{if(previous==null)activeGeneration.remove();else activeGeneration.set(previous);synchronized(callLock){calls.remove(call);}}
    }
    private synchronized List<InetAddress> resolve(String hostname)throws UnknownHostException{
        Long parent=activeGeneration.get();long token=parent==null?cancelGeneration:parent;
        if(cancelled(token))throw new UnknownHostException("Canceled");
        if(!hostname.equals("api.bgm.tv")){List<InetAddress> result=Dns.SYSTEM.lookup(hostname);if(cancelled(token))throw new UnknownHostException("Canceled");return result;}
        if(addresses!=null&&System.currentTimeMillis()<expires)return addresses;
        // Public resolver bootstrap addresses from their official documentation. TLS still verifies the resolver hostname.
        String[][] providers={{"dns.alidns.com","223.5.5.5","223.6.6.6","/resolve"},{"cloudflare-dns.com","1.1.1.1","1.0.0.1","/dns-query"},{"dns.google","8.8.8.8","8.8.4.4","/resolve"}};
        for(String[] p:providers){if(cancelled(token))throw new UnknownHostException("Canceled");try{
            OkHttpClient resolver=client(name->{if(!name.equals(p[0]))throw new UnknownHostException(name);return Arrays.asList(InetAddress.getByName(p[1]),InetAddress.getByName(p[2]));},12);
            Request request=new Request.Builder().url("https://"+p[0]+p[3]+"?name=api.bgm.tv&type=A")
                .header("Accept","application/dns-json").build();
            Result r=execute(resolver,request,"resolver_bootstrap",token);if(r.status!=200)throw new IOException("DoH HTTP "+r.status);
            JSONObject data=new JSONObject(new String(r.body,java.nio.charset.StandardCharsets.UTF_8));
            if(data.optInt("Status",-1)!=0)throw new IOException("DNS response status");
            JSONArray answer=data.optJSONArray("Answer");List<InetAddress> found=new ArrayList<>();long ttl=300;
            if(answer!=null)for(int i=0;i<answer.length();i++){JSONObject a=answer.getJSONObject(i);String ip=a.optString("data");
                if(a.optInt("type")==1&&a.optString("name").replaceAll("\\.$","").equals(hostname)&&ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")){
                    InetAddress addr=InetAddress.getByName(ip);if(addr.isAnyLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()||addr.isSiteLocalAddress())continue;
                    found.add(addr);ttl=Math.min(ttl,a.optLong("TTL",60));
                }}
            if(found.isEmpty())throw new IOException("DNS has no public A answer");
            checkCurrent(token);addresses=Collections.unmodifiableList(found);expires=System.currentTimeMillis()+Math.max(1,ttl)*1000;
            { if(PerfTrace.isEnabled()) Log.i("TwinGridChinese","DNS_HTTPS resolver="+p[0]+" host="+hostname+" addresses="+found+" ttl="+ttl+" AD="+data.optBoolean("AD")); }return addresses;
        }catch(Exception e){if(cancelled(token)||"Canceled".equals(e.getMessage()))throw new UnknownHostException("Canceled");Log.w("TwinGridChinese","DNS_HTTPS resolver="+p[0]+" failed="+e.getClass().getSimpleName());}}
        throw new UnknownHostException("Encrypted DNS unavailable for api.bgm.tv");
    }
}
