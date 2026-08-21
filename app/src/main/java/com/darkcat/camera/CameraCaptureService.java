package com.darkcat.camera;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.location.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.provider.Settings;
import android.view.KeyEvent;
import androidx.camera.core.*;
import androidx.lifecycle.LifecycleService;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** The sole owner of CameraX, location, capture controls, and background lifetime. */
public final class CameraCaptureService extends LifecycleService {
    public static final String ACTION_START="com.darkcat.camera.START", ACTION_CAPTURE="com.darkcat.camera.CAPTURE";
    public static final String ACTION_STOP="com.darkcat.camera.STOP";
    private static final String CHANNEL="camera";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Binder binder=new Binder();
    private ImageCapture imageCapture; private Preview preview; private Location lastLocation;
    private LocationManager locationManager; private LocationListener locationListener; private MediaSession mediaSession; private BroadcastReceiver lifecycleReceiver; private boolean working=true;
    public final class Binder extends android.os.Binder { public CameraCaptureService service(){return CameraCaptureService.this;} }
    @Override public void onCreate() { super.onCreate(); Diagnostics.record(this,"service","START"); lifecycleReceiver=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ Diagnostics.record(CameraCaptureService.this,"screen_lifecycle",i.getAction()); }}; IntentFilter filter=new IntentFilter(); filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT); registerReceiver(lifecycleReceiver,filter); createChannel(); startLocation(); startMediaControls(); }
    @Override public IBinder onBind(Intent intent) { super.onBind(intent); return binder; }
    public void attachPreview(PreviewView view) {
        if(preview==null) preview=new Preview.Builder().build();
        preview.setSurfaceProvider(view.getSurfaceProvider()); bindGraph();
    }
    public Location getLastLocation(){ return lastLocation; }
    public boolean isWorking(){ return working; }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        super.onStartCommand(intent, flags, id);
        if(intent!=null && ACTION_STOP.equals(intent.getAction())) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY; }
        Notification n=notification();
        if(Build.VERSION.SDK_INT>=29) startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA|ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(7,n);
        if(intent!=null && ACTION_CAPTURE.equals(intent.getAction()) && working) capture("command"); else bindGraph();
        return START_STICKY;
    }
    private void createChannel(){ if(Build.VERSION.SDK_INT>=26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"Camera",NotificationManager.IMPORTANCE_LOW)); }
    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("DarkCat Camera").setContentText("Working · "+(lastLocation==null?"GPS searching": "GPS fixed")).setContentIntent(pi).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
    }
    private void bindGraph(){
        if(imageCapture!=null) return;
        Diagnostics.runtime("service_started; camera=initializing; media_session=active");
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{ try { ProcessCameraProvider provider=future.get();
            imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
            if(preview==null) preview=new Preview.Builder().build();
            provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,imageCapture); Diagnostics.runtime("service_started; camera=bound; media_session=active");
        } catch(Exception e){ Diagnostics.runtime("service_started; camera=bind_failed"); Diagnostics.record(this,"camera_bind",e.toString()); } },ContextCompat.getMainExecutor(this));
    }
    private void capture(String trigger){
        bindGraph(); main.postDelayed(()->{ if(imageCapture==null) { Diagnostics.record(this,"capture", "camera_not_ready"); return; }
            File dir=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"captures"); if(!dir.exists()&&!dir.mkdirs()) return;
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date());
            File file=new File(dir,stamp+"_"+nextIndex(dir)+".jpg");
            ImageCapture.OutputFileOptions out=new ImageCapture.OutputFileOptions.Builder(file).build();
            imageCapture.takePicture(out,io,new ImageCapture.OnImageSavedCallback(){
                public void onImageSaved(ImageCapture.OutputFileResults r){ try { writeExif(file); createStampedDerivative(file); Diagnostics.record(CameraCaptureService.this,"capture","PASS "+trigger+" "+file.getName()); }
                    catch(Exception e){ Diagnostics.record(CameraCaptureService.this,"capture_metadata",e.toString()); } }
                public void onError(ImageCaptureException e){ Diagnostics.record(CameraCaptureService.this,"capture","FAIL "+e.getImageCaptureError()+" "+e.getMessage()); }
            });
        },150);
    }
    private int nextIndex(File dir){ String[] names=dir.list(); int max=0; if(names!=null) for(String n:names){ int p=n.lastIndexOf('_'); int q=n.lastIndexOf('.'); try { max=Math.max(max,Integer.parseInt(n.substring(p+1,q))); }catch(Exception ignored){} } return max+1; }
    private void writeExif(File file) throws IOException {
        ExifInterface exif=new ExifInterface(file.getAbsolutePath()); Date now=new Date();
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",Locale.US).format(now));
        exif.setAttribute(ExifInterface.TAG_SOFTWARE,"DarkCat Camera "+BuildConfig.VERSION_NAME+" "+BuildConfig.GIT_SHA);
        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION,"DarkCat Camera MVP-1");
        if(lastLocation!=null){ exif.setLatLong(lastLocation.getLatitude(),lastLocation.getLongitude()); exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP,new SimpleDateFormat("yyyy:MM:dd",Locale.US).format(now)); }
        exif.saveAttributes();
    }
    /** Separate visual derivative; original JPEG remains untouched except EXIF, with quality documented in the memo. */
    private void createStampedDerivative(File original) throws IOException {
        Bitmap source=BitmapFactory.decodeFile(original.getAbsolutePath()); if(source==null)return;
        Bitmap stamped=source.copy(Bitmap.Config.ARGB_8888,true); Canvas canvas=new Canvas(stamped); float size=Math.max(18,source.getWidth()/55f);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(size); paint.setColor(Color.WHITE); paint.setShadowLayer(4,2,2,Color.BLACK);
        String gps=lastLocation==null?"GPS unavailable":String.format(Locale.US,"%.6f, %.6f",lastLocation.getLatitude(),lastLocation.getLongitude());
        canvas.drawText("DarkCat · "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()),size,source.getHeight()-size*2,paint); canvas.drawText(gps,size,source.getHeight()-size/2,paint);
        File output=new File(original.getParent(),original.getName().replace(".jpg","_stamped.jpg")); try(FileOutputStream stream=new FileOutputStream(output)){ stamped.compress(Bitmap.CompressFormat.JPEG,94,stream); }
        source.recycle(); stamped.recycle();
    }
    private void startLocation(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=0 && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)!=0)return;
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE); locationListener=l->{lastLocation=l;};
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,2,locationListener,Looper.getMainLooper()); locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,5000,5,locationListener,Looper.getMainLooper()); }
        catch(SecurityException e){ Diagnostics.record(this,"location",e.toString()); }
    }
    private void startMediaControls(){
        mediaSession=new MediaSession(this,"DarkCatCamera"); mediaSession.setActive(true);
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE).setState(PlaybackState.STATE_PLAYING,0,1f).build());
        mediaSession.setCallback(new MediaSession.Callback(){ @Override public boolean onMediaButtonEvent(Intent intent){ KeyEvent key=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT); if(key!=null&&key.getAction()==KeyEvent.ACTION_UP&&(key.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY)) { if(working)capture("bluetooth_media"); return true; } return super.onMediaButtonEvent(intent); }});
        mediaSession.setPlaybackToRemote(new VolumeProvider(VolumeProvider.VOLUME_CONTROL_RELATIVE,100,50){ @Override public void onAdjustVolume(int direction){ if(direction>0&&working)capture("volume_remote"); }});
    }
    @Override public void onTaskRemoved(Intent rootIntent){ Diagnostics.record(this,"process_lifecycle","TASK_REMOVED_SERVICE_STICKY"); super.onTaskRemoved(rootIntent); }
    @Override public void onDestroy(){ Diagnostics.record(this,"service","STOP"); if(lifecycleReceiver!=null)unregisterReceiver(lifecycleReceiver); if(locationManager!=null&&locationListener!=null) locationManager.removeUpdates(locationListener); if(mediaSession!=null)mediaSession.release(); io.shutdownNow(); super.onDestroy(); }
}
