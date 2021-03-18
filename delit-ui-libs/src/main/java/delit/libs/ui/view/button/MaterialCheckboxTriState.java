package delit.libs.ui.view.button;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;

import delit.libs.R;
import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 02/10/17.
 */

public class MaterialCheckboxTriState extends MaterialCheckBox {

    private static final int[] STATE_ALWAYS_CHECKED_ARRAY = {R.attr.secondaryChecked};
    private int defaultLayoutDirection;

    private boolean secondaryOverrides = true; // if false, secondary value is ORd with primary value. If true, secondary checked will override primary
    private boolean secondaryChecked;
    private boolean checkboxAtEnd;
    private boolean primaryCheckedValueSet;
    private boolean restoringStateFlag; // used for a brief moment to ensure primaryCheckedValueSet isn't corrupted by super class

    public MaterialCheckboxTriState(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttrs(context,attrs, 0, 0);
    }

    public MaterialCheckboxTriState(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }


    public MaterialCheckboxTriState(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        initAttrs(context,attrs, defStyleAttr, defStyleRes);
    }

    private void initAttrs(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.MaterialCheckboxTriState, defStyleAttr, defStyleRes);

        defaultLayoutDirection = getLayoutDirection();

        setCheckboxAtEnd(a.getBoolean(R.styleable.MaterialCheckboxTriState_checkbox_at_end, false));

        // we set the drawable alpha here so need to do this after we've set the drawable
        setSecondaryChecked(a.getBoolean(R.styleable.MaterialCheckboxTriState_secondaryChecked, false));

        secondaryOverrides = a.getBoolean(R.styleable.MaterialCheckboxTriState_secondaryOverrides, true);

        if(a.hasValue(R.styleable.MaterialCheckboxTriState_checked)) {
            setChecked(a.getBoolean(R.styleable.MaterialCheckboxTriState_checked, false));
        } else {
            setChecked(null);
        }

        setButtonDrawable(ContextCompat.getDrawable(context,R.drawable.checkbox));

        a.recycle();

        refreshDrawableState();
    }

    /**
     * This is essentially setChecked but for a list view item
     * @param activated new value
     */
    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (activated != isChecked()) {
            setChecked(activated);
        }
    }

    public void setSecondaryChecked(boolean secondaryChecked) {
        if (this.secondaryChecked != secondaryChecked) {
            this.secondaryChecked = secondaryChecked;
            refreshDrawableState();
        }
    }

    /**
     * @param checked null will ensure the secondary checked value is used and clear the primary checked status
     */
    public void setChecked(Boolean checked) {
        if(checked == null) {
            primaryCheckedValueSet = false;
            refreshDrawableState();
        } else {
            super.setChecked(checked);
            primaryCheckedValueSet = true;
        }
    }

    /**
     * DO NOT call this if using local value overrides mode. This doesn't mark the field as locally set.
     * Use {@link #toggle()} or {@link #setChecked(Boolean)} instead.
     * @param checked new value
     */
    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        if(!restoringStateFlag) {
            primaryCheckedValueSet = true;
        }
    }

    public void setCheckboxAtEnd(boolean checkboxAtEnd) {
        if(this.checkboxAtEnd != checkboxAtEnd) {
            this.checkboxAtEnd = checkboxAtEnd;
            if (defaultLayoutDirection == View.LAYOUT_DIRECTION_LTR) {
                setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            } else {
                setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }
//            refreshDrawableState();
        }
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if(checkboxAtEnd) {
            if(params.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }
        super.setLayoutParams(params);
    }

    // Constructors, view loading etc...
    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // If the message is unread then we merge our custom message unread state into
        // the existing drawable state before returning it.
        int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        // if secondaryChecked AND no present value set
        // OR secondaryChecked AND (secondaryOverrides OR not primaryChecked)
        if(secondaryChecked && (!primaryCheckedValueSet || (secondaryOverrides && !isChecked()))) {
            // shows the checked icon slightly grayed
            mergeDrawableStates(drawableState, STATE_ALWAYS_CHECKED_ARRAY);
        }
        return drawableState;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);

        ss.secondaryChecked = secondaryChecked;
        ss.secondaryOverrides = secondaryOverrides;
        ss.checkboxAtEnd = checkboxAtEnd;
        ss.primaryCheckedValueSet = primaryCheckedValueSet;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        restoringStateFlag = true;
        SavedState ss = (SavedState) state;
        secondaryChecked = ss.secondaryChecked;
        secondaryOverrides = ss.secondaryOverrides;
        checkboxAtEnd = ss.checkboxAtEnd;
        primaryCheckedValueSet = ss.primaryCheckedValueSet;
        super.onRestoreInstanceState(state);
        restoringStateFlag = false;
    }

    static class SavedState extends BaseSavedState {
        private boolean secondaryOverrides = false;
        private boolean secondaryChecked;
        private boolean checkboxAtEnd;
        private boolean primaryCheckedValueSet;

        /**
         * Constructor called from {@link MaterialCheckboxTriState#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            secondaryOverrides = ParcelUtils.readBool(in);
            secondaryChecked = ParcelUtils.readBool(in);
            checkboxAtEnd = ParcelUtils.readBool(in);
            primaryCheckedValueSet = ParcelUtils.readBool(in);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            ParcelUtils.writeBool(out, secondaryOverrides);
            ParcelUtils.writeBool(out, secondaryChecked);
            ParcelUtils.writeBool(out, checkboxAtEnd);
            ParcelUtils.writeBool(out, primaryCheckedValueSet);
        }

        @SuppressWarnings("hiding")
        public static final @NonNull Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
