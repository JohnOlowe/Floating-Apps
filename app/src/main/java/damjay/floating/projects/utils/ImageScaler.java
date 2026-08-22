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
        return this.scale;
    }

    public void increaseScale() {
        this.scale += 0.1f;
    }

    public void decreaseScale() {
        if (f - 0.1f >= this.defaultMinScale) {
            this.scale = f - 0.1f;
        }
    }

    public void setDefaultMinScale(float defaultMinScale) {
        this.defaultMinScale = defaultMinScale;
    }

    public float getDefaultMinScale() {
        return this.defaultMinScale;
    }

    public Bitmap getFastScaled(Bitmap bitmap) {
        if (f < f2) {
            this.scale = f2;
        }
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * this.scale), (int) (bitmap.getHeight() * this.scale), false);
    }

    public Bitmap getScaled(Bitmap bitmap) {
        if (f < f2) {
            this.scale = f2;
        }
        return Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * this.scale), (int) (bitmap.getHeight() * this.scale), true);
    }
}
