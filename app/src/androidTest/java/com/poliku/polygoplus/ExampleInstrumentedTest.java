package com.poliku.polygoplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.poliku.polygoplus", appContext.getPackageName());
    }

    @Test
    public void registerForm_keepsPasswordToggleAndClickableConsent() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, RegisterActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        TextInputLayout password = activity.findViewById(R.id.tilPassword);
        TextInputLayout confirmPassword = activity.findViewById(R.id.tilConfirmPassword);
        TextInputLayout matrix = activity.findViewById(R.id.tilMatrix);
        EditText name = activity.findViewById(R.id.etFullName);
        MaterialCheckBox consent = activity.findViewById(R.id.cbTerms);
        Button submit = activity.findViewById(R.id.btnRegisterAction);

        assertEquals(TextInputLayout.END_ICON_PASSWORD_TOGGLE, password.getEndIconMode());
        assertEquals(TextInputLayout.END_ICON_PASSWORD_TOGGLE,
                confirmPassword.getEndIconMode());
        assertNull(password.getErrorIconDrawable());
        assertEquals(activity.getString(R.string.action_continue), submit.getText().toString());
        assertTrue(submit.isEnabled());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> name.setText("Maya"));
        assertNull(matrix.getError());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(submit::performClick);
        assertNotNull(matrix.getError());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(consent::performClick);
        assertTrue(consent.isChecked());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
    }
}
