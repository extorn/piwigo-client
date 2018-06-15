package delit.piwigoclient.piwigoApi.upload;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.Worker;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageFindExistingImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageCheckFilesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.NewImageUploadFileChunkResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.UploadAlbumCreateResponseHandler;
import delit.piwigoclient.util.SerializablePair;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
public abstract class BasePiwigoUploadService extends IntentService {

    public static final String INTENT_ARG_KEEP_DEVICE_AWAKE = "keepDeviceAwake";
    private static final String TAG = "UploadService";
    private static final List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<UploadJob>(1));
    private static final SecureRandom random = new SecureRandom();
    private SharedPreferences prefs;

    public BasePiwigoUploadService(String tag) {
        super(tag);
    }

    public static UploadJob createUploadJob(ConnectionPreferences.ProfilePreferences connectionPrefs, ArrayList<File> filesForUpload, CategoryItemStub category, int uploadedFilePrivacyLevel, long responseHandlerId) {
        long jobId = getNextMessageId();
        UploadJob uploadJob = new UploadJob(connectionPrefs, jobId, responseHandlerId, filesForUpload, category, uploadedFilePrivacyLevel);
        activeUploadJobs.add(uploadJob);
        return uploadJob;
    }

    public static UploadJob getFirstActiveForegroundJob(Context context) {
        if (activeUploadJobs.size() == 0) {
            return loadForegroundJobStateFromDisk(context);
        }
        for(UploadJob job : activeUploadJobs) {
            if(!job.isRunInBackground()) {
                return job;
            }
        }
        return loadForegroundJobStateFromDisk(context);
    }

    public static void removeJob(UploadJob job) {
        activeUploadJobs.remove(job);
    }

    public static UploadJob getActiveBackgroundJob(Context context) {
        for (UploadJob uploadJob : activeUploadJobs) {
            if (uploadJob.isRunInBackground()) {
                return uploadJob;
            }
        }
        activeUploadJobs.addAll(loadBackgroundJobsStateFromDisk(context));
        for (UploadJob uploadJob : activeUploadJobs) {
            if (uploadJob.isRunInBackground()) {
                return uploadJob;
            }
        }
        return null;
    }

    public static UploadJob getActiveForegroundJob(Context context, long jobId) {
        for (UploadJob uploadJob : activeUploadJobs) {
            if (uploadJob.getJobId() == jobId) {
                return uploadJob;
            }
        }
        UploadJob job = loadForegroundJobStateFromDisk(context);
        if (job != null && job.getJobId() != jobId) {
            throw new RuntimeException("Job exists on disk, but it doesn't match that expected by the app");
        }
        return job;
    }

    private static List<UploadJob> loadBackgroundJobsStateFromDisk(Context c) {
        File jobsFolder = new File(c.getApplicationContext().getExternalCacheDir(), "uploadJobs");
        if(!jobsFolder.exists()) {
            jobsFolder.mkdir();
            return new ArrayList<UploadJob>();
        }

        List<UploadJob> jobs = new ArrayList<>();
        File[] jobFiles = jobsFolder.listFiles();
        for(int i = 0; i < jobFiles.length; i++) {
            UploadJob job = loadJobFromFile(jobFiles[i]);
            if(job != null) {
                jobs.add(job);
            } else {
                jobFiles[i].delete();
            }
        }
        if(activeUploadJobs != null) {
            for (UploadJob activeJob : activeUploadJobs) {
                UploadJob loadedJob = null;
                for(Iterator<UploadJob> iter = jobs.iterator(); iter.hasNext(); loadedJob = iter.next()) {
                    if(loadedJob.getJobId() == activeJob.getJobId()) {
                        iter.remove();
                    }
                }
            }
        }
        return jobs;
    }

    private static UploadJob loadForegroundJobStateFromDisk(Context c) {

        UploadJob loadedJobState = null;

        File sourceFile = new File(c.getApplicationContext().getExternalCacheDir(), "uploadJob.state");
        if (sourceFile.exists()) {
            loadedJobState = loadJobFromFile(sourceFile);
        }
        return loadedJobState;
    }

    private static UploadJob loadJobFromFile(File sourceFile) {
        boolean deleteFileNow = false;
        ObjectInputStream ois = null;
        try {

            ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
            Object o = ois.readObject();
            return (UploadJob) o;

        } catch (FileNotFoundException e) {
            Log.d(TAG, "Error reading job state from disk", e);
        } catch (IOException e) {
            Log.d(TAG, "Error reading job state from disk", e);
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Error reading job state from disk - Job has changed shape since this version.", e);
            deleteFileNow = true;
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    Log.d(TAG, "Error closing stream when reading job from disk", e);
                }
            }
            if (deleteFileNow) {
                sourceFile.delete();
            }
        }
        return null;
    }

    public static void deleteStateFromDisk(Context c, UploadJob thisUploadJob) {
        File f = new File(c.getExternalCacheDir(), "uploadJob.state");
        if (f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Error deleting job state from disk");
            }
        }
    }

    private void callPiwigoServer(AbstractPiwigoWsResponseHandler handler, UploadJob uploadJob) {
        CustomWorker worker = new CustomWorker(handler, uploadJob, getApplicationContext());
        worker.run(handler.getMessageId());
    }

    protected PowerManager.WakeLock getWakeLock(Intent intent) {
        boolean keepDeviceAwake = intent.getBooleanExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, false);
        PowerManager.WakeLock wl = null;
        if (keepDeviceAwake) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            if (wl != null) {
                wl.acquire();
            }
        }
        return wl;
    }

    @SuppressLint("WakelockTimeout")
    @Override
    protected void onHandleIntent(Intent intent) {


        PowerManager.WakeLock wl = getWakeLock(intent);
        try {
            doWork(intent);
        } finally {
            releaseWakeLock(wl);
        }

    }

    protected void releaseWakeLock(PowerManager.WakeLock wl) {
        if (wl != null) {
            wl.release();
        }
    }

    protected abstract void doWork(Intent intent);

    protected abstract void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response);

    protected void runJob(long jobId) {
        UploadJob thisUploadJob = getActiveForegroundJob(getApplicationContext(), jobId);
        runJob(thisUploadJob);
    }

    protected void runJob(UploadJob thisUploadJob) {

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int maxChunkUploadAutoRetries = prefs.getInt(getString(R.string.preference_data_upload_chunk_auto_retries_key), getResources().getInteger(R.integer.preference_data_upload_chunk_auto_retries_default));

        if (thisUploadJob == null) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Upload job could not be located immediately after creating it - wierd!");
            }
            return;
        }
        thisUploadJob.setRunning(true);
        thisUploadJob.setSubmitted(false);

        try {

            saveStateToDisk(thisUploadJob);

            thisUploadJob.calculateChecksums();

            // is name or md5sum used for uniqueness on this server?
            boolean nameUnique = "name".equals(prefs.getString(getString(R.string.preference_gallery_unique_id_key), getResources().getString(R.string.preference_gallery_unique_id_default)));
            Collection<String> uniqueIdsList;
            if (nameUnique) {
                uniqueIdsList = thisUploadJob.getFileToFilenamesMap().values();
            } else {
                uniqueIdsList = thisUploadJob.getFileChecksums().values();
            }
            // remove any files that already exist on the server from the upload.
            ImageFindExistingImagesResponseHandler imageFindExistingHandler = new ImageFindExistingImagesResponseHandler(uniqueIdsList, nameUnique);
            int allowedAttempts = 2;
            while (!imageFindExistingHandler.isSuccess() && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(imageFindExistingHandler, thisUploadJob);
                if (imageFindExistingHandler.isSuccess()) {
                    processFindPreexistingImagesResponse(thisUploadJob, (PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse) imageFindExistingHandler.getResponse());
                }
            }
            if (!imageFindExistingHandler.isSuccess()) {
                // notify the listener of the final error we received from the server
                postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
                return;
            }

            boolean useTempFolder = !PiwigoSessionDetails.isUseCommunityPlugin(thisUploadJob.getConnectionPrefs());

            // create a secure folder to upload to if required
            if (useTempFolder && !createTemporaryUploadAlbum(thisUploadJob)) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            uploadFilesInJob(maxChunkUploadAutoRetries, thisUploadJob);

            if (thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() > 0) {
                boolean success = deleteTemporaryUploadAlbum(thisUploadJob);
                if (!success) {
                    return;
                }
            }

            if (thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                deleteStateFromDisk(getApplicationContext(), thisUploadJob);
            }

            thisUploadJob.setFinished();

        } finally {
            thisUploadJob.setRunning(false);
            if (!thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                saveStateToDisk(thisUploadJob);
            }

            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse(getNextMessageId(), thisUploadJob));
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(thisUploadJob.getJobId());
        }
    }

    public void saveStateToDisk(UploadJob thisUploadJob) {
        File f = new File(getApplicationContext().getExternalCacheDir(), "tmp-uploadJob.state");
        boolean canContinue = true;
        if (f.exists()) {
            if (!f.delete()) {
                Log.d(TAG, "Error writing job to disk - unable to delete previous temporary file");
                canContinue = false;
            }
        }
        if (!canContinue) {
            return;
        }
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
            oos.writeObject(thisUploadJob);
            oos.flush();
        } catch (IOException e) {
            Log.d(TAG, "Error writing job to disk", e);
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Log.d(TAG, "Error closing stream when writing job to disk", e);
                }
            }
        }
        File destinationFile = new File(f.getParentFile(), "uploadJob.state");
        boolean canWrite = true;
        if (destinationFile.exists()) {
            if (!destinationFile.delete()) {
                Log.d(TAG, "Error writing job to disk - unable to delete previous state file to allow replace");
                canWrite = false;
            }
        }
        if (canWrite) {
            f.renameTo(destinationFile);
        }
    }

    private boolean deleteTemporaryUploadAlbum(UploadJob thisUploadJob) {
        int allowedAttempts;
        if (thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() < 0) {
            throw new IllegalStateException("Cannot delete upload album when job is still incomplete");
        }
        // all files were uploaded successfully.
        //delete temporary hidden album
        AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(thisUploadJob.getTemporaryUploadAlbum());
        allowedAttempts = 2;
        while (!albumDelHandler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // this is blocking
            callPiwigoServer(albumDelHandler, thisUploadJob);
        }
        if (!albumDelHandler.isSuccess()) {
            // notify the listener of the final error we received from the server
            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse(getNextMessageId(), albumDelHandler.getResponse()));
        } else {
            thisUploadJob.setTemporaryUploadAlbum(-1);
        }
        return albumDelHandler.isSuccess();
    }

    private boolean createTemporaryUploadAlbum(UploadJob thisUploadJob) {

        long uploadAlbumId = thisUploadJob.getTemporaryUploadAlbum();

        if (uploadAlbumId < 0) {
            // create temporary hidden album
            UploadAlbumCreateResponseHandler albumGenHandler = new UploadAlbumCreateResponseHandler(thisUploadJob.getUploadToCategory());
            int allowedAttempts = 2;

            while (!albumGenHandler.isSuccess() && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(albumGenHandler, thisUploadJob);
                if (albumGenHandler.isSuccess()) {
                    uploadAlbumId = ((PiwigoResponseBufferingHandler.PiwigoAlbumCreatedResponse) albumGenHandler.getResponse()).getNewAlbumId();
                }
            }
            if (!albumGenHandler.isSuccess() || uploadAlbumId < 0) {
                // notify the listener of the final error we received from the server
                postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse(getNextMessageId(), albumGenHandler.getResponse()));
                return false;
            } else {
                thisUploadJob.setTemporaryUploadAlbum(uploadAlbumId);
            }
        }
        return true;
    }

    private void processFindPreexistingImagesResponse(UploadJob thisUploadJob, PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse response) {
        HashMap<String, Long> preexistingItemsMap = response.getExistingImages();
        ArrayList<File> filesExistingOnServerAlready = new ArrayList<>();
        HashMap<File, Long> resourcesToRetrieve = new HashMap<>();

        // is name or md5sum used for uniqueness on this server?
        boolean nameUnique = "name".equals(prefs.getString(getString(R.string.preference_gallery_unique_id_key), getResources().getString(R.string.preference_gallery_unique_id_default)));
        Map<File, String> uniqueIdsSet;
        if (nameUnique) {
            uniqueIdsSet = thisUploadJob.getFileToFilenamesMap();
        } else {
            uniqueIdsSet = thisUploadJob.getFileChecksums();
        }

        for (Map.Entry<File, String> fileCheckSumEntry : uniqueIdsSet.entrySet()) {
            if (preexistingItemsMap.containsKey(fileCheckSumEntry.getValue())) {
                File fileFoundOnServer = fileCheckSumEntry.getKey();
                ResourceItem resourceItem = thisUploadJob.getUploadedFileResource(fileFoundOnServer);
                if (resourceItem == null) {
                    resourcesToRetrieve.put(fileFoundOnServer, preexistingItemsMap.get(fileCheckSumEntry.getValue()));
                }
                if (thisUploadJob.needsVerification(fileFoundOnServer) || thisUploadJob.isUploadingData(fileFoundOnServer)) {
                    thisUploadJob.markFileAsVerified(fileFoundOnServer);
                } else if (thisUploadJob.needsConfiguration(fileFoundOnServer)) {
                    // we know this exists, but it isn't configured yet just move to analyse the next file.
                } else {
                    // mark this file as needing configuration (probably uploaded by ?someone else? or a different upload mechanism anyway)
                    thisUploadJob.markFileAsVerified(fileFoundOnServer);
                    filesExistingOnServerAlready.add(fileFoundOnServer);
                }
            }
        }

        if (filesExistingOnServerAlready.size() > 0) {
//            thisUploadJob.getFilesForUpload().removeAll(filesExistingOnServerAlready);
            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse(getNextMessageId(), filesExistingOnServerAlready));
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());

        String multimediaExtensionList = prefs.getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
        for (Map.Entry<File, Long> entry : resourcesToRetrieve.entrySet()) {

            ImageGetInfoResponseHandler getImageInfoHandler = new ImageGetInfoResponseHandler(new ResourceItem(entry.getValue(), null, null, null, null), multimediaExtensionList);
            int allowedAttempts = 2;
            boolean success = false;
            while (!success && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(getImageInfoHandler, thisUploadJob);
                if (getImageInfoHandler.isSuccess()) {
                    success = true;
                    PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse rsp = (PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) getImageInfoHandler.getResponse();
                    thisUploadJob.addFileUploaded(entry.getKey(), rsp.getResource());
                } else if (sessionDetails.isUseCommunityPlugin() && getImageInfoHandler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                    PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse rsp = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) getImageInfoHandler.getResponse();
                    if (rsp.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                        success = true; // image is on the server, but not yet approved.
                        thisUploadJob.addFileUploaded(entry.getKey(), null);
                        postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse(getNextMessageId(), entry.getKey(), thisUploadJob.getUploadProgress(entry.getKey())));
                    }
                }
            }
        }
    }

    private void notifyListenersOfCustomErrorUploadingFile(UploadJob thisUploadJob, File fileBeingUploaded, String errorMessage) {
        long jobId = thisUploadJob.getJobId();
        PiwigoResponseBufferingHandler.CustomErrorResponse errorResponse = new PiwigoResponseBufferingHandler.CustomErrorResponse(jobId, errorMessage);
        PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileBeingUploaded, errorResponse);
        postNewResponse(jobId, r1);
    }

    private void uploadFilesInJob(int maxChunkUploadAutoRetries, UploadJob thisUploadJob) {

        long jobId = thisUploadJob.getJobId();
        byte[] chunkBuffer = buildSensibleBuffer();

        for (File fileForUpload : thisUploadJob.getFilesForUpload()) {

            if(!fileForUpload.exists()) {
                thisUploadJob.cancelFileUpload(fileForUpload);
            }

            if (thisUploadJob.needsUpload(fileForUpload)) {
                uploadFileData(thisUploadJob, fileForUpload, chunkBuffer, maxChunkUploadAutoRetries);
            }

            saveStateToDisk(thisUploadJob);

            if (thisUploadJob.needsVerification(fileForUpload)) {
                verifyUploadedFileData(thisUploadJob, fileForUpload);
            }

            if (thisUploadJob.needsConfiguration(fileForUpload)) {
                configureUploadedFileDetails(thisUploadJob, jobId, fileForUpload);
            }

            saveStateToDisk(thisUploadJob);

            // Once added to album its too late the cancel the upload.
//            if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//                thisUploadJob.markFileAsNeedsDelete(fileForUpload);
//            }

            if (thisUploadJob.needsDelete(fileForUpload)) {
                if (deleteUploadedResourceFromServer(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUpload))) {
                    thisUploadJob.markFileAsDeleted(fileForUpload);
                    // notify the listener that upload has been cancelled for this file
                    postNewResponse(jobId, new PiwigoResponseBufferingHandler.FileUploadCancelledResponse(getNextMessageId(), fileForUpload));
                } else {
                    //TODO notify user the uploaded file couldn't be deleted - needs manual intervention to remove it. Will be handled on Retry?
                }
            }

            saveStateToDisk(thisUploadJob);

        }
    }

    private void configureUploadedFileDetails(UploadJob thisUploadJob, long jobId, File fileForUpload) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            ResourceItem uploadedResource = thisUploadJob.getUploadedFileResource(fileForUpload);
            if (uploadedResource != null) {
                PiwigoResponseBufferingHandler.BaseResponse response = updateImageInfoAndPermissions(thisUploadJob, fileForUpload, uploadedResource);

                if (response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {

                    // notify the listener of the final error we received from the server
                    postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse(getNextMessageId(), fileForUpload, response));

                } else {
                    thisUploadJob.markFileAsConfigured(fileForUpload);
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
                }
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    private void verifyUploadedFileData(UploadJob thisUploadJob, File fileForUpload) {
        if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            Boolean verifiedUploadedFile = verifyFileNotCorrupted(thisUploadJob, thisUploadJob.getUploadedFileResource(fileForUpload));
            if (verifiedUploadedFile == null) {
                // notify the listener of the final error we received from the server
                String errorMsg = String.format(getString(R.string.error_upload_file_verification_failed), fileForUpload.getName());
                notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUpload, errorMsg);
            } else if (verifiedUploadedFile) {
                thisUploadJob.markFileAsVerified(fileForUpload);
                postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse(getNextMessageId(), fileForUpload, thisUploadJob.getUploadProgress(fileForUpload)));
            }
        } else {
            thisUploadJob.markFileAsNeedsDelete(fileForUpload);
        }
    }

    private void uploadFileData(UploadJob thisUploadJob, File fileForUpload, byte[] chunkBuffer, int maxChunkUploadAutoRetries) {
        long jobId = thisUploadJob.getJobId();

        if (!thisUploadJob.isCancelUploadAsap() && thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

            postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoStartUploadFileResponse(getNextMessageId(), fileForUpload));

            try {
                String filename = fileForUpload.getName();
                String ext = null;
                int dotPos = filename.lastIndexOf('.');
                if (0 <= dotPos) {
                    ext = filename.substring(dotPos + 1);
                }

                if (ext == null) {
                    thisUploadJob.cancelFileUpload(fileForUpload);
                    // notify the listener of the final error we received from the server
                    String errorMsg = String.format(getString(R.string.error_upload_file_ext_missing_pattern), fileForUpload.getName());
                    notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUpload, errorMsg);
                }

                if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                    String tempUploadName = "PiwigoClient_Upload_" + random.nextLong() + '.' + ext;

                    ResourceItem uploadedResource = uploadFileInChunks(thisUploadJob, chunkBuffer, fileForUpload, tempUploadName, maxChunkUploadAutoRetries);

                    if (uploadedResource != null) {
                        // this should ALWAYS be the case!

                        uploadedResource.setName(fileForUpload.getName());
                        uploadedResource.setParentageChain(thisUploadJob.getUploadToCategoryParentage(), thisUploadJob.getUploadToCategory());
                        uploadedResource.setPrivacyLevel(thisUploadJob.getPrivacyLevelWanted());
                        uploadedResource.setFileChecksum(thisUploadJob.getFileChecksum(fileForUpload));

                        thisUploadJob.addFileUploaded(fileForUpload, uploadedResource);

                    } else if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                        //This block gets entered when the upload is cancelled for a file
                    } else {
                        // notify the listener of the final error we received from the server
                        String errorMsg = getString(R.string.error_upload_server_response_invalid);
                        notifyListenersOfCustomErrorUploadingFile(thisUploadJob, fileForUpload, errorMsg);
                    }
                }
            } catch (FileNotFoundException e) {
                postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fileForUpload, e));
            } catch (final IOException e) {
                postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse(getNextMessageId(), fileForUpload, e));
            }
        }
    }

    private boolean deleteUploadedResourceFromServer(UploadJob uploadJob, ResourceItem uploadedResource) {

        int allowedAttempts = 2;
        ImageDeleteResponseHandler imageDeleteHandler = new ImageDeleteResponseHandler(uploadedResource.getId());

        while (!imageDeleteHandler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(imageDeleteHandler, uploadJob);
        }
        return imageDeleteHandler.isSuccess();
    }

    private Boolean verifyFileNotCorrupted(UploadJob uploadJob, ResourceItem uploadedResource) {

        int allowedAttempts = 2;
        ImageCheckFilesResponseHandler imageFileCheckHandler = new ImageCheckFilesResponseHandler(uploadedResource);
        while (!imageFileCheckHandler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(imageFileCheckHandler, uploadJob);
        }
        return imageFileCheckHandler.isSuccess() ? imageFileCheckHandler.isFileMatch() : null;
    }

    private PiwigoResponseBufferingHandler.BaseResponse updateImageInfoAndPermissions(UploadJob thisUploadJob, File fileForUpload, ResourceItem uploadedResource) {
        int allowedAttempts = 2;
//        uploadedResource.getLinkedAlbums().clear();
        uploadedResource.getLinkedAlbums().add(thisUploadJob.getUploadToCategory());
        ImageUpdateInfoResponseHandler imageInfoUpdateHandler = new ImageUpdateInfoResponseHandler(uploadedResource);

        while (!imageInfoUpdateHandler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(imageInfoUpdateHandler, thisUploadJob);
        }
        return imageInfoUpdateHandler.getResponse();
    }

    private byte[] buildSensibleBuffer() {
        int wantedUploadSizeInKbB = prefs.getInt(getString(R.string.preference_data_upload_chunkSizeKb_key), getResources().getInteger(R.integer.preference_data_upload_chunkSizeKb_default));
        int bufferSizeBytes = 1024 * wantedUploadSizeInKbB; // 512Kb chunk size
//        bufferSizeBytes -= bufferSizeBytes % 3; // ensure 3 byte blocks so base64 encoded pieces fit together again.
        return new byte[bufferSizeBytes];
    }

    private ResourceItem uploadFileInChunks(UploadJob thisUploadJob, byte[] streamBuffer, File fileForUpload, String uploadName, int maxChunkUploadAutoRetries) throws IOException {

        long totalBytesInFile = fileForUpload.length();
        long fileBytesUploaded = 0;
        long chunkId = 0;
        int bytesOfDataInChunk;
        byte[] uploadChunkBuffer = streamBuffer;
        String uploadToFilename = uploadName;
        long chunkCount;
        long chunksUploadedAlready = 0;


        String newChecksum = thisUploadJob.getFileChecksum(fileForUpload);
        // pre-set the upload progress through file to where we got to last time.
        UploadJob.PartialUploadData skipChunksData = thisUploadJob.getChunksAlreadyUploadedData(fileForUpload);
        if (skipChunksData != null) {
            thisUploadJob.deleteChunksAlreadyUploadedData(fileForUpload);
            if (skipChunksData.getFileChecksum().equals(newChecksum)) {
                // If the checksums still match (file to upload hasn't been altered)
                chunksUploadedAlready = skipChunksData.getCountChunksUploaded();
                chunkId = chunksUploadedAlready;
                fileBytesUploaded = skipChunksData.getBytesUploaded();
                uploadToFilename = skipChunksData.getUploadName();
            }
        }
        // chunk count is chunks in part of file left plus uploaded chunk count.
        chunkCount = (long) Math.ceil((double) (totalBytesInFile - fileBytesUploaded) / uploadChunkBuffer.length);
        chunkCount += chunksUploadedAlready;


        String fileMimeType = null;

        BufferedInputStream bis = null;
        SerializablePair<Boolean, ResourceItem> lastChunkUploadResult = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(fileForUpload));
            if (fileBytesUploaded > 0) {
                bis.skip(fileBytesUploaded);
            }

            do {
                if (!thisUploadJob.isCancelUploadAsap() && thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

                    bytesOfDataInChunk = bis.read(uploadChunkBuffer);

                    if (bytesOfDataInChunk > 0) {

//                        String data = Base64.encodeToString(buffer, 0, bytesOfData, Base64.DEFAULT);
                        ByteArrayInputStream data = new ByteArrayInputStream(uploadChunkBuffer, 0, bytesOfDataInChunk);

                        long uploadToAlbumId = thisUploadJob.getTemporaryUploadAlbum();
                        if (uploadToAlbumId < 0) {
                            uploadToAlbumId = thisUploadJob.getUploadToCategory();
                        }

                        UploadFileChunk currentUploadFileChunk = new UploadFileChunk(thisUploadJob.getJobId(), fileForUpload, uploadToFilename, uploadToAlbumId, data, chunkId, chunkCount, fileMimeType);
                        lastChunkUploadResult = uploadStreamChunk(thisUploadJob, currentUploadFileChunk, maxChunkUploadAutoRetries);

                        chunkId++;
                        boolean chunkUploadedOk = lastChunkUploadResult.first;
                        if (chunkUploadedOk) {
                            fileBytesUploaded += bytesOfDataInChunk;
                            thisUploadJob.markFileAsPartiallyUploaded(fileForUpload, uploadToFilename, fileBytesUploaded, chunkId);
                            saveStateToDisk(thisUploadJob);
                            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse(getNextMessageId(), currentUploadFileChunk.getOriginalFile(), thisUploadJob.getUploadProgress(currentUploadFileChunk.getOriginalFile())));
                        } else {
                            bytesOfDataInChunk = -1; // don't upload the rest of the chunks.
                        }

                    }

                } else {
                    bytesOfDataInChunk = -1;
                }
            } while (bytesOfDataInChunk >= 0);

            if (fileBytesUploaded < totalBytesInFile) {
                if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                    // notify the listener that upload has been cancelled for this file (at user's request)
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.FileUploadCancelledResponse(getNextMessageId(), fileForUpload));
                }
            }
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Exception on closing File input stream", e);
                    }
                }
            }
        }

        return lastChunkUploadResult == null ? null : lastChunkUploadResult.second;
    }

    private SerializablePair<Boolean, ResourceItem> uploadStreamChunk(UploadJob thisUploadJob, UploadFileChunk currentUploadFileChunk, int maxChunkUploadAutoRetries) {

        // Attempt to upload this chunk of the file
        NewImageUploadFileChunkResponseHandler imageChunkUploadHandler = new NewImageUploadFileChunkResponseHandler(currentUploadFileChunk);


        while (!imageChunkUploadHandler.isSuccess() && (currentUploadFileChunk.getUploadAttempts() - 1) < maxChunkUploadAutoRetries) {
            // blocking call to webservice
            callPiwigoServer(imageChunkUploadHandler, thisUploadJob);
        }


        ResourceItem uploadedResource = null;
        if (imageChunkUploadHandler.isSuccess()) {
            uploadedResource = ((PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse) imageChunkUploadHandler.getResponse()).getUploadedResource();
        }

        if (!imageChunkUploadHandler.isSuccess()) {
            // notify listener of failure
            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse(getNextMessageId(), currentUploadFileChunk.getOriginalFile(), imageChunkUploadHandler.getResponse()));
        } else {
            if (currentUploadFileChunk.getChunkId() == 0) {
                thisUploadJob.markFileAsUploading(currentUploadFileChunk.getOriginalFile());
            }
        }

        return new SerializablePair<>(imageChunkUploadHandler.isSuccess(), uploadedResource);
    }

    private class CustomWorker extends Worker {

        private UploadJob uploadJob;

        public CustomWorker(AbstractPiwigoDirectResponseHandler handler, UploadJob uploadJob, Context context) {
            super(handler, context);
            this.uploadJob = uploadJob;
        }

        @Override
        protected ConnectionPreferences.ProfilePreferences getProfilePreferences() {
            return uploadJob.getConnectionPrefs();
        }

        @Override
        protected AbstractPiwigoDirectResponseHandler getHandler(SharedPreferences prefs) {
            AbstractPiwigoDirectResponseHandler handler = super.getHandler(prefs);
            if (handler != null) {
                handler.setPublishResponses(false);
            }
            return handler;
        }

        /**
         *
         * @param messageId
         * @return handler succeeded
         */
        public boolean run(long messageId) {
            return doInBackground(messageId);
        }
    }


}
