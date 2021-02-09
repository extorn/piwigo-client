package delit.libs.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

public class RoundableImageView extends androidx.appcompat.widget.AppCompatImageView {

    private boolean enableRoundedCorners;
    private float cornerRadius;

    public RoundableImageView(Context context) {
        super(context);
    }

    public RoundableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundableImageView(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setCornerRadius(float cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    public void setEnableRoundedCorners(boolean enableRoundedCorners) {
        this.enableRoundedCorners = enableRoundedCorners;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        if (!enableRoundedCorners || !(drawable instanceof BitmapDrawable)) {
            // pass straight through.
            super.setImageDrawable(drawable);
        } else {
            RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), ((BitmapDrawable) drawable).getBitmap());
            roundedDrawable.setCornerRadius(cornerRadius);
            super.setImageDrawable(roundedDrawable);
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        if (enableRoundedCorners) {
            RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), bm);
            roundedDrawable.setCornerRadius(cornerRadius);
            bm = roundedDrawable.getBitmap();
        }
        super.setImageBitmap(bm);
    }
}
