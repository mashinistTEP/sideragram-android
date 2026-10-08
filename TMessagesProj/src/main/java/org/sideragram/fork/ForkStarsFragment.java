package org.sideragram.fork;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Наш экран: баланс звёзд Sideragram с нашего сервера.
 * Открывается из Настроек (пункт «Sideragram»).
 */
public class ForkStarsFragment extends BaseFragment {

    private TextView balanceView;
    private TextView statusView;
    private TextView operationsView;
    private TextView linkButton;
    private TextView adminButton;
    private EditText serverField;
    private boolean busy;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.SideragramTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(24));

        // ---------- карточка «баланс» ----------
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView caption = new TextView(context);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        caption.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        caption.setText(LocaleController.getString(R.string.SideragramBalanceTitle));
        card.addView(caption, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        balanceView = new TextView(context);
        balanceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 34);
        balanceView.setTypeface(Typeface.DEFAULT_BOLD);
        balanceView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        balanceView.setText("—");
        card.addView(balanceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 0));

        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        hint.setText(LocaleController.getString(R.string.SideragramBalanceHint));
        card.addView(hint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 4, 0, 0));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statusView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        statusView.setPadding(0, AndroidUtilities.dp(12), 0, 0);
        statusView.setText(LocaleController.getString(R.string.SideragramStatusNotLinked));
        card.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---------- кнопки ----------
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        TextView refreshButton = makeButton(context, LocaleController.getString(R.string.SideragramRefresh));
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        buttons.addView(refreshButton, LayoutHelper.createLinear(0, 44, 1f, 0, 14, 4, 0));

        final TextView linkButton = makeButton(context, LocaleController.getString(R.string.SideragramLink));
        linkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (busy) {
                    return;
                }
                if (ForkSession.isLinked()) {
                    ForkSession.unlink();
                    balanceView.setText("—");
                    operationsView.setText("—");
                    statusView.setText(LocaleController.getString(R.string.SideragramStatusNotLinked));
                    linkButton.setText(LocaleController.getString(R.string.SideragramLink));
                } else {
                    doLink();
                }
            }
        });
        buttons.addView(linkButton, LayoutHelper.createLinear(0, 44, 1f, 4, 14, 0, 0));
        this.linkButton = linkButton;

        card.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка админки: показывается только тем, чей Telegram-юзернейм есть в списке
        // админов на нашем сервере (сервер сообщает об этом в ответе с балансом).
        adminButton = makeButton(context, LocaleController.getString(R.string.SideragramAdminTitle));
        adminButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presentFragment(new ForkAdminFragment());
            }
        });
        adminButton.setVisibility(ForkSession.isAdmin() ? View.VISIBLE : View.GONE);
        card.addView(adminButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 0, 8, 0, 0));
        content.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---------- карточка «сервер» ----------
        LinearLayout serverCard = new LinearLayout(context);
        serverCard.setOrientation(LinearLayout.VERTICAL);
        serverCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        serverCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView serverCaption = new TextView(context);
        serverCaption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        serverCaption.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        serverCaption.setText(LocaleController.getString(R.string.SideragramServer));
        serverCard.addView(serverCaption, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        serverField = new EditText(context);
        serverField.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        serverField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        serverField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        serverField.setHint(ForkConfig.DEFAULT_BASE_URL);
        serverField.setSingleLine(true);
        serverField.setText(ForkSession.baseUrl());
        serverCard.addView(serverField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 0));

        TextView saveServer = makeButton(context, LocaleController.getString(R.string.SideragramServerSave));
        saveServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForkSession.setBaseUrl(serverField.getText().toString());
                serverField.setText(ForkSession.baseUrl());
                toast(LocaleController.getString(R.string.SideragramServerSaved));
                refresh();
            }
        });
        serverCard.addView(saveServer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 0, 10, 0, 0));
        content.addView(serverCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 12, 0, 0));

        // ---------- карточка «операции» ----------
        LinearLayout opsCard = new LinearLayout(context);
        opsCard.setOrientation(LinearLayout.VERTICAL);
        opsCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        opsCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView opsCaption = new TextView(context);
        opsCaption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        opsCaption.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        opsCaption.setText(LocaleController.getString(R.string.SideragramOps));
        opsCard.addView(opsCaption, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        operationsView = new TextView(context);
        operationsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        operationsView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        operationsView.setPadding(0, AndroidUtilities.dp(8), 0, 0);
        operationsView.setText("—");
        opsCard.addView(operationsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(opsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 12, 0, 0));

        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        refresh();
        return fragmentView;
    }

    private TextView makeButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_featuredStickers_addButton)));
        return button;
    }

    private void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean value) {
        busy = value;
        balanceView.setAlpha(value ? 0.5f : 1f);
    }

    /** Привязка: отправляем нашему серверу id/имя из Telegram и получаем наш токен. */
    private void doLink() {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        long tgId = user == null ? 0 : user.id;
        if (tgId == 0) {
            toast(LocaleController.getString(R.string.SideragramNoUser));
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
        setBusy(true);
        statusView.setText(LocaleController.getString(R.string.SideragramLinking));
        ForkApi.call("app_link", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                setBusy(false);
                if (error != null) {
                    statusView.setText(error);
                    return;
                }
                String token = data.optString("token", "");
                JSONObject me = data.optJSONObject("me");
                String name = me == null ? "" : me.optString("name", "");
                ForkSession.setAdmin(me != null && me.optInt("is_admin", 0) == 1);
                if (token.length() > 0) {
                    ForkSession.saveLink(token, name);
                    if (linkButton != null) {
                        linkButton.setText(LocaleController.getString(R.string.SideragramUnlink));
                    }
                    toast(LocaleController.getString(R.string.SideragramLinked));
                    refresh();
                } else {
                    statusView.setText(LocaleController.getString(R.string.SideragramLinkFailed));
                }
            }
        });
    }

    /** Забираем баланс с нашего сервера. */
    private void refresh() {
        if (!ForkSession.isLinked()) {
            statusView.setText(LocaleController.getString(R.string.SideragramStatusNotLinked));
            if (adminButton != null) {
                adminButton.setVisibility(View.GONE);
            }
            return;
        }
        setBusy(true);
        statusView.setText(LocaleController.getString(R.string.SideragramLoading));
        ForkApi.call("app_balance", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                setBusy(false);
                if (error != null) {
                    statusView.setText(error);
                    return;
                }
                long balance = data.optLong("balance", 0);
                JSONObject me = data.optJSONObject("me");
                String name = me == null ? "" : me.optString("name", "");
                String username = me == null ? "" : me.optString("username", "");
                boolean admin = me != null && me.optInt("is_admin", 0) == 1;
                ForkSession.setAdmin(admin);
                if (adminButton != null) {
                    adminButton.setVisibility(admin ? View.VISIBLE : View.GONE);
                }
                balanceView.setText(String.valueOf(balance));
                statusView.setText(LocaleController.formatString(R.string.SideragramStatusLinked, name.length() > 0 ? name : username, ForkSession.baseUrl()));
                operationsView.setText(formatOperations(data.optJSONArray("operations")));
            }
        });
    }

    private String formatOperations(JSONArray operations) {
        if (operations == null || operations.length() == 0) {
            return LocaleController.getString(R.string.SideragramNoOps);
        }
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < operations.length(); a++) {
            JSONObject op = operations.optJSONObject(a);
            if (op == null) {
                continue;
            }
            long delta = op.optLong("delta", 0);
            String reason = op.optString("reason", "");
            String when = op.optString("date", "");
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(delta > 0 ? "+" : "").append(delta).append(" · ").append(reason);
            if (when.length() > 0) {
                sb.append(" · ").append(when);
            }
        }
        return sb.toString();
    }
}
