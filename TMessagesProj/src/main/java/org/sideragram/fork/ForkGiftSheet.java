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

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Наша копия экрана отправки подарка (вместо оригинальных StarGiftSheet / GiftSheet /
 * SendGiftSheet): сетка наших подарков, сообщение, получатель, отправка через наш сервер
 * и «островок»-предупреждение перед отправкой. Внешность — в стиле листов Telegram.
 */
public class ForkGiftSheet extends BottomSheet {

    private final long dialogId;
    private FrameLayout root;
    private LinearLayout gridBox;
    private EditText recipientField;
    private EditText messageField;
    private TextView balanceView;
    private String warningText = "";
    private String selectedCode = "";
    private String selectedTitle = "";
    private View selectedCell;
    private boolean busy;

    public ForkGiftSheet(Context context, long dialogId) {
        super(context, false);
        this.dialogId = dialogId;
        setTitle(loc(R.string.SideragramGiftsScreenTitle), true);

        root = new FrameLayout(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(12);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        setCustomView(root);

        if (!ForkSession.isLinked()) {
            // Устройство не привязано: показываем нашу карточку привязки вместо
            // оригинального экрана Telegram — все экраны отправки подарков наши.
            TextView note = new TextView(context);
            note.setTextColor(0xFFB3261E);
            note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            note.setText(loc(R.string.SideragramGiftsUnlinkedCard));
            content.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 12));
            TextView linkBtn = new TextView(context);
            linkBtn.setText(loc(R.string.SideragramGiftsLinkNow));
            linkBtn.setTextColor(0xFFFFFFFF);
            linkBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            linkBtn.setGravity(Gravity.CENTER);
            linkBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0xFF8E74FF));
            linkBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    doLinkAndReload();
                }
            });
            content.addView(linkBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));
            return;
        }

        balanceView = new TextView(context);
        balanceView.setTextColor(0xFF8E74FF);
        balanceView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        balanceView.setText(loc(R.string.SideragramGiftsLoadingBalance));
        content.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 10));

        recipientField = new EditText(context);
        recipientField.setHint(loc(R.string.SideragramGiftsRecipient));
        recipientField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        recipientField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        String pre = ForkGiftRouter.usernameForDialogId(UserConfig.selectedAccount, dialogId);
        if (pre.length() > 0) {
            recipientField.setText(pre);
        }
        content.addView(recipientField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 0, 0, 6));

        messageField = new EditText(context);
        messageField.setHint(loc(R.string.SideragramGiftsMessage));
        messageField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        messageField.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        content.addView(messageField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 0, 0, 10));

        gridBox = new LinearLayout(context);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(gridBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 10));

        TextView send = new TextView(context);
        send.setText(loc(R.string.SideragramGiftsSendNow));
        send.setTextColor(0xFFFFFFFF);
        send.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        send.setGravity(Gravity.CENTER);
        send.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0xFF8E74FF));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                trySend();
            }
        });
        content.addView(send, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));

        load();
        ForkGiftSync.harvest(org.telegram.messenger.UserConfig.selectedAccount, dialogId, new Runnable() {
            @Override
            public void run() {
                load();
            }
        });
    }

    // -------------------------------------------------------------- привязка из листа
    private void doLinkAndReload() {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        long tgId = user == null ? 0 : user.id;
        if (tgId == 0) {
            return;
        }
        JSONObject req = new JSONObject();
        try {
            req.put("tg_user_id", String.valueOf(tgId));
            req.put("tg_username", user.username == null ? "" : user.username);
            req.put("tg_name", user.first_name == null ? "" : user.first_name);
            req.put("device", android.os.Build.MODEL == null ? "" : android.os.Build.MODEL);
        } catch (Exception ignore) {
        }
        ForkApi.call("app_link", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    return;
                }
                String token = data.optString("token", "");
                JSONObject me = data.optJSONObject("me");
                if (token.length() == 0) {
                    return;
                }
                ForkSession.setAdmin(me != null && me.optInt("is_admin", 0) == 1);
                ForkSession.saveLink(token, me == null ? "" : me.optString("name", ""));
                dismiss();
                new ForkGiftSheet(getContext(), dialogId).show();
            }
        });
    }

    // -------------------------------------------------------------- загрузка
    private void load() {
        ForkApi.call("app_gifts", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    island(ForkIslandView.TYPE_DANGER, error, null);
                    return;
                }
                warningText = data.optString("warning", "");
                int balance = data.optInt("balance", 0);
                ForkBalance.set(balance);
                balanceView.setText(loc(R.string.SideragramGiftsBalanceLine, balance));
                org.json.JSONArray tg = data.optJSONArray("tg_catalog");
                renderGrid(tg != null && tg.length() > 0 ? tg : data.optJSONArray("catalog"));
            }
        });
    }

    private void renderGrid(JSONArray catalog) {
        gridBox.removeAllViews();
        if (catalog == null || catalog.length() == 0) {
            gridBox.addView(text(loc(R.string.SideragramGiftsEmpty), 14, 0xFF999999),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject g = catalog.optJSONObject(i);
            if (g == null) {
                continue;
            }
            if (row == null) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                gridBox.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 8));
            }
            row.addView(cell(g), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 4, 0));
            if (row.getChildCount() == 3) {
                row = null;
            }
        }
    }

    private View cell(final JSONObject g) {
        final Context context = getContext();
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = AndroidUtilities.dp(8);
        box.setPadding(p, p, p, p);
        box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x14000000));

        TextView emoji = new TextView(context);
        emoji.setText(g.optString("emoji", "🎁"));
        emoji.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 26);
        box.addView(emoji, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView title = new TextView(context);
        title.setText(g.optString("title", ""));
        title.setTextColor(0xFF000000);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        title.setGravity(Gravity.CENTER);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView price = new TextView(context);
        price.setText(g.optInt("price", 0) + " ⭐");
        price.setTextColor(0xFF8E74FF);
        price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        box.addView(price, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCode = g.optString("code", "");
                selectedTitle = g.optString("title", "");
                if (selectedCell != null) {
                    selectedCell.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x14000000));
                }
                selectedCell = v;
                v.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0x338E74FF));
            }
        });
        return box;
    }

    // -------------------------------------------------------------- отправка
    private void trySend() {
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
        // Никаких блокировок: отправлять можно любому, в том числе себе.
        String warn = warningText.length() > 0
                ? warningText.replace("{name}", ForkConfig.FORK_NAME)
                : loc(R.string.SideragramGiftsWarnDefault);
        island(ForkIslandView.TYPE_WARN, warn, new ForkIslandView.Confirm() {
            @Override
            public void onConfirm() {
                doSend(recipient);
            }

            @Override
            public void onCancel() {
                // передумал
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
                    island(ForkIslandView.TYPE_DANGER, error, null);
                    return;
                }
                island(ForkIslandView.TYPE_OK, loc(R.string.SideragramGiftsSent, selectedTitle), null);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        dismiss();
                    }
                });
            }
        });
    }

    // --------------------------------------------------------------- мелочи
    private void island(String type, String text, ForkIslandView.Confirm confirm) {
        ForkIslandView.show(root, type, text, confirm);
    }

    private TextView text(String s, int sizeDip, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, sizeDip);
        return t;
    }

    private String loc(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }

    private String loc(int res, Object... args) {
        return ApplicationLoader.applicationContext.getString(res, args);
    }
}
