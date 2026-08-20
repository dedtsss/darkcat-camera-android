package com.darkcat.camera;

import android.content.Context;
import android.os.Build;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.pm.PackageManager;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small bounded, crash-safe local event log. It deliberately stores no credentials or image data. */
public final class Diagnostics {
    private Diagnostics() {}
    public static synchronized void record(Context context,String action,String result){
        File file=new File(context.getFilesDir(),"cat-diagnostics.log");
        try(FileWriter out=new FileWriter(file,true)){ out.write(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date())+" ACTION="+safe(action)+" RESULT="+safe(result)+"\n"); trim(file); }
        catch(IOException ignored) {}
    }
    private static String safe(String value){ return value==null?"":value.replace('\n',' ').replace('\r',' ').replace("token","[redacted]").replace("password","[redacted]"); }
    private static void trim(File file) throws IOException { if(file.length()<=256*1024)return; byte[] bytes; try(ByteArrayOutputStream buffer=new ByteArrayOutputStream(); FileInputStream in=new FileInputStream(file)){ byte[] chunk=new byte[8192]; int n; while((n=in.read(chunk))>=0)buffer.write(chunk,0,n); bytes=buffer.toByteArray(); } try(FileOutputStream out=new FileOutputStream(file)){ out.write(bytes,Math.max(0,bytes.length-128*1024),Math.min(bytes.length,128*1024)); } }
    public static String about(){ return "DarkCat Camera "+BuildConfig.VERSION_NAME+" ("+BuildConfig.VERSION_CODE+")\nGit SHA: "+BuildConfig.GIT_SHA+"\nBuild: "+BuildConfig.BUILD_TIMESTAMP+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" ("+Build.VERSION.SDK_INT+")"; }
    private static String runtimeStatus="not_started";
    public static synchronized void runtime(String status){ runtimeStatus=safe(status); }
    public static synchronized String metadata(Context context){
        StringBuilder out=new StringBuilder(4096); out.append("versionName=").append(BuildConfig.VERSION_NAME).append('\n').append("versionCode=").append(BuildConfig.VERSION_CODE).append('\n').append("gitSha=").append(safe(BuildConfig.GIT_SHA)).append('\n').append("buildTimestamp=").append(safe(BuildConfig.BUILD_TIMESTAMP)).append('\n').append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n').append("android=").append(Build.VERSION.RELEASE).append(" sdk=").append(Build.VERSION.SDK_INT).append('\n');
        out.append("cameraPermission=").append(context.checkSelfPermission("android.permission.CAMERA")==PackageManager.PERMISSION_GRANTED).append('\n').append("locationPermission=").append(context.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")==PackageManager.PERMISSION_GRANTED).append('\n').append("diagnosticsEndpointConfigured=").append(!BuildConfig.BRUCE_DIAGNOSTICS_URL.isEmpty()).append('\n').append("latestEndpointConfigured=").append(!BuildConfig.BRUCE_LATEST_URL.isEmpty()).append('\n').append("runtimeStatus=").append(runtimeStatus).append('\n').append("processExitDiagnostics=\n");
        if(Build.VERSION.SDK_INT>=30){ ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE); if(manager!=null) for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(context.getPackageName(),0,5)) out.append("reason=").append(info.getReason()).append(" status=").append(info.getStatus()).append(" importance=").append(info.getImportance()).append('\n'); }
        return out.substring(0,Math.min(out.length(),16*1024));
    }
}
