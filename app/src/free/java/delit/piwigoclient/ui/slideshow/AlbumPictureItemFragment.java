package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.model.piwigo.PictureResourceItem;

public class AlbumPictureItemFragment extends AbstractAlbumPictureItemFragment {
    public static AlbumPictureItemFragment newInstance(Class<? extends ViewModelContainer> modelType, long albumId, long albumItemId, int albumResourceItemIdx, int albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment fragment = new AlbumPictureItemFragment();
        fragment.setArguments(buildArgs(modelType, albumId, albumItemId, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
        return fragment;
    }
}