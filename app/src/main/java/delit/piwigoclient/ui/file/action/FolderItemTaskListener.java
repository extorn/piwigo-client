package delit.piwigoclient.ui.file.action;

import androidx.annotation.FloatRange;

import delit.libs.util.progress.TaskProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;

public class FolderItemTaskListener implements TaskProgressListener {

    private UIHelper uiHelper;

    public FolderItemTaskListener(UIHelper uiHelper) {
        this.uiHelper = uiHelper;
    }

    @Override
    public void onProgress(@FloatRange(from = 0, to = 1) double percentageComplete) {
        int intPerc = (int)Math.rint(percentageComplete * 100);
        uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), intPerc);
    }

    @Override
    public double getUpdateStep() {
        return 0.01;//1%
    }

    @Override
    public void onTaskStarted() {
        uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), 0);
    }

    @Override
    public void onTaskFinished() {
        uiHelper.hideProgressIndicator();
    }
}
