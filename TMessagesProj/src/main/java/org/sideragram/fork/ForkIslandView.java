package org.sideragram.fork;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * «Плавающий островок» — предупреждение/уведомление поверх экрана.
 * Показываем отправителю ПЕРЕД отправкой подарка (с кнопками) и получателю
 * ПО ФАКТА получения (авто-скрытие). Обычный Telegram ничего такого не показывает.
 */
public class ForkIslandView extends FrameLayout {

    public static final String TYPE_WARN = "warn";
    public static final String TYPE_OK = "ok";
    public static final String TYPE_DANGER = "danger";

    public interface Confirm {
        void onConfirm();

        void onCancel();
    }

    public ForkIslandView(Context context, String type, String text, Confirm confirm) {
        super(context);

        int bg;
        String icon;
        if (TYPE_OK.equals(type)) {
            bg = 0xFF1D7A3D;
            icon = "✅ ";
        } else if (TYPE_DANGER.equals(type)) {
            bg = 0xFFB3261E;
            icon = "⛔ ";
        } else {
            bg = 0xFF8D5A00;
            icon = "⚠️ ";
        }

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), bg));
        int pad = AndroidUtilities.dp(14);
        box.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(context);
        label.setText(icon + text);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        label.setSingleLine(false);
        box.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (confirm != null) {
            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.addView(button(context, R.string.SideragramGiftsConfirmSend, 0xFF2196F3, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeFromParent();
                    confirm.onConfirm();
                }
            }), LayoutHelper.createLinear(0, 40, 1f, 0, 10, 4, 0));
            buttons.addView(button(context, R.string.SideragramGiftsCancel, 0x33FFFFFF, new OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeFromParent();
                    confirm.onCancel();
                }
            }), LayoutHelper.createLinear(0, 40, 1f, 4, 10, 0, 0));
            box.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.gravity = Gravity.TOP;
        int m = AndroidUtilities.dp(10);
        lp.leftMargin = m;
        lp.rightMargin = m;
        lp.topMargin = m;
        addView(box, lp);

        if (confirm == null) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    removeFromParent();
                }
            }, 4200);
        }
    }

    private TextView button(Context context, int textRes, int color, OnClickListener listener) {
        TextView v = new TextView(context);
        v.setText(loc(textRes));
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), color));
        v.setOnClickListener(listener);
        return v;
    }

    private String loc(int res) {
        return org.telegram.messenger.ApplicationLoader.applicationContext.getString(res);
    }

    public void removeFromParent() {
        if (getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) getParent()).removeView(this);
        }
    }

    /** Показать островок поверх root (root должен быть FrameLayout). */
    public static ForkIslandView show(FrameLayout root, String type, String text, Confirm confirm) {
        ForkIslandView view = new ForkIslandView(root.getContext(), type, text, confirm);
        root.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return view;
    }
}
