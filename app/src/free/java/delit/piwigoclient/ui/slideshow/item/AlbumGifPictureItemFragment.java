package delit.piwigoclient.ui.slideshow.item;

import android.widget.ImageView;

import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;

public class AlbumGifPictureItemFragment<F extends AlbumGifPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH, F>, T extends PictureResourceItem> extends AlbumPictureItemFragment<F,FUIH,T> {
    @Override
    protected ImageView createImageViewer() {
        return createAnimatedGifViewer();
    }
}
