package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.common.util.IOUtils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.preferences.AutoUploadJobConfig;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;
import delit.piwigoclient.util.CustomFileFilter;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public class BackgroundPiwigoUploadService extends BasePiwigoUploadService {

    private static final String TAG = "BkgrdUploadService";
    private static final String ACTION_BACKGROUND_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_BACKGROUND_UPLOAD_FILES";
    private CustomFileFilter fileFilter = new CustomFileFilter();
    private static volatile boolean exit = false;

    public BackgroundPiwigoUploadService() { super(TAG);}

    public static void startService(Context context, boolean keepDeviceAwake) {

        Intent intent = new Intent(context, BackgroundPiwigoUploadService.class);
        intent.setAction(ACTION_BACKGROUND_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, keepDeviceAwake);
        ComponentName name = context.startService(intent);
    }

    public static void killService() {
        exit = true;
    }

    @Override
    protected void doWork(Intent intent) {
        long pollDurationMillis = 1000 * 60 * 15; // every 15 minutes
        Context context = getApplicationContext();
        AutoUploadJobsConfig jobs = new AutoUploadJobsConfig(context);
        BackgroundPiwigoFileUploadResponseListener jobListener = new BackgroundPiwigoFileUploadResponseListener(context);
        while(!exit) {
            if (jobs.getUploadJobsCount(context) > 0) {
                List<AutoUploadJobConfig> jobConfigList = jobs.getAutoUploadJobs(context);
                for (AutoUploadJobConfig jobConfig : jobConfigList) {

                    if (jobConfig.isJobEnabled(context) && jobConfig.isJobValid(context)) {
                        UploadJob uploadJob = getUploadJob(context, jobConfig, jobListener);
                        if (uploadJob != null) {
                            runJob(uploadJob);
                        }
                    }
                }
            }
            try {
                wait(pollDurationMillis);
            } catch(InterruptedException e) {
                //TODO what about spurious interrupts?
                if(Thread.interrupted()) {
                    exit = true;
                }
            }
        }
    }

    private UploadJob getUploadJob(Context context, AutoUploadJobConfig jobConfig, BackgroundPiwigoFileUploadResponseListener jobListener) {
        File f = jobConfig.getLocalFolderToMonitor(context);
        if(!f.exists()) {
            postNewResponse(jobConfig.getJobId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(jobConfig.getJobId(),"Local folder no longer exists. Ignoring job"));
            return null;
        }
        File[] matchingFiles = f.listFiles(fileFilter.withFileExtIn(jobConfig.getFileExtsToUpload(context)).withMaxSizeMb(jobConfig.getMaxUploadSize(context)));
        if(matchingFiles.length == 0) {
            return null;
        }
        ArrayList<File> filesToUpload = new ArrayList(Arrays.asList(matchingFiles));
        //public UploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, long jobId, long responseHandlerId, ArrayList<File> filesForUpload, CategoryItemStub destinationCategory, int uploadedFilePrivacyLevel, boolean useTempFolder) {
        CategoryItemStub category = jobConfig.getUploadToAlbum(context);
        UploadJob uploadJob = createUploadJob(jobConfig.getConnectionPrefs(context), filesToUpload, category,
                jobConfig.getUploadedFilePrivacyLevel(context), jobListener.getHandlerId());
        return uploadJob;
    }

    @Override
    protected void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        //TODO do something useful with the messages... build a log file?

//        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
//        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }



}
