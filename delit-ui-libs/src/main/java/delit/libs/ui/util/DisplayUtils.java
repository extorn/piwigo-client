
package delit.libs.ui.util;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsSpinner;
import android.widget.AdapterView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStoreOwner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.util.SafeRunnable;

/**
 * Created by gareth on 30/05/17.
 */

public class DisplayUtils {

    private static final String TAG = "displayUtils";

    public static int dpToPx(Context context, int dp) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    /**
     * This is used on old versions of android only - probably pre lollipop
     * @param context
     * @param color
     */
    public static void setOverscrollGlowColor(@NonNull Context context, @ColorInt int color) {
        @DrawableRes int glowDrawableId = context.getResources().getIdentifier("overscroll_glow", "drawable", "android");
        if(glowDrawableId != Resources.ID_NULL) {
            Drawable androidGlow = ContextCompat.getDrawable(context, glowDrawableId);
            if(androidGlow != null) {
                androidGlow = DrawableCompat.wrap(androidGlow);
//        DrawableCompat.setTint(androidGlow, color);
                DrawableCompat.setTintList(androidGlow, ColorStateList.valueOf(color));
                DrawableCompat.setTintMode(androidGlow, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    /**
     * This is used on old versions of android only - probably pre lollipop
     * @param context
     * @param color
     */
    public static void setOverscrollEdgeColor(@NonNull Context context, @ColorInt int color) {
        @DrawableRes int overscrollEdgeDrawableId = context.getResources().getIdentifier("overscroll_edge", "drawable", "android");
        if(overscrollEdgeDrawableId != Resources.ID_NULL) {
            Drawable androidOverscrollEdge = ContextCompat.getDrawable(context, overscrollEdgeDrawableId);
            androidOverscrollEdge = DrawableCompat.wrap(androidOverscrollEdge);
//        DrawableCompat.setTint(androidOverscrollEdge, color);
            DrawableCompat.setTintList(androidOverscrollEdge, ColorStateList.valueOf(color));
            DrawableCompat.setTintMode(androidOverscrollEdge, PorterDuff.Mode.SRC_ATOP);
        }
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
        return type.cast(currentItem);
    }

    public static String getResourceName(Context context, int resourceId) {
        return context.getResources().getResourceName(resourceId);
    }

    /**
     * Use this for debug - to work out which theme is being used.
     * @param context
     * @return
     */
    public static String getThemeName(Context context) {
        @StyleRes int id = getThemeId(context);
        if(id == View.NO_ID) {
            return "Unknown";
        }
        return getResourceName(context, getThemeId(context));
    }

    public static @StyleRes int getThemeId(Context context) {
        int themeResId = 0;
        try {
            if(context instanceof ContextThemeWrapper) {
                Class<?> clazz = ContextThemeWrapper.class;
                Method method = clazz.getMethod("getThemeResId");
                method.setAccessible(true);
                themeResId = (Integer) method.invoke(context);
                return themeResId;
            } else if(context instanceof ContextWrapper) {
                Class<?> clazz = ContextWrapper.class;
                //noinspection JavaReflectionMemberAccess
                Method method = clazz.getMethod("getThemeResId");
                method.setAccessible(true);
                themeResId = (Integer) method.invoke(context);
                return themeResId;
            }
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Failed to get theme resource ID", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Failed to get theme resource ID", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get theme resource ID", e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Failed to get theme resource ID", e);
        }
        return View.NO_ID;
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

    public static float getScreenWidthInches(Activity activity) {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
//        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        float dpi = dm.xdpi;
        if(dm.xdpi == dm.scaledDensity) {
            //xdpi is garbage. Use DPI
            dpi = dm.densityDpi;
        }
        return (float) dm.widthPixels / dpi;
    }

    public static float getScreenHeightInches(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        float dpi = dm.xdpi;
        if(dm.xdpi == dm.scaledDensity) {
            //xdpi is garbage. Use DPI
            dpi = dm.densityDpi;
        }
        return (float) dm.heightPixels / dpi;
    }

    public static int getDefaultColumnCount(@NonNull Activity activity, int screenOrientation, double minWidthInches) {

        float screenWidth;
        if (screenOrientation == getCurrentScreenOrientation(activity)) {
            screenWidth = getScreenWidthInches(activity);
        } else {
            screenWidth = getScreenHeightInches(activity);
        }
        int columnsToShow = (int) Math.max(1, Math.round(screenWidth) / minWidthInches); // never allow less than one column by default.
        columnsToShow = Math.min(columnsToShow, 20); // sanity catch all!
        return columnsToShow;
    }

    public static void postOnUiThread(Runnable runnable) {
        runOnUiThread(runnable, 0);
    }

    public static void runOnUiThread(Runnable runnable, long delay) {
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(runnable, delay);
    }

    public static void runOnUiThread(Runnable runnable, boolean frontOfTheQueue) {
        if (isRunningOnUIThread()) {
            runnable.run();
        } else {
            Handler h = new Handler(Looper.getMainLooper());
            Runnable r = new SafeRunnable(runnable);
            if(frontOfTheQueue) {
                h.postAtFrontOfQueue(r);
            } else {
                h.post(r);
            }
        }
    }

    public static void runOnUiThread(Runnable runnable) {
        runOnUiThread(runnable, false);
    }

    public static void setUiFlags(FragmentActivity activity, boolean showNavButtons, boolean showStatusBar) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Logging.log(Log.DEBUG, TAG, "Unable to go fullscreen on old versions of android");
            return;
        }
        if (activity.hasWindowFocus()) {
            // Enables regular immersive mode.
            // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
            // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE
            Window window = activity.getWindow();
            View decorView = window.getDecorView();
            // force user to swipe up from bottom or down from top to bring back status bars
            int uiFlags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            int currentFlags = decorView.getSystemUiVisibility();
            if (!showNavButtons) {
                // Make content appear behind the navigation bar
                uiFlags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        // hide the navigation bar
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            }
            if (!showStatusBar) {
                // hide the status bar
                uiFlags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // make content appear behind the status bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN;

                // Remember that you should never show the action bar if the
                // status bar is hidden, so hide that too if necessary.
                ActionBar actionBar = activity.getActionBar();
                if (actionBar != null) {
                    actionBar.hide();
                }

            }
            if(currentFlags != uiFlags) {
                decorView.setSystemUiVisibility(uiFlags);
            }
//            decorView.setOnSystemUiVisibilityChangeListener(new SystemUiVisibilityChangeListener(decorView, uiFlags));
        }


    }

    public static Context updateContext(Context context, Locale locale) {
        Context c;
        // the non legacy way doesn't seem to work at all. Nothing gets updated. Maybe because I cache the context somewhere (old way changes items on the old context rather than cloning).
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            c = updateResourcesLocale(context, locale);
        } else*/
        {
            c = updateResourcesLocaleLegacy(context, locale);
        }
        Locale.setDefault(locale);
        return c;
    }

    @TargetApi(Build.VERSION_CODES.N)
    private static Context updateResourcesLocale(Context context, Locale locale) {
        Locale currentDefault = Locale.getDefault();
        Locale.setDefault(locale);
        LocaleListCompat resortedList = LocaleListCompat.getAdjustedDefault();
        Locale.setDefault(currentDefault);
        Configuration configuration = context.getResources().getConfiguration();
        List<Locale> locales = new ArrayList<>(resortedList.size());
        for (int i = 0; i < resortedList.size(); i++) {
            locales.add(resortedList.get(i));
        }
        LocaleList list = new LocaleList(locales.toArray(new Locale[0]));
        configuration.setLocales(list);

        return context.createConfigurationContext(configuration);
    }

    private static Context updateResourcesLocaleLegacy(Context context, Locale locale) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        configuration.locale = locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLayoutDirection(locale);
        }
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }

    public static @Nullable LifecycleOwner getLifecycleOwner(Context aContext) {
        Context context = aContext;
        while (!(context instanceof LifecycleOwner)) {
            if(context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
            } else {
                Logging.log(Log.ERROR, TAG, "Unable to find context lifecycle owner");
                Logging.recordException(new Exception().fillInStackTrace());
                return null;
            }
        }
        return (LifecycleOwner) context;
    }

    public static ViewModelStoreOwner getViewModelStoreOwner(Context aContext) {
        Context context = aContext;
        while (!(context instanceof ViewModelStoreOwner)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (ViewModelStoreOwner) context;
    }

    /**
     * Note that the view clicked on is null because there was no real click (the view does not actually exist if in dropdown mode).
     * @param spinner
     * @param newPos
     */
    public static void selectSpinnerItemAndCallItemSelectedListener(AbsSpinner spinner, int newPos) {
        int itemCount = spinner.getCount();
        if(newPos >= itemCount) {
            throw new IllegalArgumentException("Cannot select position "+newPos + " as it is out of range (0 - "+ (itemCount-1)+")");
        }
        int currentSelectedItem = spinner.getSelectedItemPosition();
        spinner.setSelection(newPos);
        newPos = spinner.getSelectedItemPosition();
        long itemId = spinner.getItemIdAtPosition(newPos);
        if(newPos != currentSelectedItem) { // try and avoid the listener being called twice.
            AdapterView.OnItemSelectedListener listener = spinner.getOnItemSelectedListener();
            if (listener != null) {
                listener.onItemSelected(spinner, null, newPos, itemId);
            }
        }
    }

    public static String getMeasureModeText(int desiredHeightMeasureMode) {
        String measureMode = null;
        switch(desiredHeightMeasureMode) {
            case View.MeasureSpec.AT_MOST: measureMode = "At most";
                break;
            case View.MeasureSpec.EXACTLY: measureMode = "Exactly";
                break;
            case View.MeasureSpec.UNSPECIFIED: measureMode = "Unspecified";
                break;
        }
        return measureMode;
    }

    /**
     * retrieve a Color int (NOT a colors ref Id) which is referenced by a theme attribute
     * @param context
     * @param attrResId
     * @return
     */
    public static @ColorInt int getColor(@NonNull Context context, @AttrRes int attrResId) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Loading color from theme : " + getThemeName(context));
        }
        theme.resolveAttribute(attrResId, typedValue, true);
        @ColorInt int color;
        if(typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            color = typedValue.data;
        } else {
            color = ContextCompat.getColor(context, typedValue.resourceId);
        }

        return color;
    }

    public static @StyleRes int getStyle(@NonNull Context context, @AttrRes int attrResId) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(attrResId, typedValue, true);
        @StyleRes int style = typedValue.data;
        return style;
    }

    public static Bitmap getBitmap(Drawable drawable) {
        Bitmap bitmap = null;
        if(drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        }
        if(bitmap == null) {
            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single colors bitmap will be created of 1x1 pixel
            } else {
                bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        return bitmap;
    }

    public static Activity getActivity(Context context)
    {
        if (context == null)
        {
            return null;
        }
        else if (context instanceof ContextWrapper)
        {
            if (context instanceof Activity)
            {
                return (Activity) context;
            }
            else
            {
                return getActivity(((ContextWrapper) context).getBaseContext());
            }
        }

        return null;
    }

    public static final int PORTRAIT = 0;
    public static final int LANDSCAPE = 1;

    public static int getAspect(View view) {
        int w = view.getWidth();
        int h = view.getHeight();

        int aspect = PORTRAIT;
        if(w >= h) {
            aspect = LANDSCAPE;
        }
        return aspect;
    }

    public static int getAspect(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int aspect = PORTRAIT;
        if(w >= h) {
            aspect = LANDSCAPE;
        }
        return aspect;
    }

    /**
     * Check if a good idea.
     * @param context
     * @return
     */
    public static boolean canShowDialog(Context context) {
        Activity activity = getActivity(context);
        if(activity != null && activity.isFinishing()) {
            return false;
        }
        return true;
    }

    public static int getIndexOfChildContainingView(@NonNull ViewGroup distantParent, @NonNull View v) {
        View av = v;
        int idxMyView;
        do {
            idxMyView = distantParent.indexOfChild((View) av);
            av = (View)av.getParent();
        } while(idxMyView < 0 && av != distantParent && av != null);
        return idxMyView;
    }

    private static class SystemUiVisibilityChangeListener implements View.OnSystemUiVisibilityChangeListener {

        private View decorView;
        private int flags;
        private Hider hider = new Hider();
        private long autoHideDelayMillis = 2000;

        public SystemUiVisibilityChangeListener(View decorView, int flags) {
            this.decorView = decorView;
            this.flags = flags;
        }

        @Override
        public void onSystemUiVisibilityChange(int visibility) {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                if (hider.activate()) {
                    decorView.postDelayed(hider, autoHideDelayMillis);
                }
            }
        }

        private class Hider implements Runnable {
            private volatile boolean isActive;

            @Override
            public void run() {
                synchronized (this) {
                    decorView.setSystemUiVisibility(flags);
                    isActive = false;
                }
            }

            private boolean activate() {
                synchronized (this) {
                    if (!isActive) {
                        isActive = true;
                        return true;
                    }
                    return false;
                }
            }
        }
    }

    public static void toggleHierachyEnabled(ViewGroup viewGroup, boolean enabled) {
        setViewAndChildrenEnabled(viewGroup, enabled);
    }

    private static void setViewAndChildrenEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }

    public static @NonNull String getPathToView(@Nullable View v) {
        List<String> path = new ArrayList<>();
        if(v == null) {
            return "<null>";
        }
        path.add(v.getClass().getName());
        ViewParent currentV = v.getParent();
        if (currentV != null) {
            do {
                path.add(currentV.getClass().getName());
                currentV = v.getParent();
            } while (currentV.getParent() != null);
        }
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = path.size() - 1; i > 0; i--) {
            pathBuilder.append(path.remove(i));
            if (i > 1) {
                pathBuilder.append(" / ");
            }
        }
        return pathBuilder.toString();
    }
}
