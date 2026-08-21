package com.darkcat.camera;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        setContentView(R.layout.activity_main);
        preview=findViewById(R.id.preview);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        state=findViewById(R.id.camera_state);
        View status=findViewById(R.id.status_overlay);
        View controls=findViewById(R.id.camera_controls);
        final int statusStart=status.getPaddingStart(), statusTop=status.getPaddingTop(), statusEnd=status.getPaddingEnd(), statusBottom=status.getPaddingBottom();
        final int controlsStart=controls.getPaddingStart(), controlsTop=controls.getPaddingTop(), controlsEnd=controls.getPaddingEnd(), controlsBottom=controls.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camera_root),(view,insets)->{
            Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            status.setPaddingRelative(statusStart+safe.left,statusTop+safe.top,statusEnd+safe.right,statusBottom);
            controls.setPaddingRelative(controlsStart+safe.left,controlsTop,controlsEnd+safe.right,controlsBottom+safe.bottom);
            return insets;
        });
        findViewById(R.id.capture_button).setOnClickListener(v->send(CameraCaptureService.ACTION_CAPTURE));
        findViewById(R.id.info_button).setOnClickListener(v->showInfo());
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
