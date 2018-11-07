package delit.piwigoclient.ui.upload;

import delit.piwigoclient.model.piwigo.CategoryItemStub;

public class UploadFragment extends AbstractUploadFragment {
    public static UploadFragment newInstance(CategoryItemStub currentAlbum, int actionId) {
        UploadFragment fragment = new UploadFragment();
        fragment.setArguments(fragment.buildArgs(currentAlbum, actionId));
        return fragment;
    }
}
