package com.rgds.ultimate.shell;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Pure rules shared by the real downloader and bounded error fixtures. */
final class RemotePolicy {
    static String kind(int status){return status==404?"HTTP_404":status==429?"RATE_LIMIT":status==401||status==403?"AUTH_OR_FORBIDDEN":status>=500?"SERVER":"HTTP_"+status;}
    static long retryAt(int status,String after,long now){
        long retry=now+(status==404?7*MetadataManager.DAY:status==401||status==403?MetadataManager.DAY:300000);
        if(after!=null)try{long seconds=Long.parseLong(after);if(seconds>=0&&seconds<=315360000)retry=Math.max(retry,now+seconds*1000);}
        catch(Exception e){try{retry=Math.max(retry,new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US).parse(after).getTime());}catch(Exception ignored){}}
        return retry;
    }
    static boolean imageMagic(byte[] b){return b.length>=16&&(b[0]==(byte)0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10||b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff||b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P');}
}
