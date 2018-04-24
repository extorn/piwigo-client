package delit.piwigoclient.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import java.lang.reflect.InvocationTargetException;

import delit.piwigoclient.BuildConfig;

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
        return Math.round((float)px / (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static boolean isRunningOnUIThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static int getScreenRotation(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        return display.getRotation();
    }

    public static int getScreenMode(Context context) {

        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        Display getOrient = windowManager.getDefaultDisplay();

        int orientation = Configuration.ORIENTATION_UNDEFINED;
        Point p = new Point();
        getOrient.getSize(p);
        if(p.x == p.y){
            orientation = Configuration.ORIENTATION_SQUARE;
        } else{
            if(p.x < p.y) {
                orientation = Configuration.ORIENTATION_PORTRAIT;
            }else {
                orientation = Configuration.ORIENTATION_LANDSCAPE;
            }
        }
        return orientation;
    }

    public static int getNavBarHeight(Context c) {
        int result = 0;
        boolean hasMenuKey = ViewConfiguration.get(c).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);

        if(!hasMenuKey && !hasBackKey) {
            //The device has a navigation bar
            Resources resources = c.getResources();

            int orientation = resources.getConfiguration().orientation;
            int resourceId;
            if (isTablet(c)){
                resourceId = resources.getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
            }  else {
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
//        int screenMode = DisplayUtils.getScreenMode(context);
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
}
