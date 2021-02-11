package delit.piwigoclient.ui.preferences;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import delit.libs.ui.view.preference.MyDialogPreference;
import delit.piwigoclient.R;

public class PaidOnlyPreference extends MyDialogPreference {
    private String value;

    public PaidOnlyPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public PaidOnlyPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public PaidOnlyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PaidOnlyPreference(Context context) {
        super(context);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        value = "" + defaultValue;
    }

    @Override
    public CharSequence getSummary() {
        //TODO establish how to get the default value and display it.
        return getContext().getString(R.string.paid_feature_only);
//        CharSequence summary = super.getSummary();
//        String value = getTextValue();
//        if(value == null) {
//            value = "";
//        }
//        if (summary != null) {
//            return String.format(summary.toString(), value);
//        } else {
//            summary = value;
//        }
//        return summary;
    }

    private String getTextValue() {
        return value;
    }

}
