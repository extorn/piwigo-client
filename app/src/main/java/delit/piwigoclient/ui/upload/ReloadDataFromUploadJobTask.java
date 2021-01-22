package delit.piwigoclient.ui.upload;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;

class ReloadDataFromUploadJobTask<T extends AbstractUploadFragment<T>> extends OwnedSafeAsyncTask<T, Void, Object, List<UploadDataItemModel.UploadDataItem>> {

    private final UploadJob uploadJob;

    ReloadDataFromUploadJobTask(T parent, UploadJob job) {
        super(parent);
        withContext(parent.requireContext());
        this.uploadJob = job;
    }

    FragmentUIHelper<T> getUiHelper() {
        return getOwner().getUiHelper();
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().onBeforeReloadDataFromUploadTask(uploadJob);
    }

    @Override
    protected List<UploadDataItemModel.UploadDataItem> doInBackgroundSafely(Void... nothing) {
        int itemCount = uploadJob.getFilesNotYetUploaded(getUiHelper().getAppContext()).size();
        List<UploadDataItemModel.UploadDataItem> itemsToBeUploaded = new ArrayList<>(itemCount);
        int currentItem = 0;
        for(Uri toUpload : uploadJob.getFilesNotYetUploaded(getUiHelper().getAppContext())) {
            currentItem++;
            // this recalculates the hash-codes - maybe unnecessary, but the file could have been altered since added to the job
            itemsToBeUploaded.add(new UploadDataItemModel.UploadDataItem(toUpload, null, null));
            int currentProgress = (int)Math.round((((double)currentItem) / itemCount) * 100);
            DisplayUtils.runOnUiThread(() -> getOwner().showOverallUploadProgressIndicator(R.string.loading_please_wait,currentProgress));
        }
        return itemsToBeUploaded;
    }

    @Override
    protected void onPostExecuteSafely(List<UploadDataItemModel.UploadDataItem> itemsToBeUploaded) {
        FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getOwner().getFilesForUploadViewAdapter();
        adapter.clear();
        adapter.addAll(itemsToBeUploaded);

        for (Uri f : adapter.getFiles()) {
            int progress = uploadJob.getUploadProgress(f);
            int compressionProgress = uploadJob.getCompressionProgress(getUiHelper().getAppContext(), f);
            if (compressionProgress == 100) {
                Uri compressedFile = uploadJob.getCompressedFile(f);
                if (compressedFile != null) {
                    adapter.updateCompressionProgress(f, compressedFile, 100);
                }
            }
            adapter.updateUploadProgress(f, progress);
        }

        boolean jobIsComplete = uploadJob.isFinished();
        getOwner().allowUserUploadConfiguration(uploadJob);
        if (!jobIsComplete) {
            // now register for any new messages (and pick up all messages in sequence)
            getUiHelper().handleAnyQueuedPiwigoMessages();
        } else {
            // reset status ready for next job
            BasePiwigoUploadService.removeJob(uploadJob);
            getOwner().setUploadJobId(null);
        }

        getOwner().hideOverallUploadProgressIndicator();
    }
}
