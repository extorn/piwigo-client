package delit.libs.ui.view.button;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

import delit.piwigoclient.R;

/**
 * Created by gareth on 09/06/17.
 */

public class CustomImageButton extends AppCompatImageButton {
    public CustomImageButton(Context context) {
        super(context);
        setAppropriateColorFilter(true);
    }

    public CustomImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAppropriateColorFilter(true);
    }

    public CustomImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAppropriateColorFilter(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setAppropriateColorFilter(enabled);
        super.setEnabled(enabled);
    }

    private void setAppropriateColorFilter(boolean enabled) {
        if (!enabled) {
            setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN);
        } else {
            setColorFilter(ContextCompat.getColor(getContext(),R.color.primary_text_default), PorterDuff.Mode.SRC_IN);
        }
    }
}