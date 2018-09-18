package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.ArrayRes;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;

import com.crashlytics.android.Crashlytics;

import delit.piwigoclient.util.ArrayUtils;

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
    protected Integer transform(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }
        return Integer.valueOf(obj.toString());
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
            Crashlytics.logException(e);
            // this will occur if swapping this pref type in for an old string type.
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                return Integer.MIN_VALUE;
            }
            throw e;
        } catch (NullPointerException e) {
            Crashlytics.logException(e);
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
            int val = Integer.valueOf(value);
            return persistInt(val);
        } catch (NumberFormatException e) {
            Crashlytics.logException(e);
            // need to persist the default
            return persistInt(Integer.MIN_VALUE);
        }
    }
}
