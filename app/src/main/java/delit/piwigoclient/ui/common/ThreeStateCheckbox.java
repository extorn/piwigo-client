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
        if(checked) {
            setAlpha(1f);
            setImageResource(R.drawable.ic_check_box_black_24dp);
        } else if(alwaysChecked) {
            setImageResource(R.drawable.ic_check_box_black_24dp);
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
