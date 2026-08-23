package damjay.floating.projects.utils;

import android.graphics.Bitmap;

/**
 * Utility for scaling bitmap images with configurable minimum scale limits.
 * Supports both fast and smooth scaling modes.
 */
public class ImageScaler {

    private static final float SCALE_CHANGE = 0.1f;

    private float defaultMinScale = 0.1f;
    private float scale = 1.0f;

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getScale() {
        return scale;
    }

    public void increaseScale() {
        scale += SCALE_CHANGE;
    }

    public void decreaseScale() {
        float adjusted = scale - SCALE_CHANGE;
        if (adjusted >= defaultMinScale) {
            scale = adjusted;
        }
    }

    public void setDefaultMinScale(float defaultMinScale) {
        this.defaultMinScale = defaultMinScale;
    }

    public float getDefaultMinScale() {
        return defaultMinScale;
    }

    /** Scale bitmap quickly without filtering */
    public Bitmap getFastScaled(Bitmap bitmap) {
        float currentScale = Math.max(scale, defaultMinScale);
        scale = currentScale;
        return Bitmap.createScaledBitmap(
                bitmap,
                (int) (bitmap.getWidth() * scale),
                (int) (bitmap.getHeight() * scale),
                false);
    }

    /** Scale bitmap with filtering for smoother results */
    public Bitmap getScaled(Bitmap bitmap) {
        float currentScale = Math.max(scale, defaultMinScale);
        scale = currentScale;
        return Bitmap.createScaledBitmap(
                bitmap,
                (int) (bitmap.getWidth() * scale),
                (int) (bitmap.getHeight() * scale),
                true);
    }
}
