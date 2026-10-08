package org.sideragram.fork;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Привязка устройства к нашей части сервиса.
 *
 * Здесь лежит только наш токен, адрес сервера и признак «есть права администратора» —
 * ни логин Telegram, ни его сессии мы не трогаем: они остаются полностью на серверах Telegram.
 */
public class ForkSession {

    private static final String PREFS = "sideragram_prefs";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Адрес сервера без слэша на конце. */
    public static String baseUrl() {
        String url = prefs().getString("base_url", ForkConfig.DEFAULT_BASE_URL);
        if (url == null || url.trim().length() == 0) {
            url = ForkConfig.DEFAULT_BASE_URL;
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static void setBaseUrl(String url) {
        prefs().edit().putString("base_url", url == null ? "" : url.trim()).apply();
    }

    /** Наш токен (пусто, если привязки ещё не было). */
    public static String token() {
        return prefs().getString("token", null);
    }

    public static boolean isLinked() {
        String t = token();
        return t != null && t.length() > 0;
    }

    public static String linkedName() {
        String n = prefs().getString("linked_name", "");
        return n == null ? "" : n;
    }

    /**
     * Права администратора приходят с нашего сервера и зависят от Telegram-юзернейма:
     * если он указан в списке админов, сервер помечает аккаунт как администратора.
     */
    public static boolean isAdmin() {
        return prefs().getBoolean("is_admin", false);
    }

    public static void setAdmin(boolean admin) {
        prefs().edit().putBoolean("is_admin", admin).apply();
    }

    public static void saveLink(String token, String name) {
        prefs().edit()
                .putString("token", token)
                .putString("linked_name", name == null ? "" : name)
                .apply();
    }

    public static void unlink() {
        prefs().edit().remove("token").remove("linked_name").remove("is_admin").apply();
    }
}
