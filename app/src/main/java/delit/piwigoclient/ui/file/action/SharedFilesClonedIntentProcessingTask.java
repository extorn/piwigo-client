package delit.piwigoclient.ui.file.action;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.ProgressListener;
import delit.libs.util.progress.TaskProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.file.RecyclerViewDocumentFileFolderItemSelectFragment;

public class SharedFilesClonedIntentProcessingTask<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,LVA>, FUIH extends FragmentUIHelper<FUIH,F>,LVA extends FolderItemRecyclerViewAdapter<LVA, FolderItem,?,?>> extends OwnedSafeAsyncTask<F, List<FolderItem>, Integer, List<FolderItem>> implements ProgressListener {


    private static final String TAG = "SharedFilesClonedIntentProcessingTask";

    public SharedFilesClonedIntentProcessingTask(F parent) {
        super(parent);
        withContext(parent.requireContext());
    }

    public AsyncTask<List<FolderItem>, Integer, List<FolderItem>> executeNow(List<FolderItem> param) {
        return super.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, param);
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),0);
    }

    @Override
    public Context getContext() {
        return getOwner().requireContext();
    }

    @Override
    protected List<FolderItem> doInBackgroundSafely(List<FolderItem> ... params) {
        List<FolderItem> itemsShared = params[0];
        List<FolderItem> copiedItemsShared = new ArrayList<>(itemsShared.size());
        TaskProgressTracker progressTracker = new TaskProgressTracker("files cloning",  100,this);
        TaskProgressTracker copyProgressTracker = progressTracker.addSubTask("copying files", itemsShared.size(), 80);
        for(FolderItem item : itemsShared) {
            DocumentFile sharedFilesFolder = IOUtils.getSharedFilesFolder(getContext());

            DocumentFile tmpFile = IOUtils.getTmpFile(sharedFilesFolder, IOUtils.getFileNameWithoutExt(item.getName()), item.getExt(), item.getMime());
            try {
                Uri newUri = IOUtils.copyDocumentUriDataToUri(getContext(), item.getContentUri(), tmpFile.getUri());
                copiedItemsShared.add(new FolderItem(newUri));
            } catch (IOException e) {
                Logging.log(Log.ERROR, TAG, "Error copying uris");
                Logging.recordException(e);
                e.printStackTrace();
            } finally {
                copyProgressTracker.incrementWorkDone(1);
            }
        }
        TaskProgressTracker cacheProgressTracker = progressTracker.addSubTask("caching files information", copiedItemsShared.size(), 20);
        itemsShared.clear();
        FolderItem.cacheDocumentInformation(getContext(), copiedItemsShared, cacheProgressTracker);
        return copiedItemsShared;
    }

    @Override
    protected void onProgressUpdateSafely(Integer... progress) {
        super.onProgressUpdateSafely(progress);
        try {
            getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_copying_imported_files),progress[0]);
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
            } else {
                getOwner().getListAdapter().addItems(getOwner().requireContext(), folderItems);
                getOwner().selectAllItems();
            }
        }
        getOwner().getUiHelper().hideProgressIndicator();
        getOwner().getFileExtFilters().setEnabled(true);
    }

    @Override
    public void onProgress(double percent) {
        publishProgress((int)Math.rint(percent*100));
    }

    @Override
    public double getUpdateStep() {
        return 0.01;//1%
    }
}
