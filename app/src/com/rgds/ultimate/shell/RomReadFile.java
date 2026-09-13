package com.rgds.ultimate.shell;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.nio.channels.FileChannel;

/** Metadata may be unknown; acceptance is based on this open, seekable descriptor. */
final class RomReadFile {
    final long metadataSize,statSize,channelSize,size,modified;
    final boolean requeried;
    private RomReadFile(long metadata,long stat,long channel,long modified,boolean retried){
        metadataSize=metadata;statSize=stat;channelSize=channel;size=channel;this.modified=modified;requeried=retried;
    }
    static long[] query(Context c,Uri uri,CancellationSignal signal)throws IOException {
        signal.throwIfCanceled();
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null,signal)){
            if(cursor==null||!cursor.moveToFirst())throw failure("DOCUMENT_QUERY","DOCUMENT_MISSING",-1,-1);
            return new long[]{cursor.isNull(0)?-1:cursor.getLong(0),cursor.isNull(1)?-1:cursor.getLong(1)};
        }
    }
    static RomReadFile inspect(Context c,Uri uri,long metadata,long modified,ParcelFileDescriptor fd,FileChannel channel,CancellationSignal signal)throws IOException {
        signal.throwIfCanceled();long stat=fd.getStatSize(),actual;
        try{actual=channel.size();channel.position(0);}catch(IOException e){throw new NdsHeaderParser.ReadFailure("FILE_SEEK","RANDOM_ACCESS_UNSUPPORTED",-1,-1,-1,-1,e);}
        if(actual<0)throw failure("FILE_STAT","LENGTH_UNAVAILABLE",-1,actual);
        if(stat>=0&&stat!=actual)throw failure("FILE_STAT","DESCRIPTOR_LENGTH_CONFLICT",stat,actual);
        boolean retried=metadata>0&&metadata!=actual;
        if(retried){
            long[] fresh=query(c,uri,signal);metadata=fresh[0];modified=fresh[1];
            long after=channel.size();
            if(after!=actual)throw failure("FILE_STAT","LENGTH_CHANGED_DURING_READ",actual,after);
            if(metadata>0&&metadata!=actual)throw failure("DOCUMENT_SIZE","METADATA_LENGTH_CONFLICT",metadata,actual);
        }
        if(actual<512)throw failure("HEADER_READ","SHORT_HEADER",512,actual);
        signal.throwIfCanceled();return new RomReadFile(metadata,stat,actual,modified,retried);
    }
    static NdsHeaderParser.ReadFailure failure(String stage,String code,long expected,long actual){return new NdsHeaderParser.ReadFailure(stage,code,expected,actual,0,actual,null);}
    org.json.JSONObject json()throws Exception{return new org.json.JSONObject().put("metadataSize",metadataSize).put("metadataUnknown",metadataSize<=0).put("statSize",statSize).put("channelSize",channelSize).put("resolvedSize",size).put("modifiedAt",modified).put("metadataRequeried",requeried).put("seekable",true);}
}
