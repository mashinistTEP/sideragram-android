package org.sideragram.fork;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Диалог ввода значения в стиле Telegram: родной нижний лист (BottomSheet)
 * с тематическим полем ввода и синей кнопкой. В свежих версиях Telegram нет
 * своего AlertDialog — все вводы делаются такими листами, поэтому и у нас так.
 */
public class ForkPromptSheet extends BottomSheet {

    public interface Callback {
        void onValue(String value);
    }

    private final EditText input;
    private final Callback callback;

    public ForkPromptSheet(Context context, String title, String hint, int inputType, Callback callback) {
        super(context, false);
        this.callback = callback;
        setTitle(title, true);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        content.setPadding(pad, AndroidUtilities.dp(8), pad, pad);

        input = new EditText(context);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 0, 0, 16));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.RIGHT);

        TextView cancel = makeButton(context, loc(R.string.SideragramDialogCancel), false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        buttons.addView(cancel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 0f, 0, 0, 8, 0));

        TextView ok = makeButton(context, loc(R.string.SideragramDialogOk), true);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String value = input.getText().toString().trim();
                dismiss();
                if (callback != null) {
                    callback.onValue(value);
                }
            }
        });
        buttons.addView(ok, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40));

        content.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setCustomView(content);
    }

    private static TextView makeButton(Context context, String text, boolean primary) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setGravity(Gravity.CENTER);
        int h = AndroidUtilities.dp(14);
        view.setPadding(h, 0, h, 0);
        if (primary) {
            view.setTextColor(0xFFFFFFFF);
            view.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0xFF3390EC));
        } else {
            view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
        return view;
    }

    private static String loc(int res) {
        return ApplicationLoader.applicationContext.getString(res);
    }
}
