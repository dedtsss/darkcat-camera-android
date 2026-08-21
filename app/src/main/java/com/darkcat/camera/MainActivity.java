package com.darkcat.camera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;

/** Normal operator UI. It never binds or configures a camera use case itself. */
public final class MainActivity extends ComponentActivity {
    private static final int PERMISSIONS=40;
    private PreviewView preview; private TextView state; private CameraCaptureService service;
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){ service=((CameraCaptureService.Binder)binder).service(); service.attachPreview(preview); updateState(); }
        public void onServiceDisconnected(ComponentName name){ service=null; state.setText("Camera service disconnected"); }
    };
    @Override public void onCreate(Bundle saved){ super.onCreate(saved); buildUi();
        if(!hasPermissions()) ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS},PERMISSIONS);
        else startCameraService();
    }
    private boolean hasPermissions(){ return ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==PERMISSIONS&&hasPermissions())startCameraService(); else state.setText("Camera and location permission are required"); }
    private void buildUi(){
        FrameLayout root=new FrameLayout(this); preview=new PreviewView(this); preview.setScaleType(PreviewView.ScaleType.FILL_CENTER); root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.VERTICAL); top.setPadding(28,24,28,12); top.setBackgroundColor(0x99000000);
        TextView title=new TextView(this); title.setText("DARKCAT CAMERA  ·  MVP-1"); title.setTextColor(Color.WHITE); title.setTextSize(16); top.addView(title);
        state=new TextView(this); state.setText("Starting camera service…"); state.setTextColor(0xffd7e8ff); state.setTextSize(14); top.addView(state);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER); controls.setPadding(20,12,20,24); controls.setBackgroundColor(0xcc101010);
        Button capture=new Button(this); capture.setText(R.string.capture); capture.setTextSize(18); capture.setOnClickListener(v->send(CameraCaptureService.ACTION_CAPTURE)); controls.addView(capture,new LinearLayout.LayoutParams(0,70,2));
        Button info=new Button(this); info.setText("Info"); info.setOnClickListener(v->showInfo()); controls.addView(info,new LinearLayout.LayoutParams(0,70,1));
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)); setContentView(root);
    }
    private void startCameraService(){ Intent i=new Intent(this,CameraCaptureService.class).setAction(CameraCaptureService.ACTION_START); ContextCompat.startForegroundService(this,i); bindService(i,connection,BIND_AUTO_CREATE); }
    private void send(String action){ Intent i=new Intent(this,CameraCaptureService.class).setAction(action); if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)ContextCompat.startForegroundService(this,i); }
    @Override public boolean onKeyUp(int keyCode,KeyEvent event){ if(keyCode==KeyEvent.KEYCODE_VOLUME_UP){ send(CameraCaptureService.ACTION_CAPTURE); return true; } return super.onKeyUp(keyCode,event); }
    private void updateState(){ if(service==null)return; state.setText(service.isWorking()?"WORKING  ·  "+(service.getLastLocation()==null?"GPS searching":"GPS fixed")+"  ·  Volume+ captures":"Paused"); }
    private void showInfo(){
        String[] items={"About",Diagnostics.about(),"\nDiagnostics are local, bounded, and credential-free.","\nHTTPS endpoints are configured only by CI/runtime deployment."};
        new AlertDialog.Builder(this).setTitle(items[0]).setMessage(items[1]+items[2]+items[3]).setPositiveButton("Upload diagnostics",(d,w)->upload()).setNeutralButton("Check updates",(d,w)->checkUpdates()).setNegativeButton("Close",null).show();
    }
    private void upload(){ DiagnosticsTransport.upload(this,(message)->new AlertDialog.Builder(this).setTitle("Diagnostics").setMessage(message).setPositiveButton("OK",null).show()); }
    private void checkUpdates(){ DiagnosticsTransport.checkLatest(this,(message)->{ String url=null; int marker=message.indexOf("APK_URL="); if(marker>=0)url=message.substring(marker+8).trim(); AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Update").setMessage(url==null?message:message.substring(0,marker)); if(url!=null&&!url.isEmpty()){ final String download=url; dialog.setPositiveButton("Download APK",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(download)))); } else dialog.setPositiveButton("OK",null); dialog.show(); }); }
    @Override protected void onDestroy(){ if(isFinishing())try{unbindService(connection);}catch(IllegalArgumentException ignored){} super.onDestroy(); }
}
