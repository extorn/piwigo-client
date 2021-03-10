package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.progress.DividableProgressTracker;
import delit.libs.util.progress.SimpleProgressListener;
import delit.piwigoclient.R;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.ChecksumCalculationActor;
import delit.piwigoclient.piwigoApi.upload.actors.CommunityUploadJobCompleteActor;
import delit.piwigoclient.piwigoApi.upload.actors.DeleteFromServerActor;
import delit.piwigoclient.piwigoApi.upload.actors.ExistingFilesCheckActor;
import delit.piwigoclient.piwigoApi.upload.actors.FileCompressionActor;
import delit.piwigoclient.piwigoApi.upload.actors.FileUploadConfigureActor;
import delit.piwigoclient.piwigoApi.upload.actors.FileUploadVerifyActor;
import delit.piwigoclient.piwigoApi.upload.actors.JobCleanupActor;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.piwigoApi.upload.actors.LocalFileNotHereCheckActor;
import delit.piwigoclient.piwigoApi.upload.actors.PrepareForUploadActor;
import delit.piwigoclient.piwigoApi.upload.actors.TemporaryUploadAlbumActor;
import delit.piwigoclient.piwigoApi.upload.actors.UploadNotificationManager;
import delit.piwigoclient.piwigoApi.upload.actors.dataupload.FileDataUploaderActor;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileJobCompleteResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadUnexpectedLocalErrorResponse;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.CancelFileUploadEvent;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;
import static org.greenrobot.eventbus.ThreadMode.ASYNC;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public abstract class BasePiwigoUploadService extends JobIntentService {

    private static final String TAG = "BaseUpldSvc";
    private UploadJob runningUploadJob = null;
    private SharedPreferences prefs;
    private ActionsBroadcastReceiver actionsBroadcastReceiver;
    private UploadNotificationManager uploadNotificationManager;

    public UploadNotificationManager getUploadNotificationManager() {
        return uploadNotificationManager;
    }

    protected void actionKillService() {
        if (runningUploadJob != null) {
            runningUploadJob.cancelUploadAsap();
        }
    }

    private void runPostJobCleanup(UploadJob uploadJob, ActorListener actorListener) {
        if (uploadJob == null) {
            return; // Do nothing.
        }

        JobCleanupActor jobFileActor = new JobCleanupActor(this, uploadJob, actorListener);
        jobFileActor.deleteSuccessfullyUploadedFilesFromDevice();

        // record all files uploaded to prevent repeated upload (do this always in case delete fails for a file!
        HashMap<Uri, String> uploadedFileChecksums = uploadJob.getSelectedFileChecksumsForBlockingFutureUploads();
        updateListOfPreviouslyUploadedFiles(uploadJob, uploadedFileChecksums);
    }

    protected void doBeforeWork(@NonNull Intent intent) {
        uploadNotificationManager = buildUploadNotificationManager();
        startForeground(uploadNotificationManager.getNotificationId(), uploadNotificationManager.getNotification());
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    protected abstract UploadNotificationManager buildUploadNotificationManager();

    public SharedPreferences getPrefs() {
        return prefs;
    }

    protected abstract void doWork(@NonNull Intent intent);

    protected abstract void updateListOfPreviouslyUploadedFiles(UploadJob uploadJob, HashMap<Uri, String> uploadedFileChecksums);




    protected void setRunningUploadJob(UploadJob thisUploadJob) {
        runningUploadJob = thisUploadJob;
    }

    protected void clearRunningUploadJob() {
        runningUploadJob = null;
    }

    protected void runJob(@NonNull JobLoadActor jobLoadActor, @NonNull UploadJob thisUploadJob, JobUploadListener listener, boolean deleteJobConfigFileOnSuccess) {
        ActorListener actorListener = buildUploadActorListener(thisUploadJob, uploadNotificationManager);

        try {
            setRunningUploadJob(thisUploadJob);

            DividableProgressTracker overallJobProgressTracker = thisUploadJob.getProgressTrackerForJob();
            overallJobProgressTracker.setListener(new SimpleProgressListener(0.01) {
                @Override
                protected void onNotifiableProgress(double percent) {
                    actorListener.recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), null, thisUploadJob.getOverallUploadProgressInt()));
                }
            });
            overallJobProgressTracker.setWorkDone(0); // this does not clear any child progress tracker allocated work still in progress.

            thisUploadJob.setStatusRunning();

            thisUploadJob.resetProcessingErrors();
            //TODO should we wipe the errors thus far recorded? Maybe not for background jobs...
            //thisUploadJob.clearUploadErrors();

            try {
                saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, actorListener);

                PrepareForUploadActor jobPrepActor = new PrepareForUploadActor(this, thisUploadJob, actorListener);
                boolean canContinue = jobPrepActor.run();
                if(!canContinue) {
                    throw new JobUnableToContinueException();
                }
                saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, actorListener);
                //TODO these albums will be used later on... maybe do the server call later on instead?
                ArrayList<CategoryItemStub> availableAlbumsOnServer = jobPrepActor.getAvailableAlbumsOnServer();

                canContinue = new ChecksumCalculationActor(this, thisUploadJob, actorListener).run();
                if(!canContinue) {
                    throw new JobUnableToContinueException();
                }
                saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, actorListener);

                if (thisUploadJob.isRunInBackground() && listener != null) {
                    listener.onJobReadyToUpload(this, thisUploadJob);
                }

                // is name or md5sum used for uniqueness on this server?
                boolean filenamesUnique = isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob);

                if (thisUploadJob.hasProcessableFiles()) {

                    new ExistingFilesCheckActor(this, thisUploadJob, actorListener, filenamesUnique).run();

                    boolean useTempFolder = !PiwigoSessionDetails.isUseCommunityPlugin(thisUploadJob.getConnectionPrefs());
                    if(useTempFolder && thisUploadJob.hasNeedOfTemporaryFolder()) {
                        // create a secure folder to upload to if required
                        if(!new TemporaryUploadAlbumActor(this, thisUploadJob, actorListener).createTemporaryUploadAlbum(thisUploadJob)) {
                            throw new JobUnableToContinueException();
                        }
                    }
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_POST_CHECKED_FOR_EXISTING_FILES);

                saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, actorListener);

                if (!thisUploadJob.isCancelUploadAsap()) {
                    if (thisUploadJob.hasProcessableFiles()) {
                        // loop over all files uploading.
                        doUploadFilesInJob(jobLoadActor, thisUploadJob, availableAlbumsOnServer, actorListener);
                    }
                }

                if (!thisUploadJob.isCancelUploadAsap()) {
                    new CommunityUploadJobCompleteActor(this, thisUploadJob, actorListener).run();
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_POST_UPLOAD_CALLS);

                if (!thisUploadJob.isCancelUploadAsap()) {
                    for(FileUploadDetails fud : thisUploadJob.getFilesForUpload()) {
                        if(!fud.hasNoActionToTake() && !fud.isPossibleToUpload(this)) {
                            fud.setStatusUnavailable();
                        }
                    }
                    if (thisUploadJob.getFilesNotYetUploaded().isEmpty() && thisUploadJob.getTemporaryUploadAlbumId() > 0) {
                        boolean success = new TemporaryUploadAlbumActor(this,thisUploadJob, actorListener).deleteTemporaryUploadAlbum(thisUploadJob);
                        if (!success) {
                            throw new JobUnableToContinueException();
                        }
                    }
                }

                overallJobProgressTracker.incrementWorkDone(UploadJob.WORK_DIVISION_DELETE_TEMP_FOLDER);

                if(thisUploadJob.getFilesWithoutFurtherActionNeeded().size() == thisUploadJob.getFilesForUpload().size()) {
                    thisUploadJob.setStatusFinished();
                    EventBus.getDefault().post(new AlbumAlteredEvent(thisUploadJob.getUploadToCategory().getId()));
                }
                
            } catch(JobUnableToContinueException e) {
                Logging.log(Log.DEBUG, TAG, "Stopping job. Unable to continue. Check recorded errors for reason");
            } catch (RuntimeException e) {
                actorListener.recordAndPostNewResponse(thisUploadJob, new PiwigoUploadUnexpectedLocalErrorResponse(getNextMessageId(), e));
                Logging.log(Log.ERROR, TAG, "An unexpected Runtime error stopped upload job");
                Logging.recordException(e);
            } finally {
                if(thisUploadJob.isStatusRunningNow()) {
                    thisUploadJob.setStatusStopped();
                }
                thisUploadJob.clearCancelUploadAsapFlag();

                actorListener.updateNotificationProgressText(thisUploadJob.getOverallUploadProgressInt());

                if (!thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                    jobLoadActor.saveStateToDisk(thisUploadJob);
                } else {
                    jobLoadActor.deleteStateFromDisk(thisUploadJob, deleteJobConfigFileOnSuccess);
                }
            }

            try {
                runPostJobCleanup(thisUploadJob, actorListener);
            } catch (RuntimeException e) {
                actorListener.recordAndPostNewResponse(thisUploadJob, new PiwigoUploadUnexpectedLocalErrorResponse(getNextMessageId(), e));
                Logging.log(Log.ERROR, TAG, "An unexpected Runtime error stopped upload job");
                Logging.recordException(e);
            } finally {
                if (thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                    overallJobProgressTracker.markComplete();
                }
                actorListener.recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileJobCompleteResponse(getNextMessageId(), thisUploadJob));
                PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(thisUploadJob.getJobId());
                AbstractPiwigoDirectResponseHandler.unblockMessageId(thisUploadJob.getJobId());
            }
        } finally {
            clearRunningUploadJob();
        }
    }

    private boolean isUseFilenamesOverMd5ChecksumForUniqueness(UploadJob thisUploadJob) {
        String uniqueResourceKey = thisUploadJob.getConnectionPrefs().getPiwigoUniqueResourceKey(prefs, this);
        return "name".equals(uniqueResourceKey);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        actionsBroadcastReceiver = buildActionBroadcastReceiver();
        registerReceiver(actionsBroadcastReceiver, actionsBroadcastReceiver.getFilter());
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(actionsBroadcastReceiver);
        super.onDestroy();
    }

    protected abstract ActionsBroadcastReceiver buildActionBroadcastReceiver();

    private void doUploadFilesInJob(JobLoadActor jobLoadActor, UploadJob thisUploadJob, ArrayList<CategoryItemStub> availableAlbumsOnServer, ActorListener listener) throws JobUnableToContinueException {
        thisUploadJob.buildTaskProgressTrackerForOverallCompressionAndUploadOfData(this);
        for (FileUploadDetails fud : thisUploadJob.getFilesForUpload()) {
            runAllFileUploadTasksForFile(jobLoadActor, fud, thisUploadJob, availableAlbumsOnServer, listener);
        }
    }

    protected void runAllFileUploadTasksForFile(JobLoadActor jobLoadActor, FileUploadDetails fud, UploadJob thisUploadJob, ArrayList<CategoryItemStub> availableAlbumsOnServer, ActorListener listener) throws JobUnableToContinueException {
        int maxChunkUploadAutoRetries = UploadPreferences.getUploadChunkMaxRetries(this, prefs);

        if(fud.isUploadCancelled()) {
            listener.doHandleUserCancelledUpload(thisUploadJob, fud.getFileUri());
            // it may need delete - let the rest of the method run
        }

        if (!fud.isProcessingFailed() && !fud.isDoneWithLocalFiles()) {
            // check its still here.
            new LocalFileNotHereCheckActor(this, thisUploadJob, listener).runCheck(fud);
        }

        if (!fud.isProcessingFailed() && fud.isCompressionNeeded()) {
            FileCompressionActor fca = new FileCompressionActor(this, thisUploadJob, listener);
            fca.run(fud);
        }

        saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, listener);

        if (!fud.isProcessingFailed() && fud.needsUpload()) {
            FileDataUploaderActor fdua = new FileDataUploaderActor(this, thisUploadJob, listener, isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob));
            fdua.doUploadFileData(thisUploadJob, fud, maxChunkUploadAutoRetries);
        }

        saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, listener);

        if (!fud.isProcessingFailed() && fud.needsVerification()) {
            new FileUploadVerifyActor(this, thisUploadJob, listener).run(fud);
        }

        saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, listener);

        if (!fud.isProcessingFailed() && fud.needsConfiguration()) {
            Set<Long> allServerAlbumIds = PiwigoUtils.toSetOfIds(availableAlbumsOnServer);
            new FileUploadConfigureActor(this, thisUploadJob, listener).run(fud, allServerAlbumIds);
        }

        saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, listener);

        // Once added to album its too late the cancel the upload.
//            if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//                thisUploadJob.markFileAsNeedsDelete(fileForUpload);
//            }

        // No !fud.isProcessingFailed() && check because we should always tidy the server if there was an issue.
        if (fud.needsDeleteFromServer() || fud.isFileUploadCorrupt()) {
            DeleteFromServerActor deleteActor = new DeleteFromServerActor(this, thisUploadJob, listener);
            // we allow retry of corrupt uploads, if its set to delete, the user cancelled and thus server needs cleaning.
            deleteActor.run(fud, fud.isFileUploadCorrupt());
        }

        saveStatusAndStopJobIfRequested(jobLoadActor, thisUploadJob, listener);

        listener.updateNotificationProgressText(thisUploadJob.getOverallUploadProgressInt());
    }

    private void saveStatusAndStopJobIfRequested(JobLoadActor jobLoadActor, UploadJob thisUploadJob, ActorListener listener) throws JobUnableToContinueException {
        jobLoadActor.saveStateToDisk(thisUploadJob);
        if (thisUploadJob.isCancelUploadAsap()) {
            listener.reportForUser(getString(R.string.upload_job_stopped_by_user));
            throw new JobUnableToContinueException();
        }
    }

    @Subscribe(threadMode = ASYNC)
    public void onEvent(CancelFileUploadEvent event) {
        if(runningUploadJob.getJobId() == event.getJobId()) {
            runningUploadJob.wakeAnyWaitingThreads();
        }
    }

    protected abstract ActorListener buildUploadActorListener(UploadJob uploadJob, UploadNotificationManager notificationManager);

    public interface JobUploadListener {
        void onJobReadyToUpload(Context c, UploadJob thisUploadJob);
    }

    protected class ActionsBroadcastReceiver extends BroadcastReceiver {

        private final String stopAction;

        public ActionsBroadcastReceiver(@NonNull String stopAction) {
            this.stopAction = stopAction;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (stopAction.equals(intent.getAction())) {
                actionKillService();
            }
        }

        public IntentFilter getFilter() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(stopAction);
            return filter;
        }
    }
}
