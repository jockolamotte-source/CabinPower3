package com.cabinpower.solar3;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Only requests the two observed, read-only Enerwise endpoints. */
final class EnerwiseClient {
    static final String HOST = "https://cloud.ktechenergy.com:1443";
    static final String STATION_ID = "9551630424543290018";
    static final String DTU_ID = "312201012144251495";
    private String accessToken;

    static final class ApiError extends IOException {
        final int code;
        ApiError(int code) { super("HTTP " + code); this.code = code; }
    }

    private String read(String route, String method, JSONObject body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(HOST + route).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(9000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CabinPowerAndroid/3.0");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) throw new ApiError(code);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 2_000_000) throw new IOException("Oversize response");
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            }
        } finally { conn.disconnect(); }
    }

    void login(String user, String password) throws Exception {
        JSONObject params = new JSONObject();
        params.put("username", user);
        params.put("password", password);
        JSONObject response = new JSONObject(read("/requestToken", "POST", params, null));
        String t = response.optString("accessToken", "");
        if (t.length() < 10) throw new IOException("Enerwise did not return an access token");
        accessToken = t; // deliberately not persisted
    }

    JSONObject fetch(String user, String password) throws Exception {
        if (accessToken == null) login(user, password);
        JSONArray records;
        try {
            records = new JSONArray(read("/station/" + STATION_ID + "/ieds", "GET", null, accessToken));
        } catch (ApiError e) {
            if (e.code != 401) throw e;
            login(user, password);
            records = new JSONArray(read("/station/" + STATION_ID + "/ieds", "GET", null, accessToken));
        }
        JSONObject selected = null;
        for (int i = 0; i < records.length() && selected == null; i++) {
            JSONObject station = records.optJSONObject(i);
            if (station == null || !STATION_ID.equals(station.optString("ResourceId"))) continue;
            JSONArray devices = station.optJSONArray("Dtus");
            if (devices == null) continue;
            for (int j = 0; j < devices.length(); j++) {
                JSONObject device = devices.optJSONObject(j);
                if (device != null && DTU_ID.equals(device.optString("ResourceId"))) {
                    selected = device; break;
                }
            }
        }
        if (selected == null) throw new IOException("Configured cabin inverter not returned by Enerwise");
        JSONObject vars = selected.optJSONObject("vars");
        if (vars == null) throw new IOException("Inverter reported no telemetry");
        Object raw = vars.opt("MessageData");
        JSONObject values;
        if (raw instanceof JSONObject) values = (JSONObject) raw;
        else if (raw instanceof String) values = new JSONObject((String) raw);
        else throw new IOException("Missing MessageData");
        JSONObject output = new JSONObject();
        output.put("station", "Cabin");
        output.put("state", selected.optString("State", "unknown"));
        output.put("sampleTime", vars.optString("Date_Time", ""));
        output.put("receivedAtMs", System.currentTimeMillis());
        output.put("values", values);
        return output;
    }

    static String safeError(Exception e) {
        if (e instanceof ApiError) {
            int c = ((ApiError) e).code;
            if (c == 401) return "Enerwise returned 401. Check your saved login.";
            if (c == 429) return "Enerwise rate limit reached; monitoring will retry later.";
            return "Enerwise HTTP " + c;
        }
        if (e instanceof java.net.UnknownHostException) return "Cannot reach Enerwise. Check network connectivity.";
        if (e instanceof java.net.SocketTimeoutException) return "Enerwise connection timed out.";
        return "Unable to read inverter: " + e.getClass().getSimpleName();
    }
}
