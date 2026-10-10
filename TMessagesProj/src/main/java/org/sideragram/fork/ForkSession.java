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

    /**
     * Приводит адрес к рабочему виду: добавляет схему, если её нет, убирает слэши на конце.
     * Старый https-адрес подарка AwardSpace заменяет на http: у бесплатного поддомена
     * нет доверенного сертификата, Android отвергает такое соединение (SSLHandshakeException).
     */
    private static String fixUrl(String url) {
        if (url == null) {
            url = "";
        }
        url = url.trim();
        if (url.length() == 0) {
            return ForkConfig.DEFAULT_BASE_URL;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if ("https://sideragram.atwebpages.com".equals(url)) {
            url = "http://sideragram.atwebpages.com";
        }
        return url;
    }

    /** Адрес сервера без слэша на конце. */
    public static String baseUrl() {
        return fixUrl(prefs().getString("base_url", ForkConfig.DEFAULT_BASE_URL));
    }

    public static void setBaseUrl(String url) {
        prefs().edit().putString("base_url", fixUrl(url)).apply();
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
    public static final String OWNER_USERNAME = "mashinist_TEP70BS_145";

    public static boolean isAdmin() {
        if (prefs().getBoolean("is_admin", false)) {
            return true;
        }
        return isOwner();
    }

    /** Админ-панель — отдельный пункт меню только для владельца форка. */
    public static boolean isOwner() {
        try {
            org.telegram.tgnet.TLRPC.User me = org.telegram.messenger.UserConfig
                    .getInstance(org.telegram.messenger.UserConfig.selectedAccount)
                    .getCurrentUser();
            String u = (me == null || me.username == null) ? "" : me.username;
            if (u.startsWith("@")) {
                u = u.substring(1);
            }
            return OWNER_USERNAME.equalsIgnoreCase(u);
        } catch (Throwable ignore) {
            return false;
        }
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
