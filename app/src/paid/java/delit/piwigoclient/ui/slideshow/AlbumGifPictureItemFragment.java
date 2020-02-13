package delit.piwigoclient.ui.slideshow;

import android.widget.ImageView;

public class AlbumGifPictureItemFragment extends AlbumPictureItemFragment {
    @Override
    protected ImageView createImageViewer() {
        return createAnimatedGifViewer();
    }
}
