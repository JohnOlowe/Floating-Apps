package damjay.floating.projects.utils;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.Locale;

public class ViewsUtils {
    public static Class<?> mainClass;
    public static Context mainContext;
        
    public static View.OnTouchListener getViewTouchListener(
            final Context context,
            final View parentLayout,
            final WindowManager window,
            final WindowManager.LayoutParams params) {
        return (view, event) -> {
            TouchState touchState = TouchState.getInstance();
            TouchState.moveTolerance = ViewConfiguration.get(context).getScaledTouchSlop();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchState.setInitialPosition(event.getRawX(), event.getRawY());
                    touchState.setOriginalPosition(params.x, params.y);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    touchState.setFinalPosition(event.getRawX(), event.getRawY());
                    if (touchState.hasMoved()) {
                        int maxX = Resources.getSystem().getDisplayMetrics().widthPixels - parentLayout.getMeasuredWidth();
                        int maxY = Resources.getSystem().getDisplayMetrics().heightPixels - parentLayout.getMeasuredHeight();
                        params.x = Math.min(maxX, touchState.updatedPositionX());
                        params.y = Math.min(maxY, touchState.updatedPositionY());
                        window.updateViewLayout(parentLayout, params);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!touchState.hasMoved()) {
                        return view.performClick();
                    }
            }
            return false;
        };
    }
    
    /**
     * If applyToChildren is set, and recursive isn't, if this item is a view group,
     * the touch listener is only added to the immediate allowed child views, and
     * never added to the grand child views.
     *
     * Flag recursive doesn't depend on applyToChildren, if recursive is set, allowed
     * child and grand child views get the touch listener applied to them
     * 
     * If the last element of allowedClasses array is null, the array is treated as
     * forbidden classes. Also if allowedClasses is null, no class filter is applied.
     */
    public static void addTouchListener(
            View parentView,
            View.OnTouchListener listener,
            boolean applyToChildren,
            boolean recursive,
            Class... allowedClasses) {
        // Check if absent if the last class in the array is null
        boolean checkAbsent =
                allowedClasses != null
                        && allowedClasses.length > 0
                        && allowedClasses[allowedClasses.length - 1] == null;
        if (parentView instanceof ViewGroup && (applyToChildren || recursive)) {
            ViewGroup viewGroup = (ViewGroup) parentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                boolean present = isPresent(child.getClass(), allowedClasses);
                present = checkAbsent != present;
                if (recursive && present) {
                    addTouchListener(child, listener, applyToChildren, recursive, allowedClasses);
                } else {
                    if (present) {
                        child.setOnTouchListener(listener);
                    }
                }
            }
        }
        boolean present = isPresent(parentView.getClass(), allowedClasses);
        // If we are to check the absent classes in the allowed classes array, toggle the "present" variable.
        present = checkAbsent != present;
        if (present) {
            parentView.setOnTouchListener(listener);
        }
    }

    // Check if viewClass is present
    private static boolean isPresent(Class<?> viewClass, Class<?>[] classes) {
        if (classes != null && viewClass != null) {
            for (Class<?> clazz : classes) {
                if (viewClass == clazz) {
                    return true;
                }
            }
        }
        // If nothing was specified, match all classes
        return classes == null || classes.length == 0;
    }
    
    public static void openAppInfo(Activity activity, String packageName) {
        openAppInfo(activity, packageName, -911);
    }
    
    public static void openAppInfo(Activity activity, String packageName, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", packageName, null);
        intent.setData(uri);
        if (requestCode == -911) {
            activity.startActivity(intent);
        } else {
            activity.startActivityForResult(intent, requestCode);
        }
    }

    public static void launchApp(Context context, Class<?> mainActivity) {
        Intent intent = mainActivity.getName().contains("MainActivity")
                ? new Intent("android.intent.category.LAUNCHER")
                : new Intent();
        String classPackage = mainClass != null ? mainClass.getPackage().getName() : mainActivity.getPackage().getName();
        String fullClassName = mainActivity.getCanonicalName();
        intent.setClassName(classPackage, fullClassName);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static int getViewWidth(float minSmallestWidth) {
        float smallestDeviceWidth;
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        smallestDeviceWidth = (float) metrics.widthPixels / metrics.density;

        int viewWidth;
        if (smallestDeviceWidth / 2 < minSmallestWidth) {
            // If the smallest device width is less than min smallest width, use the device width
            if (smallestDeviceWidth < minSmallestWidth) viewWidth = metrics.widthPixels;
            // Use the min smallest width, convert it to width pixels
            else viewWidth = (int) (minSmallestWidth * metrics.density);
        } else {
            // Use half of the device width
            viewWidth = metrics.widthPixels / 2;
        }
        return viewWidth;
    }
    
    public static int getViewHeight(float minSmallestHeight) {
        float smallestDeviceHeight;
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        smallestDeviceHeight = (float) metrics.heightPixels / metrics.density;

        int viewHeight;
        if (smallestDeviceHeight / 2 < minSmallestHeight) {
            // If the smallest device height is less than min smallest height, use the device height
            if (smallestDeviceHeight < minSmallestHeight) viewHeight = metrics.heightPixels;
            // Use the min smallest height, convert it to height pixels
            else viewHeight = (int) (minSmallestHeight * metrics.density);
        } else {
            // Use half of the device height
            viewHeight = metrics.widthPixels / 2;
        }
        return viewHeight;
    }
    
    
    public static LayoutParams getFloatingLayoutParams(int x, int y) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? LayoutParams.TYPE_APPLICATION_OVERLAY : LayoutParams.TYPE_PHONE;
        
        LayoutParams params = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            type,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }
    
    public static LayoutParams getFloatingLayoutParams() {
        return getFloatingLayoutParams(0, 100);
    }

    /**
     * Window params for a view added by an AccessibilityService.
     *
     * <p>TYPE_ACCESSIBILITY_OVERLAY is the right window type for these: unlike
     * TYPE_APPLICATION_OVERLAY it does not require the "display over other apps"
     * permission, because the user already granted the far stronger accessibility
     * permission.
     */
    @RequiresApi(22)
    public static LayoutParams getAccessibilityOverlayParams(int x, int y) {
        LayoutParams params = getFloatingLayoutParams(x, y);
        params.type = LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        return params;
    }

    /**
     * As {@link #getAccessibilityOverlayParams(int, int)} but with an explicit size.
     *
     * <p>A view added straight to the WindowManager is sized by these params, not by the
     * layout_width/layout_height in its XML, so anything that has to stay square (a round
     * click point, say) must say so here.
     */
    @RequiresApi(22)
    public static LayoutParams getAccessibilityOverlayParams(int x, int y, int width, int height) {
        LayoutParams params = getAccessibilityOverlayParams(x, y);
        params.width = width;
        params.height = height;
        return params;
    }

    public static LayoutParams getFloatingLayoutParams(boolean focusable) {
        LayoutParams params = getFloatingLayoutParams();
        if (focusable) {
            params.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        return params;
    }

    public static int dpToPx(int value, Context context) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    public static void openDownloads(@NonNull Activity activity) {
        if (isSamsung()) {
            Intent intent = activity.getPackageManager()
                .getLaunchIntentForPackage("com.sec.android.app.myfiles");
            intent.setAction("samsung.myfiles.intent.action.LAUNCH_MY_FILES");
            intent.putExtra("samsung.myfiles.intent.extra.START_PATH", 
                            getDownloadsFile().getPath());
            activity.startActivity(intent);
        } else activity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
    }

    public static boolean isSamsung() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) return manufacturer.toLowerCase(Locale.getDefault()).equals("samsung");
        return false;
    }

    public static File getDownloadsFile() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }


}
