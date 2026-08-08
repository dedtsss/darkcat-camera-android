package ru.darkcat.camera.ui;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import ru.darkcat.camera.data.DarkCatDatabase;
import ru.darkcat.camera.data.DarkCatSettings;

public final class DarkCatUi {
    public static void install(Activity activity) {
        FrameLayout preview = activity.findViewById(com.linkedcamera.app.R.id.preview);
        if (preview != null) preview.addView(new CrosshairView(activity), new FrameLayout.LayoutParams(-1, -1));
        RelativeLayout root = activity.findViewById(com.linkedcamera.app.R.id.main_layout);
        if (root == null) root = (RelativeLayout) activity.findViewById(android.R.id.content).getRootView();
        Button darkcat = new Button(activity); darkcat.setText("DarkCat"); darkcat.setTextSize(10); darkcat.setTextColor(Color.WHITE); darkcat.setOnClickListener(v -> activity.startActivity(new android.content.Intent(activity, DarkCatSettingsActivity.class)));
        RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams(-2, -2); buttonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP); buttonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT); buttonParams.topMargin = 12; buttonParams.rightMargin = 8;
        if (root instanceof RelativeLayout) ((RelativeLayout) root).addView(darkcat, buttonParams);
        TextView status = new TextView(activity); status.setTextColor(Color.WHITE); status.setTextSize(11); status.setGravity(Gravity.CENTER); status.setPadding(8,4,8,4); status.setBackgroundColor(0x99000000);
        RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(-2, -2); statusParams.addRule(RelativeLayout.ALIGN_PARENT_TOP); statusParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT); statusParams.topMargin=12; statusParams.leftMargin=8;
        if (root instanceof RelativeLayout) ((RelativeLayout) root).addView(status, statusParams);
        status.postDelayed(new Runnable() { @Override public void run() { status.setText((DarkCatSettings.isSecureMode(activity) ? "Secure ON" : "Secure OFF") + " • " + (DarkCatSettings.MODE_EDIT.equals(DarkCatSettings.workflow(activity)) ? "EDIT" : "FAST") + " • queue " + DarkCatDatabase.get(activity).queueCount()); status.postDelayed(this, 1500); } }, 300);
    }
    private DarkCatUi() { }
}
