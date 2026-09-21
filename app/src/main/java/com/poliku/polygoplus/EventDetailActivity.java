package com.poliku.polygoplus;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.util.EventPalette;
import com.poliku.polygoplus.util.EventTimes;
import com.poliku.polygoplus.worker.EventReminderWorker;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class EventDetailActivity extends BaseActivity {
    public static final String EXTRA_EVENT = "event_json";

    private PolyGoApi.CampusEvent event;
    private int accent;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_event_detail);

        String json = getIntent().getStringExtra(EXTRA_EVENT);
        event = TextUtils.isEmpty(json) ? new PolyGoApi.CampusEvent()
                : new Gson().fromJson(json, PolyGoApi.CampusEvent.class);
        if (event == null) {
            event = new PolyGoApi.CampusEvent();
        }
        accent = accentFor(event);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        renderHero();
        renderBody();
    }

    private int accentFor(PolyGoApi.CampusEvent e) {
        int[] colors = EventPalette.gradientColors(e.theme);
        if (EventPalette.validHex(e.accent_color)) {
            return EventPalette.parseHex(e.accent_color);
        }
        return colors[1];
    }

    private void renderHero() {
        FrameLayout hero = findViewById(R.id.evHero);
        hero.setBackground(EventPalette.gradient(event.theme));

        boolean hasCover = !TextUtils.isEmpty(event.cover_url);
        ImageView cover = findViewById(R.id.evHeroCover);
        if (hasCover) {
            cover.setVisibility(View.VISIBLE);
            Glide.with(this).load(event.cover_url).centerCrop().into(cover);
        }

        TextView emoji = findViewById(R.id.evHeroEmoji);
        boolean hasEmoji = !TextUtils.isEmpty(event.emoji);
        emoji.setVisibility(hasEmoji ? View.VISIBLE : View.GONE);
        if (hasEmoji) emoji.setText(event.emoji);

        TextView date = findViewById(R.id.evHeroDate);
        date.setText(EventTimes.pill(event.starts_at));
        date.setTextColor(EventPalette.validHex(event.accent_color) ? accent : 0xFF0F172A);

        TextView featured = findViewById(R.id.evHeroFeatured);
        featured.setVisibility(event.isFeatured != 0 ? View.VISIBLE : View.GONE);
    }

    private void renderBody() {
        boolean hasLabel = !TextUtils.isEmpty(event.label);

        TextView label = findViewById(R.id.tvDetailLabel);
        label.setVisibility(hasLabel ? View.VISIBLE : View.GONE);
        if (hasLabel) {
            label.setText(event.label);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(accent);
            pill.setCornerRadius(getResources().getDisplayMetrics().density * 999);
            label.setBackground(pill);
            label.setTextColor(EventPalette.contrastColor(accent));
        }

        ((TextView) findViewById(R.id.tvDetailTitle)).setText(event.title);

        TextView dateExplain = findViewById(R.id.tvDetailDateExplain);
        dateExplain.setText(EventTimes.full(event.starts_at));

        TextView venue = findViewById(R.id.tvDetailVenue);
        venue.setText(event.venue);

        TextView organizer = findViewById(R.id.tvDetailOrganizer);
        LinearLayout organizerRow = findViewById(R.id.rowOrganizer);
        String name = event.organizer_name;
        String contact = event.organizer_contact;
        boolean hasOrganizer = !TextUtils.isEmpty(name) || !TextUtils.isEmpty(contact);
        organizerRow.setVisibility(hasOrganizer ? View.VISIBLE : View.GONE);
        if (hasOrganizer) {
            String text = name;
            if (!TextUtils.isEmpty(contact)) text = name + " \u00B7 " + contact;
            organizer.setText(text);
            organizerRow.setOnClickListener(v -> openContact(contact));
        }

        TextView capacity = findViewById(R.id.tvDetailCapacity);
        if (event.capacity >= 1) {
            capacity.setVisibility(View.VISIBLE);
            capacity.setText(getString(event.capacity == 1
                    ? R.string.event_capacity_spot : R.string.event_capacity_spots,
                    event.capacity));
        } else {
            capacity.setVisibility(View.GONE);
        }

        MaterialButton register = findViewById(R.id.btnRegister);
        boolean hasRegister = !TextUtils.isEmpty(event.registration_url);
        register.setVisibility(hasRegister ? View.VISIBLE : View.GONE);
        if (hasRegister) {
            register.setBackgroundColor(accent);
            register.setTextColor(EventPalette.contrastColor(accent));
            register.setOnClickListener(v -> openUrl(event.registration_url));
        }

        MaterialButton directions = findViewById(R.id.btnDirections);
        boolean hasMap = !TextUtils.isEmpty(event.map_url);
        directions.setVisibility(hasMap ? View.VISIBLE : View.GONE);
        if (hasMap) {
            directions.setStrokeColor(ColorStateList.valueOf(accent));
            directions.setIconTint(ColorStateList.valueOf(accent));
            directions.setOnClickListener(v -> openUrl(event.map_url));
        }

        MaterialButton remind = findViewById(R.id.btnRemind);
        MaterialButton cancelReminder = findViewById(R.id.btnCancelReminder);
        remind.setOnClickListener(v -> {
            long start = EventTimes.parse(event.starts_at);
            if (start <= System.currentTimeMillis()) {
                Toast.makeText(this, R.string.event_reminder_too_late, Toast.LENGTH_SHORT).show();
                return;
            }
            EventReminderWorker.schedule(this, event, start);
            Toast.makeText(this, R.string.event_reminder_set, Toast.LENGTH_SHORT).show();
            remind.setVisibility(View.GONE);
            cancelReminder.setVisibility(View.VISIBLE);
        });
        cancelReminder.setOnClickListener(v -> {
            EventReminderWorker.cancel(this, event.id);
            Toast.makeText(this, R.string.event_reminder_cancelled, Toast.LENGTH_SHORT).show();
            cancelReminder.setVisibility(View.GONE);
            remind.setVisibility(View.VISIBLE);
        });

        ((TextView) findViewById(R.id.tvDetailDescription)).setText(event.description);
    }

    private void openContact(String contact) {
        if (TextUtils.isEmpty(contact)) return;
        String trimmed = contact.trim();
        String target = null;
        if (trimmed.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            target = "mailto:" + trimmed;
        } else if (trimmed.matches("https?://.*")) {
            target = trimmed;
        } else if (trimmed.replaceAll("[^\\d]", "").length() >= 7) {
            target = "tel:" + trimmed.replaceAll("[^+\\d]", "");
        }
        if (target != null) openUrl(target);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_message_failed, Toast.LENGTH_SHORT).show();
        }
    }
}