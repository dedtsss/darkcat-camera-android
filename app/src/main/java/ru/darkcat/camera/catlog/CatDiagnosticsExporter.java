package ru.darkcat.camera.catlog;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates an explicit user-shareable CAT Log ZIP; no network path is involved. */
public final class CatDiagnosticsExporter {
    private CatDiagnosticsExporter() { }

    public static File export(Context context) throws IOException {
        CatLog.flush(1_500L);
        File source = CatLog.sessionDirectory();
        if (source == null) throw new IOException("CAT Log is not initialized");
        File share = new File(context.getCacheDir(), "darkcat-share");
        if (!share.exists() && !share.mkdirs()) throw new IOException("Cannot create diagnostics share directory");
        String name = exportFileName(Build.MANUFACTURER, Build.MODEL, new Date());
        File output = new File(share, name);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            addFile(zip, new File(source, "cat-events.ndjson"), "cat-events.ndjson");
            addJson(zip, "session.json", CatLog.sessionInfoJson());
            addJson(zip, "app-info.json", CatLog.appInfoJson());
            addJson(zip, "device-info.json", CatLog.deviceInfoJson());
            addJson(zip, "exit-info.json", CatLog.exitInfoJson());
            addJson(zip, "user-notes.json", userNotes(new File(source, "cat-events.ndjson")));
        } catch (Exception error) {
            if (output.exists()) output.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot create CAT diagnostics", error);
        }
        return output;
    }

    /** Keeps the user-visible device hint while preventing unsafe path characters in the share filename. */
    static String exportFileName(String manufacturer, String model, Date exportedAt) {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(exportedAt);
        return "cat-" + safeFilenameComponent(manufacturer) + "-" + safeFilenameComponent(model)
                + "-" + timestamp + ".zip";
    }

    private static String safeFilenameComponent(String value) {
        if (value == null) return "unknown";
        StringBuilder safe = new StringBuilder();
        boolean lastWasSeparator = false;
        for (int index = 0; index < value.length() && safe.length() < 48; index++) {
            char character = value.charAt(index);
            if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9') || character == '-' || character == '.') {
                safe.append(character);
                lastWasSeparator = false;
            } else if (!lastWasSeparator) {
                safe.append('_');
                lastWasSeparator = true;
            }
        }
        while (safe.length() > 0 && (safe.charAt(0) == '_' || safe.charAt(0) == '-' || safe.charAt(0) == '.')) {
            safe.deleteCharAt(0);
        }
        while (safe.length() > 0 && (safe.charAt(safe.length() - 1) == '_' || safe.charAt(safe.length() - 1) == '-' || safe.charAt(safe.length() - 1) == '.')) {
            safe.deleteCharAt(safe.length() - 1);
        }
        return safe.length() == 0 ? "unknown" : safe.toString();
    }

    private static void addFile(ZipOutputStream zip, File file, String entry) throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        if (file.isFile()) {
            byte[] buffer = new byte[8 * 1024];
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                int count;
                while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
    }

    private static void addJson(ZipOutputStream zip, String entry, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static JSONArray userNotes(File events) {
        JSONArray notes = new JSONArray();
        if (!events.isFile()) return notes;
        try (BufferedReader reader = new BufferedReader(new FileReader(events))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject event = new JSONObject(line);
                if (!"user.note".equals(event.optString("event"))) continue;
                JSONObject note = new JSONObject();
                note.put("wall_clock_ms", event.optLong("wall_clock_ms"));
                note.put("note", event.optString("actual"));
                notes.put(note);
            }
        } catch (Exception ignored) { }
        return notes;
    }
}
