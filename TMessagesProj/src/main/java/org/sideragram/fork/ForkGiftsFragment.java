package org.sideragram.fork;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Экран подарков форка: витрина наших подарков, отправка с «островком»-предупреждением,
 * полученные подарки (сначала наши, затем «Подарки Telegram» из зеркала синхронизации)
 * и само зеркало каталога Telegram.
 */
public class ForkGiftsFragment extends BaseFragment {

    private FrameLayout root;
    private LinearLayout content;
    private TextView balanceView;
    private EditText recipientField;
    private EditText messageField;
    private TextView selectedView;
    private String selectedCode = "";
    private String selectedTitle = "";
    private String warningText = "";
    private boolean busy;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(loc(R.string.SideragramGiftsScreenTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(10);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        refresh();
        return fragmentView;
    }

    // ------------------------------------------------------------- загрузка
    public void refresh() {
        if (!ForkSession.isLinked()) {
            content.removeAllViews();
            content.addView(plainText(loc(R.string.SideragramStatusNotLinked)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        ForkApi.call("app_gifts", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    island(ForkIslandView.TYPE_DANGER, error, null);
                    return;
                }
                warningText = data.optString("warning", "");
                render(data);
                int news = countNew(data.optJSONArray("received")) + countNew(data.optJSONArray("tg_received"));
                if (news > 0) {
                    island(ForkIslandView.TYPE_OK,
                            loc(R.string.SideragramGiftsIslandNew, news), null);
                    ForkApi.call("app_gifts_seen", new JSONObject(), new ForkApi.Callback() {
                        @Override
                        public void onResult(JSONObject d2, String e2) {
                            // просто отмечаем просмотренными
                        }
                    });
                }
            }
        });
        ForkApi.call("telegram_catalog", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error == null && content != null) {
                    renderTgCatalog(data.optJSONArray("catalog"));
                }
            }
        });
    }

    private int countNew(JSONArray arr) {
        int n = 0;
        if (arr == null) {
            return 0;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optInt("is_new", 0) == 1) {
                n++;
            }
        }
        return n;
    }

    // -------------------------------------------------------------- отрисовка
    private void render(JSONObject data) {
        content.removeAllViews();

        LinearLayout head = card();
        balanceView = caption(R.string.SideragramBalanceTitle, head);
        balanceView.setText(loc(R.string.SideragramGiftsBalanceLine, data.optInt("balance", 0)));
        content.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));

        LinearLayout sendCard = card();
        caption(R.string.SideragramGiftsSendTitle, sendCard);
        recipientField = fieldInput(R.string.SideragramGiftsRecipient);
        recipientField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        sendCard.addView(recipientField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 6, 0, 0));
        messageField = fieldInput(R.string.SideragramGiftsMessage);
        messageField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        sendCard.addView(messageField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 6, 0, 0));
        selectedView = plainText(loc(R.string.SideragramGiftsNothingSelected));
        sendCard.addView(selectedView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0, 0));
        TextView sendNow = makeButton(loc(R.string.SideragramGiftsSendNow));
        sendNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trySend();
            }
        });
        sendCard.addView(sendNow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0, 0, 8, 0, 0));
        content.addView(sendCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));

        LinearLayout shop = card();
        caption(R.string.SideragramGiftsTitle, shop);
        JSONArray catalog = data.optJSONArray("catalog");
        if (catalog == null || catalog.length() == 0) {
            shop.addView(plainText(loc(R.string.SideragramGiftsEmpty)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            for (int i = 0; i < catalog.length(); i++) {
                final JSONObject g = catalog.optJSONObject(i);
                if (g == null) {
                    continue;
                }
                shop.addView(giftRow(g), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
        }
        content.addView(shop, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));

        LinearLayout got = card();
        caption(R.string.SideragramGiftsReceived, got);
        addReceived(got, data.optJSONArray("received"), true);
        content.addView(got, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));

        LinearLayout tgGot = card();
        caption(R.string.SideragramGiftsTgReceived, tgGot);
        addReceived(tgGot, data.optJSONArray("tg_received"), false);
        content.addView(tgGot, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));

        LinearLayout tgCat = card();
        tgCat.setTag("tgcat");
        caption(R.string.SideragramGiftsTgCatalog, tgCat);
        tgCat.addView(plainText(loc(R.string.SideragramGiftsTgHint)),
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(tgCat, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void renderTgCatalog(JSONArray catalog) {
        View v = content.findViewWithTag("tgcat");
        if (!(v instanceof LinearLayout)) {
            return;
        }
        LinearLayout box = (LinearLayout) v;
        if (catalog == null || catalog.length() == 0) {
            box.addView(plainText(loc(R.string.SideragramGiftsTgEmpty)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        int limit = Math.min(catalog.length(), 15);
        for (int i = 0; i < limit; i++) {
            JSONObject g = catalog.optJSONObject(i);
            if (g == null) {
                continue;
            }
            box.addView(row(
                    g.optString("emoji", "🎁"),
                    g.optString("title"),
                    g.optInt("price", 0) + " ⭐ · " + kindLabel(g.optString("kind", "regular")),
                    null), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private String kindLabel(String kind) {
        if ("limited".equals(kind)) {
            return loc(R.string.SideragramGiftsKindLimited);
        }
        if ("unique".equals(kind)) {
            return loc(R.string.SideragramGiftsKindUnique);
        }
        return loc(R.string.SideragramGiftsKindRegular);
    }

    private View giftRow(final JSONObject g) {
        final String code = g.optString("code", "");
        final String title = g.optString("title", "");
        String extra = g.optInt("price", 0) + " ⭐";
        if (g.optInt("is_nft", 0) == 1) {
            extra += " · NFT";
        } else if (g.optString("rarity", "").length() > 0) {
            extra += " · " + g.optString("rarity");
        }
        TextView pick = makeButton(loc(R.string.SideragramGiftsSend));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCode = code;
                selectedTitle = title;
                if (selectedView != null) {
                    selectedView.setText(loc(R.string.SideragramGiftsSelected, title));
                }
            }
        });
        return row(g.optString("emoji", "🎁"), title, extra, pick);
    }

    private void addReceived(LinearLayout box, JSONArray arr, boolean withSender) {
        if (arr == null || arr.length() == 0) {
            box.addView(plainText(loc(R.string.SideragramGiftsEmpty)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject g = arr.optJSONObject(i);
            if (g == null) {
                continue;
            }
            String extra = g.optString("date", "");
            String sender = g.optString("sender_name", "");
            if (withSender && sender.length() > 0) {
                extra = loc(R.string.SideragramGiftsFrom, sender) + " · " + extra;
            }
            if (g.optInt("is_new", 0) == 1) {
                extra += " · " + loc(R.string.SideragramGiftsNew);
            }
            box.addView(row(g.optString("emoji", "🎁"), g.optString("title", ""), extra, null),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    // -------------------------------------------------------------- отправка
    public void trySend() {
        if (busy) {
            return;
        }
        if (selectedCode.length() == 0) {
            island(ForkIslandView.TYPE_WARN, loc(R.string.SideragramGiftsPickFirst), null);
            return;
        }
        final String recipient = recipientField.getText().toString().trim();
        if (recipient.length() == 0) {
            island(ForkIslandView.TYPE_WARN, loc(R.string.SideragramGiftsNoRecipient), null);
            return;
        }
        busy = true;
        JSONObject req = new JSONObject();
        try {
            req.put("username", recipient);
        } catch (Exception ignore) {
        }
        ForkApi.call("app_resolve", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                busy = false;
                if (error != null) {
                    island(ForkIslandView.TYPE_DANGER, error, null);
                    return;
                }
                if (!data.optBoolean("linked", false)) {
                    island(ForkIslandView.TYPE_DANGER, loc(R.string.SideragramGiftsNotLinkedUser), null);
                    return;
                }
                if (data.optBoolean("is_self", false)) {
                    island(ForkIslandView.TYPE_WARN, loc(R.string.SideragramGiftsSelf), null);
                    return;
                }
                String warn = warningText.length() > 0
                        ? warningText.replace("{name}", "Sideragram")
                        : loc(R.string.SideragramGiftsWarnDefault);
                island(ForkIslandView.TYPE_WARN, warn, new ForkIslandView.Confirm() {
                    @Override
                    public void onConfirm() {
                        doSend(recipient);
                    }

                    @Override
                    public void onCancel() {
                        // пользователь передумал
                    }
                });
            }
        });
    }

    private void doSend(String recipient) {
        busy = true;
        JSONObject req = new JSONObject();
        try {
            req.put("code", selectedCode);
            req.put("recipient_username", recipient);
            req.put("message", messageField.getText().toString().trim());
        } catch (Exception ignore) {
        }
        ForkApi.call("app_send_gift", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                busy = false;
                if (error != null) {
                    if ("RECIPIENT_NOT_LINKED".equals(error)) {
                        island(ForkIslandView.TYPE_DANGER, loc(R.string.SideragramGiftsNotLinkedUser), null);
                    } else {
                        island(ForkIslandView.TYPE_DANGER, error, null);
                    }
                    return;
                }
                island(ForkIslandView.TYPE_OK, loc(R.string.SideragramGiftsSent, selectedTitle), null);
                refresh();
            }
        });
    }

    // --------------------------------------------------------------- виджеты
    private void island(String type, String text, ForkIslandView.Confirm confirm) {
        ForkIslandView.show(root, type, text, confirm);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(root.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int p = AndroidUtilities.dp(12);
        box.setPadding(p, p, p, p);
        box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                getThemedColor(Theme.key_windowBackgroundWhite)));
        return box;
    }

    private TextView caption(int stringRes, LinearLayout box) {
        TextView t = new TextView(root.getContext());
        t.setText(loc(stringRes));
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        box.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 6));
        return t;
    }

    private TextView plainText(String text) {
        TextView t = new TextView(root.getContext());
        t.setText(text);
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        return t;
    }

    private EditText fieldInput(int hintRes) {
        EditText field = new EditText(root.getContext());
        field.setHint(loc(hintRes));
        field.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        field.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        field.setBackground(null);
        return field;
    }

    private TextView makeButton(String text) {
        TextView v = new TextView(root.getContext());
        v.setText(text);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                getThemedColor(Theme.key_featuredStickers_addButton)));
        return v;
    }

    private View row(String emoji, String title, String extra, TextView action) {
        LinearLayout line = new LinearLayout(root.getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));

        TextView e = new TextView(root.getContext());
        e.setText(emoji);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        line.addView(e, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0));

        LinearLayout texts = new LinearLayout(root.getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(root.getContext());
        t1.setText(title);
        t1.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        t1.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        texts.addView(t1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView t2 = new TextView(root.getContext());
        t2.setText(extra);
        t2.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        t2.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        texts.addView(t2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        line.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (action != null) {
            line.addView(action, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36));
        }
        return line;
    }

    private String loc(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }

    private String loc(int res, Object... args) {
        return ApplicationLoader.applicationContext.getString(res, args);
    }

    private void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
    }
}
