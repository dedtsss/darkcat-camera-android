package ru.darkcat.camera.upload;

import android.annotation.SuppressLint;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@SuppressLint("NewApi")
final class WebDavClient {
    static UploadProvider.UploadResult put(String url, String username, String password, String mime, File file) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("PUT"); connection.setDoOutput(true); connection.setConnectTimeout(20_000); connection.setReadTimeout(60_000);
        connection.setRequestProperty("Content-Type", mime == null ? "application/octet-stream" : mime);
        connection.setFixedLengthStreamingMode(file.length());
        if (username != null && !username.isEmpty()) connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        try (InputStream input = new FileInputStream(file); java.io.OutputStream output = connection.getOutputStream()) { byte[] buffer = new byte[64 * 1024]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n); }
        int code = connection.getResponseCode(); boolean accepted = code >= 200 && code < 300;
        return new UploadProvider.UploadResult(accepted, accepted, "HTTP " + code);
    }
    static boolean verify(String url, String username, String password, long expectedLength) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(); connection.setRequestMethod("HEAD"); connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
        if (username != null && !username.isEmpty()) connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        int code = connection.getResponseCode(); if (code < 200 || code >= 300) return false;
        long length = connection.getHeaderFieldLong("Content-Length", -1); return length < 0 || expectedLength < 0 || length == expectedLength;
    }
    static String appendPath(String base, String folder, String file) {
        StringBuilder value = new StringBuilder(base == null ? "" : base.replaceAll("/+$", ""));
        for (String part : (folder == null ? "" : folder).split("/")) if (!part.isEmpty()) value.append('/').append(encode(part));
        value.append('/').append(encode(file)); return value.toString();
    }
    private static String encode(String value) { try { return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); } catch (java.io.UnsupportedEncodingException error) { throw new IllegalStateException(error); } }
    private WebDavClient() { }
}
