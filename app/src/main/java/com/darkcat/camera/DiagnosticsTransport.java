package com.darkcat.camera;

import android.content.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.*;

/** HTTPS-only, opt-in transport. Authentication is supplied by the Bruce endpoint, never embedded in APK. */
public final class DiagnosticsTransport {
    public interface Callback { void done(String message); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private DiagnosticsTransport(){}
    public static void upload(Context context,Callback callback){ IO.execute(()->{ String result; try{
        String endpoint=BuildConfig.BRUCE_DIAGNOSTICS_URL; if(!isHttps(endpoint)) throw new IOException("Bruce diagnostics HTTPS endpoint is not configured");
        File zip=new File(context.getCacheDir(),"diagnostics.zip"); try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
            out.putNextEntry(new ZipEntry("diagnostics-metadata.txt")); out.write(Diagnostics.metadata(context).getBytes(StandardCharsets.UTF_8)); out.closeEntry();
            File log=new File(context.getFilesDir(),"cat-diagnostics.log"); if(log.exists()){out.putNextEntry(new ZipEntry("cat-diagnostics.log")); copy(log,out); out.closeEntry();}
        }
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/zip"); c.setFixedLengthStreamingMode(zip.length()); try(OutputStream out=c.getOutputStream()){copy(zip,out);} int code=c.getResponseCode(); if(code>=200&&code<300){String response=read(c.getInputStream()); String id=extract(response,"uploadId"); result="Uploaded; Bruce upload ID: "+(id.isEmpty()?response:id);} else result="Upload failed (HTTP "+code+")";
    }catch(Exception e){result="Upload unavailable: "+e.getMessage();} String message=result; new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(message)); }); }
    public static void checkLatest(Context context,Callback callback){ IO.execute(()->{String result; try{
        String endpoint=BuildConfig.BRUCE_LATEST_URL; if(!isHttps(endpoint))throw new IOException("Bruce update HTTPS endpoint is not configured"); HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000); if(c.getResponseCode()!=200) result="Update check failed (HTTP "+c.getResponseCode()+")"; else { String json=read(c.getInputStream()); long versionCode=number(json,"versionCode"); String apk=extract(json,"apk"); URL finalUrl=apk.isEmpty()?null:new URL(new URL(endpoint),apk); result="Latest versionCode="+versionCode+(versionCode>BuildConfig.VERSION_CODE&&finalUrl!=null&&isHttps(finalUrl.toString())?"\nAPK_URL="+finalUrl:"\nNo newer APK available"); }
    }catch(Exception e){result="Update check unavailable: "+e.getMessage();}String message=result;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(message));}); }
    private static String read(InputStream in)throws IOException{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))>=0)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8).replaceAll("[\\r\\n]+"," ").trim();}}
    private static String extract(String json,String key){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);return m.find()?m.group(1):"";}
    private static long number(String json,String key){java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*(\\d+)").matcher(json);return m.find()?Long.parseLong(m.group(1)):-1;}
    private static boolean isHttps(String value){try{return "https".equalsIgnoreCase(new URL(value).getProtocol());}catch(Exception e){return false;}}
    private static void copy(File f,OutputStream out)throws IOException{try(InputStream in=new FileInputStream(f)){copy(in,out);}}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
}
