package delit.piwigoclient.ui.common;

import android.content.Context;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.AttributeSet;

import delit.piwigoclient.R;

/**
 * Created by gareth on 02/10/17.
 */

public class AppCompatCheckboxTriState extends AppCompatCheckBox {

    private static final int[] STATE_ALWAYS_CHECKED = {R.attr.state_always_checked};

    private boolean alwaysChecked;

    public AppCompatCheckboxTriState(Context context) {
        super(context);
    }

    public AppCompatCheckboxTriState(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AppCompatCheckboxTriState(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAlwaysChecked(boolean alwaysChecked) {
        if (this.alwaysChecked != alwaysChecked) {
            this.alwaysChecked = alwaysChecked;

            if(alwaysChecked) {
                setAlpha(0.5f);
            } else {
                setAlpha(1f);
            }

            // Refresh the drawable state so that it includes the message unread
            // state if required.
            refreshDrawableState();
        }
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
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
