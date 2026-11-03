package com.evilspins.app.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "EvilSpinsLightSensor")
public class LightSensorPlugin extends Plugin implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean isListening = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long LOG_INTERVAL_MS = 1000; // Log every 1 second

    private Runnable loggingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isListening) {
                logCurrentLux();
                handler.postDelayed(this, LOG_INTERVAL_MS);
            }
        }
    };

    @Override
    public void load() {
        super.load();
        sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // Auto-start listening on load (like NFC)
        startListeningInternal();
    }

    private void startListeningInternal() {
        if (lightSensor == null) {
            logEvent("error", "Light sensor not available on this device");
            return;
        }

        isListening = true;
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);

        // Start periodic logging
        handler.post(loggingRunnable);

        logEvent("info", "Light sensor listener auto-started (SENSOR_DELAY_NORMAL)");
    }

    private void stopListeningInternal() {
        isListening = false;
        handler.removeCallbacks(loggingRunnable);

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }

        logEvent("info", "Light sensor listener stopped");
    }

    @PluginMethod
    public void startListening(PluginCall call) {
        startListeningInternal();
        call.resolve(new JSObject().put("listening", true));
    }

    @PluginMethod
    public void stopListening(PluginCall call) {
        stopListeningInternal();
        call.resolve(new JSObject().put("listening", false));
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        boolean supported = lightSensor != null;
        call.resolve(new JSObject().put("supported", supported));
    }

    @PluginMethod
    public void getCurrentLux(PluginCall call) {
        // This will be called when we have a sensor event
        // For now, return the last known value or 0
        call.resolve(new JSObject().put("lux", 0).put("timestamp", System.currentTimeMillis()));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            logEvent("lux", String.format("LUX: %.2f", lux));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        String accuracyStr;
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_UNRELIABLE:
                accuracyStr = "UNRELIABLE";
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                accuracyStr = "LOW";
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                accuracyStr = "MEDIUM";
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                accuracyStr = "HIGH";
                break;
            default:
                accuracyStr = "UNKNOWN(" + accuracy + ")";
        }
        logEvent("lux", "Light sensor accuracy changed: " + accuracyStr);
    }

    private void logCurrentLux() {
        // The actual lux value is received in onSensorChanged
        // This is just to keep the periodic logging going
    }

    private void logEvent(String type, String message) {
        JSObject event = new JSObject();
        try {
            event.put("event", "log");
            event.put("type", type);
            event.put("message", message);
            notifyListeners("lightSensorEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}