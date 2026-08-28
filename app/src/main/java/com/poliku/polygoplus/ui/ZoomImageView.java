package com.poliku.polygoplus.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

public class ZoomImageView extends AppCompatImageView {
    private final Matrix matrix = new Matrix();
    private final Matrix saved = new Matrix();
    private final PointF start = new PointF();
    private final ScaleGestureDetector detector;
    private Mode mode = Mode.NONE;

    private enum Mode { NONE, DRAG, ZOOM }

    public ZoomImageView(Context context) {
        this(context, null);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float scale = d.getScaleFactor();
                matrix.postScale(scale, scale, d.getFocusX(), d.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        detector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                saved.set(matrix);
                start.set(event.getX(), event.getY());
                mode = Mode.DRAG;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == Mode.DRAG) {
                    matrix.set(saved);
                    matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                    setImageMatrix(matrix);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mode = Mode.ZOOM;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = Mode.NONE;
                break;
        }
        return true;
    }
}
