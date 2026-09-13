package com.rgds.ultimate.shell;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.json.*;
import java.util.*;
import java.nio.ByteBuffer;

/** Optional read acceleration. Existing user preferences remain authoritative and unchanged. */
final class UserReadCache {
    static final class Records {
        final Set<String> favorites=new LinkedHashSet<>();
        final Map<String,ShellStateRepository.UsageRecord> usage=new LinkedHashMap<>();
    }
    private static AtomicFile recordsFile(File files){return new AtomicFile(new File(files,"user_records_fast_v5.bin"));}
    static Records loadRecords(File files){try{
        AtomicFile f=recordsFile(files);if(!f.getBaseFile().isFile()||f.getBaseFile().length()>4*1024*1024)return null;
        byte[] all=f.readFully();ByteBuffer in=ByteBuffer.wrap(all);
        if(in.getInt()!=0x55435635)return null;
        String sourceHash=string(in),bodyHash=string(in);byte[] body=new byte[in.remaining()];in.get(body);
        if(!sourceHash.equals(hash(source(files)))||!bodyHash.equals(MetadataManager.hashBytes(body)))return null;
        in=ByteBuffer.wrap(body);Records r=new Records();int n=count(in);for(int i=0;i<n;i++)r.favorites.add(string(in));
        n=count(in);for(int i=0;i<n;i++){
            String id=string(in),uri=string(in),title=string(in);long played=in.getLong();int plays=in.getInt();long selected=in.getLong(),launched=in.getLong();int launches=in.getInt();
            if(r.usage.containsKey(id))return null;r.usage.put(id,new ShellStateRepository.UsageRecord(id,uri,title,played,plays,selected,launched,launches));
        }if(in.hasRemaining())return null;return r;
    }catch(Exception e){return null;}}
    static void saveRecords(File files,Set<String> favorites,Collection<ShellStateRepository.UsageRecord> usage){
        AtomicFile f=recordsFile(files);FileOutputStream raw=null;try{
            ByteArrayOutputStream body=new ByteArrayOutputStream();DataOutputStream data=new DataOutputStream(body);data.writeInt(favorites.size());for(String id:favorites)string(data,id);
            data.writeInt(usage.size());for(ShellStateRepository.UsageRecord r:usage){string(data,r.gameId);string(data,r.uri);string(data,r.title);data.writeLong(r.lastPlayedAt);data.writeInt(r.playCount);data.writeLong(r.lastSelectedAt);data.writeLong(r.lastLaunchAt);data.writeInt(r.launchRequestCount);}data.flush();
            byte[] bytes=body.toByteArray();raw=f.startWrite();DataOutputStream out=new DataOutputStream(new BufferedOutputStream(raw));out.writeInt(0x55435635);string(out,hash(source(files)));string(out,MetadataManager.hashBytes(bytes));out.write(bytes);out.flush();f.finishWrite(raw);
        }catch(Exception e){if(raw!=null)f.failWrite(raw);}
    }
    private static int count(ByteBuffer b)throws IOException{int n=b.getInt();if(n<0||n>20000)throw new IOException("Count limit");return n;}
    private static String string(ByteBuffer b)throws IOException{int n=b.getInt();if(n<0||n>262144||n>b.remaining())throw new IOException("String limit");String s=new String(b.array(),b.position(),n,StandardCharsets.UTF_8);b.position(b.position()+n);return s;}
    private static void string(DataOutputStream b,String s)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);b.writeInt(bytes.length);b.write(bytes);}
    private static File source(File files){return new File(files.getParentFile(),"shared_prefs/rgds_shell_user_v3.xml");}
    private static AtomicFile cache(File files){return new AtomicFile(new File(files,"user_records_fast_v4.json"));}
    private static String hash(File f)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        char[] hex="0123456789abcdef".toCharArray();StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(hex[(b&255)>>>4]).append(hex[b&15]);return s.toString();
    }
    static JSONObject load(File files){try{
        AtomicFile file=cache(files);if(!file.getBaseFile().isFile()||file.getBaseFile().length()>4*1024*1024||!source(files).isFile())return null;
        JSONObject o=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        if(o.getInt("schema")!=1||!o.getString("sourceSHA256").equals(hash(source(files))))return null;
        o.getJSONArray("favorites");o.getJSONArray("usage");return o;
    }catch(Exception ignored){return null;}}
    static void save(File files,JSONArray favorites,JSONArray usage){
        // Best effort: a missing/corrupt/stale cache always falls back to original preferences.
        FileOutputStream out=null;AtomicFile file=cache(files);try{
            if(!source(files).isFile())return;
            byte[] data=new JSONObject().put("schema",1).put("sourceSHA256",hash(source(files))).put("favorites",favorites).put("usage",usage).toString().getBytes(StandardCharsets.UTF_8);
            out=file.startWrite();out.write(data);file.finishWrite(out);
        }catch(Exception ignored){if(out!=null)file.failWrite(out);}
    }
}
