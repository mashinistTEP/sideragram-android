package org.sideragram.fork;

import org.telegram.ui.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
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
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Админ-панель Sideragram (видна только Telegram-юзернеймам из списка админов).
 *
 * Три раздела по спецификации:
 *   1. Администраторы — список пар «id/юзернейм», кнопки «Добавить» и «Разжаловать»
 *      (диалог: id или юзернейм → пароль админ-панели);
 *   2. Звёзды — список «id/юзернейм количество», тап по строке → диалог нового значения,
 *      кнопка «Добавить» → диалог id/юзернейм → диалог количества;
 *   3. Премиум — список «id/юзернейм дата окончания время окончания»,
 *      тап по строке → список с одним пунктом «Позже (+30 дней)».
 * Внизу — карточка настроек сервера (название, предупреждение, токен синхронизации).
 */
public class ForkAdminFragment extends BaseFragment {

    private Context ctx;
    private TextView errorView;
    private LinearLayout adminsBox;
    private LinearLayout starsBox;
    private LinearLayout premiumBox;
    private EditText nameField;
    private EditText warnField;
    private EditText tokenField;

    private interface OnText {
        void onText(String value);
    }

    @Override
    public View createView(Context context) {
        ctx = context;
        if (actionBar != null) {
            actionBar.setTitle(loc(R.string.SideragramAdminTitle));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }

        FrameLayout root = new FrameLayout(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(12);
        content.setPadding(pad, pad, pad, pad);
        scroll.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;

        errorView = new TextView(context);
        errorView.setTextColor(0xFFB3261E);
        errorView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        errorView.setVisibility(View.GONE);
        content.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 8));

        // ---------- администраторы ----------
        LinearLayout adminsCard = card();
        adminsCard.addView(caption(R.string.SideragramAdminAdmins), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        adminsBox = new LinearLayout(context);
        adminsBox.setOrientation(LinearLayout.VERTICAL);
        adminsCard.addView(headerRow(loc(R.string.SideragramAdminColWho), ""), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 4));
        adminsCard.addView(adminsBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 8));

        TextView addAdmin = makeButton(loc(R.string.SideragramAdminAddAdmin));
        addAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askWhoThenPassword(true);
            }
        });
        adminsCard.addView(addAdmin, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 0, 0, 0, 8));

        TextView removeAdmin = makeButton(loc(R.string.SideragramAdminRemoveAdmin));
        removeAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askWhoThenPassword(false);
            }
        });
        adminsCard.addView(removeAdmin, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        content.addView(adminsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 12));

        // ---------- звёзды ----------
        LinearLayout starsCard = card();
        starsCard.addView(caption(R.string.SideragramAdminStarsSection), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        starsBox = new LinearLayout(context);
        starsBox.setOrientation(LinearLayout.VERTICAL);
        starsCard.addView(headerRow(loc(R.string.SideragramAdminColWho), loc(R.string.SideragramAdminColAmount)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 4));
        starsCard.addView(starsBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 8));

        TextView addStars = makeButton(loc(R.string.SideragramAdminStarsAdd));
        addStars.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptText(loc(R.string.SideragramAdminStarsAdd), loc(R.string.SideragramAdminWho), false, new OnText() {
                    @Override
                    public void onText(final String who) {
                        if (who.length() == 0) {
                            return;
                        }
                        promptAmount(loc(R.string.SideragramAdminStarsNew), new OnText() {
                            @Override
                            public void onText(String value) {
                                int amount = parseAmount(value);
                                if (amount < 0) {
                                    return;
                                }
                                JSONObject req = new JSONObject();
                                try {
                                    req.put("who", who);
                                    req.put("value", amount);
                                } catch (Exception ignore) {
                                }
                                call("app_admin_stars_add", req);
                            }
                        });
                    }
                });
            }
        });
        starsCard.addView(addStars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        content.addView(starsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 12));

        // ---------- премиум ----------
        LinearLayout premiumCard = card();
        premiumCard.addView(caption(R.string.SideragramAdminPremiumSection), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        premiumBox = new LinearLayout(context);
        premiumBox.setOrientation(LinearLayout.VERTICAL);
        premiumBox.addView(plainText(loc(R.string.SideragramAdminPremiumLater)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        premiumCard.addView(premiumBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(premiumCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 12));

        // ---------- настройки сервера ----------
        LinearLayout settingsCard = card();
        settingsCard.addView(caption(R.string.SideragramAdminSettings), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        nameField = fieldInput(R.string.SideragramAdminForkName);
        settingsCard.addView(nameField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 6, 0, 6));
        warnField = fieldInput(R.string.SideragramAdminWarning);
        settingsCard.addView(warnField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 6));
        tokenField = fieldInput(R.string.SideragramAdminSyncToken);
        settingsCard.addView(tokenField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 8));
        TextView save = makeButton(loc(R.string.SideragramAdminSave));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        settingsCard.addView(save, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        content.addView(settingsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        load();
        return fragmentView;
    }

    // -------------------------------------------------------------- загрузка
    private void load() {
        ForkApi.call("app_admin_list", new JSONObject(), new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    showError(error);
                    return;
                }
                errorView.setVisibility(View.GONE);
                renderAdmins(data.optJSONArray("admins"));
                renderStars(data.optJSONArray("stars"));
                JSONObject settings = data.optJSONObject("settings");
                if (settings != null) {
                    nameField.setText(settings.optString("fork_name", ""));
                    warnField.setText(settings.optString("gift_warning", ""));
                    tokenField.setText(settings.optString("sync_token", ""));
                }
            }
        });
    }

    private void renderAdmins(JSONArray admins) {
        adminsBox.removeAllViews();
        if (admins == null || admins.length() == 0) {
            adminsBox.addView(plainText("—"), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (int i = 0; i < admins.length(); i++) {
            JSONObject a = admins.optJSONObject(i);
            if (a == null) {
                continue;
            }
            long id = a.optLong("id", 0);
            String username = a.optString("username", "");
            String line = (id > 0 ? String.valueOf(id) : "—") + "/" + (username.length() > 0 ? username : "—");
            adminsBox.addView(plainText(line), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 4));
        }
    }

    private void renderStars(JSONArray stars) {
        starsBox.removeAllViews();
        if (stars == null || stars.length() == 0) {
            starsBox.addView(plainText("—"), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            return;
        }
        for (int i = 0; i < stars.length(); i++) {
            JSONObject u = stars.optJSONObject(i);
            if (u == null) {
                continue;
            }
            final long id = u.optLong("id", 0);
            final String username = u.optString("username", "");
            final int amount = u.optInt("stars", 0);
            String who = username.length() > 0 ? username : String.valueOf(id);
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView left = plainText((id > 0 ? String.valueOf(id) : "—") + "/" + (username.length() > 0 ? username : "—"));
            row.addView(left, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            TextView right = plainText(String.valueOf(amount));
            right.setGravity(android.view.Gravity.RIGHT);
            row.addView(right, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptAmount(loc(R.string.SideragramAdminStarsNew), new OnText() {
                        @Override
                        public void onText(String value) {
                            int next = parseAmount(value);
                            if (next < 0) {
                                return;
                            }
                            JSONObject req = new JSONObject();
                            try {
                                req.put("who", username.length() > 0 ? username : String.valueOf(id));
                                req.put("value", next);
                            } catch (Exception ignore) {
                            }
                            call("app_admin_stars_set", req);
                        }
                    });
                }
            });
            starsBox.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 4));
        }
    }

    // -------------------------------------------------------------- диалоги
    private void askWhoThenPassword(final boolean add) {
        promptText(add ? loc(R.string.SideragramAdminAddAdmin) : loc(R.string.SideragramAdminRemoveAdmin),
                loc(R.string.SideragramAdminWho), false, new OnText() {
                    @Override
                    public void onText(final String who) {
                        if (who.length() == 0) {
                            return;
                        }
                        promptText(loc(R.string.SideragramAdminPass), loc(R.string.SideragramAdminPass), true, new OnText() {
                            @Override
                            public void onText(String password) {
                                if (password.length() == 0) {
                                    return;
                                }
                                JSONObject req = new JSONObject();
                                try {
                                    req.put("who", who);
                                    req.put("password", password);
                                } catch (Exception ignore) {
                                }
                                call(add ? "app_admin_add" : "app_admin_remove", req);
                            }
                        });
                    }
                });
    }

    private void promptText(String title, String hint, boolean secret, final OnText callback) {
        final EditText input = new EditText(ctx);
        input.setHint(hint);
        input.setSingleLine(true);
        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(loc(R.string.SideragramDialogOk), new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onText(input.getText().toString().trim());
                    }
                })
                .setNegativeButton(loc(R.string.SideragramDialogCancel), null)
                .show();
    }

    private void promptAmount(String title, final OnText callback) {
        final EditText input = new EditText(ctx);
        input.setHint(title);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(loc(R.string.SideragramDialogOk), new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        callback.onText(input.getText().toString().trim());
                    }
                })
                .setNegativeButton(loc(R.string.SideragramDialogCancel), null)
                .show();
    }

    // -------------------------------------------------------------- запросы
    private void call(String action, JSONObject req) {
        ForkApi.call(action, req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    showError(error);
                    return;
                }
                toast(loc(R.string.SideragramAdminDone));
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
        } catch (Exception ignore) {
        }
        ForkApi.call("app_admin_settings", req, new ForkApi.Callback() {
            @Override
            public void onResult(JSONObject data, String error) {
                if (error != null) {
                    showError(error);
                    return;
                }
                toast(loc(R.string.SideragramAdminDone));
            }
        });
    }

    private int parseAmount(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            toast(loc(R.string.SideragramAdminBadNumber));
            return -1;
        }
    }

    // -------------------------------------------------------------- виджеты
    private LinearLayout card() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), getThemedColor(Theme.key_windowBackgroundWhite)));
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        return box;
    }

    private TextView caption(int stringRes) {
        TextView view = new TextView(ctx);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        view.setText(loc(stringRes));
        return view;
    }

    private TextView plainText(String text) {
        TextView view = new TextView(ctx);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setText(text);
        return view;
    }

    private EditText fieldInput(int hintRes) {
        EditText field = new EditText(ctx);
        field.setHint(loc(hintRes));
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        field.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        return field;
    }

    private LinearLayout headerRow(String left, String right) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = new TextView(ctx);
        l.setText(left);
        l.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        l.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
        row.addView(l, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        if (right != null && right.length() > 0) {
            TextView r = new TextView(ctx);
            r.setText(right);
            r.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
            r.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            r.setGravity(android.view.Gravity.RIGHT);
            row.addView(r, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }
        return row;
    }

    private TextView makeButton(String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setGravity(android.view.Gravity.CENTER);
        view.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), 0xFF3390EC));
        return view;
    }

    private void showError(String error) {
        if (errorView == null) {
            return;
        }
        errorView.setText(error);
        errorView.setVisibility(View.VISIBLE);
    }

    private void toast(String text) {
        android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
    }

    private String loc(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }

    private String loc(int res, Object... args) {
        return ApplicationLoader.applicationContext.getString(res, args);
    }
}
