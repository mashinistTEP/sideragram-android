package org.sideragram.fork;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Мини-клиент нашего API (api.php).
 *
 * Специально на голом HttpURLConnection + org.json — без внешних библиотек,
 * чтобы ничего не конфликтовало с зависимостями Telegram.
 *
 * Все вызовы асинхронные: результат приходит в UI-потоке.
 */
public class ForkApi {

    public interface Callback {
        /** Ровно одно из двух: data != null (успех) либо error != null. */
        void onResult(JSONObject data, String error);
    }

    public static void call(final String action, final JSONObject params, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject data = null;
                String error = null;
                try {
                    data = callSync(action, params);
                    if (!data.optBoolean("ok", false)) {
                        error = data.optString("error", "Ошибка сервера");
                        data = null;
                    }
                } catch (Throwable e) {
                    String msg = e.getMessage();
                    error = e.getClass().getSimpleName() + (msg != null && msg.length() > 0 ? ": " + msg : "");
                }
                final JSONObject resultData = data;
                final String resultError = error;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(resultData, resultError);
                    }
                });
            }
        }).start();
    }

    /** Синхронный вызов — только из фонового потока. */
    public static JSONObject callSync(String action, JSONObject params) throws Exception {
        String urlStr = ForkSession.baseUrl() + "/api.php?a=" + action;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            String token = ForkSession.token();
            if (token != null && token.length() > 0) {
                conn.setRequestProperty("X-Fork-Token", token);
            }
            byte[] payload = (params == null ? "{}" : params.toString()).getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
            }
            String text = sb.toString().trim();
            if (text.length() == 0) {
                throw new Exception("пустой ответ (HTTP " + code + ")");
            }
            return new JSONObject(text);
        } finally {
            conn.disconnect();
        }
    }
}
