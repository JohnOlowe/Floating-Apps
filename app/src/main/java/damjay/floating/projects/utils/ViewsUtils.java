package damjay.floating.projects.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.io.File;
import java.util.Locale;

public class ViewsUtils {
    public static Class<?> mainClass;
    public static Context mainContext;

    public static View.OnTouchListener getViewTouchListener(
            Context context, View parentLayout, WindowManager window, WindowManager.LayoutParams params) {
        return (view, event) -> {
            TouchState touchState = TouchState.getInstance();
            TouchState.moveTolerance = ViewConfiguration.get(context).getScaledTouchSlop();
            int maxParamsX = Resources.getSystem().getDisplayMetrics().widthPixels - parentLayout.getMeasuredWidth();
            int maxParamsY = Resources.getSystem().getDisplayMetrics().heightPixels - parentLayout.getMeasuredHeight();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchState.setInitialPosition(event.getRawX(), event.getRawY());
                    touchState.setOriginalPosition(params.x, params.y);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!touchState.hasMoved()) {
                        return view.performClick();
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    touchState.setFinalPosition(event.getRawX(), event.getRawY());
                    if (touchState.hasMoved()) {
                        params.x = Math.min(maxParamsX, touchState.updatedPositionX());
                        params.y = Math.min(maxParamsY, touchState.updatedPositionY());
                        window.updateViewLayout(parentLayout, params);
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        };
    }

    public static void addTouchListener(View parentView, View.OnTouchListener listener, boolean applyToChildren,
            boolean recursive, Class... allowedClasses) {
        boolean checkAbsent = allowedClasses != null && allowedClasses.length > 0
                && allowedClasses[allowedClasses.length - 1] == null;
        if ((parentView instanceof ViewGroup) && (applyToChildren || recursive)) {
            ViewGroup viewGroup = (ViewGroup) parentView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                boolean present = isPresent(child.getClass(), allowedClasses);
                boolean present2 = checkAbsent != present;
                if (recursive && present2) {
                    addTouchListener(child, listener, applyToChildren, recursive, allowedClasses);
                } else if (present2) {
                    child.setOnTouchListener(listener);
                }
            }
        }
        boolean present3 = isPresent(parentView.getClass(), allowedClasses);
        boolean present4 = checkAbsent != present3;
        if (present4) {
            parentView.setOnTouchListener(listener);
        }
    }

    private static boolean isPresent(Class<?> viewClass, Class<?>[] classes) {
        if (classes != null && viewClass != null) {
            for (Class<?> clazz : classes) {
                if (viewClass == clazz) {
                    return true;
                }
            }
        }
        return classes == null || classes.length == 0;
    }

    public static void openAppInfo(Activity activity, String packageName) {
        openAppInfo(activity, packageName, -911);
    }

    public static void openAppInfo(Activity activity, String packageName, int requestCode) {
        Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
        Uri uri = Uri.fromParts("package", packageName, null);
        intent.setData(uri);
        if (requestCode == -911) {
            activity.startActivity(intent);
        } else {
            activity.startActivityForResult(intent, requestCode);
        }
    }

    public static void launchApp(Context context, Class<?> mainActivity) {
        Intent intent = mainActivity.getName().contains("MainActivity") ? new Intent("android.intent.category.LAUNCHER")
                                                                        : new Intent();
        Class<?> cls = mainClass;
        String classPackage = cls == null ? mainActivity.getPackage().getName() : cls.getPackage().getName();
        String fullClassName = mainActivity.getCanonicalName();
        intent.setClassName(classPackage, fullClassName);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static int getViewWidth(float minSmallestWidth) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float smallestDeviceWidth = metrics.widthPixels / metrics.density;
        if (smallestDeviceWidth / 2.0f >= minSmallestWidth) {
            int viewWidth = metrics.widthPixels;
            return viewWidth / 2;
        }
        if (smallestDeviceWidth >= minSmallestWidth) {
            int viewWidth2 = (int) (metrics.density * minSmallestWidth);
            return viewWidth2;
        }
        int viewWidth3 = metrics.widthPixels;
        return viewWidth3;
    }

    public static int getViewHeight(float minSmallestHeight) {
        DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        float smallestDeviceHeight = metrics.heightPixels / metrics.density;
        if (smallestDeviceHeight / 2.0f >= minSmallestHeight) {
            int viewHeight = metrics.widthPixels;
            return viewHeight / 2;
        }
        if (smallestDeviceHeight >= minSmallestHeight) {
            int viewHeight2 = (int) (metrics.density * minSmallestHeight);
            return viewHeight2;
        }
        int viewHeight3 = metrics.heightPixels;
        return viewHeight3;
    }

    public static WindowManager.LayoutParams getFloatingLayoutParams(int x, int y) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                                                  : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    public static WindowManager.LayoutParams getFloatingLayoutParams() {
        return getFloatingLayoutParams(0, 100);
    }

    public static WindowManager.LayoutParams getFloatingLayoutParams(boolean focused) {
        WindowManager.LayoutParams params = getFloatingLayoutParams();
        if (focused) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
        return params;
    }

    public static int spToPx(int sp, Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    public static int dpToPx(int dp, Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static void openDownloads(Activity activity) {
        if (isSamsung()) {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.sec.android.app.myfiles");
            intent.setAction("samsung.myfiles.intent.action.LAUNCH_MY_FILES");
            intent.putExtra("samsung.myfiles.intent.extra.START_PATH", getDownloadsFile().getPath());
            activity.startActivity(intent);
            return;
        }
        activity.startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
    }

    public static boolean isSamsung() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null) {
            return manufacturer.toLowerCase(Locale.getDefault()).equals("samsung");
        }
        return false;
    }

    public static File getDownloadsFile() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }
}
