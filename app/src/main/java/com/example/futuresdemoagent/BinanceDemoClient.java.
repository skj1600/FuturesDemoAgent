
package com.example.futuresdemoagent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class BinanceDemoClient {

    private static final String BASE_URL =
            "https://demo-fapi.binance.com";

    private final String apiKey;
    private final String apiSecret;

    public BinanceDemoClient(String apiKey, String apiSecret) {
        if (apiKey == null || apiSecret == null
                || apiKey.trim().isEmpty()
                || apiSecret.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Demo API credentials are required");
        }

        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public JSONObject getAccount() throws Exception {
        return signedGet("/fapi/v2/account");
    }

    public JSONArray getPositions() throws Exception {
        JSONObject account = getAccount();
        return account.optJSONArray("positions") == null
                ? new JSONArray()
                : account.getJSONArray("positions");
    }

    private JSONObject signedGet(String path) throws Exception {
        long timestamp = System.currentTimeMillis();

        String query = "timestamp=" + timestamp
                + "&recvWindow=5000";

        String signature = sign(query);
        String address = BASE_URL + path
                + "?" + query
                + "&signature=" + signature;

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("X-MBX-APIKEY", apiKey);

        try {
            int code = connection.getResponseCode();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            code >= 200 && code < 300
                                    ? connection.getInputStream()
                                    : connection.getErrorStream(),
                            StandardCharsets.UTF_8));

            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            reader.close();

            if (code < 200 || code >= 300) {
                throw new Exception(
                        "Demo API HTTP " + code + ": " + body);
            }

            return new JSONObject(body.toString());

        } finally {
            connection.disconnect();
        }
    }

    private String sign(String query) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec key = new SecretKeySpec(
                apiSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");

        mac.init(key);

        byte[] bytes = mac.doFinal(
                query.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();

        for (byte b : bytes) {
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }

        return hex.toString();
    }
}
