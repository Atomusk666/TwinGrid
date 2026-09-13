package com.rgds.ultimate.shell;

import java.io.*;
import java.security.MessageDigest;

/** Pure file validation shared with host fault fixtures. Never changes the input. */
final class CatalogIntegrity {
    static final long MAX_BYTES=128L*1024*1024;
    static String verify(File file,String expectedHash,long expectedBytes)throws IOException {
        if(expectedHash==null||!expectedHash.matches("[a-f0-9]{64}"))throw new IOException("Invalid catalog hash");
        long size=file.length();if(!file.isFile()||size<4096||size>MAX_BYTES||expectedBytes>=0&&size!=expectedBytes)throw new IOException("Catalog size mismatch");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];long total=0;int count;
            while((count=in.read(buffer))!=-1){total+=count;if(total>size)throw new IOException("Catalog changed while reading");digest.update(buffer,0,count);}
            if(total!=size||file.length()!=size)throw new IOException("Catalog changed while reading");
            byte[] bytes=digest.digest();char[] hex="0123456789abcdef".toCharArray(),out=new char[bytes.length*2];for(int n=0;n<bytes.length;n++){out[n*2]=hex[(bytes[n]&255)>>>4];out[n*2+1]=hex[bytes[n]&15];}
            String actual=new String(out);if(!actual.equals(expectedHash))throw new IOException("Catalog integrity mismatch");return actual;
        }catch(java.security.NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}
    }
}
