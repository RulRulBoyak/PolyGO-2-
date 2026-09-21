package com.poliku.polygoplus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.ui.BaseActivity;
import com.poliku.polygoplus.util.CaCalculator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CaCalculatorActivity extends BaseActivity {
    private LinearLayout componentContainer;
    private EditText subject;
    private EditText caWeight;
    private EditText feWeight;
    private EditText target;
    private View resultCard;
    private TextView resultText;

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_ca_calculator);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        componentContainer = findViewById(R.id.componentContainer);
        subject = findViewById(R.id.etSubject);
        caWeight = findViewById(R.id.etCaWeight);
        feWeight = findViewById(R.id.etFeWeight);
        target = findViewById(R.id.etTarget);
        resultCard = findViewById(R.id.resultCard);
        resultText = findViewById(R.id.tvResult);
        findViewById(R.id.btnAddComponent).setOnClickListener(v -> addRow(null));
        findViewById(R.id.btnCalculate).setOnClickListener(v -> calculate());
        findViewById(R.id.btnSavePreset).setOnClickListener(v -> savePreset());
        findViewById(R.id.btnLoadPreset).setOnClickListener(v -> choosePreset());
        if (state == null) {
            addRow(component(getString(R.string.ca_default_quiz), 0, 100, 30));
            addRow(component(getString(R.string.ca_default_lab), 0, 100, 30));
        }
    }

    private JSONObject component(String name, double obtained, double maximum, double weight) {
        JSONObject value = new JSONObject();
        try {
            value.put("name", name);
            value.put("obtained", obtained);
            value.put("maximum", maximum);
            value.put("weight", weight);
        } catch (JSONException ignored) {
        }
        return value;
    }

    private void addRow(@Nullable JSONObject value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_ca_component,
                componentContainer, false);
        EditText name = row.findViewById(R.id.etComponentName);
        EditText obtained = row.findViewById(R.id.etObtained);
        EditText maximum = row.findViewById(R.id.etMaximum);
        EditText weight = row.findViewById(R.id.etComponentWeight);
        if (value != null) {
            name.setText(value.optString("name"));
            obtained.setText(format(value.optDouble("obtained")));
            maximum.setText(format(value.optDouble("maximum")));
            weight.setText(format(value.optDouble("weight")));
        }
        row.findViewById(R.id.btnRemoveComponent).setOnClickListener(v -> {
            if (componentContainer.getChildCount() > 1) componentContainer.removeView(row);
        });
        row.findViewById(R.id.btnMoveUp).setOnClickListener(v -> moveRow(row, -1));
        row.findViewById(R.id.btnMoveDown).setOnClickListener(v -> moveRow(row, 1));
        componentContainer.addView(row);
    }

    private void moveRow(View row, int direction) {
        int current = componentContainer.indexOfChild(row);
        int destination = current + direction;
        if (destination < 0 || destination >= componentContainer.getChildCount()) return;
        componentContainer.removeViewAt(current);
        componentContainer.addView(row, destination);
    }

    private void calculate() {
        try {
            double ca = number(caWeight);
            double fe = number(feWeight);
            double goal = number(target);
            if (goal < 0 || goal > 100) throw new IllegalArgumentException();
            CaCalculator.Result result = CaCalculator.calculate(ca, fe, goal,
                    readComponents(null));
            String feLine = fe == 0 ? getString(R.string.ca_no_final_exam)
                    : getString(R.string.ca_required_fe, result.requiredFePercent);
            String warning = result.targetPossible ? ""
                    : "\n\n" + getString(R.string.ca_impossible);
            String minimum = result.caMinimumMet ? getString(R.string.ca_minimum_met)
                    : getString(R.string.ca_minimum_not_met);
            resultText.setText(getString(R.string.ca_result_format, result.caContribution,
                    result.caPercent, feLine, minimum) + warning);
            resultCard.setVisibility(View.VISIBLE);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.ca_invalid_input, Toast.LENGTH_LONG).show();
        }
    }

    private List<CaCalculator.Component> readComponents(@Nullable JSONArray json) {
        List<CaCalculator.Component> values = new ArrayList<>();
        for (int i = 0; i < componentContainer.getChildCount(); i++) {
            View row = componentContainer.getChildAt(i);
            String name = text(row, R.id.etComponentName);
            if (name.isEmpty()) throw new IllegalArgumentException();
            double obtained = number(row.findViewById(R.id.etObtained));
            double maximum = number(row.findViewById(R.id.etMaximum));
            double weight = number(row.findViewById(R.id.etComponentWeight));
            values.add(new CaCalculator.Component(obtained, maximum, weight));
            if (json != null) json.put(component(name, obtained, maximum, weight));
        }
        return values;
    }

    private void savePreset() {
        String name = subject.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.ca_subject_required, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray components = new JSONArray();
            List<CaCalculator.Component> values = readComponents(components);
            CaCalculator.calculate(number(caWeight), number(feWeight), number(target), values);
            JSONArray presets = new JSONArray(AppDataStore.caPresetsJson(this));
            JSONObject preset = new JSONObject();
            preset.put("name", name);
            preset.put("ca", number(caWeight));
            preset.put("fe", number(feWeight));
            preset.put("target", number(target));
            preset.put("components", components);
            JSONArray updated = new JSONArray();
            updated.put(preset);
            for (int i = 0; i < presets.length(); i++) {
                JSONObject old = presets.optJSONObject(i);
                if (old != null && !name.equalsIgnoreCase(old.optString("name"))) {
                    updated.put(old);
                }
            }
            AppDataStore.saveCaPresetsJson(this, updated.toString());
            Toast.makeText(this, R.string.ca_preset_saved, Toast.LENGTH_SHORT).show();
        } catch (JSONException | IllegalArgumentException e) {
            Toast.makeText(this, R.string.ca_invalid_input, Toast.LENGTH_LONG).show();
        }
    }

    private void choosePreset() {
        try {
            JSONArray presets = new JSONArray(AppDataStore.caPresetsJson(this));
            if (presets.length() == 0) {
                Toast.makeText(this, R.string.ca_no_presets, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[presets.length()];
            for (int i = 0; i < presets.length(); i++) {
                names[i] = presets.getJSONObject(i).getString("name");
            }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.ca_load_preset)
                    .setItems(names, (dialog, which) -> loadPreset(presets.optJSONObject(which)))
                    .show();
        } catch (JSONException e) {
            Toast.makeText(this, R.string.ca_no_presets, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPreset(JSONObject preset) {
        if (preset == null) return;
        subject.setText(preset.optString("name"));
        caWeight.setText(format(preset.optDouble("ca")));
        feWeight.setText(format(preset.optDouble("fe")));
        target.setText(format(preset.optDouble("target")));
        componentContainer.removeAllViews();
        JSONArray items = preset.optJSONArray("components");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) addRow(items.optJSONObject(i));
        }
    }

    private double number(EditText field) {
        String raw = field.getText().toString().trim();
        try {
            ParsePosition position = new ParsePosition(0);
            Number parsed = NumberFormat.getNumberInstance(Locale.getDefault())
                    .parse(raw, position);
            if (parsed == null || !Double.isFinite(parsed.doubleValue())) {
                throw new IllegalArgumentException();
            }
            if (position.getIndex() != raw.length()) throw new IllegalArgumentException();
            return parsed.doubleValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String text(View row, int id) {
        return ((EditText) row.findViewById(id)).getText().toString().trim();
    }

    private String format(double value) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value);
    }
}
