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
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.util.TimerThreshold;

class ReloadDataFromUploadJobTask<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends OwnedSafeAsyncTask<F, Void, Object, List<UploadDataItem>> {

    private final UploadJob uploadJob;

    ReloadDataFromUploadJobTask(F parent, UploadJob job) {
        super(parent);
        withContext(parent.requireContext());
        this.uploadJob = job;
    }

    FUIH getUiHelper() {
        return getOwner().getUiHelper();
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().onBeforeReloadDataFromUploadTask(uploadJob);
    }

    @Override
    protected List<UploadDataItem> doInBackgroundSafely(Void... nothing) {
        int itemCount = uploadJob.getFilesNotYetUploaded().size();
        List<UploadDataItem> itemsToBeUploaded = new ArrayList<>(itemCount);
        int currentItem = 0;
        TimerThreshold thesholdGate = new TimerThreshold(1000); // max update the ui once per second
        for(Uri toUpload : uploadJob.getFilesNotYetUploaded()) {
            currentItem++;
            long size = uploadJob.getFileSize(toUpload);
            // this recalculates the hash-codes - maybe unnecessary, but the file could have been altered since added to the job
            itemsToBeUploaded.add(new UploadDataItem(toUpload, null, null, -1));
            int currentProgress = (int)Math.round((((double)currentItem) / itemCount) * 100);
            if(thesholdGate.thresholdMet()) {
                DisplayUtils.runOnUiThread(() -> getOwner().showOverallUploadProgressIndicator(R.string.loading_please_wait, currentProgress));
            }
        }
        return itemsToBeUploaded;
    }

    @Override
    protected void onPostExecuteSafely(List<UploadDataItem> itemsToBeUploaded) {
        FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getOwner().getFilesForUploadViewAdapter();
        adapter.clear();
        adapter.addAll(itemsToBeUploaded);

        for (Uri f : adapter.getFilesAndSizes().keySet()) {
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
