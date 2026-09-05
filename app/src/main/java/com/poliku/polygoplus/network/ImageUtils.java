package com.poliku.polygoplus.network;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Utility class to compress images before uploading to save bandwidth and server space.
 */
public final class ImageUtils {
    private static final String TAG = "ImageUtils";

    private ImageUtils() {}

    /**
     * Compresses an image from a Uri and returns a new Uri pointing to the temporary compressed file.
     */
    public static Uri compressImage(Context context, Uri originalUri) {
        try {
            // 1. Decode bitmap with scaling to prevent OutOfMemory and save space
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream input = context.getContentResolver().openInputStream(originalUri);
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            int width = options.outWidth;
            int height = options.outHeight;
            int reqWidth = 1080; // Standard High Definition width
            int reqHeight = 1080;

            int inSampleSize = 1;
            if (height > reqHeight || width > reqWidth) {
                final int halfHeight = height / 2;
                final int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inSampleSize = inSampleSize;
            options.inJustDecodeBounds = false;
            
            input = context.getContentResolver().openInputStream(originalUri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();

            if (bitmap == null) return originalUri;

            // 2. Compress and save to temporary file
            File tempFile = new File(context.getCacheDir(), "upload_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out); // 80% quality is a good balance
            out.flush();
            out.close();

            Log.d(TAG, "Compressed image from " + (width * height * 4 / 1024) + "KB to " + (tempFile.length() / 1024) + "KB");
            
            return Uri.fromFile(tempFile);

        } catch (Exception e) {
            Log.e(TAG, "Error compressing image: " + e.getMessage());
            return originalUri; // Fallback to original if compression fails
        }
    }
}
