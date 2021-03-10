package delit.piwigoclient.piwigoApi.upload.actors.dataupload;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDataTxInfo;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.StatelessErrorRecordingServerCaller;
import delit.piwigoclient.piwigoApi.upload.handlers.NewImageUploadFileChunkResponseHandler;

public class FileChunkUploaderActorThread extends Thread {
    private static final String TAG = "FileChunkUploader";
    private final ChunkUploadListener chunkUploadListener;
    private final UploadJob thisUploadJob;
    private final int maxChunkUploadAutoRetries;
    private BlockingQueue<UploadFileChunk> chunkQueue;
    private boolean isUploadComplete = false;
    private boolean cancelUpload;
    private StatelessErrorRecordingServerCaller serverCaller;
    private final LinkedHashMap<Long,Integer> chunkUploadHistory = new LinkedHashMap<>();
    private boolean isFinished;
    private boolean completedSuccessfully;
    private Context context;
    private AtomicInteger idGen = new AtomicInteger();
    private int chunkUploaderId = idGen.getAndAdd(1);

    public int getChunkUploaderId() {
        return chunkUploaderId;
    }

    public FileChunkUploaderActorThread(UploadJob thisUploadJob, int maxChunkUploadAutoRetries, ChunkUploadListener chunkUploadListener) {
        this.thisUploadJob = thisUploadJob;
        this.maxChunkUploadAutoRetries = maxChunkUploadAutoRetries;
        this.chunkUploadListener = chunkUploadListener;
    }

    public void startConsuming(@NonNull Context context, BlockingQueue<UploadFileChunk> chunkQueue) {
        this.chunkQueue = chunkQueue;
        this.context = context;
        this.serverCaller = new StatelessErrorRecordingServerCaller(context);
        completedSuccessfully = false;
        isFinished = false;
        start();
    }

    @Override
    public void run() {
        try {
            consumeChunks(chunkQueue);
            completedSuccessfully = !cancelUpload;
        } catch(RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Unexpected error");
            Logging.recordException(e);
        } finally {
            isFinished = true;
            synchronized (this) {
                notifyAll();
            }
            Logging.log(Log.DEBUG,TAG, getUploadHistory());
        }
    }

    public boolean isCompletedSuccessfully() {
        return completedSuccessfully;
    }

    public void consumeChunks(BlockingQueue<UploadFileChunk> chunkQueue) {
        setName("FileChunkConsumer: ");
        do {
            UploadFileChunk chunk = null;
            long timeoutAt = System.currentTimeMillis() + 5000; // 5 sec (more than long enough)
            boolean timedOut;
            do {
                try {
                    chunk = chunkQueue.poll(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Logging.log(Log.DEBUG, TAG, "poll of chunk queue interrupted");
                }
                timedOut = System.currentTimeMillis() > timeoutAt;
                if(timedOut) {
                    Logging.log(Log.ERROR, TAG, "Cancelling unfinished upload of chunks as starved of data");
                    cancelUpload = true;
                }
            } while(!cancelUpload && chunk == null);
            if(!cancelUpload) {
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(chunk.getUploadJobItemKey());
                FileUploadDataTxInfo data = fud.getChunksAlreadyUploadedData();
                if(data != null && data.isHasUploadedChunk(chunk.getChunkId())) {
                    throw new IllegalStateException("Attempt made to upload chunk twice " + chunk);
                }
                if(chunk.getChunkId() < 0 || chunk.getChunkDataStream() == null) {
                    Logging.log(Log.ERROR,TAG, "Chunk received for upload that has no data. Ignoring. " + chunk);
                    continue;
                }
                if(!fud.isUploadCancelled()) {
                    setName("FileChunkConsumer: " + chunk.getFileBeingUploaded());
                    uploadStreamChunk(thisUploadJob, chunk, maxChunkUploadAutoRetries);
                } else {
                    stopAsap();
                }
                isUploadComplete = fud.isStatusAllChunksUploaded();
            }
        } while(!isUploadComplete && !cancelUpload);
        if(cancelUpload) {
            chunkUploadListener.onUploadCancelled();
        }
    }


    public boolean isUploadComplete() {
        return isUploadComplete;
    }

    public void stopAsap() {
        cancelUpload = true;
        synchronized (this) {
            interrupt();
        }
    }

    /**
     *
     * @param thisUploadJob
     * @param currentUploadFileChunk
     * @param maxChunkUploadAutoRetries
     * @return upload complete
     */
    private boolean uploadStreamChunk(UploadJob thisUploadJob, UploadFileChunk currentUploadFileChunk, int maxChunkUploadAutoRetries) {

        // Attempt to upload this chunk of the file
        NewImageUploadFileChunkResponseHandler imageChunkUploadHandler = new NewImageUploadFileChunkResponseHandler(currentUploadFileChunk);
        FileUploadDetails fud = thisUploadJob.getFileUploadDetails(currentUploadFileChunk.getUploadJobItemKey());
        serverCaller.invokeWithRetries(thisUploadJob, fud, imageChunkUploadHandler, maxChunkUploadAutoRetries);

        ResourceItem uploadedResource = null;
        boolean uploadComplete = false;
        chunkUploadHistory.put(currentUploadFileChunk.getChunkId(), currentUploadFileChunk.getChunkSizeBytes());
        if (imageChunkUploadHandler.isSuccess()) {
            uploadedResource = ((NewImageUploadFileChunkResponseHandler.PiwigoUploadFileChunkResponse) imageChunkUploadHandler.getResponse()).getUploadedResource();
            uploadComplete = chunkUploadListener.onChunkUploadSuccess(thisUploadJob, currentUploadFileChunk);
            if(fud.getChunksAlreadyUploadedData().isUploadFinished()) {
                fud.setStatusUploaded();
                if(uploadedResource != null) {
                    fud.setServerResource(uploadedResource);
                } else {
                    Logging.log(Log.ERROR,TAG,"Server did not correctly provide a server resource after the final chunk upload");
                    chunkUploadListener.onChunkUploadFailed(thisUploadJob, currentUploadFileChunk, new PiwigoResponseBufferingHandler.CustomErrorResponse(-1, context.getString(R.string.upload_error_final_chunk_uploaded_successfully_bu_no_server_resource_provided)));
                }
            } else if(uploadedResource != null) {
                //should never happen
                fud.setServerResource(uploadedResource);
                Logging.log(Log.ERROR,TAG, "Somehow a resource was returned from the server though not all bytes uploaded! - very likely corrupted upload");
            }
        } else {
            chunkUploadListener.onChunkUploadFailed(thisUploadJob, currentUploadFileChunk, imageChunkUploadHandler.getResponse());
        }


        return uploadComplete;
    }

    private boolean isUsingCommunityPlugin(UploadJob thisUploadJob) {
        return PiwigoSessionDetails.isUseCommunityPlugin(thisUploadJob.getConnectionPrefs());
    }

    private String getUploadHistory() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Integer> longIntegerEntry : chunkUploadHistory.entrySet()) {
            sb.append(longIntegerEntry.getKey());
            sb.append(":");
            sb.append(IOUtils.bytesToNormalizedText(longIntegerEntry.getValue()));
            sb.append("\n");
        }
        return sb.toString();
    }

    public boolean isFinished() {
        return isFinished;
    }

    public interface ChunkUploadListener {

        void onChunkUploadFailed(UploadJob thisUploadJob, UploadFileChunk currentUploadFileChunk, PiwigoResponseBufferingHandler.BaseResponse errorResponse);

        void onUploadCancelled();

        boolean onChunkUploadSuccess(UploadJob thisUploadJob, UploadFileChunk currentUploadFileChunk);
    }
}
