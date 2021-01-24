package delit.piwigoclient.ui.common;

import androidx.annotation.LayoutRes;

import delit.piwigoclient.piwigoApi.upload.BackgroundUploadServiceEventHandler;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyActivity<A extends MyActivity<A,AUIH>, AUIH extends ActivityUIHelper<AUIH,A>> extends BaseMyActivity<A,AUIH> {

    private BackgroundUploadServiceEventHandler backgroundUploadServiceEventHandler = new BackgroundUploadServiceEventHandler();

    public MyActivity(@LayoutRes int contentView) {
        super(contentView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        backgroundUploadServiceEventHandler.register(getUiHelper());
    }

    @Override
    public void onStop() {
        backgroundUploadServiceEventHandler.unregister();
        super.onStop();
    }
}
