package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.model.ViewModelContainer;

public class AlbumPictureItemFragment<F extends AlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH, F>, T extends PictureResourceItem> extends AbstractAlbumPictureItemFragment<F,FUIH,T> {
    public static AlbumPictureItemFragment<?,?,?> newInstance(Class<? extends ViewModelContainer> modelType, long albumId, long albumItemId, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment<?,?,?> fragment = new AlbumPictureItemFragment<>();
        fragment.setArguments(buildArgs(modelType, albumId, albumItemId, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
        return fragment;
    }
}