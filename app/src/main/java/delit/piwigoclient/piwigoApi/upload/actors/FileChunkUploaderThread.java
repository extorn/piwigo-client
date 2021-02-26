package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.handlers.NewImageUploadFileChunkResponseHandler;

public class FileChunkUploaderThread extends Thread {
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

    public FileChunkUploaderThread(UploadJob thisUploadJob, int maxChunkUploadAutoRetries, ChunkUploadListener chunkUploadListener) {
        this.thisUploadJob = thisUploadJob;
        this.maxChunkUploadAutoRetries = maxChunkUploadAutoRetries;
        this.chunkUploadListener = chunkUploadListener;
    }

    public void startConsuming(@NonNull Context context, BlockingQueue<UploadFileChunk> chunkQueue) {
        this.chunkQueue = chunkQueue;
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
            long timeoutAt = System.currentTimeMillis() + 5000;
            boolean timedOut;
            do {
                try {
                    chunk = chunkQueue.poll(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Logging.log(Log.DEBUG, TAG, "poll interrupted");
                }
                timedOut = System.currentTimeMillis() > timeoutAt;
                if(timedOut) {
                    Logging.log(Log.ERROR, TAG, "Cancelling unfinished upload of chunks as starved of data");
                    cancelUpload = true;
                }
            } while(!cancelUpload && chunk == null);
            if(!cancelUpload && chunk != null) {
                UploadJob.PartialUploadData data = thisUploadJob.getChunksAlreadyUploadedData(chunk.getUploadJobItemKey());
                if(data != null && data.hasUploadedChunk(chunk.getChunkId())) {
                    throw new IllegalStateException("Attempt made to upload chunk twice " + chunk);
                }
                if(chunk.getChunkId() < 0 || chunk.getChunkDataStream() == null) {
                    Logging.log(Log.ERROR,TAG, "Chunk received for upload that has no data. Ignoring. " + chunk);
                    continue;
                }
                if(thisUploadJob.isFileUploadStillWanted(chunk.getUploadJobItemKey())) {
                    setName("FileChunkConsumer: " + chunk.getFileBeingUploaded());
                    boolean chunkUploadedOk = uploadStreamChunk(thisUploadJob, chunk, maxChunkUploadAutoRetries);

                    if (chunkUploadedOk) {
                        isUploadComplete = chunkUploadListener.onChunkUploadSuccess(thisUploadJob, chunk);
                    }
                } else {
                    stopAsap();
                }
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
        serverCaller.invokeWithRetries(thisUploadJob, currentUploadFileChunk.getUploadJobItemKey(), imageChunkUploadHandler, maxChunkUploadAutoRetries);

        ResourceItem uploadedResource = null;
        boolean uploadComplete = false;
        chunkUploadHistory.put(currentUploadFileChunk.getChunkId(), currentUploadFileChunk.getChunkSizeBytes());
        if (imageChunkUploadHandler.isSuccess()) {
            uploadedResource = ((NewImageUploadFileChunkResponseHandler.PiwigoUploadFileChunkResponse) imageChunkUploadHandler.getResponse()).getUploadedResource();
            uploadComplete = chunkUploadListener.onChunkUploadSuccess(thisUploadJob, currentUploadFileChunk);
        } else {
            chunkUploadListener.onChunkUploadFailed(thisUploadJob, currentUploadFileChunk, imageChunkUploadHandler.getResponse());
        }

        if(imageChunkUploadHandler.isSuccess() && uploadedResource != null) {
            thisUploadJob.addFileUploaded(currentUploadFileChunk.getUploadJobItemKey(), uploadedResource);
        }
        return uploadComplete;
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

    public void waitForever() throws InterruptedException {
        synchronized(this) {
            wait();
        }
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
