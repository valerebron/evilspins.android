package com.evilspins.app.storage;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "EvilSpinsFileStorage")
public class FileStoragePlugin extends Plugin {

    private static final String TAG = "EvilSpinsFileStorage";
    private static final int REQUEST_WRITE_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 1002;
    private File baseStorageDir;
    private PluginCall pendingWriteCall;
    private String pendingFileName;
    private String pendingContent;
    private String pendingDirectory;

    @Override
    public void load() {
        super.load();

        // Always use shared external storage root
        baseStorageDir = Environment.getExternalStorageDirectory();
        logEvent("info", "FileStorage loaded. Using shared storage: " + baseStorageDir.getAbsolutePath());

        // Check initial permissions
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Check MANAGE_EXTERNAL_STORAGE
            boolean hasPermission = Environment.isExternalStorageManager();
            logEvent("info", "Android 11+ - MANAGE_EXTERNAL_STORAGE: " + (hasPermission ? "GRANTED" : "NOT GRANTED"));
        } else {
            // Android 10 and below: Check WRITE_EXTERNAL_STORAGE
            Activity activity = getActivity();
            if (activity != null) {
                int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                logEvent("info", "Android 10- - WRITE_EXTERNAL_STORAGE: " + (permission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "NOT GRANTED"));
            }
        }
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Request MANAGE_EXTERNAL_STORAGE
            requestManageStoragePermission();
            call.resolve(new JSObject().put("requested", "MANAGE_EXTERNAL_STORAGE"));
        } else {
            // Android 10 and below: Request WRITE_EXTERNAL_STORAGE
            Activity activity = getActivity();
            if (activity != null) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_PERMISSION);
                call.resolve(new JSObject().put("requested", "WRITE_EXTERNAL_STORAGE"));
            } else {
                call.reject("Activity not available");
            }
        }
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        JSObject result = new JSObject();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasPermission = Environment.isExternalStorageManager();
            result.put("manageExternalStorage", hasPermission);
            result.put("android11Plus", true);
        } else {
            Activity activity = getActivity();
            boolean hasPermission = false;
            if (activity != null) {
                int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                hasPermission = (permission == PackageManager.PERMISSION_GRANTED);
            }
            result.put("writeExternalStorage", hasPermission);
            result.put("android11Plus", false);
        }

        call.resolve(result);
    }

    private void requestManageStoragePermission() {
        Activity activity = getActivity();
        if (activity != null) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
                logEvent("info", "Opened Settings for MANAGE_EXTERNAL_STORAGE permission");
            } catch (Exception e) {
                logEvent("error", "Error requesting MANAGE_EXTERNAL_STORAGE: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        String fileName = call.getString("fileName");
        String content = call.getString("content", "");
        String directory = call.getString("directory", "");

        if (fileName == null || fileName.isEmpty()) {
            call.reject("fileName is required");
            return;
        }

        // Check permission before writing
        if (!hasRequiredPermission()) {
            // Store pending call and request permission
            pendingWriteCall = call;
            pendingFileName = fileName;
            pendingContent = content;
            pendingDirectory = directory;

            logEvent("info", "Permission not granted, requesting...");
            requestPermissionsInternal();
            return;
        }

        writeFileInternal(call, fileName, content, directory);
    }

    private boolean hasRequiredPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            Activity activity = getActivity();
            if (activity != null) {
                int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return permission == PackageManager.PERMISSION_GRANTED;
            }
            return false;
        }
    }

    private void requestPermissionsInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageStoragePermission();
        } else {
            Activity activity = getActivity();
            if (activity != null) {
                ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_PERMISSION);
            }
        }
    }

    private void writeFileInternal(PluginCall call, String fileName, String content, String directory) {
        try {
            File file = getFile(fileName, directory);

            // Create parent directories if needed
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean created = parent.mkdirs();
                logEvent("info", "Created directories: " + parent.getAbsolutePath() + " - " + created);
            }

            // Write file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }

            logEvent("info", "File written: " + file.getAbsolutePath() + " (" + content.length() + " bytes)");

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("path", file.getAbsolutePath());
            result.put("size", content.length());
            call.resolve(result);
        } catch (Exception e) {
            logEvent("error", "Error writing file: " + e.getMessage());
            e.printStackTrace();
            call.reject("Error writing file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void readFile(PluginCall call) {
        String fileName = call.getString("fileName");
        String directory = call.getString("directory", "");

        if (fileName == null || fileName.isEmpty()) {
            call.reject("fileName is required");
            return;
        }

        try {
            File file = getFile(fileName, directory);

            if (!file.exists()) {
                call.reject("File not found: " + file.getAbsolutePath());
                return;
            }

            // Read file
            StringBuilder content = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                    content.append("\n");
                }
            }

            String fileContent = content.toString();
            logEvent("info", "File read: " + file.getAbsolutePath() + " (" + fileContent.length() + " bytes)");

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("path", file.getAbsolutePath());
            result.put("content", fileContent);
            result.put("size", fileContent.length());
            call.resolve(result);
        } catch (Exception e) {
            logEvent("error", "Error reading file: " + e.getMessage());
            e.printStackTrace();
            call.reject("Error reading file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void listFiles(PluginCall call) {
        String directory = call.getString("directory", "");

        try {
            File dir = getDirectory(directory);

            if (!dir.exists() || !dir.isDirectory()) {
                call.reject("Directory not found: " + dir.getAbsolutePath());
                return;
            }

            File[] files = dir.listFiles();
            List<JSObject> fileList = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    JSObject fileInfo = new JSObject();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("path", file.getAbsolutePath());
                    fileInfo.put("isDirectory", file.isDirectory());
                    fileInfo.put("size", file.length());
                    fileInfo.put("lastModified", file.lastModified());
                    fileList.add(fileInfo);
                }
            }

            JSObject result = new JSObject();
            result.put("directory", dir.getAbsolutePath());
            result.put("files", new JSArray(fileList));
            call.resolve(result);
        } catch (Exception e) {
            logEvent("error", "Error listing files: " + e.getMessage());
            e.printStackTrace();
            call.reject("Error listing files: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        String fileName = call.getString("fileName");
        String directory = call.getString("directory", "");

        if (fileName == null || fileName.isEmpty()) {
            call.reject("fileName is required");
            return;
        }

        try {
            File file = getFile(fileName, directory);

            if (!file.exists()) {
                call.reject("File not found: " + file.getAbsolutePath());
                return;
            }

            boolean deleted = file.delete();
            logEvent("info", "File deleted: " + file.getAbsolutePath() + " - " + deleted);

            JSObject result = new JSObject();
            result.put("success", deleted);
            result.put("path", file.getAbsolutePath());
            call.resolve(result);
        } catch (Exception e) {
            logEvent("error", "Error deleting file: " + e.getMessage());
            e.printStackTrace();
            call.reject("Error deleting file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void createDirectory(PluginCall call) {
        String directory = call.getString("directory", "");

        if (directory == null || directory.isEmpty()) {
            call.reject("directory is required");
            return;
        }

        try {
            File dir = getDirectory(directory);
            boolean created = dir.mkdirs();
            logEvent("info", "Directory created: " + dir.getAbsolutePath() + " - " + created);

            JSObject result = new JSObject();
            result.put("success", created);
            result.put("path", dir.getAbsolutePath());
            call.resolve(result);
        } catch (Exception e) {
            logEvent("error", "Error creating directory: " + e.getMessage());
            e.printStackTrace();
            call.reject("Error creating directory: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getExternalStoragePath(PluginCall call) {
        JSObject result = new JSObject();
        result.put("path", baseStorageDir.getAbsolutePath());
        result.put("canRead", baseStorageDir.canRead());
        result.put("canWrite", baseStorageDir.canWrite());
        result.put("freeSpace", baseStorageDir.getFreeSpace());
        result.put("totalSpace", baseStorageDir.getTotalSpace());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result.put("hasManageStoragePermission", Environment.isExternalStorageManager());
        } else {
            Activity activity = getActivity();
            boolean hasPermission = false;
            if (activity != null) {
                int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                hasPermission = (permission == PackageManager.PERMISSION_GRANTED);
            }
            result.put("hasWriteExternalStoragePermission", hasPermission);
        }

        call.resolve(result);
    }

    private File getFile(String fileName, String directory) {
        File dir = getDirectory(directory);
        return new File(dir, fileName);
    }

    private File getDirectory(String directory) {
        if (directory == null || directory.isEmpty()) {
            return baseStorageDir;
        }

        // Handle absolute paths
        if (directory.startsWith("/")) {
            return new File(directory);
        } else {
            return new File(baseStorageDir, directory);
        }
    }

    private void logEvent(String type, String message) {
        JSObject event = new JSObject();
        try {
            event.put("event", "log");
            event.put("type", type);
            event.put("message", message);
            notifyListeners("fileStorageEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}