package org.sideragram.fork;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Stars.StarsController;

/**
 * Синхронизация подарков с обычным Telegram — ВЕСЬ каталог сразу.
 * Приложение дёргает родной StarsController.loadStarGifts(): Telegram отдаёт
 * полный список своих подарков (публичное поле gifts). Мы читаем его и отправляем
 * снимок на наш сервер (app_sync_catalog). Сервер заменяет заглушки живым каталогом,
 * и экран отправки показывает все настоящие подарки Telegram.
 * Поля подарков читаются отражением, чтобы не зависеть от версии TL-классов.
 */
public class ForkGiftSync {

    private static boolean busy = false;
    private static long lastPush = 0;
    private static final long COOLDOWN = 5 * 60 * 1000;

    /** whenDone вызывается ТОЛЬКО если снимок успешно ушёл на сервер. */
    public static void harvest(final int account, long dialogId, final Runnable whenDone) {
        if (busy || !ForkSession.isLinked() || System.currentTimeMillis() - lastPush < COOLDOWN) {
            return;
        }
        try {
            final StarsController controller = StarsController.getInstance(account);
            busy = true;
            controller.loadStarGifts();
            poll(controller, 0, whenDone);
        } catch (Throwable ignore) {
            busy = false;
        }
    }

    private static void poll(final StarsController controller, final int attempt, final Runnable whenDone) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int size = 0;
                try {
                    size = controller.gifts == null ? 0 : controller.gifts.size();
                } catch (Throwable ignore) {
                }
                if (size <= 0 && attempt < 8) {
                    if (attempt == 3) {
                        try {
                            controller.loadStarGifts();
                        } catch (Throwable ignore) {
                        }
                    }
                    poll(controller, attempt + 1, whenDone);
                    return;
                }
                push(controller, size, whenDone);
            }
        }, 900);
    }

    private static void push(StarsController controller, int size, final Runnable whenDone) {
        JSONArray items = new JSONArray();
        for (int i = 0; i < size; i++) {
            try {
                Object gift = controller.gifts.get(i);
                if (gift == null) {
                    continue;
                }
                long id = longField(gift, "id");
                if (id <= 0) {
                    continue;
                }
                JSONObject it = new JSONObject();
                it.put("id", id);
                String title = stringField(gift, "title");
                it.put("title", title.length() > 0 ? title : "Подарок Telegram");
                it.put("emoji", "\uD83C\uDF81");
                it.put("stars", longField(gift, "stars"));
                boolean unique = gift.getClass().getName().contains("Unique");
                it.put("kind", unique ? "unique" : (boolField(gift, "limited") ? "limited" : "regular"));
                int total = intField(gift, "availability_total");
                if (total > 0) {
                    it.put("total", total);
                    it.put("remains", intField(gift, "availability_remains"));
                }
                if (boolField(gift, "sold_out")) {
                    it.put("remains", 0);
                }
                items.put(it);
            } catch (Throwable ignore) {
            }
        }
        if (items.length() == 0) {
            busy = false;
            lastPush = System.currentTimeMillis();
            return;
        }
        JSONObject req = new JSONObject();
        try {
            req.put("items", items);
        } catch (Throwable ignore) {
        }
        ForkApi.call("app_sync_catalog", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                busy = false;
                lastPush = System.currentTimeMillis();
                if (error == null && whenDone != null) {
                    whenDone.run();
                }
            }
        });
    }

    private static Object field(Object o, String name) {
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static long longField(Object o, String name) {
        Object v = field(o, name);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static int intField(Object o, String name) {
        Object v = field(o, name);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static String stringField(Object o, String name) {
        Object v = field(o, name);
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean boolField(Object o, String name) {
        Object v = field(o, name);
        return v instanceof Boolean && (Boolean) v;
    }
}
