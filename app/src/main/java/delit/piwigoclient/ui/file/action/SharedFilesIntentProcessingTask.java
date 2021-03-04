package delit.piwigoclient.ui.file.action;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.FloatRange;

import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.util.progress.ProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

public class SharedFilesIntentProcessingTask<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,LVA>, FUIH extends FragmentUIHelper<FUIH,F>,LVA extends FolderItemRecyclerViewAdapter<LVA, FolderItem,?,?>> extends OwnedSafeAsyncTask<F, Void, Integer, List<FolderItem>> implements ProgressListener {


    private static final String TAG = "SharedFilesIntentProcessingTask";
    private final Intent intent;

    public SharedFilesIntentProcessingTask(F parent, Intent intent) {
         super(parent);
         this.intent = intent;
         withContext(parent.requireContext());
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),0);
    }

    @Override
    protected List<FolderItem> doInBackgroundSafely(Void... nothing) {
        if (intent.getClipData() != null) {
            return getOwner().processOpenDocuments(intent, this);
        } else {
            return getOwner().processOpenDocumentTree(intent, this);
        }
    }

    @Override
    protected void onProgressUpdateSafely(Integer... progress) {
        super.onProgressUpdateSafely(progress);
        try {
            getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),progress[0]);
        } catch(NullPointerException e) {
            Logging.log(Log.ERROR, TAG, "Unable to report progress. Likely not attached");
            Logging.recordException(e);
        }

    }

    @Override
    protected void onPostExecuteSafely(List<FolderItem> folderItems) {
        if(folderItems != null) {
            if (folderItems.size() == 1 && folderItems.get(0).isFolder()) {
                FolderItem item = folderItems.get(0);
                getOwner().addRootFolder(item.getDocumentFile());
            } else if(folderItems.size() > 0) {
                getOwner().getListAdapter().addItems(getOwner().requireContext(), folderItems);
                getOwner().selectAllItems();
            }
        }
        getOwner().getUiHelper().hideProgressIndicator();
        getOwner().getFileExtFilters().setEnabled(true);
    }

    @Override
    public void onProgress(@FloatRange(from = 0, to = 1) double percent) {
        publishProgress((int) Math.rint(percent * 100));
    }

    @Override
    public double getUpdateStep() {
        return 0.01;//1%
    }
}
