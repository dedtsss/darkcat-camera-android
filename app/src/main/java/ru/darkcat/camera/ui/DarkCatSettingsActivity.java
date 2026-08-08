package ru.darkcat.camera.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import ru.darkcat.camera.crypto.SecureCredentialStore;
import ru.darkcat.camera.data.DarkCatSettings;

public final class DarkCatSettingsActivity extends Activity {
    private Switch secure, autoUpload, deleteAfterVerified;
    private CheckBox wifiOnly;
    private Spinner workflow, crosshair, provider, color, size, thickness;
    private EditText share, base, folder, user, password;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); setTitle("DarkCat Camera");
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(24, 18, 24, 18);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(content);
        secure = new Switch(this); secure.setText("DarkCat Secure Storage (default ON)"); secure.setChecked(DarkCatSettings.isSecureMode(this)); content.addView(secure);
        workflow = spinner(content, "Workflow", new String[]{"FAST", "EDIT"}, DarkCatSettings.MODE_EDIT.equals(DarkCatSettings.workflow(this)) ? 1 : 0);
        crosshair = spinner(content, "Crosshair", new String[]{"OFF", "PREVIEW", "STAMP"}, index(DarkCatSettings.crosshair(this), new String[]{"off","preview","stamp"}));
        color = spinner(content, "Crosshair color", new String[]{"Yellow", "Red", "White"}, DarkCatSettings.crosshairColor(this) == 0xffff0000 ? 1 : DarkCatSettings.crosshairColor(this) == 0xffffffff ? 2 : 0);
        size = spinner(content, "Crosshair size", new String[]{"24", "36", "52"}, DarkCatSettings.crosshairSize(this) == 24 ? 0 : DarkCatSettings.crosshairSize(this) == 52 ? 2 : 1);
        thickness = spinner(content, "Crosshair thickness", new String[]{"1", "2", "4"}, DarkCatSettings.crosshairThickness(this) == 1 ? 0 : DarkCatSettings.crosshairThickness(this) == 4 ? 2 : 1);
        provider = spinner(content, "Upload provider", new String[]{"Local/Fake", "Nextcloud Public Share", "Generic WebDAV", "DarkCat API stub"}, providerIndex());
        share = field(content, "Nextcloud share URL", false); share.setText(DarkCatSettings.nextcloudShare(this));
        base = field(content, "Generic WebDAV base URL", false); base.setText(DarkCatSettings.baseUrl(this));
        folder = field(content, "Remote folder/path", false); folder.setText(DarkCatSettings.remoteFolder(this));
        user = field(content, "WebDAV username", false); user.setText(SecureCredentialStore.get(this, "webdav_user"));
        password = field(content, "Password or share password", true); password.setText("");
        autoUpload = new Switch(this); autoUpload.setText("Auto upload (queue is always persistent)"); autoUpload.setChecked(DarkCatSettings.autoUpload(this)); content.addView(autoUpload);
        wifiOnly = new CheckBox(this); wifiOnly.setText("Wi-Fi/unmetered network only"); wifiOnly.setChecked(DarkCatSettings.wifiOnly(this)); content.addView(wifiOnly);
        deleteAfterVerified = new Switch(this); deleteAfterVerified.setText("Delete local vault after verified upload"); deleteAfterVerified.setChecked(DarkCatSettings.deleteAfterVerified(this)); content.addView(deleteAfterVerified);
        TextView note = new TextView(this); note.setText("Keep local is the default. Secure mode uses AES-256-GCM, Android Keystore and UUID vault names. Crosshair STAMP is drawn at the output bitmap center."); note.setPadding(0, 18, 0, 18); content.addView(note);
        Button save = new Button(this); save.setText("Save DarkCat settings"); save.setOnClickListener(v -> save()); content.addView(save);
        Button vault = new Button(this); vault.setText("Open Protected Gallery / Vault"); vault.setOnClickListener(v -> startActivity(new android.content.Intent(this, VaultActivity.class))); content.addView(vault);
        setContentView(scroll);
    }
    private EditText field(LinearLayout parent, String hint, boolean secret) { TextView label = new TextView(this); label.setText(hint); parent.addView(label); EditText field = new EditText(this); field.setSingleLine(true); if (secret) field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); parent.addView(field); return field; }
    private Spinner spinner(LinearLayout parent, String label, String[] values, int selected) { TextView title = new TextView(this); title.setText(label); parent.addView(title); Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); spinner.setSelection(selected); parent.addView(spinner); return spinner; }
    private int index(String value, String[] values) { for (int i=0;i<values.length;i++) if (values[i].equals(value)) return i; return 0; }
    private int providerIndex() { String p = DarkCatSettings.provider(this); if (DarkCatSettings.PROVIDER_NEXTCLOUD.equals(p)) return 1; if (DarkCatSettings.PROVIDER_WEBDAV.equals(p)) return 2; if (DarkCatSettings.PROVIDER_DARKCAT_API.equals(p)) return 3; return 0; }
    private void save() {
        DarkCatSettings.set(this, "darkcat_secure_mode", secure.isChecked()); DarkCatSettings.set(this, "darkcat_workflow", workflow.getSelectedItemPosition() == 1 ? DarkCatSettings.MODE_EDIT : DarkCatSettings.MODE_FAST);
        DarkCatSettings.set(this, "darkcat_crosshair", new String[]{DarkCatSettings.CROSSHAIR_OFF,DarkCatSettings.CROSSHAIR_PREVIEW,DarkCatSettings.CROSSHAIR_STAMP}[crosshair.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_crosshair_color", new int[]{0xffffcc00,0xffff0000,0xffffffff}[color.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_crosshair_size", new int[]{24,36,52}[size.getSelectedItemPosition()]); DarkCatSettings.set(this, "darkcat_crosshair_thickness", new int[]{1,2,4}[thickness.getSelectedItemPosition()]);
        DarkCatSettings.set(this, "darkcat_provider", new String[]{DarkCatSettings.PROVIDER_LOCAL,DarkCatSettings.PROVIDER_NEXTCLOUD,DarkCatSettings.PROVIDER_WEBDAV,DarkCatSettings.PROVIDER_DARKCAT_API}[provider.getSelectedItemPosition()]);
        SecureCredentialStore.put(this, "nextcloud_share", share.getText().toString().trim()); DarkCatSettings.set(this, "darkcat_webdav_base", base.getText().toString().trim()); DarkCatSettings.set(this, "darkcat_remote_folder", folder.getText().toString().trim());
        SecureCredentialStore.put(this, "webdav_user", user.getText().toString()); if (password.getText().length() > 0) { SecureCredentialStore.put(this, "webdav_password", password.getText().toString()); SecureCredentialStore.put(this, "nextcloud_password", password.getText().toString()); }
        DarkCatSettings.set(this, "darkcat_auto_upload", autoUpload.isChecked()); DarkCatSettings.set(this, "darkcat_wifi_only", wifiOnly.isChecked()); DarkCatSettings.set(this, "darkcat_delete_after_verified", deleteAfterVerified.isChecked());
        Toast.makeText(this, "DarkCat settings saved", Toast.LENGTH_SHORT).show(); finish();
    }
}
