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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.R;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
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
public class NewPiwigoUploadService extends IntentService {

    private static final String TAG = "PiwigoUploadService";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_UPLOAD_FILES";
    public static final String INTENT_ARG_KEEP_DEVICE_AWAKE = "keepDeviceAwake";
    public static final String INTENT_ARG_JOB_ID = "jobId";
    private static List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<UploadJob>(1));
    private static final SecureRandom random = new SecureRandom();
    private SharedPreferences prefs;

    public NewPiwigoUploadService() {
        super(TAG);
    }

    public static UploadJob createUploadJob(Context context, ArrayList<File> filesForUpload, CategoryItemStub category, int uploadedFilePrivacyLevel, long responseHandlerId, boolean useTempFolder) {
        long jobId = getNextMessageId();
        UploadJob uploadJob = new UploadJob(jobId, responseHandlerId, filesForUpload, category, uploadedFilePrivacyLevel, useTempFolder);
        activeUploadJobs.add(uploadJob);
        return uploadJob;
    }

    public static long startActionRunOrReRunUploadJob(Context context, UploadJob uploadJob, boolean keepDeviceAwake) {

        Intent intent = new Intent(context, NewPiwigoUploadService.class);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra(INTENT_ARG_JOB_ID, uploadJob.getJobId());
        intent.putExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, keepDeviceAwake);
        context.startService(intent);
        uploadJob.setSubmitted(true);
        return uploadJob.getJobId();
    }

    public static UploadJob getFirstActiveJob(Context context) {
        if(activeUploadJobs.size() == 0) {
            return loadStateFromDisk(context);
        }
        return activeUploadJobs.get(0);
    }

    public static void removeJob(UploadJob job) {
        activeUploadJobs.remove(job);
    }

    public static UploadJob getActiveJob(Context context, long jobId) {
        for (UploadJob uploadJob : activeUploadJobs) {
            if (uploadJob.getJobId() == jobId) {
                return uploadJob;
            }
        }
        UploadJob job = loadStateFromDisk(context);
        if(job != null && job.getJobId() != jobId) {
            throw new RuntimeException("Job exists on disk, but it doesn't match that expected by the app");
        }
        return job;
    }

    private long callPiwigoServer(AbstractPiwigoWsResponseHandler handler) {
        String piwigoServerUrl = prefs.getString(getApplicationContext().getString(R.string.preference_piwigo_server_address_key), null);
        // need to start the calls async as otherwise their responses wont be placed on the queue to be handled.
        boolean isAsyncMode = true;
        handler.setCallDetails(getApplicationContext(), piwigoServerUrl, isAsyncMode);
        handler.setUsePoolThread(true); // This is requried since though this is a looper thread, we're just running our code in it... hmmm....
        handler.setPublishResponses(false);
        try {
            handler.runCall();
            handler.getRequestHandle();
            if(isAsyncMode) {
                while (handler.isRunning()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting for image chunk to be uploaded", e);
                    }
                }
            }
        } catch (Throwable th) {
            // this catch is just to allow debugging
            th.printStackTrace();
            // now rethrow the exception
            throw th;
        }
        return handler.getMessageId();
    }

    @SuppressLint("WakelockTimeout")
    @Override
    protected void onHandleIntent(Intent intent) {

        boolean keepDeviceAwake = intent.getBooleanExtra(INTENT_ARG_KEEP_DEVICE_AWAKE, false);

        PowerManager.WakeLock wl = null;
        if(keepDeviceAwake) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            wl.acquire();
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int maxChunkUploadAutoRetries = prefs.getInt(getString(R.string.preference_data_upload_chunk_auto_retries_key), getResources().getInteger(R.integer.preference_data_upload_chunk_auto_retries_default));

        long jobId = intent.getLongExtra(INTENT_ARG_JOB_ID, -1);
        UploadJob thisUploadJob = getActiveJob(getApplicationContext(), jobId);
        thisUploadJob.setRunning(true);
        thisUploadJob.setSubmitted(false);

        try {

            saveStateToDisk(thisUploadJob);

            thisUploadJob.calculateChecksums();

            // remove any files that already exist on the server from the upload.
            ImageFindExistingImagesResponseHandler handler = new ImageFindExistingImagesResponseHandler(thisUploadJob.getFileChecksums().values());
            int allowedAttempts = 2;
            while (!handler.isSuccess() && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(handler);
                if(handler.isSuccess()) {
                    processFindPreexistingImagesResponse(thisUploadJob, (PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse)handler.getResponse());
                }
            }
            if (!handler.isSuccess()) {
                // notify the listener of the final error we received from the server
                postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                return;
            }

            // create a secure folder to upload to if required
            if(thisUploadJob.isUseTempFolder() && !createTemporaryUploadAlbum(thisUploadJob)) {
                return;
            }

            saveStateToDisk(thisUploadJob);

            uploadFilesInJob(maxChunkUploadAutoRetries, thisUploadJob);

            if(thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() > 0) {
                boolean success = deleteTemporaryUploadAlbum(thisUploadJob);
                if(!success) {
                    return;
                }
            }

            if(thisUploadJob.hasJobCompletedAllActionsSuccessfully()) {
                deleteStateFromDisk(thisUploadJob);
            }

        } finally {
            thisUploadJob.setRunning(false);
            thisUploadJob.setFinished();
            postNewResponse(jobId, new PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse(getNextMessageId(), thisUploadJob));
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(jobId);
            if(keepDeviceAwake) {
                wl.release();
            }
        }
    }

    public void saveStateToDisk(UploadJob thisUploadJob) {
        File f = new File(getApplicationContext().getExternalCacheDir(), "tmp-uploadJob.state");
        boolean canContinue = true;
        if(f.exists()) {
            if(!f.delete()) {
                Log.d(TAG, "Error writing job to disk - unable to delete previous temporary file");
                canContinue = false;
            }
        }
        if(!canContinue) {
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
            if(oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    Log.d(TAG, "Error closing stream when writing job to disk", e);
                }
            }
        }
        File destinationFile = new File(f.getParentFile(), "uploadJob.state");
        boolean canWrite = true;
        if(destinationFile.exists()) {
            if(!destinationFile.delete()) {
                Log.d(TAG, "Error writing job to disk - unable to delete previous state file to allow replace");
                canWrite = false;
            }
        }
        if(canWrite) {
            f.renameTo(destinationFile);
        }
    }

    private static UploadJob loadStateFromDisk(Context c) {

        UploadJob loadedJobState = null;

        File sourceFile = new File(c.getApplicationContext().getExternalCacheDir(), "uploadJob.state");
        if(sourceFile.exists()) {

            boolean deleteFileNow = false;
            ObjectInputStream ois = null;
            try {

                ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(sourceFile)));
                Object o = ois.readObject();
                loadedJobState = (UploadJob) o;

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
        }
        return loadedJobState;
    }

    public void deleteStateFromDisk(UploadJob thisUploadJob) {
        File f = new File(getApplicationContext().getExternalCacheDir(), "uploadJob.state");
        if(f.exists()) {
            if(!f.delete()) {
                Log.d(TAG, "Error deleting job state from disk");
            }
        }
    }

    private boolean deleteTemporaryUploadAlbum(UploadJob thisUploadJob) {
        int allowedAttempts;
        if (thisUploadJob.getFilesNotYetUploaded().size() == 0 && thisUploadJob.getTemporaryUploadAlbum() > 0) {
            throw new IllegalStateException("Cannot delete upload album when job is still incomplete");
        }
        // all files were uploaded successfully.
        //delete temporary hidden album
        AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(thisUploadJob.getTemporaryUploadAlbum());
        allowedAttempts = 2;
        while (!albumDelHandler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // this is blocking
            callPiwigoServer(albumDelHandler);
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

        if(uploadAlbumId < 0) {
            // create temporary hidden album
            UploadAlbumCreateResponseHandler albumGenHandler = new UploadAlbumCreateResponseHandler(thisUploadJob.getUploadToCategory());
            int allowedAttempts = 2;

            while (!albumGenHandler.isSuccess() && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(albumGenHandler);
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
        for (Map.Entry<File, String> fileCheckSumEntry : thisUploadJob.getFileChecksums().entrySet()) {
            if (preexistingItemsMap.containsKey(fileCheckSumEntry.getValue())) {
                File fileFoundOnServer = fileCheckSumEntry.getKey();
                ResourceItem resourceItem = thisUploadJob.getUploadedFileResource(fileFoundOnServer);
                if(resourceItem == null) {
                    resourcesToRetrieve.put(fileFoundOnServer, preexistingItemsMap.get(fileCheckSumEntry.getValue()));
                }
                if(thisUploadJob.needsVerification(fileFoundOnServer) || thisUploadJob.isUploadingData(fileFoundOnServer)) {
                    thisUploadJob.markFileAsVerified(fileFoundOnServer);
                } else if(thisUploadJob.needsConfiguration(fileFoundOnServer)) {
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

        for(Map.Entry<File,Long> entry : resourcesToRetrieve.entrySet()) {
            ImageGetInfoResponseHandler handler = new ImageGetInfoResponseHandler(new ResourceItem(entry.getValue(), null, null, null, null));
            int allowedAttempts = 2;
            while (!handler.isSuccess() && allowedAttempts > 0) {
                allowedAttempts--;
                // this is blocking
                callPiwigoServer(handler);
                if (handler.isSuccess()) {
                    PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse rsp =(PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse)handler.getResponse();
                    thisUploadJob.addFileUploaded(entry.getKey(), rsp.getResource());
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

            if(thisUploadJob.needsUpload(fileForUpload)) {
                uploadFileData(thisUploadJob, fileForUpload, chunkBuffer, maxChunkUploadAutoRetries);
            }

            saveStateToDisk(thisUploadJob);

            if(thisUploadJob.needsVerification(fileForUpload)) {
                verifyUploadedFileData(thisUploadJob, fileForUpload);
            }

            if(thisUploadJob.needsConfiguration(fileForUpload)) {
                configureUploadedFileDetails(thisUploadJob, jobId, fileForUpload);
            }

            saveStateToDisk(thisUploadJob);

            // Once added to album its too late the cancel the upload.
//            if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
//                thisUploadJob.markFileAsNeedsDelete(fileForUpload);
//            }

            if(thisUploadJob.needsDelete(fileForUpload)) {
                if(deleteUploadedResourceFromServer(thisUploadJob.getUploadedFileResource(fileForUpload))) {
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
            if(uploadedResource != null) {
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

            Boolean verifiedUploadedFile = verifyFileNotCorrupted(thisUploadJob.getUploadedFileResource(fileForUpload));
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

        if(thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

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

    private void postNewResponse(long jobId, PiwigoResponseBufferingHandler.Response response) {
        PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, response.getMessageId());
        PiwigoResponseBufferingHandler.getDefault().processResponse(response);
    }

    private boolean deleteUploadedResourceFromServer(ResourceItem uploadedResource) {

        int allowedAttempts = 2;
        ImageDeleteResponseHandler handler = new ImageDeleteResponseHandler(uploadedResource.getId());

        while (!handler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(handler);
        }
        return handler.isSuccess();
    }

    private Boolean verifyFileNotCorrupted(ResourceItem uploadedResource) {

        int allowedAttempts = 2;
        ImageCheckFilesResponseHandler handler = new ImageCheckFilesResponseHandler(uploadedResource);
        while (!handler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(handler);
        }
        return handler.isSuccess()?handler.isFileMatch():null;
    }

    private PiwigoResponseBufferingHandler.BaseResponse updateImageInfoAndPermissions(UploadJob thisUploadJob, File fileForUpload, ResourceItem uploadedResource) {
        int allowedAttempts = 2;
//        uploadedResource.getLinkedAlbums().clear();
        uploadedResource.getLinkedAlbums().add(thisUploadJob.getUploadToCategory());
        ImageUpdateInfoResponseHandler handler = new ImageUpdateInfoResponseHandler(uploadedResource);

        while (!handler.isSuccess() && allowedAttempts > 0) {
            allowedAttempts--;
            // start blocking webservice call
            callPiwigoServer(handler);
        }
        return handler.getResponse();
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
        if(skipChunksData != null) {
            thisUploadJob.deleteChunksAlreadyUploadedData(fileForUpload);
            if(skipChunksData.getFileChecksum().equals(newChecksum)) {
                // If the checksums still match (file to upload hasn't been altered)
                chunksUploadedAlready = skipChunksData.getCountChunksUploaded();
                chunkId = chunksUploadedAlready;
                fileBytesUploaded = skipChunksData.getBytesUploaded();
                uploadToFilename = skipChunksData.getUploadName();
            }
        }
        // chunk count is chunks in part of file left plus uploaded chunk count.
        chunkCount = (long)Math.ceil((double)(totalBytesInFile - fileBytesUploaded) / uploadChunkBuffer.length);
        chunkCount += chunksUploadedAlready;



        String fileMimeType = null;

        BufferedInputStream bis = null;
        SerializablePair<Boolean,ResourceItem> lastChunkUploadResult = null;

        try {
            bis = new BufferedInputStream(new FileInputStream(fileForUpload));
            if(fileBytesUploaded > 0) {
                bis.skip(fileBytesUploaded);
            }

            do {
                if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

                    bytesOfDataInChunk = bis.read(uploadChunkBuffer);

                    if (bytesOfDataInChunk > 0) {

//                        String data = Base64.encodeToString(buffer, 0, bytesOfData, Base64.DEFAULT);
                        ByteArrayInputStream data = new ByteArrayInputStream(uploadChunkBuffer, 0, bytesOfDataInChunk);

                        long uploadToAlbumId = thisUploadJob.getTemporaryUploadAlbum();
                        if(uploadToAlbumId < 0) {
                            uploadToAlbumId = thisUploadJob.getUploadToCategory();
                        }

                        UploadFileChunk currentUploadFileChunk = new UploadFileChunk(thisUploadJob.getJobId(), fileForUpload, uploadToFilename, uploadToAlbumId, data, chunkId, chunkCount, fileMimeType);
                        lastChunkUploadResult = uploadStreamChunk(thisUploadJob, currentUploadFileChunk, maxChunkUploadAutoRetries);

                        chunkId++;
                        boolean chunkUploadedOk = lastChunkUploadResult.first;
                        if(chunkUploadedOk) {
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

            if(fileBytesUploaded < totalBytesInFile) {
                if(!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                    // notify the listener that upload has been cancelled for this file (at user's request)
                    postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.FileUploadCancelledResponse(getNextMessageId(), fileForUpload));
                }
            }
        } finally {
            if(bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception on closing File input stream", e);
                }
            }
        }

        return lastChunkUploadResult == null ? null : lastChunkUploadResult.second;
    }

    private SerializablePair<Boolean,ResourceItem> uploadStreamChunk(UploadJob thisUploadJob, UploadFileChunk currentUploadFileChunk, int maxChunkUploadAutoRetries) {

        // Attempt to upload this chunk of the file
        NewImageUploadFileChunkResponseHandler handler = new NewImageUploadFileChunkResponseHandler(currentUploadFileChunk);



        while (!handler.isSuccess() && (currentUploadFileChunk.getUploadAttempts() - 1) < maxChunkUploadAutoRetries) {
            // blocking call to webservice
            callPiwigoServer(handler);
        }


        ResourceItem uploadedResource = null;
        if(handler.isSuccess()) {
            uploadedResource = ((PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse)handler.getResponse()).getUploadedResource();
        }

        if (!handler.isSuccess()) {
            // notify listener of failure
            postNewResponse(thisUploadJob.getJobId(), new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse(getNextMessageId(), currentUploadFileChunk.getOriginalFile(), handler.getResponse()));
        } else {
            if(currentUploadFileChunk.getChunkId() == 0) {
                thisUploadJob.markFileAsUploading(currentUploadFileChunk.getOriginalFile());
            }
        }

        return new SerializablePair<>(handler.isSuccess(), uploadedResource);
    }


}
