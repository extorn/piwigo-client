package delit.piwigoclient.ui.upload;

import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.util.TimerThreshold;

class ReloadDataFromUploadJobTask<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends OwnedSafeAsyncTask<F, Void, Object, List<UploadDataItem>> {

    private final UploadJob uploadJob;
    private static final String TAG = "ReloadDataFromUploadJobTask";

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
        for(FileUploadDetails fud : uploadJob.getFilesForUpload()) {
            if(fud.hasNoActionToTake()) {
                continue;
            }
            currentItem++;
            itemsToBeUploaded.add(new UploadDataItem(fud.getFileUri(), null, null, -1));
            int currentProgress = (int)Math.round((((double)currentItem) / itemCount) * 100);
            if(thesholdGate.thresholdMet()) {
                DisplayUtils.runOnUiThread(() -> getOwner().showOverallUploadProgressIndicator(R.string.loading_please_wait, currentProgress));
            }
        }
        if(itemsToBeUploaded.size() > 0) {
            if(uploadJob.isStatusFinished()) {
                Logging.log(Log.ERROR, TAG, "Revoking finished status - job has outstanding work");
                uploadJob.setStatusStopped();
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
            FileUploadDetails fud = uploadJob.getFileUploadDetails(f);
            int progress = fud.getOverallUploadProgress();
            if (fud.hasCompressedFile()) {
                Uri compressedFile = fud.getCompressedFileUri();
                adapter.updateCompressionProgress(f, compressedFile, 100);
            }
            adapter.updateUploadProgress(f, progress);
        }

        boolean jobIsComplete = uploadJob.isStatusFinished();
        getOwner().populateUiFromJob(uploadJob);

        if (!jobIsComplete) {
            // now register for any new messages (and pick up all messages in sequence)
            getUiHelper().handleAnyQueuedPiwigoMessages();
        } else {
            // reset status ready for next job
            JobLoadActor.removeJob(uploadJob);
            getOwner().setUploadJobId(null);
        }

        getOwner().hideOverallUploadProgressIndicator();
    }
}
