package com.linkedcamera.app;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Achtergrond-service voor het uploaden van foto's en video's naar Nextcloud via WebDAV.
 */
public class NextcloudUploadService extends IntentService {
    private static final String TAG = "NextcloudUploadService";
    private static final String ACTION_UPLOAD = "net.sourceforge.opencamera.action.UPLOAD";
    private static final String ACTION_UPLOAD_URI = "net.sourceforge.opencamera.action.UPLOAD_URI";
    private static final String ACTION_PROCESS_QUEUE = "net.sourceforge.opencamera.action.PROCESS_QUEUE";
    private static final String EXTRA_FILE_PATH = "net.sourceforge.opencamera.extra.FILE_PATH";
    private static final String EXTRA_DISPLAY_NAME = "net.sourceforge.opencamera.extra.DISPLAY_NAME";
    private static final String PREF_UPLOAD_QUEUE = "nextcloud_upload_queue";
    private static final String QUEUE_URI_PREFIX = "uri:";
    private static final String CHANNEL_ID = "nextcloud_upload_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int PROGRESS_NOTIFICATION_ID = 1002;

    public NextcloudUploadService() {
        super("NextcloudUploadService");
    }

    /**
     * Start upload voor een bestand via filesystem pad.
     */
    public static void startUpload(Context context, String filePath) {
        if (MyDebug.LOG)
            Log.d(TAG, "startUpload called with filePath: " + filePath);
        Intent intent = new Intent(context, NextcloudUploadService.class);
        intent.setAction(ACTION_UPLOAD);
        intent.putExtra(EXTRA_FILE_PATH, filePath);
        context.startService(intent);
        if (MyDebug.LOG)
            Log.d(TAG, "startService called");
    }

    /**
     * Start upload voor een bestand via content URI (voor SAF/MEDIASTORE/URI video methoden).
     */
    public static void startUpload(Context context, Uri uri, String displayName) {
        if (MyDebug.LOG)
            Log.d(TAG, "startUpload called with uri: " + uri + ", displayName: " + displayName);
        Intent intent = new Intent(context, NextcloudUploadService.class);
        intent.setAction(ACTION_UPLOAD_URI);
        intent.setData(uri);
        intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
        context.startService(intent);
        if (MyDebug.LOG)
            Log.d(TAG, "startService called for URI upload");
    }

    /**
     * Verwerk alle uploads in de wachtrij.
     */
    public static void processUploadQueue(Context context) {
        Intent intent = new Intent(context, NextcloudUploadService.class);
        intent.setAction(ACTION_PROCESS_QUEUE);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (MyDebug.LOG)
            Log.d(TAG, "onHandleIntent called");
        if (intent != null) {
            final String action = intent.getAction();
            if (MyDebug.LOG)
                Log.d(TAG, "Action: " + action);
            if (ACTION_UPLOAD.equals(action)) {
                final String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
                if (MyDebug.LOG)
                    Log.d(TAG, "Handling upload for: " + filePath);
                handleUpload(filePath);
            } else if (ACTION_UPLOAD_URI.equals(action)) {
                final Uri uri = intent.getData();
                final String displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME);
                if (MyDebug.LOG)
                    Log.d(TAG, "Handling URI upload for: " + displayName);
                handleUploadUri(uri, displayName);
            } else if (ACTION_PROCESS_QUEUE.equals(action)) {
                if (MyDebug.LOG)
                    Log.d(TAG, "Processing upload queue");
                handleProcessQueue();
            }
        }
    }

    /**
     * Upload een enkel bestand via filesystem pad (foto's en VideoMethod.FILE video's).
     */
    private void handleUpload(String filePath) {
        if (MyDebug.LOG)
            Log.d(TAG, "handleUpload called with: " + filePath);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Controleer of upload is ingeschakeld
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);
        if (MyDebug.LOG)
            Log.d(TAG, "Upload enabled: " + uploadEnabled);
        if (!uploadEnabled) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload not enabled");
            return;
        }

        // Haal Nextcloud URL op
        String nextcloudUrl = prefs.getString(PreferenceKeys.NextcloudUrlPreferenceKey, "").trim();
        if (MyDebug.LOG)
            Log.d(TAG, "Nextcloud URL configured: " + (!nextcloudUrl.isEmpty()));
        if (nextcloudUrl.isEmpty()) {
            if (MyDebug.LOG)
                Log.e(TAG, "Nextcloud URL not configured");
            showNotification(getString(R.string.nextcloud_upload_failed), "URL not configured");
            return;
        }

        // Controleer WiFi-vereiste
        boolean wifiOnly = prefs.getBoolean(PreferenceKeys.NextcloudWifiOnlyPreferenceKey, true);
        boolean isWifi = isWifiConnected();
        if (MyDebug.LOG)
            Log.d(TAG, "WiFi only: " + wifiOnly + ", WiFi connected: " + isWifi);

        if (wifiOnly && !isWifi) {
            if (MyDebug.LOG)
                Log.d(TAG, "WiFi only enabled but not connected, queueing");
            addToUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_waiting_wifi), new File(filePath).getName());
            return;
        }

        // Upload uitvoeren
        File file = new File(filePath);

        if (MyDebug.LOG)
            Log.d(TAG, "File exists: " + file.exists() + ", path: " + filePath);
        if (!file.exists()) {
            if (MyDebug.LOG)
                Log.e(TAG, "File does not exist: " + filePath);
            removeFromUploadQueue(filePath);
            return;
        }
        if (MyDebug.LOG)
            Log.d(TAG, "Starting upload for file: " + file.getName() + ", size: " + file.length());

        boolean success;
        try {
            FileInputStream fis = new FileInputStream(file);
            success = uploadToNextcloud(fis, file.length(), file.getName(), nextcloudUrl);
        } catch (IOException e) {
            if (MyDebug.LOG)
                Log.e(TAG, "Failed to open file for upload: " + e.getMessage(), e);
            success = false;
        }

        if (success) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload successful");
            removeFromUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_success), file.getName());

            // Automatisch verwijderen indien ingeschakeld
            boolean autoDelete = prefs.getBoolean(PreferenceKeys.NextcloudAutoDeletePreferenceKey, false);
            if (autoDelete) {
                if (file.delete()) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "File deleted after upload");
                    showNotification(getString(R.string.nextcloud_photo_deleted), file.getName());
                } else {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Failed to delete file after upload");
                }
            }
        } else {
            if (MyDebug.LOG)
                Log.e(TAG, "Upload failed");
            addToUploadQueue(filePath);
            showNotification(getString(R.string.nextcloud_upload_failed), file.getName());
        }
    }

    /**
     * Upload een enkel bestand via content URI (voor SAF/MEDIASTORE/URI video methoden).
     */
    private void handleUploadUri(Uri uri, String displayName) {
        if (MyDebug.LOG)
            Log.d(TAG, "handleUploadUri called with uri: " + uri + ", displayName: " + displayName);

        if (uri == null) {
            if (MyDebug.LOG)
                Log.e(TAG, "URI is null, skipping upload");
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Controleer of upload is ingeschakeld
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);
        if (!uploadEnabled) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload not enabled");
            return;
        }

        // Haal Nextcloud URL op
        String nextcloudUrl = prefs.getString(PreferenceKeys.NextcloudUrlPreferenceKey, "").trim();
        if (nextcloudUrl.isEmpty()) {
            if (MyDebug.LOG)
                Log.e(TAG, "Nextcloud URL not configured");
            showNotification(getString(R.string.nextcloud_upload_failed), "URL not configured");
            return;
        }

        // Controleer WiFi-vereiste
        boolean wifiOnly = prefs.getBoolean(PreferenceKeys.NextcloudWifiOnlyPreferenceKey, true);
        boolean isWifi = isWifiConnected();

        String queueEntry = QUEUE_URI_PREFIX + displayName + ":" + uri.toString();

        if (wifiOnly && !isWifi) {
            if (MyDebug.LOG)
                Log.d(TAG, "WiFi only enabled but not connected, queueing URI");
            addToUploadQueue(queueEntry);
            showNotification(getString(R.string.nextcloud_upload_waiting_wifi), displayName);
            return;
        }

        // Bestandsgrootte ophalen via ContentResolver
        long fileSize = getUriFileSize(uri);
        if (MyDebug.LOG)
            Log.d(TAG, "Starting URI upload for: " + displayName + ", size: " + fileSize);

        boolean success;
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                if (MyDebug.LOG)
                    Log.e(TAG, "Failed to open InputStream for URI: " + uri);
                addToUploadQueue(queueEntry);
                showNotification(getString(R.string.nextcloud_upload_failed), displayName);
                return;
            }
            success = uploadToNextcloud(inputStream, fileSize, displayName, nextcloudUrl);
        } catch (IOException e) {
            if (MyDebug.LOG)
                Log.e(TAG, "Failed to open URI for upload: " + e.getMessage(), e);
            success = false;
        }

        if (success) {
            if (MyDebug.LOG)
                Log.d(TAG, "URI upload successful");
            removeFromUploadQueue(queueEntry);
            showNotification(getString(R.string.nextcloud_upload_success), displayName);

            // Automatisch verwijderen indien ingeschakeld
            boolean autoDelete = prefs.getBoolean(PreferenceKeys.NextcloudAutoDeletePreferenceKey, false);
            if (autoDelete) {
                try {
                    int deleted = getContentResolver().delete(uri, null, null);
                    if (MyDebug.LOG)
                        Log.d(TAG, "Content URI delete result: " + deleted);
                    if (deleted > 0) {
                        showNotification(getString(R.string.nextcloud_photo_deleted), displayName);
                    }
                } catch (Exception e) {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Failed to delete content URI after upload: " + e.getMessage(), e);
                }
            }
        } else {
            if (MyDebug.LOG)
                Log.e(TAG, "URI upload failed");
            addToUploadQueue(queueEntry);
            showNotification(getString(R.string.nextcloud_upload_failed), displayName);
        }
    }

    /**
     * Verwerk alle bestanden in de upload wachtrij met voortgangsnotificatie.
     * Ondersteunt zowel filesystem-paden als content URI entries (prefix "uri:").
     */
    private void handleProcessQueue() {
        if (MyDebug.LOG)
            Log.d(TAG, "handleProcessQueue called");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queueSet = getUploadQueue();
        List<String> queue = new ArrayList<>(queueSet);

        int totalFiles = queue.size();

        if (MyDebug.LOG)
            Log.d(TAG, "Processing upload queue with " + totalFiles + " items");

        if (queue.isEmpty()) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload queue is empty");
            return;
        }

        // Controleer of upload is ingeschakeld
        boolean uploadEnabled = prefs.getBoolean(PreferenceKeys.NextcloudUploadPreferenceKey, false);
        if (!uploadEnabled) {
            if (MyDebug.LOG)
                Log.d(TAG, "Upload not enabled, skipping queue processing");
            return;
        }

        // WiFi-check vooraf
        boolean wifiOnly = prefs.getBoolean(PreferenceKeys.NextcloudWifiOnlyPreferenceKey, true);

        if (wifiOnly) {
            boolean isWifi = isWifiConnected();
            if (!isWifi) {
                if (MyDebug.LOG)
                    Log.d(TAG, "WiFi-only enabled but not connected, skipping queue processing");
                return;
            }
            if (MyDebug.LOG)
                Log.d(TAG, "WiFi condition met");
        }

        // Haal Nextcloud URL op
        String nextcloudUrl = prefs.getString(PreferenceKeys.NextcloudUrlPreferenceKey, "").trim();
        if (nextcloudUrl.isEmpty()) {
            if (MyDebug.LOG)
                Log.e(TAG, "Nextcloud URL not configured");
            showNotification(getString(R.string.nextcloud_upload_failed), "URL not configured");
            return;
        }

        // Verwerk elke entry met voortgangsnotificatie
        if (MyDebug.LOG)
            Log.d(TAG, "Starting to process " + totalFiles + " files in upload queue");

        int successCount = 0;
        int currentFile = 0;
        boolean autoDelete = prefs.getBoolean(PreferenceKeys.NextcloudAutoDeletePreferenceKey, false);

        for (String entry : queue) {
            currentFile++;
            showProgressNotification(currentFile, totalFiles);

            boolean success = false;

            if (entry.startsWith(QUEUE_URI_PREFIX)) {
                // URI-based entry: "uri:DISPLAY_NAME:content://..."
                String remainder = entry.substring(QUEUE_URI_PREFIX.length());
                int colonPos = remainder.indexOf(':');
                if (colonPos == -1) {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Malformed URI queue entry: " + entry);
                    removeFromUploadQueue(entry);
                    continue;
                }
                String displayName = remainder.substring(0, colonPos);
                String uriString = remainder.substring(colonPos + 1);
                Uri uri = Uri.parse(uriString);

                long fileSize = getUriFileSize(uri);
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        success = uploadToNextcloud(inputStream, fileSize, displayName, nextcloudUrl);
                    } else {
                        if (MyDebug.LOG)
                            Log.e(TAG, "Failed to open InputStream for queued URI: " + uriString);
                    }
                } catch (IOException e) {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Error opening queued URI: " + e.getMessage(), e);
                }

                if (success) {
                    removeFromUploadQueue(entry);
                    successCount++;
                    if (autoDelete) {
                        try {
                            getContentResolver().delete(uri, null, null);
                        } catch (Exception e) {
                            if (MyDebug.LOG)
                                Log.e(TAG, "Failed to delete queued URI: " + e.getMessage(), e);
                        }
                    }
                }
            } else {
                // Filesystem pad entry (bestaand gedrag voor foto's en FILE video's)
                File file = new File(entry);

                if (!file.exists()) {
                    if (MyDebug.LOG)
                        Log.e(TAG, "File does not exist: " + entry);
                    removeFromUploadQueue(entry);
                    continue;
                }

                try {
                    FileInputStream fis = new FileInputStream(file);
                    success = uploadToNextcloud(fis, file.length(), file.getName(), nextcloudUrl);
                } catch (IOException e) {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Error opening queued file: " + e.getMessage(), e);
                }

                if (success) {
                    if (MyDebug.LOG)
                        Log.d(TAG, "Upload successful for: " + file.getName());
                    removeFromUploadQueue(entry);
                    successCount++;
                    if (autoDelete) {
                        if (file.delete()) {
                            if (MyDebug.LOG)
                                Log.d(TAG, "File deleted after upload");
                        }
                    }
                } else {
                    if (MyDebug.LOG)
                        Log.e(TAG, "Upload failed for: " + file.getName());
                    // Bewaar in wachtrij voor opnieuw proberen
                }
            }
        }

        // Wis voortgangsnotificatie en toon voltooiing
        cancelProgressNotification();

        if (successCount > 0) {
            showNotification(
                getString(R.string.nextcloud_upload_complete, successCount),
                successCount + "/" + totalFiles + " uploaded"
            );
        }

        if (MyDebug.LOG)
            Log.d(TAG, "Finished processing upload queue. Success: " + successCount + "/" + totalFiles);
    }

    /**
     * Upload een bestand naar Nextcloud via WebDAV.
     * Accepteert een InputStream zodat zowel filesystem-bestanden als content URIs
     * geüpload kunnen worden. De InputStream wordt altijd gesloten na afloop.
     *
     * @param inputStream  De te uploaden data stream.
     * @param fileSize     Bestandsgrootte in bytes (voor Content-Length header en timeout).
     * @param fileName     Bestandsnaam voor de WebDAV URL.
     * @param shareUrl     Nextcloud public share URL.
     * @return true als de upload geslaagd is (HTTP 2xx).
     */
    private boolean uploadToNextcloud(InputStream inputStream, long fileSize, String fileName, String shareUrl) {
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;

        if (MyDebug.LOG) {
            Log.d(TAG, "uploadToNextcloud: Starting upload with WebDAV");
            Log.d(TAG, "fileName: " + fileName + ", size: " + fileSize);
        }

        try {
            // Parse share URL
            // Formaat: https://cloud.example.com/index.php/s/TOKEN
            int indexPos = shareUrl.indexOf("/index.php/s/");
            if (indexPos == -1) {
                if (MyDebug.LOG)
                    Log.e(TAG, "Invalid share URL format");
                return false;
            }

            String baseUrl = shareUrl.substring(0, indexPos);
            String token = shareUrl.substring(indexPos + "/index.php/s/".length());

            // Schoon token op (verwijder slashes, query params)
            token = token.split("[/?]")[0];

            // Bouw WebDAV upload URL
            String uploadUrl = baseUrl + "/public.php/webdav/" + fileName;

            if (MyDebug.LOG)
                Log.d(TAG, "Upload URL constructed");

            // Dynamische timeout: 30s basis + 60s per 100 MB, max 30 minuten
            int readTimeoutMs = (int) Math.min(
                30_000L + (fileSize / (100L * 1024L * 1024L)) * 60_000L,
                30L * 60L * 1000L
            );
            if (MyDebug.LOG)
                Log.d(TAG, "Read timeout: " + readTimeoutMs + "ms for " + fileSize + " bytes");

            // Maak HTTP-verbinding
            URL url = new URL(uploadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(readTimeoutMs);

            if (MyDebug.LOG)
                Log.d(TAG, "Connection created, method: PUT");

            // Wachtwoord ophalen (optioneel, voor beschermde shares)
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String password = prefs.getString(PreferenceKeys.NextcloudPasswordPreferenceKey, "").trim();

            // Autorisatie instellen (token als gebruikersnaam, optioneel wachtwoord)
            String credentials;
            if (password.isEmpty()) {
                credentials = token + ":";
                if (MyDebug.LOG)
                    Log.d(TAG, "Auth: using token as username, empty password (public share)");
            } else {
                credentials = token + ":" + password;
                if (MyDebug.LOG)
                    Log.d(TAG, "Auth: using token as username with password (password-protected share)");
            }

            String basicAuth = "Basic " + Base64.encodeToString(
                credentials.getBytes(), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", basicAuth);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            if (fileSize > 0) {
                connection.setRequestProperty("Content-Length", String.valueOf(fileSize));
            }

            if (MyDebug.LOG)
                Log.d(TAG, "Headers set, starting file upload");

            // Upload bestandsdata met 64 KB buffer voor betere doorvoer bij grote bestanden
            outputStream = new DataOutputStream(connection.getOutputStream());

            byte[] buffer = new byte[65536];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.flush();

            if (MyDebug.LOG)
                Log.d(TAG, "Upload completed, " + totalBytes + " bytes sent");

            // Controleer response
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            if (MyDebug.LOG)
                Log.d(TAG, "Response code: " + responseCode + ", message: " + responseMessage);

            boolean success = responseCode >= 200 && responseCode < 300;
            if (MyDebug.LOG)
                Log.d(TAG, "Upload success: " + success);

            return success;

        } catch (IOException e) {
            if (MyDebug.LOG)
                Log.e(TAG, "Upload error: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (connection != null) connection.disconnect();
                if (MyDebug.LOG)
                    Log.d(TAG, "Cleanup completed");
            } catch (IOException e) {
                if (MyDebug.LOG)
                    Log.e(TAG, "Error closing streams: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Haal de bestandsgrootte op voor een content URI.
     *
     * @return Bestandsgrootte in bytes, of -1 als onbekend.
     */
    private long getUriFileSize(Uri uri) {
        long size = -1;
        try {
            AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd != null) {
                size = afd.getLength();
                afd.close();
            }
        } catch (IOException e) {
            if (MyDebug.LOG)
                Log.e(TAG, "Failed to get file size for URI: " + e.getMessage(), e);
        }
        return size;
    }

    /**
     * Controleer of WiFi is verbonden.
     */
    private boolean isWifiConnected() {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiInfo != null && wifiInfo.isConnected();
        }

        return false;
    }

    /**
     * Voeg een entry toe aan de upload wachtrij.
     */
    private void addToUploadQueue(String entry) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queue = new HashSet<>(getUploadQueue());
        boolean wasAdded = queue.add(entry);

        if (wasAdded) {
            prefs.edit().putStringSet(PREF_UPLOAD_QUEUE, queue).apply();
            if (MyDebug.LOG)
                Log.d(TAG, "Added to queue: " + entry);
        } else {
            if (MyDebug.LOG)
                Log.d(TAG, "Entry already in queue: " + entry);
        }
    }

    /**
     * Verwijder een entry uit de upload wachtrij.
     */
    private void removeFromUploadQueue(String entry) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> queue = new HashSet<>(getUploadQueue());
        queue.remove(entry);
        prefs.edit().putStringSet(PREF_UPLOAD_QUEUE, queue).apply();

        if (MyDebug.LOG)
            Log.d(TAG, "Removed from queue: " + entry);
    }

    /**
     * Haal de huidige upload wachtrij op.
     */
    private Set<String> getUploadQueue() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return new HashSet<>(prefs.getStringSet(PREF_UPLOAD_QUEUE, new HashSet<String>()));
    }

    /**
     * Toon een notificatie.
     */
    private void showNotification(String title, String message) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nextcloud Upload",
                NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Toon voortgangsnotificatie met voortgangsbalk.
     */
    private void showProgressNotification(int current, int total) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nextcloud Upload",
                NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }

        String title = getString(R.string.nextcloud_uploading_progress, current, total);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText("Uploading to Nextcloud...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(total, current, false)
            .setOngoing(true)
            .setAutoCancel(false);

        notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build());
    }

    /**
     * Annuleer de voortgangsnotificatie.
     */
    private void cancelProgressNotification() {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.cancel(PROGRESS_NOTIFICATION_ID);
        }
    }
}
