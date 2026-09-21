package com.poliku.polygoplus.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.poliku.polygoplus.R;

public final class GuidedTourOverlay extends FrameLayout {
    private final View[] targets;
    private final String[] titles;
    private final String[] bodies;
    private final Runnable onFinish;
    private final Paint dimPaint = new Paint();
    private final Paint clearPaint = new Paint();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearLayout card;
    private final TextView progress;
    private final TextView title;
    private final TextView body;
    private final Button back;
    private final Button next;
    private RectF spotlight = new RectF();
    private int step;

    public GuidedTourOverlay(Context context) {
        this(context, null);
    }

    public GuidedTourOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidedTourOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Context app = context.getApplicationContext();
        this.targets = new View[0];
        this.titles = new String[0];
        this.bodies = new String[0];
        this.onFinish = () -> { };
        this.card = null;
        this.progress = null;
        this.title = null;
        this.body = null;
        this.back = null;
        this.next = null;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        dimPaint.setColor(0xB8000000);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        if (app instanceof Activity) {
            this.ringPaint.setColor(((Activity) app).getColor(R.color.pks_blue));
        } else {
            this.ringPaint.setColor(0xFF1A63C8);
        }
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(3));
    }

    public GuidedTourOverlay(Activity activity, View[] targets, String[] titles,
                             String[] bodies, Runnable onFinish) {
        super(activity);
        this.targets = targets;
        this.titles = titles;
        this.bodies = bodies;
        this.onFinish = onFinish == null ? () -> { } : onFinish;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        dimPaint.setColor(0xB8000000);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ringPaint.setColor(activity.getColor(R.color.pks_blue));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(3));
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(16));
        card.setElevation(dp(12));
        card.setFocusable(true);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(22));
        card.setBackground(cardBackground);
        progress = new TextView(activity);
        progress.setTextColor(activity.getColor(R.color.pks_blue));
        progress.setTextSize(12);
        progress.setTypeface(progress.getTypeface(), android.graphics.Typeface.BOLD);
        progress.setLetterSpacing(0.08f);
        title = new TextView(activity);
        title.setTextColor(activity.getColor(R.color.airbnb_ink));
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        body = new TextView(activity);
        body.setTextColor(activity.getColor(R.color.airbnb_muted));
        body.setTextSize(14);
        body.setPadding(0, dp(8), 0, dp(14));
        card.addView(progress);
        card.addView(title);
        card.addView(body);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        Button skip = new Button(activity);
        skip.setText(R.string.onboarding_skip);
        skip.setOnClickListener(v -> finish());
        back = new Button(activity);
        back.setText(R.string.tour_back);
        back.setOnClickListener(v -> showStep(step - 1));
        next = new Button(activity);
        next.setText(R.string.onboarding_next);
        next.setOnClickListener(v -> {
            if (step == targets.length - 1) finish();
            else showStep(step + 1);
        });
        actions.addView(skip);
        actions.addView(back);
        actions.addView(next);
        card.addView(actions);

        LayoutParams cardParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        cardParams.setMargins(dp(16), dp(16), dp(16), dp(16));
        addView(card, cardParams);
        post(() -> showStep(0));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.drawRoundRect(spotlight, dp(18), dp(18), clearPaint);
        canvas.drawRoundRect(spotlight, dp(18), dp(18), ringPaint);
    }

    private void showStep(int position) {
        if (position < 0 || position >= targets.length) return;
        step = position;
        progress.setText((position + 1) + " / " + targets.length);
        title.setText(titles[position]);
        body.setText(bodies[position]);
        back.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        next.setText(position == targets.length - 1
                ? R.string.tour_finish : R.string.onboarding_next);
        Rect location = new Rect();
        targets[position].getGlobalVisibleRect(location);
        int[] overlayLocation = new int[2];
        getLocationOnScreen(overlayLocation);
        spotlight = new RectF(location.left - overlayLocation[0] - dp(8),
                location.top - overlayLocation[1] - dp(8),
                location.right - overlayLocation[0] + dp(8),
                location.bottom - overlayLocation[1] + dp(8));
        invalidate();
        card.post(this::positionCard);
        card.setContentDescription(titles[position] + ". " + bodies[position]);
        card.requestFocus();
        card.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void positionCard() {
        int margin = dp(16);
        int gap = dp(18);
        int width = Math.min(getWidth() - margin * 2, dp(380));
        LayoutParams params = (LayoutParams) card.getLayoutParams();
        params.width = width;
        params.gravity = Gravity.TOP | Gravity.START;
        card.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int cardHeight = card.getMeasuredHeight();
        int spaceLeft = Math.round(spotlight.left) - gap - margin;
        int spaceRight = getWidth() - Math.round(spotlight.right) - gap - margin;
        int spaceAbove = Math.round(spotlight.top) - gap - margin;
        int spaceBelow = getHeight() - Math.round(spotlight.bottom) - gap - margin;
        int left;
        int top;
        if (spaceRight >= width || spaceLeft >= width) {
            left = spaceRight >= width
                    ? Math.round(spotlight.right) + gap
                    : Math.round(spotlight.left) - gap - width;
            top = Math.round(spotlight.centerY()) - cardHeight / 2;
        } else {
            left = (getWidth() - width) / 2;
            top = spaceBelow >= cardHeight || spaceBelow > spaceAbove
                    ? Math.round(spotlight.bottom) + gap
                    : Math.round(spotlight.top) - gap - cardHeight;
        }
        params.leftMargin = Math.max(margin,
                Math.min(left, getWidth() - width - margin));
        params.rightMargin = 0;
        params.topMargin = Math.max(margin,
                Math.min(top, getHeight() - cardHeight - margin));
        params.bottomMargin = 0;
        card.setLayoutParams(params);
    }

    private void finish() {
        ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        if (parent != null) parent.removeView(this);
        onFinish.run();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
