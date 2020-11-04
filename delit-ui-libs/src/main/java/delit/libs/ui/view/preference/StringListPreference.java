package delit.libs.ui.view.preference;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.ArrayRes;
import androidx.annotation.RequiresApi;

import delit.libs.core.util.Logging;

public class StringListPreference extends MappedListPreference<String> {

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public StringListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public StringListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StringListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StringListPreference(Context context) {
        super(context);
    }

    @Override
    protected String transform(Object obj) {
        return String.valueOf(obj);
    }

    @Override
    public Class<String> getEntriesClazz() {
        return String.class;
    }

    @Override
    protected String[] loadEntryValuesFromResourceId(Resources res, @ArrayRes int resourceId) {
        String[] basicVals = res.getStringArray(resourceId);
        return basicVals;
    }

    @Override
    protected void persistValue(String value) {
        persistString(value);
    }

    @Override
    protected String getPersistedValue(String defaultValue) {
        try {
            return getPersistedString(defaultValue);
        } catch (ClassCastException e) {
            Logging.recordException(e);
            // this will occur if swapping this pref type in for an old string type.
            if (e.getMessage().equals("java.lang.String cannot be cast to java.lang.String")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    protected String valueAsString(String value) {
        return value;
    }

    @Override
    protected String valueFromString(String valueAsString) {
        return null;
    }
}
