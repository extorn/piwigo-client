package delit.piwigoclient.ui.file.action;

import delit.libs.util.progress.SimpleProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;

public class FolderItemTaskListener extends SimpleProgressListener {

    private final UIHelper<?,?> uiHelper;

    public FolderItemTaskListener(UIHelper<?,?> uiHelper) {
        super(0.01);
        this.uiHelper = uiHelper;
    }

    @Override
    protected void onNotifiableProgress(double percent) {
        int intPerc = (int)Math.rint(percent * 100);
        uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), intPerc);
    }

    @Override
    public void onStarted() {
        uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), 0);
    }

    @Override
    public void onComplete() {
        super.onComplete();
        uiHelper.hideProgressIndicator();
    }
}
