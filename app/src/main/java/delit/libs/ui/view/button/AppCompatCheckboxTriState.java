package delit.libs.ui.view.button;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.view.ViewCompat;

import delit.piwigoclient.R;

/**
 * Created by gareth on 02/10/17.
 */

public class AppCompatCheckboxTriState extends AppCompatCheckBox {

    private static final int[] STATE_ALWAYS_CHECKED = {R.attr.state_always_checked};

    private boolean alwaysChecked;
    private boolean checkboxAtEnd;

    public AppCompatCheckboxTriState(Context context) {
        this(context, null);
    }

    public AppCompatCheckboxTriState(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.checkboxStyle);
    }

    public AppCompatCheckboxTriState(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.AppCompatCheckboxTriState, defStyleAttr, defStyleRes);

        checkboxAtEnd = a.getBoolean(R.styleable.AppCompatCheckboxTriState_checkbox_at_end, false);

        a.recycle();

        updateComponentLayout();
    }

    public AppCompatCheckboxTriState(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);

    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (activated != isChecked()) {
            setChecked(activated);
        }
        updateDrawable();
    }

    public void setAlwaysChecked(boolean alwaysChecked) {
        if (this.alwaysChecked != alwaysChecked) {
            this.alwaysChecked = alwaysChecked;
            updateDrawable();
        }
    }

    public void setCheckboxAtEnd(boolean checkboxAtEnd) {
        if (this.checkboxAtEnd != checkboxAtEnd) {
            this.checkboxAtEnd = checkboxAtEnd;

            updateComponentLayout();
        }
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        if(checkboxAtEnd) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        super.setLayoutParams(params);
    }

    private void updateComponentLayout() {
        if(checkboxAtEnd) {
            if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
                ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_RTL);
            } else {
                ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
            }
        }
    }

    private void updateDrawable() {
        if (alwaysChecked && !(isChecked() || isActivated())) {
            setAlpha(0.5f);
        } else {
            setAlpha(1f);
        }

        // Refresh the drawable state so that it includes the message unread
        // state if required.
        refreshDrawableState();
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        updateDrawable();
    }

    // Constructors, view loading etc...
    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        // If the message is unread then we merge our custom message unread state into
        // the existing drawable state before returning it.
        if (alwaysChecked) {
            // We are going to add 1 extra state.
            final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

            mergeDrawableStates(drawableState, STATE_ALWAYS_CHECKED);
            return drawableState;
        } else {
            return super.onCreateDrawableState(extraSpace);
        }
    }

}
