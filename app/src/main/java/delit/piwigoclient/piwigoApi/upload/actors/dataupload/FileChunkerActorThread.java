package delit.piwigoclient.piwigoApi.upload.actors.dataupload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.libs.util.ObjectUtils;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.piwigoApi.upload.FileUploadDataTxInfo;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class FileChunkerActorThread extends Thread {

    private static final String TAG = "UploadChunkProducer";
    private final UploadJob thisUploadJob;
    private final FileUploadDetails fud;
    private String uploadName;
    private final boolean filenameIsUniqueKey;
    private final int defaultMaxChunkSize;
    private final LinkedHashMap<Long,Integer> chunkProductionHistory = new LinkedHashMap<>();
    ConcurrentLinkedQueue<UploadFileChunk> reusableChunks = new ConcurrentLinkedQueue<>();
    private Context context;
    private BlockingQueue<UploadFileChunk> chunkQueue;
    private boolean cancelChunking;
    private boolean isFinished;
    private int chunkDataErrorCount;
    private boolean completedSuccessfully;
    private int consumerId;

    //filenameIsUniqueKey = isUseFilenamesOverMd5ChecksumForUniqueness(thisUploadJob)
    public FileChunkerActorThread(UploadJob thisUploadJob, FileUploadDetails fud, String uploadName, boolean filenameIsUniqueKey, int defaultMaxChunkSize) {
        this.thisUploadJob = thisUploadJob;
        this.fud = fud;
        this.uploadName = uploadName;
        this.filenameIsUniqueKey = filenameIsUniqueKey;
        this.defaultMaxChunkSize = defaultMaxChunkSize;
    }

    public void returnChunkToPool(UploadFileChunk chunk) {
        chunk.withData(null, 0, -1);
        reusableChunks.add(chunk);
    }

    private long uploadToAlbumId() {
        long uploadToAlbumId = thisUploadJob.getTemporaryUploadAlbumId();
        if (uploadToAlbumId < 0) {
            uploadToAlbumId = thisUploadJob.getUploadToCategory().getId();
        }
        return uploadToAlbumId;
    }

    public void startProducing(@NonNull Context context, BlockingQueue<UploadFileChunk> chunkQueue) {
        this.context = context;
        this.chunkQueue = chunkQueue;
        completedSuccessfully = false;
        isFinished = false;
        start();
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public void run() {
        try {
            produceChunks(context);
            completedSuccessfully = !cancelChunking;
        } catch (IOException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, TAG, "Unable to produce chunks. Error reading file");
        } catch(RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Unexpected error");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
        } finally {
            isFinished = true;
            synchronized (this) {
                notifyAll();
            }
            Logging.log(Log.DEBUG,TAG, getProductionHistory());
        }
    }

    public boolean isCompletedSuccessfully() {
        return completedSuccessfully;
    }

    public boolean isFinished() {
        return isFinished;
    }

    /**
     * Will generate chunks and add them to the queue. This code is blocking. It waits if no space on the queue.
     * @param context an active context
     * @throws IOException if the file could not be read
     */
    public void produceChunks(@NonNull Context context) throws IOException {
        setName("FileChunkProducer: " + fud.getFileToBeUploaded());
        long totalBytesInFile = IOUtils.getFilesize(context, fud.getFileToBeUploaded());
        long chunkId = 0;
        long totalChunkCount = 0;
        long maxChunkSize = -1;

        if (totalBytesInFile < 0) {
            throw new IOException("Unable to ascertain file size - essential to upload");
        }

        String newChecksum = fud.getChecksumOfFileToUpload();
        if (filenameIsUniqueKey) {
            newChecksum = fud.getFilename(context);
        }
        // pre-set the upload progress through file to where we got to last time.
        FileUploadDataTxInfo skipChunksData = fud.getChunksAlreadyUploadedData();
        if (skipChunksData != null) {
//            thisUploadJob.deleteChunksAlreadyUploadedData(uploadItemKey);
            if (ObjectUtils.areEqual(skipChunksData.getFileChecksum(), newChecksum)) {
                // If the checksums still match (file to upload hasn't been altered)
//                fileBytesUploaded = skipChunksData.getBytesUploaded();
                uploadName = skipChunksData.getUploadName();
                // ensure we keep uploading chunks the same size.
                maxChunkSize = skipChunksData.getMaxUploadChunkSizeBytes();
                totalChunkCount = skipChunksData.getTotalChunksToUpload();
            } else {
                Logging.log(Log.ERROR,TAG,"Checksum of file has changed since the upload of chunks was started. Upload of file aborted for now");
                fud.setStatusCorrupt();
                stopAsap();
            }
        }
        if(cancelChunking) {
            return;
        }

        if(maxChunkSize < 0) {
            // chunk count is chunks in part of file left plus uploaded chunk count.
            maxChunkSize = defaultMaxChunkSize;
            totalChunkCount = (long) Math.ceil(((double) totalBytesInFile) / maxChunkSize);
        }

        String fileMimeType = null; // MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileForUpload.getName()));
        //FIXME get the fileMimeType from somewhere - maybe?

        long currentFilePos = 0;
        try (InputStream is = context.getContentResolver().openInputStream(fud.getFileToBeUploaded())) {
            if (is == null) {
                throw new IOException("Unable to open input stream for file " + fud.getFileToBeUploaded());
            }
            try (BufferedInputStream bis = new BufferedInputStream(is)) {
                do {
                    if(skipChunksData == null) {
                        skipChunksData = fud.getChunksAlreadyUploadedData();
                    }
                    if(skipChunksData != null) {
                        // this is okay (even though some are waiting for upload) because this loop always tries the next sequential chunk id.
                        chunkId = skipChunksData.getFirstMissingChunk(chunkId);
                    }
                    if(chunkId >= 0 && chunkId < totalChunkCount) { // chunks 0,1,2,3,4 for 5 chunk  file.

                        //we're not yet finished

                        long skipBytes = (chunkId * maxChunkSize) - currentFilePos;
                        if (skipBytes > 0) {
                            if (skipBytes != bis.skip(skipBytes)) {
                                throw new IOException("Unable to skip through previously uploaded bytes in file. File has likely been altered");
                            }
                            currentFilePos += skipBytes;
                        }

                        if(currentFilePos != totalBytesInFile) {
                            if (!thisUploadJob.isCancelUploadAsap() && !fud.isUploadCancelled()) {
                                UploadFileChunk chunk = buildChunk(chunkId, bis, fileMimeType, totalBytesInFile, maxChunkSize, totalChunkCount);
                                if(chunk != null) {
                                    queueChunk(chunk);
                                    // move to the next chunk
                                    currentFilePos += chunk.getChunkSizeBytes();
                                    chunkId++;
                                } else {
                                    chunkDataErrorCount++;
                                    Logging.log(Log.ERROR, TAG, "Chunk %1$d was unexpectedly empty %2$d. Retrying", chunkId, chunkDataErrorCount);
                                    if (chunkDataErrorCount >= 3) {
                                        stopAsap();
                                    }
                                }
                            } else {
                                Logging.log(Log.ERROR, TAG, "Chunk build stopped. Either file or job upload no longer wanted.");
                            }
                        }

                        if (fud.isUploadCancelled() || thisUploadJob.isCancelUploadAsap()) {
                            Logging.log(Log.DEBUG, TAG, "Upload of file or job cancelled.");
                            flushChunksFromQueueMatching(fud.getFileUri());
                            stopAsap(); //TODO is this call needed or a duplicate?
                        }
                    }
                } while ((chunkId >= 0) && (chunkId < totalChunkCount) && !cancelChunking);
                if(chunkId + 1 == totalChunkCount) {
                    Logging.log(Log.DEBUG, TAG, "All chunks have now been produced. Producer ending");
                } else {
                    Logging.log(Log.WARN, TAG, "Producer ending before fully chunking file");
                }
            }
        }
        thisUploadJob.wakeAnyWaitingThreads();
    }

    private UploadFileChunk buildChunk(long chunkId, BufferedInputStream bis, String fileMimeType, long totalBytesInFile, long maxChunkSize, long totalChunkCount) throws IOException {
        int bytesOfDataInChunk;
        int maxChunkSizeInt = (int) Math.min(Integer.MAX_VALUE, maxChunkSize);
        if (maxChunkSize > Integer.MAX_VALUE) {
            Logging.log(Log.WARN, TAG, "Reducing chunk size to Integer.MAX_VALUE so can use a max length byte[]");
        }
        byte[] uploadChunkBuffer = new byte[maxChunkSizeInt];
        bytesOfDataInChunk = IOUtils.fillBufferFromStream(bis, uploadChunkBuffer);

        UploadFileChunk chunk = null;
        if (bytesOfDataInChunk > 0) {
            try {
                chunk = reusableChunks.remove();
                chunk.withData(uploadChunkBuffer, bytesOfDataInChunk, chunkId);
            } catch (NoSuchElementException e) {
                chunk = new UploadFileChunk(thisUploadJob.getJobId(), fud.getFileUri(), fud.getFileToBeUploaded(), totalBytesInFile, uploadName, uploadToAlbumId(), uploadChunkBuffer,
                        bytesOfDataInChunk, chunkId, totalChunkCount, fileMimeType, maxChunkSizeInt);
            }
        } else {
            Logging.log(Log.WARN, TAG, "Unable to create chunk - no bytes available");
        }
        return chunk;
    }

    private String getProductionHistory() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Integer> longIntegerEntry : chunkProductionHistory.entrySet()) {
            sb.append(longIntegerEntry.getKey());
            sb.append(":");
            sb.append(IOUtils.bytesToNormalizedText(longIntegerEntry.getValue()));
            sb.append("\n");
        }
        return sb.toString();
    }

    private void queueChunk(UploadFileChunk chunk) {
        boolean queued = false;
        do {
            try {
                chunkQueue.put(chunk);
                chunkProductionHistory.put(chunk.getChunkId(), chunk.getChunkSizeBytes());
                Logging.log(Log.DEBUG,TAG, "Chunk queued %1$d [%2$s/%3$d]", consumerId, chunk.getChunkId(), chunk.getChunkCount());
                queued = true;
            } catch (InterruptedException e) {
                Logging.log(Log.DEBUG,TAG, "ChunkProducer is waiting for room on the chunk queue %1$d", consumerId);
            }
        } while (!cancelChunking && !queued);
    }

    private void flushChunksFromQueueMatching(Uri uploadItemKey) {
        Logging.log(Log.DEBUG, TAG, "Flushing chunks queue");
        for (Iterator<UploadFileChunk> iterator = chunkQueue.iterator(); iterator.hasNext(); ) {
            UploadFileChunk uploadFileChunk = iterator.next();
            if(uploadFileChunk.getUploadJobItemKey().equals(uploadItemKey)) {
                iterator.remove();
            }
        }
    }

    public void stopAsap() {
        cancelChunking = true;
        chunkQueue.clear();
        synchronized (this) {
            interrupt();
        }
    }

    public void setConsumerId(int chunkUploaderId) {
        this.consumerId = chunkUploaderId;
    }
}