package delit.piwigoclient.piwigoApi.upload.actors.dataupload;

import android.content.Context;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

import delit.libs.util.IOUtils;
import delit.libs.util.progress.BasicProgressTracker;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDataTxInfo;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.JobLoadActor;
import delit.piwigoclient.piwigoApi.upload.messages.FileUploadCancelledResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileChunkFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class MyChunkUploadListener implements FileChunkUploaderActorThread.ChunkUploadListener {

    private final FileChunkerActorThread chunkProducer;
    private final ActorListener listener;
    private final boolean filenamesAreUnique;
    private Map<Uri, BasicProgressTracker> chunkProgressTrackers;
    private Context context;


    public MyChunkUploadListener(Context context, FileChunkerActorThread chunkProducer, ActorListener listener, boolean filenamesAreUnique) {
        this.context = context;
        this.listener = listener;
        this.chunkProducer = chunkProducer;
        this.filenamesAreUnique = filenamesAreUnique;
        chunkProgressTrackers = new HashMap<>();
    }

    public ActorListener getListener() {
        return listener;
    }

    @Override
        public void onChunkUploadFailed(UploadJob thisUploadJob, UploadFileChunk chunk, PiwigoResponseBufferingHandler.BaseResponse piwigoServerResponse) {
            try {
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileChunkFailedResponse(getNextMessageId(), chunk.getUploadJobItemKey(), piwigoServerResponse));
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(chunk.getUploadJobItemKey());
                if (fud.isUploadCancelled()) {
                    // notify the listener that upload has been cancelled for this file (at user's request)
                    getListener().recordAndPostNewResponse(thisUploadJob, new FileUploadCancelledResponse(getNextMessageId(), chunk.getUploadJobItemKey()));
                }
            } finally {
                chunkProducer.returnChunkToPool(chunk);
                //TODO maybe stop uploading the rest of the chunks.
                //chunkProducer.stopAsap();
            }
        }

        @Override
        public void onUploadCancelled() {
            chunkProgressTrackers.clear();
        }

        @Override
        public boolean onChunkUploadSuccess(UploadJob thisUploadJob, UploadFileChunk chunk) {
            BasicProgressTracker chunkProgressTracker = getChunkProgressTracker(thisUploadJob, chunk.getUploadJobItemKey());

            try {
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(chunk.getUploadJobItemKey());
                String fileChecksum = fud.getChecksumOfFileToUpload();
                if (filenamesAreUnique) {
                    fileChecksum = IOUtils.getFilename(getContext(), chunk.getFileBeingUploaded());
                }
                if (chunkProgressTracker == null) {
                    // this file upload is just starting. The overall progress has no record of this file upload byte transfer progress
                    chunkProgressTracker = thisUploadJob.getTaskProgressTrackerForSingleFileChunkParsing(chunk.getUploadJobItemKey(), chunk.getFileSizeBytes());
                    storeChunkProgressTracker(chunk.getUploadJobItemKey(), chunkProgressTracker);
                    fud.setStatusUploading();
                    fud.recordChunkUploaded(chunk.getFilenameOnServer(), fileChecksum, chunk.getFileSizeBytes(), chunk.getChunkSizeBytes(), chunk.getChunkId(), chunk.getMaxChunkSize());
                } else {
                    fud.recordChunkUploaded(fileChecksum, chunk.getChunkSizeBytes(), chunk.getChunkId());
                }
                chunkProgressTracker.incrementWorkDone(chunk.getChunkSizeBytes());

                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), chunk.getUploadJobItemKey(), thisUploadJob.getFileUploadDetails(chunk.getUploadJobItemKey()).getOverallUploadProgress()));
                new JobLoadActor(getContext()).saveStateToDisk(thisUploadJob);
            } finally {
                chunkProducer.returnChunkToPool(chunk);
            }
            return chunkProgressTracker.isComplete();
        }

    private Context getContext() {
            return context;
    }

    private void storeChunkProgressTracker(Uri uploadJobItemKey, BasicProgressTracker chunkProgressTracker) {
            chunkProgressTrackers.put(uploadJobItemKey, chunkProgressTracker);
        }

        private BasicProgressTracker getChunkProgressTracker(UploadJob thisUploadJob, Uri uploadJobItemKey) {
            BasicProgressTracker tracker = chunkProgressTrackers.get(uploadJobItemKey);
            if (tracker == null) {
                FileUploadDataTxInfo uploadData = thisUploadJob.getFileUploadDetails(uploadJobItemKey).getChunksAlreadyUploadedData();
                if (uploadData != null) {
                    tracker = thisUploadJob.getTaskProgressTrackerForSingleFileChunkParsing(uploadJobItemKey, uploadData.getTotalBytesToUpload());
                    storeChunkProgressTracker(uploadJobItemKey, tracker);
                }
            }
            return tracker;
        }
        

    }