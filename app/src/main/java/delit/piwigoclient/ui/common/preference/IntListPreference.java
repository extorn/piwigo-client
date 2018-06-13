package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.preference.ListPreference;
import android.support.annotation.ArrayRes;
import android.support.annotation.IdRes;
import android.support.annotation.RequiresApi;
import android.support.v4.content.res.TypedArrayUtils;
import android.util.AttributeSet;

import com.google.android.gms.common.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
        super(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public IntListPreference(Context context) {
        super(context, null);
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
//        return getPersistedInt(defaultValue);
        try {
            return getPersistedInt(defaultValue);
        } catch(ClassCastException e) {
            // this will occur if swapping this pref type in for an old string type.
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.Integer")) {
                return Integer.MIN_VALUE;
            }
            throw e;
        } catch(NullPointerException e) {
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
        } catch(NumberFormatException e) {
            // need to persist the default
            return persistInt(Integer.MIN_VALUE);
        }
    }
}
