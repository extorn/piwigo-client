package delit.piwigoclient.util;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Created by gareth on 30/05/17.
 */

public class DisplayUtils {

    private static final String TAG = "displayUtils";

    public static int dpToPx(Context context, int dp) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static int pxToDp(Context context, int px) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round((float) px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static boolean isRunningOnUIThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static int getScreenRotation(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getRotation();
    }

    public static <T extends View> T getParentOfType(View item, Class<T> type) {
        ViewParent currentItem = item.getParent();
        while (currentItem != null && !type.isAssignableFrom(currentItem.getClass())) {
            currentItem = currentItem.getParent();
        }
        return (T) currentItem;
    }

    public static int getCurrentScreenOrientation(Context context) {

        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        Display getOrient = windowManager.getDefaultDisplay();

        int orientation = Configuration.ORIENTATION_LANDSCAPE;
        Point p = new Point();
        getOrient.getSize(p);
        if (p.x < p.y) {
            orientation = Configuration.ORIENTATION_PORTRAIT;
        }
        return orientation;
    }

    public static int getNavBarHeight(Context c) {
        int result = 0;
        boolean hasMenuKey = ViewConfiguration.get(c).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);

        if (!hasMenuKey && !hasBackKey) {
            //The device has a navigation bar
            Resources resources = c.getResources();

            int orientation = resources.getConfiguration().orientation;
            int resourceId;
            if (isTablet(c)) {
                resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
            } else {
                resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_width", "dimen", "android");
            }

            if (resourceId > 0) {
                return resources.getDimensionPixelSize(resourceId);
            }
        }
        return result;
    }


    private static boolean isTablet(Context c) {
        return (c.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    public static int getCurrentNavigationBarHeight(Context context) {
        Point navbarSize = getNavigationBarSize(context);
        return Math.min(navbarSize.x, navbarSize.y);
        //TODO why is this all wrong?
//        int screenMode = DisplayUtils.getCurrentScreenOrientation(context);
//        int navBarHeight = 0;
//        switch(screenMode) {
//            case Configuration.ORIENTATION_SQUARE:
//                navBarHeight = Math.min(navbarSize.x, navbarSize.y);
//                break;
//            case Configuration.ORIENTATION_UNDEFINED:
//                navBarHeight = Math.min(navbarSize.x, navbarSize.y);
//                break;
//            case Configuration.ORIENTATION_LANDSCAPE:
//                navBarHeight = navbarSize.x;
//                break;
//            case Configuration.ORIENTATION_PORTRAIT:
//                navBarHeight = navbarSize.y;
//                break;
//            default:
//                navBarHeight = Math.min(navbarSize.x, navbarSize.y);
//        }
//        return navBarHeight;
    }

    public static Point getNavigationBarSize(Context context) {
        Point appUsableSize = getAppUsableScreenSize(context);
        Point realScreenSize = getRealScreenSize(context);

        return new Point(realScreenSize.x - appUsableSize.x, realScreenSize.y - appUsableSize.y);
    }

    public static Point getAppUsableScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static Point getRealScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();

        display.getRealSize(size);

        return size;
    }

    public static boolean hideKeyboardFrom(Context context, Window window) {
        if(window == null) {
            return false;
        }
        View attachedView = window.getDecorView();
        if(attachedView == null) {
            return false;
        }

        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(attachedView.getWindowToken(), 0);
        return true;
    }

    public static boolean hideKeyboardFrom(Fragment fragment) {
        if(fragment == null || fragment.getActivity() == null) {
            return false;
        }
        Window window = fragment.getActivity().getWindow();
        return hideKeyboardFrom(fragment.getActivity().getApplicationContext(), window);
    }

    public static boolean hideKeyboardFrom(Context context, DialogInterface dialog) {
        if(dialog == null) {
            return false;
        }
        Window window = ((AlertDialog)dialog).getWindow();
        return hideKeyboardFrom(context, window);
    }

    private static float getScreenWidthInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.widthPixels / dm.xdpi;
    }

    private static float getScreenHeightInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.heightPixels / dm.xdpi;
    }

    public static int getDefaultColumnCount(Activity activity, int screenOrientation, double minWidthInches) {

        float screenWidth;
        if (screenOrientation == getCurrentScreenOrientation(activity)) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = (int) Math.max(1, Math.round(screenWidth) / minWidthInches); // never allow less than one column by default.
        return columnsToShow;
    }

    public static void runOnUiThread(Runnable runnable, long delay) {
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(runnable, delay);
    }

    public static void runOnUiThread(Runnable runnable) {
        if (isRunningOnUIThread()) {
            runnable.run();
        } else {
            Handler h = new Handler(Looper.getMainLooper());
            h.post(runnable);
        }
    }

    public static void hideAndroidStatusBar(FragmentActivity activity) {
        if (activity.hasWindowFocus()) {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE
                            // Set the content to appear under the system bars so that the
                            // content doesn't resize when the system bars hide and show.
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }
}
