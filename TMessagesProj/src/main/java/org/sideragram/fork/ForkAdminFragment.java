package org.sideragram.fork;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
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
 * Админ-Панель Sideragram — прямо в приложении.
 *
 * Доступна тем, чей Telegram-юзернейм указан в списке админов на нашем сервере
 * (сам список меняется здесь же). Панель управляет только нашей частью:
 * наши звёзды, наши подарки, настройки форка. Экономику Telegram она не трогает.
 */
public class ForkAdminFragment extends BaseFragment {

    private Context ctx;
    private TextView statsView;
    private TextView errorView;
    private LinearLayout usersBox;
    private EditText nameField;
    private EditText warnField;
    private EditText tokenField;
    private EditText adminsField;

    @Override
    public View createView(Context context) {
        ctx = context;
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(loc(R.string.SideragramAdminTitle));
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
        content.setPadding(dp(16), dp(12), dp(16), dp(24));

        // ---------- сообщение об ошибке / отсутствии прав ----------
        errorView = new TextView(context);
        errorView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        errorView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
        errorView.setPadding(dp(16), dp(12), dp(16), dp(12));
        errorView.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        errorView.setVisibility(View.GONE);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---------- сводка ----------
        LinearLayout statsCard = card();
        statsCard.addView(caption(R.string.SideragramAdminStats), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        statsView = new TextView(context);
        statsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        statsView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        statsView.setPadding(0, dp(6), 0, 0);
        statsView.setText("…");
        statsCard.addView(statsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(statsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // ---------- настройки форка ----------
        LinearLayout setCard = card();
        setCard.addView(caption(R.string.SideragramAdminSettings), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        nameField = fieldInput(R.string.SideragramAdminForkName);
        setCard.addView(nameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 8, 0, 0));
        warnField = fieldInput(R.string.SideragramAdminWarning);
        setCard.addView(warnField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 8, 0, 0));
        tokenField = fieldInput(R.string.SideragramAdminSyncToken);
        setCard.addView(tokenField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 8, 0, 0));
        adminsField = fieldInput(R.string.SideragramAdminUsernames);
        setCard.addView(adminsField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 8, 0, 0));

        TextView save = makeButton(loc(R.string.SideragramAdminSave));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        setCard.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 0, 12, 0, 0));
        content.addView(setCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 12, 0, 0));

        // ---------- пользователи ----------
        LinearLayout usersCard = card();
        usersCard.addView(caption(R.string.SideragramAdminUsers), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        usersBox = new LinearLayout(context);
        usersBox.setOrientation(LinearLayout.VERTICAL);
        usersCard.addView(usersBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(usersCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 12, 0, 0));

        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        fragmentView = root;
        load();
        return fragmentView;
    }

    // ================================================================
    // данные
    // ================================================================

    private void load() {
        ForkApi.call("app_admin_panel", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    showError(error);
                    return;
                }
                showError(null);

                JSONObject st = data.optJSONObject("stats");
                if (st != null) {
                    statsView.setText(loc(R.string.SideragramAdminStatsLine,
                            st.optInt("users", 0), st.optInt("gifts_sent", 0),
                            st.optInt("stars_issued", 0), st.optInt("catalog", 0)));
                }

                JSONObject s = data.optJSONObject("settings");
                if (s != null) {
                    nameField.setText(s.optString("fork_name", ""));
                    warnField.setText(s.optString("gift_warning", ""));
                    tokenField.setText(s.optString("sync_token", ""));
                    adminsField.setText(s.optString("admin_usernames", ""));
                }

                renderUsers(data.optJSONArray("users"));
            }
        });
    }

    private void renderUsers(JSONArray users) {
        usersBox.removeAllViews();
        if (users == null || users.length() == 0) {
            usersBox.addView(plainText(loc(R.string.SideragramAdminNoUsers)));
            return;
        }
        for (int a = 0; a < users.length(); a++) {
            final JSONObject u = users.optJSONObject(a);
            if (u == null) {
                continue;
            }
            final int userId = u.optInt("id", 0);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, 0);

            TextView who = new TextView(ctx);
            who.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            who.setTypeface(Typeface.DEFAULT_BOLD);
            who.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            who.setText("@" + u.optString("username", "?") + " — " + u.optInt("stars", 0) + " ⭐"
                    + (u.optInt("is_admin", 0) == 1 ? " · " + loc(R.string.SideragramAdminMark) : ""));
            row.addView(who, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout actions = new LinearLayout(ctx);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            final EditText amount = new EditText(ctx);
            amount.setInputType(InputType.TYPE_CLASS_NUMBER);
            amount.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            amount.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            amount.setText("100");
            amount.setSingleLine(true);
            actions.addView(amount, LayoutHelper.createLinear(0, 42, 1f, 0, 6, 4, 0));

            TextView plus = makeButton("+ " + loc(R.string.SideragramAdminGrant));
            plus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    grant(userId, readAmount(amount));
                }
            });
            actions.addView(plus, LayoutHelper.createLinear(0, 42, 1.5f, 4, 6, 4, 0));

            TextView minus = makeButton("- " + loc(R.string.SideragramAdminDeduct));
            minus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    grant(userId, -readAmount(amount));
                }
            });
            actions.addView(minus, LayoutHelper.createLinear(0, 42, 1.5f, 4, 6, 0, 0));

            row.addView(actions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            usersBox.addView(row);
        }
    }

    private int readAmount(EditText field) {
        try {
            return Math.abs(Integer.parseInt(field.getText().toString().trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private void grant(int userId, int delta) {
        if (userId <= 0 || delta == 0) {
            toast(loc(R.string.SideragramAdminBadAmount));
            return;
        }
        JSONObject req = new JSONObject();
        try {
            req.put("user_id", userId);
            req.put("delta", delta);
            req.put("reason", loc(R.string.SideragramAdminReason));
        } catch (Exception ignore) {
        }
        ForkApi.call("app_admin_grant", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    toast(error);
                    return;
                }
                JSONObject u = data.optJSONObject("user");
                toast(u == null
                        ? loc(R.string.SideragramAdminSaved)
                        : "@" + u.optString("username", "") + " — " + u.optInt("stars", 0) + " ⭐");
                load();
            }
        });
    }

    private void saveSettings() {
        JSONObject req = new JSONObject();
        try {
            req.put("fork_name", nameField.getText().toString().trim());
            req.put("gift_warning", warnField.getText().toString().trim());
            req.put("sync_token", tokenField.getText().toString().trim());
            req.put("admin_usernames", adminsField.getText().toString().trim());
        } catch (Exception ignore) {
        }
        ForkApi.call("app_admin_settings", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    toast(error);
                    return;
                }
                toast(loc(R.string.SideragramAdminSaved));
                load();
            }
        });
    }

    // ================================================================
    // мелкая отрисовка
    // ================================================================

    private int dp(int value) {
        return AndroidUtilities.dp(value);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        return card;
    }

    private TextView caption(int stringRes) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        t.setText(loc(stringRes));
        return t;
    }

    private TextView plainText(String text) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        t.setPadding(0, dp(8), 0, 0);
        t.setText(text);
        return t;
    }

    private EditText fieldInput(int hintRes) {
        EditText field = new EditText(ctx);
        field.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        field.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHint(loc(hintRes));
        return field;
    }

    private TextView makeButton(String text) {
        TextView button = new TextView(ctx);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createRoundRectDrawable(dp(10), getThemedColor(Theme.key_featuredStickers_addButton)));
        return button;
    }

    private void showError(String error) {
        if (error == null) {
            errorView.setVisibility(View.GONE);
            return;
        }
        errorView.setText(error);
        errorView.setVisibility(View.VISIBLE);
    }

    private void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
    }

    private String loc(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }

    private String loc(int res, Object... args) {
        return ApplicationLoader.applicationContext.getString(res, args);
    }
}
