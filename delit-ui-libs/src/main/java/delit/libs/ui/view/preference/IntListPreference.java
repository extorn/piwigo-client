package delit.libs.ui.view.preference;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.ArrayRes;
import androidx.annotation.RequiresApi;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.PreferenceUtils;
import delit.libs.util.ArrayUtils;

public class IntListPreference extends MappedListPreference<Integer> {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public IntListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public IntListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IntListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntListPreference(Context context) {
        super(context);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return PreferenceUtils.getIntDefaultValue(this, a, index, 0);
    }

    @Override
    protected Integer transform(Object obj) {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else {
            throw new IllegalArgumentException("object must be assignable to Integer (" + obj + ")");
        }
    }

    @Override
    public Class<Integer> getEntriesClazz() {
        return Integer.class;
    }

    @Override
    protected Integer[] loadEntryValuesFromResourceId(Resources res, @ArrayRes int resourceId) {
        int[] basicVals = res.getIntArray(resourceId);
        return ArrayUtils.wrap(basicVals);
    }

    @Override
    protected void persistValue(Integer value) {
        persistInt(value);
    }

    @Override
    protected Integer getPersistedValue(Integer defaultValue) {
        int defaultInt = defaultValue != null ? defaultValue : Integer.MIN_VALUE;
        try {
            return getPersistedInt(defaultInt);
        } catch (ClassCastException e) {
            Logging.recordException(e);
            // this will occur if swapping this pref type in for an old string type.
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                return Integer.MIN_VALUE;
            }
            throw e;
        } catch (NullPointerException e) {
            Logging.recordException(e);
            // thrown if no persisted value
            return Integer.MIN_VALUE;
        }
    }

    @Override
    protected String valueAsString(Integer value) {
        return String.valueOf(value);
    }

    @Override
    protected Integer valueFromString(String valueAsString) {
        return null;
    }


    @Override
    protected boolean persistString(String value) {
        try {
            int val = Integer.parseInt(value);
            return persistInt(val);
        } catch (NumberFormatException e) {
            Logging.recordException(e);
            // need to persist the default
            return persistInt(Integer.MIN_VALUE);
        }
    }
}
