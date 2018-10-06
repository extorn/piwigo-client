package delit.piwigoclient.ui.upload;

public class UploadFragment extends AbstractUploadFragment {
    public static UploadFragment newInstance(long currentGalleryId, int actionId) {
        UploadFragment fragment = new UploadFragment();
        fragment.setArguments(fragment.buildArgs(currentGalleryId, actionId));
        return fragment;
    }
}
