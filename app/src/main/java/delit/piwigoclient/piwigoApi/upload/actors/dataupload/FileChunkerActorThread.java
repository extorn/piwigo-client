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
            produceChunks(context, chunkQueue);
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

    public void waitForever() throws InterruptedException {
        synchronized(this) {
            wait();
        }
    }

    /**
     * Will generate chunks and add them to the queue. This code is blocking. It waits if no space on the queue.
     * @param context an active context
     * @param chunkQueue queue to add items to
     * @throws IOException if the file could not be read
     * @throws InterruptedException if the operation to add to queue was interrupted.
     */
    public void produceChunks(@NonNull Context context, BlockingQueue<UploadFileChunk> chunkQueue) throws IOException {
        setName("FileChunkProducer: " + fud.getFileToBeUploaded());
        long totalBytesInFile = IOUtils.getFilesize(context, fud.getFileToBeUploaded());
        long fileBytesUploaded = 0;
        long chunkId = 0;
        int bytesOfDataInChunk;
        long totalChunkCount = 0;
        long maxChunkSize = defaultMaxChunkSize;

        if (totalBytesInFile < 0) {
            throw new IOException("Unable to ascertain file size - essential to upload");
        }

        String newChecksum = fud.getChecksumOfFileToUpload();
        if (filenameIsUniqueKey) {
            newChecksum = IOUtils.getFilename(context, fud.getFileUri());
        }
        // pre-set the upload progress through file to where we got to last time.
        FileUploadDataTxInfo skipChunksData = fud.getChunksAlreadyUploadedData();
        if (skipChunksData != null) {
//            thisUploadJob.deleteChunksAlreadyUploadedData(uploadItemKey);
            if (ObjectUtils.areEqual(skipChunksData.getFileChecksum(), newChecksum)) {
                // If the checksums still match (file to upload hasn't been altered)
                fileBytesUploaded = skipChunksData.getBytesUploaded();
                uploadName = skipChunksData.getUploadName();
                // ensure we keep uploading chunks the same size.
                maxChunkSize = skipChunksData.getMaxUploadChunkSizeBytes();
                totalChunkCount = skipChunksData.getChunksToUpload();
            }
        } else {
            // chunk count is chunks in part of file left plus uploaded chunk count.
            totalChunkCount = (long) Math.ceil(((double) totalBytesInFile) / maxChunkSize);
        }


        String fileMimeType = null; // MimeTypeMap.getSingleton().getMimeTypeFromExtension(IOUtils.getFileExt(fileForUpload.getName()));
        //FIXME get the fileMimeType from somewhere - why?!

        long currentFilePos = 0;
        try (InputStream is = context.getContentResolver().openInputStream(fud.getFileToBeUploaded())) {
            try (BufferedInputStream bis = new BufferedInputStream(is)) {
                do {
                    if(skipChunksData == null) {
                        skipChunksData = fud.getChunksAlreadyUploadedData();
                    }
                    if(skipChunksData != null) {
                        // this is okay (even though some are waiting for upload) because this loop always tries the next sequential chunk id.
                        chunkId = skipChunksData.getFirstMissingChunk(chunkId);
                    }
                    if(chunkId >= 0 && chunkId < totalChunkCount) {
                        //we're not yet finished

                        long skipBytes = (chunkId * maxChunkSize) - currentFilePos;

                        if (is == null) {
                            throw new IOException("Unable to open input stream for file " + fud.getFileToBeUploaded());
                        }

                        if (skipBytes > 0) {
                            if (skipBytes != bis.skip(skipBytes)) {
                                throw new IOException("Unable to skip through previously uploaded bytes in file. File has likely been altered");
                            }
                            currentFilePos += skipBytes;
                        }

                        bytesOfDataInChunk = -1;
                        if (!thisUploadJob.isCancelUploadAsap() && !fud.isUploadCancelled()) {

                            int maxChunkSizeInt = (int) Math.min(Integer.MAX_VALUE, maxChunkSize);
                            if (maxChunkSize > Integer.MAX_VALUE) {
                                Logging.log(Log.WARN, TAG, "Reducing chunk size to Integer.MAX_VALUE so can use a max length byte[]");
                            }
                            byte[] uploadChunkBuffer = new byte[maxChunkSizeInt];
                            bytesOfDataInChunk = IOUtils.fillBufferFromStream(bis, uploadChunkBuffer);

                            if (bytesOfDataInChunk > 0) {
                                currentFilePos += bytesOfDataInChunk;

                                UploadFileChunk chunk;
                                try {
                                    chunk = reusableChunks.remove();
                                    chunk.withData(uploadChunkBuffer, bytesOfDataInChunk, chunkId);
                                } catch (NoSuchElementException e) {
                                    chunk = new UploadFileChunk(thisUploadJob.getJobId(), fud.getFileUri(), fud.getFileToBeUploaded(), totalBytesInFile, uploadName, uploadToAlbumId(), uploadChunkBuffer,
                                            bytesOfDataInChunk, chunkId, totalChunkCount, fileMimeType, maxChunkSizeInt);
                                }
                                boolean queued = false;
                                do {
                                    try {
                                        queueChunk(chunk);
                                        queued = true;
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                } while (!cancelChunking && !queued);
                            }
                        }
                        if (fud.isUploadCancelled()) {
                            flushChunksFromQueueMatching(fud.getFileUri());
                        }
                        if (bytesOfDataInChunk >= 0) {
                            // move to the next chunk
                            chunkId++;
                        } else {
                            chunkDataErrorCount++;
                            Logging.log(Log.ERROR, TAG, "Chunk %1$d was unexpectedly empty %2$d. Retrying", chunkId, chunkDataErrorCount);
                            if (chunkDataErrorCount >= 3) {
                                stopAsap();
                            }

                        }
                    }
                } while (/*bytesOfDataInChunk >= 0*/ (chunkId + 1 < totalChunkCount) && !cancelChunking);
                if(chunkId + 1 == totalChunkCount) {
                    Logging.log(Log.DEBUG, TAG, "All chunks have now been produced. Producer ending");
                }
            }
        }
        synchronized (thisUploadJob) {
            thisUploadJob.notifyAll();
        }
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

    private void queueChunk(UploadFileChunk chunk) throws InterruptedException {
        chunkQueue.put(chunk);
        chunkProductionHistory.put(chunk.getChunkId(), chunk.getChunkSizeBytes());
    }

    private void flushChunksFromQueueMatching(Uri uploadItemKey) {
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
}