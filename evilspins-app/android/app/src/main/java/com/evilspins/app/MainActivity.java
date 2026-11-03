package com.evilspins.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.evilspins.app.permissions.PermissionsPlugin;
import com.evilspins.app.nfc.NfcPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register plugins BEFORE super.onCreate() which calls load()
        registerPlugin(PermissionsPlugin.class);
        registerPlugin(NfcPlugin.class);
        super.onCreate(savedInstanceState);
    }
}