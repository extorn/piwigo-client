package delit.libs.ui.view.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.ArrayList;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;

public class NumberPickerPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {

    private static final String STATE_MIN_VALUE = "NumberPickerPreference.min";
    private static final String STATE_MAX_VALUE = "NumberPickerPreference.max";
    private static final String STATE_WRAP_VALUES = "NumberPickerPreference.wrap";
    private static final String STATE_VALUE = "NumberPickerPreference.currentValue";
    private static final String TAG = "NumPickPrefDF";
    private NumberPicker mPicker;
    private int minValue;
    private int maxValue;
    private boolean wrapSelectionWheel;
    private int selectedValue;


    @Override
    public Preference findPreference(@NonNull CharSequence key) {
        return getPreference();
    }

    @Override
    public NumberPickerPreference getPreference() {
        return (NumberPickerPreference) super.getPreference();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {

        if (positiveResult) {
            Logging.log(Log.INFO, TAG, "Closing number picker preference dialog");
            mPicker.clearFocus();
            Logging.log(Log.INFO, TAG, "Removed focus from picker field");
            int pickerValue = mPicker.getValue();
            getPreference().setValue(pickerValue);
            Logging.log(Log.INFO, TAG, "Updated value of preference from picker field");
            if (getPreference().getOnPreferenceChangeListener() != null) {
                Logging.log(Log.INFO, TAG, "Calling on pref change");
                getPreference().getOnPreferenceChangeListener().onPreferenceChange(getPreference(), pickerValue);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState == null) {
            NumberPickerPreference pref = getPreference();
            maxValue = pref.getMaxValue();
            minValue = pref.getMinValue();
            selectedValue = pref.getValue();
            wrapSelectionWheel = pref.isWrapPickList();
        } else {
            maxValue = savedInstanceState.getInt(STATE_MAX_VALUE);
            minValue = savedInstanceState.getInt(STATE_MIN_VALUE);
            selectedValue = savedInstanceState.getInt(STATE_VALUE);
            wrapSelectionWheel = savedInstanceState.getBoolean(STATE_WRAP_VALUES);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_VALUE,selectedValue);
        outState.putInt(STATE_MIN_VALUE,minValue);
        outState.putInt(STATE_MAX_VALUE,maxValue);
        outState.putBoolean(STATE_WRAP_VALUES, wrapSelectionWheel);
    }

    @Override
    protected View onCreateDialogView(Context context) {
        mPicker = new NumberPicker(getContext());
        mPicker.setSaveFromParentEnabled(false);
        mPicker.setSaveEnabled(true);
        fixEditTextsForMarshmallow(mPicker);
        return mPicker;
    }

    private static void fixEditTextsForMarshmallow(View rootView) {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            ArrayList<View> views = rootView.getTouchables();
            for (View view : views) {
                if (view instanceof EditText) {
                    EditText mEditText = (EditText) view;
                    mEditText.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                        @Override
                        public boolean performAccessibilityAction(View host, int action, Bundle arguments) {

                            if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                                //do something here to make sure index out of bounds does not occur
                                int maxLength = 0;
                                for (InputFilter filter : mEditText.getFilters()) {
                                    if (filter instanceof InputFilter.LengthFilter) {
                                        maxLength = ((InputFilter.LengthFilter) filter).getMax();
                                    }
                                }

                                Set<String> keys = arguments.keySet();
                                for (String key : keys) {
                                    if (arguments.get(key) instanceof CharSequence) {
                                        if (arguments.get(key) != null) {
                                            CharSequence arg = ((CharSequence) arguments.get(key));
                                            arguments.putCharSequence(key, arg == null ? null : arg.subSequence(0, maxLength));
                                            mEditText.setText(arguments.getCharSequence(key));
                                        }
                                    }
                                }
                            }
                            return true;
                        }
                    });

                }
            }
        }
    }


    /**
     * fill created view with data
     *
     * @param view
     */
    @Override
    protected void onBindDialogView(View view) {
        Logging.log(Log.DEBUG, TAG, "Binding data for preference : " + getPreference().getKey());
        super.onBindDialogView(view);
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (applying values from state) : " + getPreference().getKey() + " : " + minValue + "-" + maxValue);
        NumberPicker picker = (NumberPicker) view;
        picker.setMinValue(minValue);
        picker.setMaxValue(maxValue);
        picker.setWrapSelectorWheel(wrapSelectionWheel);
        Logging.log(Log.DEBUG, TAG, "Binding data for preference (complete) : " + getPreference().getKey());
    }

    @Override
    public void onStart() {
        super.onStart();
        mPicker.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                Logging.log(Log.DEBUG, TAG, "Setting value for picker ("+selectedValue+") : " + getPreference().getKey());
                mPicker.setValue(selectedValue);
                Logging.log(Log.DEBUG, TAG, "Requesting focus for picker : " + getPreference().getKey());
                mPicker.requestFocus();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });
    }

    public static DialogFragment newInstance(String key) {
        final NumberPickerPreferenceDialogFragmentCompat fragment =
                new NumberPickerPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
