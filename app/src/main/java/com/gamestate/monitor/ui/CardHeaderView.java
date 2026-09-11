package com.gamestate.monitor.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.gamestate.monitor.R;

/**
 * CardHeaderView
 * --------------
 * Reusable header component for GameState Monitor cards.
 * Combines a squircle icon container, title text, and an optional end text/badge.
 */
public class CardHeaderView extends LinearLayout {

    private ImageView ivIcon;
    private TextView tvTitle;
    private TextView tvEndText;

    public CardHeaderView(Context context) {
        super(context);
        init(context, null);
    }

    public CardHeaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CardHeaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_card_header, this, true);

        ivIcon = findViewById(R.id.cardHeaderIcon);
        tvTitle = findViewById(R.id.cardHeaderTitle);
        tvEndText = findViewById(R.id.cardHeaderEndText);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CardHeaderView);
            String title = a.getString(R.styleable.CardHeaderView_headerTitle);
            if (title != null) {
                tvTitle.setText(title);
            }

            int iconRes = a.getResourceId(R.styleable.CardHeaderView_headerIcon, 0);
            if (iconRes != 0) {
                ivIcon.setImageResource(iconRes);
            }

            String endText = a.getString(R.styleable.CardHeaderView_headerEndText);
            if (endText != null && !endText.isEmpty()) {
                tvEndText.setText(endText);
                tvEndText.setVisibility(VISIBLE);
            }

            int endTextColor = a.getColor(R.styleable.CardHeaderView_headerEndTextColor, 0);
            if (endTextColor != 0) {
                tvEndText.setTextColor(endTextColor);
            }

            int badgeBg = a.getResourceId(R.styleable.CardHeaderView_headerEndBadgeBackground, 0);
            if (badgeBg != 0) {
                tvEndText.setBackgroundResource(badgeBg);
            }

            a.recycle();
        }
    }

    public void setTitle(CharSequence title) {
        tvTitle.setText(title);
    }

    public void setIcon(@DrawableRes int resId) {
        ivIcon.setImageResource(resId);
    }

    public void setEndText(CharSequence text) {
        tvEndText.setText(text);
        if (text != null && text.length() > 0) {
            tvEndText.setVisibility(VISIBLE);
        }
    }

    public void setEndTextColor(int color) {
        tvEndText.setTextColor(color);
    }

    public TextView getTitleTextView() {
        return tvTitle;
    }

    public ImageView getIconView() {
        return ivIcon;
    }

    public TextView getEndTextView() {
        return tvEndText;
    }
}
