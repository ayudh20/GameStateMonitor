package com.gamestate.monitor.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.gamestate.monitor.R;

/**
 * KeyValueRowView
 * ---------------
 * Reusable row widget for displaying a muted key label on the left
 * and a bold highlighted value on the right.
 */
public class KeyValueRowView extends LinearLayout {

    private TextView tvRowLabel;
    private TextView tvRowValue;

    public KeyValueRowView(Context context) {
        super(context);
        init(context, null);
    }

    public KeyValueRowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public KeyValueRowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(HORIZONTAL);
        LayoutInflater.from(context).inflate(R.layout.view_key_value_row, this, true);

        tvRowLabel = findViewById(R.id.tvRowLabel);
        tvRowValue = findViewById(R.id.tvRowValue);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.KeyValueRowView);
            String label = a.getString(R.styleable.KeyValueRowView_rowLabel);
            if (label != null) {
                tvRowLabel.setText(label);
            }

            String value = a.getString(R.styleable.KeyValueRowView_rowValue);
            if (value != null) {
                tvRowValue.setText(value);
            }

            int valueColor = a.getColor(R.styleable.KeyValueRowView_rowValueColor, 0);
            if (valueColor != 0) {
                tvRowValue.setTextColor(valueColor);
            }

            a.recycle();
        }
    }

    public void setLabel(CharSequence label) {
        tvRowLabel.setText(label);
    }

    public void setValue(CharSequence value) {
        tvRowValue.setText(value);
    }

    public void setValueColor(int color) {
        tvRowValue.setTextColor(color);
    }

    public TextView getLabelTextView() {
        return tvRowLabel;
    }

    public TextView getValueTextView() {
        return tvRowValue;
    }
}
