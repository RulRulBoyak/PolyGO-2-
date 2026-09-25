package com.poliku.polygoplus;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.poliku.polygoplus.ui.UiUtils;

import java.io.File;
import java.io.FileOutputStream;

/** A small, dependency-free square cropper for profile photos. */
public class ProfilePhotoCropActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_URI = "source_uri";
    public static final String EXTRA_RESULT_URI = "result_uri";

    private ProfileCropView cropView;
    private CustomTarget<Bitmap> imageTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_photo_crop);

        cropView = findViewById(R.id.profileCropView);
        MaterialButton usePhoto = findViewById(R.id.btnUsePhoto);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        usePhoto.setOnClickListener(v -> saveCrop(usePhoto));

        String source = getIntent().getStringExtra(EXTRA_SOURCE_URI);
        if (source == null || source.isEmpty()) {
            finish();
            return;
        }
        usePhoto.setEnabled(false);
        imageTarget = new CustomTarget<Bitmap>(2048, 2048) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                cropView.setBitmap(resource);
                usePhoto.setEnabled(true);
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                // The activity owns the target until it closes.
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                UiUtils.snackbarError(findViewById(android.R.id.content), R.string.crop_photo_load_failed);
            }
        };
        Glide.with(this).asBitmap().load(Uri.parse(source)).into(imageTarget);
    }

    private void saveCrop(View trigger) {
        trigger.setEnabled(false);
        try {
            File folder = new File(getCacheDir(), "profile-crops");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Cannot create crop folder");
            File[] oldCrops = folder.listFiles();
            if (oldCrops != null) {
                for (File oldCrop : oldCrops) oldCrop.delete();
            }
            File output = new File(folder, "profile-" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!cropView.createCroppedBitmap(1024).compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    throw new IllegalStateException("Cannot encode crop");
                }
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", output);
            Intent result = new Intent().putExtra(EXTRA_RESULT_URI, uri.toString());
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, result);
            finish();
        } catch (Exception e) {
            trigger.setEnabled(true);
            UiUtils.snackbarError(findViewById(android.R.id.content), R.string.crop_photo_save_failed);
        }
    }

    @Override
    protected void onDestroy() {
        if (imageTarget != null) Glide.with(this).clear(imageTarget);
        super.onDestroy();
    }

    public static final class ProfileCropView extends View {
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outsidePath = new Path();
        private final Matrix imageMatrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private Bitmap bitmap;
        private float baseScale;
        private float scale = 1f;
        private float translateX;
        private float translateY;
        private float lastX;
        private float lastY;
        private boolean dragging;

        public ProfileCropView(Context context, @Nullable android.util.AttributeSet attrs) {
            super(context, attrs);
            shadePaint.setColor(0x99000000);
            guidePaint.setColor(Color.WHITE);
            guidePaint.setStyle(Paint.Style.STROKE);
            guidePaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float oldScale = scale;
                    scale = clamp(scale * detector.getScaleFactor(), 1f, 5f);
                    float ratio = scale / oldScale;
                    translateX = detector.getFocusX() - (detector.getFocusX() - translateX) * ratio;
                    translateY = detector.getFocusY() - (detector.getFocusY() - translateY) * ratio;
                    constrainTranslation();
                    invalidate();
                    return true;
                }
            });
        }

        void setBitmap(Bitmap value) {
            bitmap = value;
            resetTransform();
        }

        private void resetTransform() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
            baseScale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            scale = 1f;
            translateX = (getWidth() - bitmap.getWidth() * baseScale) / 2f;
            translateY = (getHeight() - bitmap.getHeight() * baseScale) / 2f;
            updateMatrix();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            resetTransform();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            updateMatrix();
            canvas.drawBitmap(bitmap, imageMatrix, imagePaint);

            float radius = Math.min(getWidth(), getHeight()) / 2f - guidePaint.getStrokeWidth();
            outsidePath.reset();
            outsidePath.setFillType(Path.FillType.EVEN_ODD);
            outsidePath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            outsidePath.addCircle(getWidth() / 2f, getHeight() / 2f, radius, Path.Direction.CW);
            canvas.drawPath(outsidePath, shadePaint);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, guidePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !scaleDetector.isInProgress()) {
                        translateX += event.getX() - lastX;
                        translateY += event.getY() - lastY;
                        constrainTranslation();
                        invalidate();
                    }
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        Bitmap createCroppedBitmap(int size) {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
                throw new IllegalStateException("No image loaded");
            }
            Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            float outputScale = size / (float) getWidth();
            canvas.scale(outputScale, outputScale);
            updateMatrix();
            canvas.drawBitmap(bitmap, imageMatrix, imagePaint);
            return output;
        }

        private void updateMatrix() {
            imageMatrix.reset();
            imageMatrix.postScale(baseScale * scale, baseScale * scale);
            imageMatrix.postTranslate(translateX, translateY);
        }

        private void constrainTranslation() {
            if (bitmap == null) return;
            float scaledWidth = bitmap.getWidth() * baseScale * scale;
            float scaledHeight = bitmap.getHeight() * baseScale * scale;
            translateX = clamp(translateX, getWidth() - scaledWidth, 0f);
            translateY = clamp(translateY, getHeight() - scaledHeight, 0f);
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
