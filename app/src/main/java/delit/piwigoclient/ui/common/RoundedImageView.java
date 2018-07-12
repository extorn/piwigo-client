package delit.piwigoclient.ui.common;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.AppCompatImageView;

public class RoundedImageView extends android.support.v7.widget.AppCompatImageView {

    private final float cornerRadius;
    private AppCompatImageView delegate;

    public RoundedImageView(AppCompatImageView delegate, float cornerRadius) {
        super(delegate.getContext());
        this.cornerRadius = cornerRadius;
        this.delegate = delegate;
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        if(!(drawable instanceof BitmapDrawable)) {
            // pass straight through.
            delegate.setImageDrawable(drawable);
            return;
        }
        RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), ((BitmapDrawable) drawable).getBitmap());
        roundedDrawable.setCornerRadius(cornerRadius);
        delegate.setImageDrawable(roundedDrawable);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        RoundedBitmapDrawable roundedDrawable = RoundedBitmapDrawableFactory.create(getResources(), bm);
        delegate.setImageBitmap(bm);
    }
}
