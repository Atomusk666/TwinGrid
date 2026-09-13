package com.rgds.ultimate.shell;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import org.json.JSONObject;
import java.io.IOException;

/** Bounded identity check, not a claim that the emulator can execute a valid header. */
final class LaunchFile {
    final long size,modified;final String headerHash;
    LaunchFile(long s,long m,String h){size=s;modified=m;headerHash=h;}
    static LaunchFile read(Context c,GameEntry g,CancellationSignal cancel)throws Exception {
        if(!g.fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".nds"))throw new LaunchFailure(LaunchFailure.FILE_CHANGED);
        long[] queried=RomReadFile.query(c,g.uri,cancel);long size,modified;
        cancel.throwIfCanceled();
        ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(g.uri,"r",cancel);
        if(fd==null)throw new IOException("No file descriptor");byte[] header=new byte[512];
        try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            RomReadFile identity=RomReadFile.inspect(c,g.uri,queried[0],queried[1],fd,in.getChannel(),cancel);
            size=identity.size;modified=identity.modified;
            if(g.romBytes>0&&size!=g.romBytes||(g.romModifiedAt>0&&modified>0&&modified!=g.romModifiedAt))throw new LaunchFailure(LaunchFailure.FILE_CHANGED);
            int n=0,empty=0;while(n<header.length){cancel.throwIfCanceled();int got=in.read(header,n,header.length-n);if(got<0)throw RomReadFile.failure("LAUNCH_HEADER_READ","UNEXPECTED_EOF",header.length,n);if(got==0&&++empty>3)throw RomReadFile.failure("LAUNCH_HEADER_READ","NO_READ_PROGRESS",header.length,n);if(got>0){n+=got;empty=0;}}
            if(in.getChannel().size()!=size)throw new LaunchFailure(LaunchFailure.FILE_CHANGED);
        }
        cancel.throwIfCanceled();validate(header,size);return new LaunchFile(size,modified,LaunchDiagnostic.hash(header));
    }
    static long u32(byte[] h,int o){return (h[o]&255L)|((h[o+1]&255L)<<8)|((h[o+2]&255L)<<16)|((h[o+3]&255L)<<24);}
    static void validate(byte[] h,long size)throws IOException {
        if(h.length<512||size<512||(h[18]!=0&&h[18]!=2))throw new LaunchFailure(LaunchFailure.FILE_CHANGED);
        for(int o:new int[]{0x20,0x30}){long start=u32(h,o),length=u32(h,o+12);if(start<512||length<4||start>size||length>size-start)throw new LaunchFailure(LaunchFailure.FILE_CHANGED);}
    }
    boolean same(LaunchFile x){return size==x.size&&modified==x.modified&&headerHash.equals(x.headerHash);}
    JSONObject json()throws Exception{return new JSONObject().put("bytes",size).put("modifiedAt",modified).put("first512SHA256",headerHash).put("check","HEADER_BOUNDS_AND_SCAN_IDENTITY_NOT_EXECUTION_PROOF");}
}
