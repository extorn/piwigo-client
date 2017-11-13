package delit.piwigoclient.piwigoApi.upload;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.loopj.android.http.Base64;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.UploadFileFragment;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageFindExistingImagesResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageAddResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.ImageUploadFileChunkResponseHandler;
import delit.piwigoclient.piwigoApi.upload.handlers.StubAddImageListener;
import delit.piwigoclient.piwigoApi.upload.handlers.StubFindExistingImagesListener;
import delit.piwigoclient.piwigoApi.upload.handlers.StubUploadChunkListener;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 */
@Deprecated
public class PiwigoUploadService extends IntentService {

    private static final String TAG = "PiwigoUploadService";
    private static final String ACTION_UPLOAD_FILES = "delit.piwigoclient.action.ACTION_UPLOAD_FILES";
    private static List<UploadJob> activeUploadJobs = Collections.synchronizedList(new ArrayList<UploadJob>(1));
    private SharedPreferences prefs;

    public PiwigoUploadService() {
        super(TAG);
    }

    public static long startActionUploadImages(Context context, ArrayList<File> filesForUpload, CategoryItemStub category, int uploadedFilePrivacyLevel, long responseHandlerId) {

        long jobId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
        UploadJob uploadJob = new UploadJob(jobId, responseHandlerId, filesForUpload, category, uploadedFilePrivacyLevel);
        activeUploadJobs.add(uploadJob);

        Intent intent = new Intent(context, PiwigoUploadService.class);
        intent.setAction(ACTION_UPLOAD_FILES);
        intent.putExtra("jobId", uploadJob.getJobId());
        context.startService(intent);
        return uploadJob.getJobId();
    }

    public static UploadJob getFirstActiveJob() {
        if(activeUploadJobs.size() == 0) {
            return null;
        }
        return activeUploadJobs.get(0);
    }

    public static void removeJob(UploadJob job) {
        activeUploadJobs.remove(job);
    }

    public static UploadJob getActiveJob(long jobId) {
        for (UploadJob uploadJob : activeUploadJobs) {
            if (uploadJob.getJobId() == jobId) {
                return uploadJob;
            }
        }
        return null;
    }

    private long callPiwigoServer(AbstractPiwigoWsResponseHandler handler) {
        String piwigoServerUrl = prefs.getString(getApplicationContext().getString(R.string.preference_piwigo_server_address_key), null);
        boolean isAsyncMode = true;
        handler.setCallDetails(getApplicationContext(), piwigoServerUrl, isAsyncMode);
        handler.setUsePoolThread(true); // This is requried since though this is a looper thread, we're just running our code in it... hmmm....
        handler.setPublishResponses(false);
        try {
            handler.runCall();
            while(handler.isRunning()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    handler.cancelCallAsap();
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

    private long startActionUploadFileChunk(UploadFileFragment fileChunk) {
        ImageUploadFileChunkResponseHandler handler = new ImageUploadFileChunkResponseHandler(fileChunk);
        
        return callPiwigoServer(handler);
    }

    private long startActionAddImage(String name, String checksum, Long albumId, int privacyLevel) {
        ImageAddResponseHandler handler = new ImageAddResponseHandler(name, checksum, albumId, privacyLevel);
        
        return callPiwigoServer(handler);
    }

    private long startActionImageExists(UploadJob uploadJob) {
        ImageFindExistingImagesResponseHandler handler = new ImageFindExistingImagesResponseHandler(uploadJob.getFileChecksums().values());
        
        return callPiwigoServer(handler);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int maxChunkUploadAutoRetries = prefs.getInt(getString(R.string.preference_data_upload_chunk_auto_retries_key), getResources().getInteger(R.integer.preference_data_upload_chunk_auto_retries_default));

        long jobId = intent.getLongExtra("jobId", -1);
        UploadJob thisUploadJob = getActiveJob(jobId);

        try {

            thisUploadJob.calculateChecksums();


            StubFindExistingImagesListener imageExistsListener = new StubFindExistingImagesListener(thisUploadJob);
            int allowedAttempts = 2;
            while (!imageExistsListener.isSuccess() && allowedAttempts > 0) {
                imageExistsListener.reset();
                allowedAttempts--;
                // this is blocking
                long messageId = startActionImageExists(thisUploadJob);
                // at this point we are guaranteed a response of some kind to process.
                PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, imageExistsListener);
                while(!imageExistsListener.isResponseHandled()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Log.e(TAG,"Interrupted while waiting for image chunk to be uploaded", e);
                    }
                }
            }
            if (!imageExistsListener.isSuccess()) {
                // notify the listener of the final error we received from the server
                long prepareForUploadFailedMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse r = new PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse(prepareForUploadFailedMessageId, imageExistsListener.getError());
                PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, prepareForUploadFailedMessageId);
                PiwigoResponseBufferingHandler.getDefault().processResponse(r);
                return;
            }

            for (File fileForUpload : thisUploadJob.getFilesForUpload()) {

                if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                    continue;
                }
                long startFileUploadMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                PiwigoResponseBufferingHandler.PiwigoStartUploadFileResponse r = new PiwigoResponseBufferingHandler.PiwigoStartUploadFileResponse(startFileUploadMessageId, fileForUpload);
                PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, startFileUploadMessageId);
                PiwigoResponseBufferingHandler.getDefault().processResponse(r);

                try {

                    uploadFile(thisUploadJob, fileForUpload, maxChunkUploadAutoRetries);

                    if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                        StubAddImageListener stubAddImageHandler = new StubAddImageListener();
                        allowedAttempts = 2;
                        final String checksum = thisUploadJob.getFileChecksum(fileForUpload);

                        while (!stubAddImageHandler.isSuccess() && allowedAttempts > 0) {
                            stubAddImageHandler.reset();
                            allowedAttempts--;
                            // start blocking webservice call
                            long messageId = startActionAddImage(fileForUpload.getName(), checksum, thisUploadJob.getUploadToCategory(), thisUploadJob.getPrivacyLevelWanted());
                            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(messageId, stubAddImageHandler);
                            // wait here until a response has been processed.
                            while(!stubAddImageHandler.isResponseHandled()) {
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException e) {
                                    Log.e(TAG,"Interrupted while waiting for image to be added", e);
                                }
                            }
                        }
                        if (stubAddImageHandler.isSuccess()) {
//        TODO to remove this, must enable UploadFragment handler for realtime processing of messages again
                            for(Long albumParent : thisUploadJob.getUploadToCategoryParentage()) {
                                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
                            }
                            EventBus.getDefault().post(new AlbumAlteredEvent(thisUploadJob.getUploadToCategory()));

                            thisUploadJob.addFileUploaded(fileForUpload, null);
                            long addToAlbumSuccessMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                            PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumSuccessResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumSuccessResponse(addToAlbumSuccessMessageId, fileForUpload);
                            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, addToAlbumSuccessMessageId);
                            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                        } else {
                            // notify the listener of the final error we received from the server
                            long addToAlbumFailureMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                            PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse(addToAlbumFailureMessageId, fileForUpload, stubAddImageHandler.getError());
                            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, addToAlbumFailureMessageId);
                            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                        }
                    }
                } catch (FileNotFoundException e) {
                    long fileUploadFailureMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                    PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse(fileUploadFailureMessageId, fileForUpload, e);
                    PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, fileUploadFailureMessageId);
                    PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                } catch (final IOException e) {
                    long fileUploadSuccessMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                    PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse(fileUploadSuccessMessageId, fileForUpload, e);
                    PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, fileUploadSuccessMessageId);
                    PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                }
            }
        } finally {
            long fileUploadSuccessMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
            PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileJobCompleteResponse(fileUploadSuccessMessageId, thisUploadJob);
            // must not use jobid as the message id else it will be handled out of sequence.
            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(jobId, fileUploadSuccessMessageId);
            //now deregister as a listener for this job.
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(jobId);
            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
            thisUploadJob.setFinished();
        }
    }

    private byte[] buildSensibleBuffer() {
        int wantedUploadSizeInKbB = prefs.getInt(getString(R.string.preference_data_upload_chunkSizeKb_key), getResources().getInteger(R.integer.preference_data_upload_chunkSizeKb_default));
        int bufferSizeBytes = 1024 * wantedUploadSizeInKbB; // 512Kb chunk size
        bufferSizeBytes -= bufferSizeBytes % 3; // ensure 3 byte blocks so base64 encoded pieces fit together again.
        return new byte[bufferSizeBytes];
    }

    private void uploadFile(UploadJob thisUploadJob, File fileForUpload, int maxChunkUploadAutoRetries) throws IOException {
        long totalBytesInFile = fileForUpload.length();
        long fileBytesUploaded = 0;
        int chunkId = 1;
        int bytesOfData;

        byte[] buffer = buildSensibleBuffer();

        final String checksum = thisUploadJob.getFileChecksum(fileForUpload);
        StubUploadChunkListener stubUploadChunkHandler = new StubUploadChunkListener();

        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(fileForUpload));

            do {
                if (thisUploadJob.isFileUploadStillWanted(fileForUpload)) {

                    bytesOfData = bis.read(buffer);

                    if (bytesOfData > 0) {

                        String data = Base64.encodeToString(buffer, 0, bytesOfData, Base64.DEFAULT);
//                        ByteArrayInputStream data = new ByteArrayInputStream(buffer, 0, bytesOfData);
//                        byte[] data = Base64.encode(buffer, 0, bytesOfData, Base64.DEFAULT);

                        UploadFileFragment currentUploadFileFragment = new UploadFileFragment(fileForUpload.getName(), fileForUpload, data, checksum, thisUploadJob.getJobId(), chunkId);

                        boolean chunkUploadedOkay = uploadStreamChunk(stubUploadChunkHandler, currentUploadFileFragment, maxChunkUploadAutoRetries);
                        if (!chunkUploadedOkay) {
                            // notify listener of failure
                            long chunkFailedMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                            PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse(chunkFailedMessageId, currentUploadFileFragment.getOriginalFile(), stubUploadChunkHandler.getError());
                            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(thisUploadJob.getJobId(), chunkFailedMessageId);
                            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                            //terminate upload of this file now
                            thisUploadJob.cancelFileUpload(currentUploadFileFragment.getOriginalFile());
                        } else {
                            fileBytesUploaded += bytesOfData;
                            final int progress = Math.round(((float) fileBytesUploaded) / totalBytesInFile * 100);
                            long chunkSuccessMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
                            PiwigoResponseBufferingHandler.PiwigoUploadFileChunkSuccessResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileChunkSuccessResponse(chunkSuccessMessageId, currentUploadFileFragment.getOriginalFile(), progress);
                            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(thisUploadJob.getJobId(), chunkSuccessMessageId);
                            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
                        }
                        chunkId++;
                    }

                } else {
                    bytesOfData = -1;
                }
                if (!thisUploadJob.isFileUploadStillWanted(fileForUpload)) {
                    bytesOfData = -1;
                }
            } while (bytesOfData >= 0);

        } finally {
            if(bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    Log.e(TAG, "Exception on closing File input stream", e);
                }
            }
        }
    }

    private boolean uploadStreamChunk(StubUploadChunkListener stubUploadChunkHandler, UploadFileFragment currentUploadFileFragment, int maxChunkUploadAutoRetries) {

        // Attempt to upload this chunk of the file
        stubUploadChunkHandler.setUploadFileFragment(currentUploadFileFragment);
        while (!stubUploadChunkHandler.isSuccess() && (currentUploadFileFragment.getUploadAttempts() - 1) < maxChunkUploadAutoRetries) {
            stubUploadChunkHandler.reset();
            // blocking call to webservice
            long chunkUploadMessageId = startActionUploadFileChunk(currentUploadFileFragment);
            // handle the webservice response
            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(chunkUploadMessageId, stubUploadChunkHandler);
            while(!stubUploadChunkHandler.isResponseHandled()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Log.e(TAG,"Interrupted while waiting for image chunk to be uploaded", e);
                }
            }
        }

        return stubUploadChunkHandler.isSuccess();
    }


}