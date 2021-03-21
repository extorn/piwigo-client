package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.util.progress.DividableProgressTracker;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

class ReloadDataFromUploadJobTask<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends OwnedSafeAsyncTask<F, Void, Object, List<UploadDataItem>> {

    private final UploadJob uploadJob;
    private static final String TAG = "ReloadDataFromUploadJobTask";
    private DividableProgressTracker tracker;

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
        tracker = new DividableProgressTracker("Parse UploadJob Details", 100, new UiUpdatingProgressListener(getOwner().getOverallUploadProgressIndicator(),R.string.loading_please_wait));
        getOwner().onBeforeReloadDataFromUploadTask(uploadJob);
    }

    @Override
    protected List<UploadDataItem> doInBackgroundSafely(Void... nothing) {
        Context context = getContext();
        Set<FileUploadDetails> filesToProcess = uploadJob.getFilesRequiringProcessing();
        List<UploadDataItem> itemsToBeUploaded = new ArrayList<>(filesToProcess.size());
        if(!filesToProcess.isEmpty()) {
            DividableProgressTracker fileParser = tracker.addChildTask("parse files", filesToProcess.size(), 80);
            for (FileUploadDetails fud : filesToProcess) {
                itemsToBeUploaded.add(new UploadDataItem(fud.getFileUri(), fud.getFilename(context), fud.getFileExt(context), fud.getFileToUploadMimeType(context), fud.getFileSize()));
                fileParser.incrementWorkDone(1);
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
        DividableProgressTracker uiUpdateTracker = tracker.addChildTask("Updating UI", itemsToBeUploaded.size(), tracker.getRemainingWork());
        FilesToUploadRecyclerViewAdapter<?,?,?> adapter = getOwner().getFilesForUploadViewAdapter();
        adapter.clear();
        adapter.addAll(itemsToBeUploaded);
        //FIXME why is this needed?!
        for (Uri f : adapter.getFilesAndSizes().keySet()) {
            try {
                FileUploadDetails fud = uploadJob.getFileUploadDetails(f);
                int progress = fud.getOverallUploadProgress();
                if (fud.hasCompressedFile()) {
                    Uri compressedFile = fud.getCompressedFileUri();
                    adapter.updateCompressionProgress(f, compressedFile, 100);
                }
                adapter.updateUploadProgress(f, progress);
            } finally {
                uiUpdateTracker.incrementWorkDone(1);
            }
        }

        boolean jobIsComplete = uploadJob.isStatusFinished();
        getOwner().populateUiFromJob(uploadJob);

        if (!jobIsComplete) {
            // now register for any new messages (and pick up all messages in sequence)
            getUiHelper().handleAnyQueuedPiwigoMessages();
        } else {
            // reset status ready for next job
            new JobLoadActor(getContext()).removeJob(uploadJob, true);
            getOwner().setUploadJobId(null);
        }

        tracker.markComplete();
    }
}
