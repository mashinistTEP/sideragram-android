package org.sideragram.fork;

import android.content.Context;

/**
 * Точка подмены оригинальных экранов отправки подарков Telegram.
 *
 * В конструкторах StarGiftSheet / GiftSheet / SendGiftSheet (наши врезки в install.sh)
 * первым делом вызывается showGiftSheet(...): если устройство привязано к нашему
 * серверу — показываем нашу копию экрана отправки и возвращаем true
 * (оригинальный sheet после этого не показывается, его show() — no-op).
 * Если привязки нет — возвращаем false, и Telegram работает как обычно.
 */
public class ForkGiftRouter {

    private static long lastShownAt;

    public static boolean showGiftSheet(Context context, int currentAccount, long dialogId) {
        if (!ForkSession.isLinked()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastShownAt < 1500) {
            // повторный вход из конструктора-делегата: лист уже показан
            return true;
        }
        lastShownAt = now;
        try {
            new ForkGiftSheet(context, dialogId).show();
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

    /** Имя пользователя Telegram по dialogId, если оно есть в кэше клиента. */
    public static String usernameForDialogId(int currentAccount, long dialogId) {
        if (dialogId <= 0) {
            return "";
        }
        try {
            org.telegram.tgnet.TLRPC.User user =
                    org.telegram.messenger.MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user != null && user.username != null) {
                return user.username;
            }
        } catch (Throwable ignored) {
            // без кэша — пользователь впишет юзернейм сам
        }
        return "";
    }
}
