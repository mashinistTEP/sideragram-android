package org.sideragram.fork;

import org.json.JSONObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.tl.TL_stars;

/**
 * Кэш баланса звёзд НАШЕГО сервера.
 * Оригинальный экран звёзд Telegram показывает именно этот баланс:
 * ForkBalance.wrap() подставляет наше значение в объект Telegram,
 * а refresh() обновляет кэш и дёргает родное уведомление starBalanceUpdated,
 * чтобы экран перерисовался сам.
 */
public class ForkBalance {

    private static long cached = -1;

    public static void set(long amount) {
        cached = amount;
    }

    public static long get() {
        return cached;
    }

    public static TL_stars.StarsAmount wrap(TL_stars.StarsAmount amount) {
        if (amount != null && ForkSession.isLinked() && cached >= 0) {
            try {
                java.lang.reflect.Field f = amount.getClass().getField("amount");
                f.setLong(amount, cached);
            } catch (Throwable ignore) {
            }
        }
        return amount;
    }

    public static void refresh(final int account) {
        if (!ForkSession.isLinked()) {
            return;
        }
        ForkApi.call("app_balance", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null || data == null) {
                    return;
                }
                set(data.optLong("balance", 0));
                try {
                    NotificationCenter.getInstance(account)
                            .postNotificationName(NotificationCenter.starBalanceUpdated);
                } catch (Throwable ignore) {
                }
            }
        });
    }
}
