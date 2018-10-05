package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.AttributeSet;
import android.widget.TextView;

import delit.piwigoclient.R;

/**
 * Created by gareth on 26/01/18.
 */

public class CustomEditTextPreference extends EditTextPreference {

    private int inputFieldType;

    public CustomEditTextPreference(Context context, AttributeSet attrs, int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        initPreference(context, attrs);
    }

    public CustomEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initPreference(context, attrs);
    }

    public CustomEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    public CustomEditTextPreference(Context context) {
        super(context);
        initPreference(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        summaryView.setInputType(getInputFieldType());
        super.onBindViewHolder(holder);
    }

    private void initPreference(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomEditTextPreference, 0, 0);
        inputFieldType = a.getInt(R.styleable.CustomEditTextPreference_android_inputType, -1);
        a.recycle();

    }

    public int getInputFieldType() {
        return inputFieldType;
    }


    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.inputFieldType = inputFieldType;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        inputFieldType = myState.inputFieldType;
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summary = super.getSummary();
        String value = getText();
        if(value == null) {
            value = "";
        }
        if (summary != null) {
            return String.format(summary.toString(), value);
        } else {
            summary = value;
        }
        return summary;
    }

    public static class SavedState extends BaseSavedState {
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        private int inputFieldType;

        public SavedState(Parcel source) {
            super(source);
            inputFieldType = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(inputFieldType);
        }
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        notifyChanged();
    }
}
