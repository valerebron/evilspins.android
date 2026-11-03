package com.evilspins.app.nfc;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Locale;

@CapacitorPlugin(name = "EvilSpinsNfc")
public class NfcPlugin extends Plugin {

    private NfcAdapter nfcAdapter;
    private boolean isListening = false;
    private NfcAdapter.ReaderCallback readerCallback;

    // Tag removal detection via NfcAdapter.ignore()
    private NfcAdapter.OnTagRemovedListener tagRemovedListener;
    private final Handler removalHandler = new Handler(Looper.getMainLooper());
    private static final int REMOVAL_DEBOUNCE_MS = 500; // Minimum time tag must be out of range

    @Override
    public void load() {
        super.load();
        nfcAdapter = NfcAdapter.getDefaultAdapter(getContext());

        // Create tag removal listener (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tagRemovedListener = new NfcAdapter.OnTagRemovedListener() {
                @Override
                public void onTagRemoved() {
                    removalHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleTagRemoved();
                        }
                    });
                }
            };
        }

        // Create ReaderCallback for Reader Mode (API 19+)
        readerCallback = new NfcAdapter.ReaderCallback() {
            public void onTagDiscovered(Tag tag) {
                if (isListening) {
                    handleTagDiscovered(tag);
                }
            }

            public void onTagLost(Tag tag) {
                if (isListening) {
                    // Fallback - may not fire on all devices
                    handleTagLost(tag);
                }
            }
        };

        // Auto-start listening on load
        startListeningInternal();
    }

    private void startListeningInternal() {
        if (nfcAdapter == null) {
            logEvent("error", "NFC not supported on this device");
            return;
        }

        if (!nfcAdapter.isEnabled()) {
            logEvent("error", "NFC is disabled. Please enable NFC in settings.");
            return;
        }

        isListening = true;

        // Enable Reader Mode (API 19+)
        Activity activity = getActivity();
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int flags = NfcAdapter.FLAG_READER_NFC_A |
                       NfcAdapter.FLAG_READER_NFC_B |
                       NfcAdapter.FLAG_READER_NFC_F |
                       NfcAdapter.FLAG_READER_NFC_V |
                       NfcAdapter.FLAG_READER_NFC_BARCODE |
                       NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
            // NOTE: FLAG_READER_SKIP_NDEF_CHECK removed - it prevents NDEF reading!

            nfcAdapter.enableReaderMode(activity, readerCallback, flags, null);
            logEvent("info", "Reader Mode enabled (API " + Build.VERSION.SDK_INT + ")");
            logEvent("info", "OnTagRemovedListener available: " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N));
            logEvent("info", "Flags: " + flagsToString(flags));
        }
    }

    private void stopListeningInternal() {
        isListening = false;

        if (nfcAdapter != null) {
            Activity activity = getActivity();
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    nfcAdapter.disableReaderMode(activity);
                    logEvent("info", "Reader Mode disabled");
                } catch (IllegalStateException e) {
                    logEvent("warning", "Reader Mode already disabled");
                }
            }
        }
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
    public void isEnabled(PluginCall call) {
        boolean enabled = nfcAdapter != null && nfcAdapter.isEnabled();
        call.resolve(new JSObject().put("enabled", enabled));
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        boolean supported = nfcAdapter != null;
        call.resolve(new JSObject().put("supported", supported));
    }

    @PluginMethod
    public void getAdapterInfo(PluginCall call) {
        JSObject result = new JSObject();
        if (nfcAdapter != null) {
            result.put("supported", true);
            result.put("enabled", nfcAdapter.isEnabled());
            result.put("apiLevel", Build.VERSION.SDK_INT);
            result.put("onTagRemovedListenerSupported", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        } else {
            result.put("supported", false);
        }
        call.resolve(result);
    }

    private void handleTagDiscovered(Tag tag) {
        logEvent("tag_discovered", "=== TAG DISCOVERED ===");

        String tagId = bytesToHex(tag.getId());
        String[] techList = tag.getTechList();

        logEvent("tag_discovered", "Tag ID: " + tagId);
        logEvent("tag_discovered", "Tech List (" + techList.length + "): " + Arrays.toString(techList));

        for (String tech : techList) {
            logEvent("tag_discovered", "  Tech: " + tech);
        }

        // Parse NDEF message from tag
        NdefMessage[] messages = readNdefFromTag(tag);
        if (messages != null && messages.length > 0) {
            logEvent("tag_discovered", "NDEF Messages: " + messages.length);
            for (int i = 0; i < messages.length; i++) {
                NdefMessage msg = messages[i];
                NdefRecord[] records = msg.getRecords();
                logEvent("tag_discovered", "  Message " + i + " - Records: " + records.length);

                for (int j = 0; j < records.length; j++) {
                    NdefRecord record = records[j];
                    logNdefRecord(record, j);
                }
            }
        } else {
            logEvent("tag_discovered", "No NDEF messages found");
        }

        // CRITICAL: Register for tag removal detection using NfcAdapter.ignore() (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                boolean success = nfcAdapter.ignore(tag, REMOVAL_DEBOUNCE_MS, tagRemovedListener, removalHandler);
                logEvent("info", "NfcAdapter.ignore() called: " + (success ? "SUCCESS" : "FAILED"));
                if (!success) {
                    logEvent("warning", "ignore() failed - tag may already be out of range or not supported");
                }
            } catch (Exception e) {
                logEvent("error", "Error calling ignore(): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            logEvent("warning", "OnTagRemovedListener not supported on API " + Build.VERSION.SDK_INT + " (need API 24+)");
        }

        // Emit structured event
        emitStructuredEvent("tag_detected", tagId, techList, messages);
    }

    private void handleTagRemoved() {
        logEvent("tag_removed", "=== TAG REMOVED (via OnTagRemovedListener) ===");

        // Emit structured event
        JSObject event = new JSObject();
        try {
            event.put("event", "tag_removed");
            event.put("source", "OnTagRemovedListener");
            notifyListeners("nfcEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTagLost(Tag tag) {
        String tagId = bytesToHex(tag.getId());
        logEvent("tag_lost", "=== TAG LOST (via ReaderCallback.onTagLost - fallback) ===");
        logEvent("tag_lost", "Tag ID: " + tagId);

        // Emit structured event
        JSObject event = new JSObject();
        try {
            event.put("event", "tag_lost");
            event.put("tagId", tagId);
            event.put("source", "ReaderCallback");
            notifyListeners("nfcEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void emitStructuredEvent(String eventType, String tagId, String[] techList, NdefMessage[] messages) {
        JSObject event = new JSObject();
        try {
            event.put("event", eventType);
            event.put("tagId", tagId);
            event.put("techList", techList != null ? Arrays.toString(techList) : "[]");

            if (messages != null && messages.length > 0) {
                JSONArray recordsArray = new JSONArray();
                for (NdefMessage msg : messages) {
                    for (NdefRecord record : msg.getRecords()) {
                        JSONObject recordObj = parseNdefRecord(record);
                        if (recordObj != null) {
                            recordsArray.put(recordObj);
                        }
                    }
                }
                event.put("ndefRecords", recordsArray);
            } else {
                event.put("ndefRecords", new JSONArray());
            }

            notifyListeners("nfcEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logEvent(String type, String message) {
        JSObject event = new JSObject();
        try {
            event.put("event", "log");
            event.put("type", type);
            event.put("message", message);
            notifyListeners("nfcEvent", event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logNdefRecord(NdefRecord record, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append("    Record ").append(index).append(":");
        sb.append(" TNF=").append(record.getTnf());
        sb.append(" Type=").append(bytesToHex(record.getType()));
        sb.append(" ID=").append(bytesToHex(record.getId()));
        sb.append(" PayloadLen=").append(record.getPayload() != null ? record.getPayload().length : 0);
        sb.append(" PayloadHex=").append(bytesToHex(record.getPayload()));

        String parsed = parsePayload(record);
        if (!parsed.isEmpty()) {
            sb.append(" Parsed=\"").append(parsed.replace("\"", "\\\"")).append("\"");
        }

        logEvent("tag_discovered", sb.toString());
    }

    private NdefMessage[] readNdefFromTag(Tag tag) {
        String[] techList = tag.getTechList();
        logEvent("tag_discovered", "Tech list for NDEF read: " + Arrays.toString(techList));

        // Check if Ndef is in tech list
        boolean hasNdef = false;
        for (String tech : techList) {
            if (tech.contains("Ndef") || tech.contains("ndef")) {
                hasNdef = true;
                break;
            }
        }
        logEvent("tag_discovered", "Has Ndef tech: " + hasNdef);

        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            logEvent("tag_discovered", "Ndef.get(tag) returned null - tag may not support NDEF");
            return null;
        }

        try {
            logEvent("tag_discovered", "Connecting to NDEF...");
            ndef.connect();
            logEvent("tag_discovered", "NDEF connected, reading message...");
            NdefMessage message = ndef.getNdefMessage();
            ndef.close();
            logEvent("tag_discovered", "NDEF message read: " + (message != null ? "SUCCESS" : "NULL"));

            if (message != null) {
                return new NdefMessage[]{message};
            }
        } catch (Exception e) {
            logEvent("tag_discovered", "Error reading NDEF: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject parseNdefRecord(NdefRecord record) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("tnf", record.getTnf());
            obj.put("type", bytesToHex(record.getType()));
            obj.put("id", bytesToHex(record.getId()));
            obj.put("payload", parsePayload(record));
            obj.put("payloadHex", bytesToHex(record.getPayload()));
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String parsePayload(NdefRecord record) {
        byte[] payload = record.getPayload();
        if (payload == null || payload.length == 0) {
            return "";
        }

        int tnf = record.getTnf();

        if (tnf == NdefRecord.TNF_WELL_KNOWN) {
            byte[] type = record.getType();
            if (Arrays.equals(type, NdefRecord.RTD_TEXT)) {
                return parseTextRecord(payload);
            } else if (Arrays.equals(type, NdefRecord.RTD_URI)) {
                return parseUriRecord(payload);
            } else if (Arrays.equals(type, NdefRecord.RTD_SMART_POSTER)) {
                return "Smart Poster";
            }
        } else if (tnf == NdefRecord.TNF_MIME_MEDIA) {
            return "MIME: " + new String(record.getType());
        } else if (tnf == NdefRecord.TNF_ABSOLUTE_URI) {
            try {
                return new String(payload, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return bytesToHex(payload);
            }
        } else if (tnf == NdefRecord.TNF_EXTERNAL_TYPE) {
            return "External: " + new String(record.getType());
        }

        try {
            return new String(payload, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return bytesToHex(payload);
        }
    }

    private String parseTextRecord(byte[] payload) {
        if (payload.length < 1) return "";

        int status = payload[0] & 0xFF;
        boolean isUtf16 = (status & 0x80) != 0;
        int langCodeLen = status & 0x3F;

        if (payload.length < 1 + langCodeLen) return "";

        byte[] textBytes = Arrays.copyOfRange(payload, 1 + langCodeLen, payload.length);

        try {
            if (isUtf16) {
                return new String(textBytes, "UTF-16");
            } else {
                return new String(textBytes, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            return bytesToHex(textBytes);
        }
    }

    private String parseUriRecord(byte[] payload) {
        if (payload.length < 1) return "";

        int uriCode = payload[0] & 0xFF;
        String prefix = getUriPrefix(uriCode);

        try {
            String uri = new String(Arrays.copyOfRange(payload, 1, payload.length), "UTF-8");
            return prefix + uri;
        } catch (UnsupportedEncodingException e) {
            return bytesToHex(payload);
        }
    }

    private String getUriPrefix(int code) {
        switch (code) {
            case 0x00: return "";
            case 0x01: return "http://www.";
            case 0x02: return "https://www.";
            case 0x03: return "http://";
            case 0x04: return "https://";
            case 0x05: return "tel:";
            case 0x06: return "mailto:";
            case 0x07: return "ftp://anonymous:anonymous@";
            case 0x08: return "ftp://ftp.";
            case 0x09: return "ftps://";
            case 0x0A: return "sftp://";
            case 0x0B: return "smb://";
            case 0x0C: return "nfs://";
            case 0x0D: return "ftp://";
            case 0x0E: return "dav://";
            case 0x0F: return "news:";
            case 0x10: return "telnet://";
            case 0x11: return "imap:";
            case 0x12: return "rtsp://";
            case 0x13: return "urn:";
            case 0x14: return "pop:";
            case 0x15: return "sip:";
            case 0x16: return "sips:";
            case 0x17: return "tftp:";
            case 0x18: return "btspp://";
            case 0x19: return "btl2cap://";
            case 0x1A: return "btgoep://";
            case 0x1B: return "tcpobex://";
            case 0x1C: return "irdaobex://";
            case 0x1D: return "file://";
            case 0x1E: return "urn:epc:id:";
            case 0x1F: return "urn:epc:tag:";
            case 0x20: return "urn:epc:pat:";
            case 0x21: return "urn:epc:raw:";
            case 0x22: return "urn:epc:";
            case 0x23: return "urn:nfc:";
            default: return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }

    private String flagsToString(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & NfcAdapter.FLAG_READER_NFC_A) != 0) sb.append("NFC_A ");
        if ((flags & NfcAdapter.FLAG_READER_NFC_B) != 0) sb.append("NFC_B ");
        if ((flags & NfcAdapter.FLAG_READER_NFC_F) != 0) sb.append("NFC_F ");
        if ((flags & NfcAdapter.FLAG_READER_NFC_V) != 0) sb.append("NFC_V ");
        if ((flags & NfcAdapter.FLAG_READER_NFC_BARCODE) != 0) sb.append("BARCODE ");
        if ((flags & NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK) != 0) sb.append("SKIP_NDEF_CHECK ");
        if ((flags & NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS) != 0) sb.append("NO_PLATFORM_SOUNDS ");
        return sb.toString().trim();
    }
}