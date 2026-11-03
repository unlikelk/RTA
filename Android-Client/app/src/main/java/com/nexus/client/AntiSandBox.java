package com.nexus.client;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import java.io.File;

public class AntiSandbox {
    public static boolean isCompromised(Context context) {
        return isEmulator() || isDebugged() || isRooted() || isFridaRunning();
    }

    private static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private static boolean isDebugged() {
        return android.os.Debug.isDebuggerConnected();
    }

    private static boolean isRooted() {
        String[] paths = {"/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/su/bin/su"};
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    private static boolean isFridaRunning() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/self/maps"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("frida") || line.contains("gum-js-loop") || line.contains("gmain")) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (Exception e) {}
        return false;
    }
}