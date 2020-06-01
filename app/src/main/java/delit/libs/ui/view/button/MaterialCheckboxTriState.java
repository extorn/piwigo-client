package delit.libs.ui.view.button;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;

import delit.piwigoclient.R;

/**
 * Created by gareth on 02/10/17.
 */

public class MaterialCheckboxTriState extends MaterialCheckBox {

    private static final int[] STATE_ALWAYS_CHECKED_ARRAY = {R.attr.state_always_checked};
    private int defaultLayoutDirection;

    private boolean alwaysChecked;
    private boolean checkboxAtEnd;

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

        setButtonDrawable(ContextCompat.getDrawable(context,R.drawable.checkbox));
        // we set the drawable alpha here so need to do this after we've set the drawable
        setAlwaysChecked(a.getBoolean(R.styleable.MaterialCheckboxTriState_state_always_checked, false));

        a.recycle();

        refreshDrawableState();
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (activated != isChecked()) {
            setChecked(activated);
        }
    }

    public void setAlwaysChecked(boolean alwaysChecked) {
        if (this.alwaysChecked != alwaysChecked) {
            this.alwaysChecked = alwaysChecked;
            refreshDrawableState();
        }
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
//        updateAlwaysChecked(); // clear the alpha tint if now checked
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
        if(alwaysChecked) {
            mergeDrawableStates(drawableState, STATE_ALWAYS_CHECKED_ARRAY);
        }
        return drawableState;
    }

}
