package delit.piwigoclient.ui.common;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.widget.Checkable;

import delit.piwigoclient.R;

/**
 * Created by gareth on 02/10/17.
 */

public class ThreeStateCheckbox extends AppCompatImageView implements Checkable {

    private boolean alwaysChecked;
    private boolean checked;

    public ThreeStateCheckbox(Context context) {
        super(context);
    }

    public ThreeStateCheckbox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThreeStateCheckbox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setImageResource(R.drawable.ic_check_box_outline_blank_black_24dp);
    }

    public void setAlwaysChecked(boolean alwaysChecked) {
        this.alwaysChecked = alwaysChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
        int drawableId = R.drawable.ic_check_box_black_24dp;
        if(isEnabled()) {
            drawableId = R.drawable.ic_check_box_accent_24dp;
        }
        if(checked) {
            setAlpha(1f);
            setImageResource(drawableId);
        } else if(alwaysChecked) {
            setImageResource(drawableId);
            setAlpha(0.5f);
        } else {
            setAlpha(1f);
            setImageResource(R.drawable.ic_check_box_outline_blank_black_24dp);
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }
}
