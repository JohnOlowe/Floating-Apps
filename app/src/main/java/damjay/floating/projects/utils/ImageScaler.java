package damjay.floating.projects.utils;

import android.graphics.Bitmap;

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
        scale += 0.1f;
    }

    public void decreaseScale() {
        if (scale - 0.1f >= defaultMinScale) {
            scale = scale - 0.1f;
        }
    }

    public void setDefaultMinScale(float defaultMinScale) {
        this.defaultMinScale = defaultMinScale;
    }

    public float getDefaultMinScale() {
        return defaultMinScale;
    }

    public Bitmap getFastScaled(Bitmap bitmap) {
        if (scale < defaultMinScale) {
            scale = defaultMinScale;
        }
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), false);
    }

    public Bitmap getScaled(Bitmap bitmap) {
        if (scale < defaultMinScale) {
            scale = defaultMinScale;
        }
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), true);
    }
}
