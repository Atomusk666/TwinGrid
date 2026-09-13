package com.rgds.ultimate.shell;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Bounded random-access NDS header/banner reader. No Android dependency; host-testable. */
public final class NdsHeaderParser {
    public static final class ReadFailure extends IOException {
        public final String stage,code;
        public final long expected,actual,offset,limit;
        ReadFailure(String stage,IOException cause){this(stage,"IO_FAILURE",-1,-1,-1,-1,cause);}
        ReadFailure(String stage,String code,long expected,long actual,long offset,long limit,IOException cause){super(code,cause);this.stage=stage;this.code=code;this.expected=expected;this.actual=actual;this.offset=offset;this.limit=limit;}
    }
    public static final class Metadata {
        public String internalTitle="",gameCode="",makerCode="",bannerTitle="",warning="";
        public String[] titles=new String[0];
        public byte[] icon=new byte[0];
        public int bytesRead;
        public long actualSize;
        public String warningCode="";
    }
    public static Metadata parse(FileChannel channel,long documentSize) throws IOException {
        return parse(channel,documentSize,()->{});
    }
    public static Metadata parse(FileChannel channel,long documentSize,Runnable check) throws IOException {
        check.run();
        long actualSize;
        try{actualSize=channel.size();}catch(IOException e){throw new ReadFailure("FILE_STAT",e);}
        if(documentSize>0&&actualSize!=documentSize)throw new ReadFailure("FILE_STAT","METADATA_LENGTH_CONFLICT",documentSize,actualSize,0,actualSize,null);
        long limit=actualSize;
        Metadata m=new Metadata();
        m.actualSize=actualSize;
        byte[] header;
        header=read(channel,0,512,limit,"HEADER_READ",check);
        m.bytesRead+=header.length;
        m.internalTitle=ascii(header,0,12);
        m.gameCode=ascii(header,12,4);
        m.makerCode=ascii(header,16,2);
        long banner=u32(header,0x68);
        if(banner==0) {m.warning="ROM 无 banner";m.warningCode="NO_BANNER";return m;}
        try {
            if(banner<0x70) throw new ReadFailure("BANNER_READ","BANNER_OVERLAPS_HEADER",0x70,banner,banner,limit,null);
            byte[] versionData=read(channel,banner,2,limit,"BANNER_READ",check); m.bytesRead+=2;
            int version=u16(versionData,0);
            int languages;
            if(version==1) languages=6;
            else if(version==2) languages=7;
            else if(version==3||version==0x103) languages=8;
            else {m.warning="未支持的 banner 版本 "+version;m.warningCode="UNSUPPORTED_BANNER_VERSION";return m;}
            byte[] data=read(channel,banner,0x240+languages*0x100,limit,"BANNER_READ",check);m.bytesRead+=data.length;
            m.icon=Arrays.copyOfRange(data,0x20,0x240);
            m.titles=new String[languages];
            for(int i=0;i<languages;i++) {
                String text=new String(data,0x240+i*0x100,0x100,StandardCharsets.UTF_16LE);
                int end=text.indexOf('\0');
                m.titles[i]=(end<0?text:text.substring(0,end)).trim();
            }
            m.bannerTitle=languages>6&&!m.titles[6].isEmpty()?m.titles[6]:
                    (!m.titles[1].isEmpty()?m.titles[1]:m.titles[0]);
        } catch(IOException e) {m.warningCode=e instanceof ReadFailure?((ReadFailure)e).code:"BANNER_IO_FAILURE";m.warning=m.warningCode;}
        check.run();
        return m;
    }
    private static byte[] read(FileChannel c,long offset,int count,long limit,String stage,Runnable check) throws IOException {
        check.run();
        if(offset<0||count<0||offset>limit||count>limit-offset) throw new ReadFailure(stage,"RANGE_OUT_OF_BOUNDS",count,Math.max(0,limit-offset),offset,limit,null);
        ByteBuffer buffer=ByteBuffer.allocate(count);
        int emptyReads=0;
        try{c.position(offset);
        while(buffer.hasRemaining()) {
            check.run();
            int n=c.read(buffer);
            if(n<0) throw new ReadFailure(stage,"UNEXPECTED_EOF",count,buffer.position(),offset,limit,null);
            if(n==0 && ++emptyReads>3) throw new ReadFailure(stage,"NO_READ_PROGRESS",count,buffer.position(),offset,limit,null);
            if(n>0)emptyReads=0;
        }
        }catch(ReadFailure e){throw e;}catch(IOException e){throw new ReadFailure(stage,"IO_FAILURE",count,buffer.position(),offset,limit,e);}
        return buffer.array();
    }
    private static String ascii(byte[] b,int at,int count) {
        String s=new String(b,at,count,StandardCharsets.US_ASCII);
        int zero=s.indexOf('\0');if(zero>=0)s=s.substring(0,zero);
        return s.replaceAll("[^\\x20-\\x7e]","").trim();
    }
    private static int u16(byte[]b,int at) {return (b[at]&255)|((b[at+1]&255)<<8);}
    private static long u32(byte[]b,int at) {return (u16(b,at)&0xffffL)|((long)u16(b,at+2)<<16);}
    public static int[] iconPixels(byte[] data) {
        if(data==null||data.length!=544)return null;
        int[] colors=new int[16],pixels=new int[1024];
        for(int i=1;i<16;i++) {
            int rgb=u16(data,512+i*2);
            int r=(rgb&31)*255/31,g=((rgb>>5)&31)*255/31,b=((rgb>>10)&31)*255/31;
            colors[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        for(int y=0;y<32;y++) for(int x=0;x<32;x++) {
            int tile=(y/8)*4+x/8,within=(y%8)*8+x%8;
            int packed=data[tile*32+within/2]&255;
            int index=(within%2==0?packed:packed>>4)&15;
            pixels[y*32+x]=colors[index];
        }
        return pixels;
    }
}
