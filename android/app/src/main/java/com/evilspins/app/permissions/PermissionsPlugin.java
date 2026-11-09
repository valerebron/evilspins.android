package com.evilspins.app.permissions;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import android.Manifest;
import android.content.pm.PackageManager;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CapacitorPlugin(
    name = "EvilSpinsPermissions",
    permissions = {
        @Permission(strings = { Manifest.permission.NFC }, alias = "nfc"),
        @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera"),
        @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "readStorage"),
        @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "writeStorage"),
    }
)
public class PermissionsPlugin extends Plugin {

    private static final Map<String, String> PERMISSION_ALIASES = new HashMap<String, String>() {{
        put("nfc", Manifest.permission.NFC);
        put("camera", Manifest.permission.CAMERA);
        put("readStorage", Manifest.permission.READ_EXTERNAL_STORAGE);
        put("writeStorage", Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }};

    @PluginMethod
    public void query(PluginCall call) {
        JSObject result = new JSObject();
        JSArray permissionsArray = call.getArray("permissions");

        if (permissionsArray == null || permissionsArray.length() == 0) {
            call.reject("No permissions provided");
            return;
        }

        for (int i = 0; i < permissionsArray.length(); i++) {
            String perm;
            try {
                perm = permissionsArray.getString(i);
            } catch (JSONException e) {
                continue;
            }
            String androidPerm = PERMISSION_ALIASES.get(perm);
            if (androidPerm != null) {
                int checkResult = getContext().checkSelfPermission(androidPerm);
                String state = (checkResult == PackageManager.PERMISSION_GRANTED) ? "granted" : "denied";
                result.put(perm, state);
            } else {
                result.put(perm, "unknown");
            }
        }
        call.resolve(result);
    }

    @PluginMethod
    public void request(PluginCall call) {
        JSArray permissionsArray = call.getArray("permissions");

        if (permissionsArray == null || permissionsArray.length() == 0) {
            call.reject("No permissions provided");
            return;
        }

        List<String> androidPerms = new ArrayList<>();
        for (int i = 0; i < permissionsArray.length(); i++) {
            String perm;
            try {
                perm = permissionsArray.getString(i);
            } catch (JSONException e) {
                continue;
            }
            String androidPerm = PERMISSION_ALIASES.get(perm);
            if (androidPerm != null) {
                androidPerms.add(androidPerm);
            }
        }

        if (androidPerms.isEmpty()) {
            call.reject("No valid permissions to request");
            return;
        }

        // Save the call to handle the callback
        saveCall(call);

        // Request permissions using Activity
        String[] permArray = androidPerms.toArray(new String[0]);
        getActivity().requestPermissions(permArray, 1001);
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        PluginCall call = getSavedCall();
        if (call == null) {
            super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        JSObject result = new JSObject();
        for (int i = 0; i < permissions.length; i++) {
            String perm = permissions[i];
            String alias = getAliasForPermission(perm);
            if (alias != null) {
                String state = (grantResults[i] == PackageManager.PERMISSION_GRANTED) ? "granted" : "denied";
                result.put(alias, state);
            }
        }
        call.resolve(result);
    }

    private String getAliasForPermission(String androidPerm) {
        for (Map.Entry<String, String> entry : PERMISSION_ALIASES.entrySet()) {
            if (entry.getValue().equals(androidPerm)) {
                return entry.getKey();
            }
        }
        return null;
    }
}