package delit.piwigoclient.ui.slideshow;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import delit.piwigoclient.util.DisplayUtils;
import pl.droidsonroids.gif.GifImageView;

public class AlbumGifPictureItemFragment extends AlbumPictureItemFragment {
    @Override
    protected ImageView createImageViewer() {
        return createAnimatedGifViewer();
    }

    protected ImageView createAnimatedGifViewer() {
        final GifImageView imageView = new GifImageView(getContext());

        imageView.setMinimumHeight(DisplayUtils.dpToPx(getContext(), 120));
        imageView.setMinimumWidth(DisplayUtils.dpToPx(getContext(), 120));
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(layoutParams);
        //TODO allow zooming in on the image.... or scrap all of this and load the gif into the ExoPlayer as a movie (probably better!)
//        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                getOverlaysVisibilityControl().runWithDelay(imageView);
                return false;
            }
        });

        return imageView;
    }
}
