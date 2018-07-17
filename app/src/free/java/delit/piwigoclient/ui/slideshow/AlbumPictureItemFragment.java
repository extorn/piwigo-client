package delit.piwigoclient.ui.slideshow;

import delit.piwigoclient.model.piwigo.PictureResourceItem;

public class AlbumPictureItemFragment extends AbstractAlbumPictureItemFragment {
    public static AlbumPictureItemFragment newInstance(PictureResourceItem galleryItem, long albumResourceItemIdx, long albumResourceItemCount, long totalResourceItemCount) {
        AlbumPictureItemFragment fragment = new AlbumPictureItemFragment();
        fragment.setArguments(buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount));
        return fragment;
    }
}