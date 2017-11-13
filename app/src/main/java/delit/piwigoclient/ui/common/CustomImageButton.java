package delit.piwigoclient.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;

/**
 * Created by gareth on 09/06/17.
 */

public class CustomImageButton extends AppCompatImageButton {
    public CustomImageButton(Context context) {
        super(context);
    }

    public CustomImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
        } else {
            setColorFilter(null);
        }
        super.setEnabled(enabled);
    }
}
