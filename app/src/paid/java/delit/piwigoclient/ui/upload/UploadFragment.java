package delit.piwigoclient.ui.upload;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.FragmentUIHelper;

public class UploadFragment<F extends UploadFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends AbstractUploadFragment<F,FUIH> {
    public static UploadFragment newInstance(CategoryItemStub currentAlbum, int actionId) {
        UploadFragment fragment = new UploadFragment();
        fragment.setArguments(fragment.buildArgs(currentAlbum, actionId));
        return fragment;
    }
}
