package delit.piwigoclient.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

public class RoundableImageView extends android.support.v7.widget.AppCompatImageView {

    private boolean enableRoundedCorners;
    private float cornerRadius;

    public RoundableImageView(Context context) {
        super(context);
    }

    public RoundableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
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
        if(!enableRoundedCorners || !(drawable instanceof BitmapDrawable)) {
            // pass straight through.
            super.setImageDrawable(drawable);
            return;
        } else {
            RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), ((BitmapDrawable) drawable).getBitmap());
            roundedDrawable.setCornerRadius(cornerRadius);
            super.setImageDrawable(roundedDrawable);
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        if(enableRoundedCorners) {
            RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), bm);
            roundedDrawable.setCornerRadius(cornerRadius);
            bm = roundedDrawable.getBitmap();
        }
        super.setImageBitmap(bm);
    }
}
