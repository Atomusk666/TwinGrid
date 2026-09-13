package com.rgds.ultimate.shell;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
/** Derived metadata only. V1 JSON remains available as a one-time fallback; no user data here. */
final class RomCache {
    private static final int MAX_BYTES=16*1024*1024;
    private final AtomicFile file;
    RomCache(File directory){file=new AtomicFile(new File(directory,"rom_metadata_v3.bin"));}
    boolean exists(){return file.getBaseFile().exists();}
    List<GameEntry> read(Uri tree)throws Exception{
        byte[] data;
        // Check the opened file before allocation, then enforce the bound while reading
        // too: a concurrently growing/corrupt derived cache must not allocate without limit.
        try(FileInputStream raw=file.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream(32768)){
            if(raw.getChannel().size()>MAX_BYTES)throw new IOException("Cache size limit");
            byte[] buffer=new byte[32768];int empty=0,n;
            while((n=raw.read(buffer,0,Math.min(buffer.length,MAX_BYTES-bytes.size()+1)))!=-1){
                if(n==0){if(++empty>3)throw new IOException("Cache read made no progress");continue;}
                empty=0;if(n>MAX_BYTES-bytes.size())throw new IOException("Cache size limit");bytes.write(buffer,0,n);
            }
            data=bytes.toByteArray();
        }
        ByteBuffer in=ByteBuffer.wrap(data);
        {
            if(in.getInt()!=0x52474434||!string(in).equals(tree.toString()))throw new IOException("Cache tree/schema mismatch");
            int count=in.getInt();if(count<0||count>100000)throw new IOException("Invalid cache count");
            List<GameEntry> games=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                String id=string(in);Uri uri=Uri.parse(string(in));String name=string(in),title=string(in);
                String internal=string(in),banner=string(in),code=string(in),maker=string(in);
                long size=in.getLong(),mtime=in.getLong();GameEntry.SaveState state=GameEntry.SaveState.valueOf(string(in));
                long saveSize=in.getLong(),saveTime=in.getLong();String warning=string(in);
                int n=in.getInt();if(n<0||n>16384)throw new IOException("Invalid icon length");
                byte[] icon=new byte[n];in.get(icon);
                int languageCount=in.getInt();if(languageCount<0||languageCount>16)throw new IOException("Invalid language count");
                String[] titles=new String[languageCount];for(int j=0;j<titles.length;j++)titles[j]=string(in);
                games.add(new GameEntry(id,uri,name,title,internal,banner,code,maker,size,mtime,state,saveSize,saveTime,icon,titles,warning));
            }
            return games;
        }
    }
    void write(Uri tree,List<GameEntry> games)throws Exception{
        FileOutputStream raw=null;
        try{
            raw=file.startWrite();DataOutputStream out=new DataOutputStream(new BufferedOutputStream(raw));
            out.writeInt(0x52474434);string(out,tree.toString());out.writeInt(games.size());
            for(GameEntry g:games){
                string(out,g.gameId);string(out,g.uriString());string(out,g.fileName);string(out,g.displayTitle);
                string(out,g.internalTitle);string(out,g.bannerTitle);string(out,g.gameCode);string(out,g.makerCode);
                out.writeLong(g.romBytes);out.writeLong(g.romModifiedAt);string(out,g.saveState.name());
                out.writeLong(g.saveBytes);out.writeLong(g.saveModifiedAt);string(out,g.readWarning);
                out.writeInt(g.iconData.length);out.write(g.iconData);
                out.writeInt(g.bannerTitles.length);for(String title:g.bannerTitles)string(out,title);
            }
            out.flush();file.finishWrite(raw);
        }catch(Exception e){if(raw!=null)file.failWrite(raw);throw e;}
    }
    private static String string(ByteBuffer in)throws IOException{
        int length=in.getInt();if(length<0||length>262144||length>in.remaining())throw new IOException("Invalid string length");
        String value=new String(in.array(),in.position(),length,StandardCharsets.UTF_8);in.position(in.position()+length);return value;
    }
    private static void string(DataOutputStream out,String value)throws IOException{
        byte[] data=value.getBytes(StandardCharsets.UTF_8);out.writeInt(data.length);out.write(data);
    }
}
