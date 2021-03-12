package delit.piwigoclient.piwigoApi.upload.actors.dataupload;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.iptc.IptcDirectory;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.UploadFileChunk;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ActorListener;
import delit.piwigoclient.piwigoApi.upload.actors.UploadActor;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoStartUploadFileResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class FileDataUploaderActor extends UploadActor {
    private static final String TAG = "FileDataUploadActor";
    private static final SecureRandom random = new SecureRandom();
    private final boolean filenamesAreUnique;

    public FileDataUploaderActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener, boolean filenamesAreUnique) {
        super(context, uploadJob, listener);
        this.filenamesAreUnique = filenamesAreUnique;
    }

    public void doUploadFileData(UploadJob thisUploadJob, FileUploadDetails fud, int maxChunkUploadAutoRetries) {

        Uri uploadJobKey = fud.getFileUri();

        long jobId = thisUploadJob.getJobId();

        if (!thisUploadJob.isCancelUploadAsap() && !fud.isUploadCancelled()) {

            getListener().postNewResponse(jobId, new PiwigoStartUploadFileResponse(getNextMessageId(), uploadJobKey));

            String ext = IOUtils.getFileExt(getContext(), fud.getFileToBeUploaded());

            if (ext.length() == 0) {
                fud.setProcessingFailed();
                // notify the listener of the final error we received from the server
                String errorMsg = getContext().getString(R.string.error_upload_file_ext_missing_pattern, fud.getFileToBeUploaded().getPath());
                getListener().notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, true, errorMsg);
            }

            if (!fud.isProcessingFailed() && !fud.isUploadCancelled()) {
                String tempUploadName = "PiwigoClient_Upload_" + random.nextLong() + '.' + ext;

                uploadFileInChunks(thisUploadJob, uploadJobKey, tempUploadName, maxChunkUploadAutoRetries);

                ResourceItem uploadedResource = fud.getServerResource();
                if (uploadedResource != null) {
                    // this should ALWAYS be the case (unless the upload of this file was cancelled)!
                    fillBlankResourceItem(thisUploadJob, uploadedResource, uploadJobKey, fud.getFileToBeUploaded());
                } else if(!fud.isUploadCancelled()) {
                    // notify the listener of the final error we received from the server (if it was cancelled they won't care)
                    String errorMsg = getContext().getString(R.string.error_upload_file_chunk_upload_failed_after_retries, maxChunkUploadAutoRetries);
                    getListener().notifyListenersOfCustomErrorUploadingFile(thisUploadJob, uploadJobKey, false, errorMsg);
                }
            }
        }
    }

    private void uploadFileInChunks(UploadJob thisUploadJob, Uri uploadItemKey, String uploadName, int maxChunkUploadAutoRetries) {

        // Have at most one chunk queued ready for upload
        BlockingQueue<UploadFileChunk> chunkQueue = new ArrayBlockingQueue<>(10);
        FileUploadDetails fud = thisUploadJob.getFileUploadDetails(uploadItemKey);
        FileChunkerActorThread chunkProducer = new FileChunkerActorThread(thisUploadJob, fud, uploadName, filenamesAreUnique, getMaxChunkUploadSizeBytes());
        FileChunkUploaderActorThread chunkConsumer = new FileChunkUploaderActorThread(thisUploadJob, maxChunkUploadAutoRetries, new MyChunkUploadListener(getContext(), chunkProducer, getListener(), filenamesAreUnique));
        chunkProducer.setConsumerId(chunkConsumer.getChunkUploaderId());

        // start the upload
        FileUploadCancelMonitorThread watchThread = new FileUploadCancelMonitorThread(thisUploadJob, uploadItemKey) {
            @Override
            public void onFileUploadCancelled(Uri f) {
                Logging.log(Log.DEBUG, TAG, "FileUploadCancel Thread - Cancelling file upload");
                getListener().doHandleUserCancelledUpload(thisUploadJob, f);
                chunkProducer.stopAsap();
                chunkConsumer.stopAsap();
            }
        };

        watchThread.setDaemon(true);
        watchThread.start();
        chunkProducer.startProducing(getContext(), chunkQueue);
        chunkConsumer.startConsuming(getContext(), chunkQueue);

        // wait until complete or the
        do {
            try {
                // 250millis is a reasonable delay to discover the producer died for some unknown reason
                chunkConsumer.waitOnBriefly(250); // wait for 250millis then wait again.
            } catch (InterruptedException e) {
                if (thisUploadJob.isCancelUploadAsap()) {
                    chunkProducer.stopAsap();
                    chunkConsumer.stopAsap();
                    watchThread.markDone();
                }
            }
            if(chunkConsumer.isUploadComplete()) {
                watchThread.markDone();
            }
            if(chunkProducer.isFinished()){
                if(!chunkProducer.isCompletedSuccessfully()) {
                    chunkConsumer.stopAsap();
                    watchThread.markDone();
                }
            }
        } while (!thisUploadJob.isCancelUploadAsap() && !chunkConsumer.isUploadComplete() && !chunkConsumer.isFinished());
        if(!chunkConsumer.isUploadComplete()) {
            fud.setProcessingFailed();
        }
        if(!chunkProducer.isFinished()) {
            chunkProducer.stopAsap();
        }
        if(!chunkConsumer.isFinished()) {
            chunkConsumer.stopAsap();
        }
        if(!watchThread.isNoLongerNeeded()) {
            watchThread.markDone();
        }
    }

    private void fillBlankResourceItem(UploadJob thisUploadJob, ResourceItem uploadedResource, Uri fileForUploadItemKey, Uri fileForUploadUri) {
        uploadedResource.setName(IOUtils.getFilename(getContext(), fileForUploadUri));
        CategoryItemStub uploadToAlbum = thisUploadJob.getUploadToCategory();
        uploadedResource.setParentageChain(uploadToAlbum.getParentageChain(), uploadToAlbum.getId());
        uploadedResource.setPrivacyLevel(thisUploadJob.getPrivacyLevelWanted());
        uploadedResource.setFileChecksum(thisUploadJob.getFileUploadDetails(fileForUploadItemKey).getChecksumOfFileToUpload());

        long lastModifiedTime = IOUtils.getLastModifiedTime(getContext(), fileForUploadItemKey);
        if (lastModifiedTime > 0) {
            Date lastModDate = new Date(lastModifiedTime);
            uploadedResource.setCreationDate(lastModDate);
        }
        setUploadedImageDetailsFromExifData(fileForUploadUri, uploadedResource);
    }

    private void setUploadedImageDetailsFromExifData(Uri fileForUpload, ResourceItem uploadedResource) {

        try (InputStream is = getContext().getContentResolver().openInputStream(fileForUpload)) {
            if (is == null) {
                throw new IOException("Error ");
            }

            try (BufferedInputStream bis = new BufferedInputStream(is)) {

                Metadata metadata = ImageMetadataReader.readMetadata(bis);
                Directory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                setCreationDateAndFilenameFromExifMetadata(uploadedResource, directory);
                directory = metadata.getFirstDirectoryOfType(IptcDirectory.class);
                setCreationDateAndFilenameFromIptcMetadata(uploadedResource, directory);
            } catch (ImageProcessingException e) {
                Logging.recordException(e);
                Logging.log(Log.ERROR, TAG, "Error parsing EXIF data : sinking");
            }
        } catch (FileNotFoundException e) {
            Logging.log(Log.WARN, TAG, "File Not found - Unable to parse EXIF data : sinking");
        } catch (IOException e) {
            Logging.recordException(e);
            // ignore for now
            Logging.log(Log.ERROR, TAG, "Error parsing EXIF data : sinking");
        }
    }


    private boolean setCreationDateAndFilenameFromExifMetadata(@NonNull ResourceItem uploadedResource, @Nullable Directory directory) {
        if (directory == null) {
            return false;
        }
        Date creationDate = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
        if (creationDate != null) {
            uploadedResource.setCreationDate(creationDate);
        }
        String imageDescription = directory.getString(ExifSubIFDDirectory.TAG_IMAGE_DESCRIPTION);
        if (imageDescription != null) {
            uploadedResource.setName(imageDescription);
        }
        return true;
    }


    private boolean setCreationDateAndFilenameFromIptcMetadata(@NonNull ResourceItem uploadedResource, @Nullable Directory directory) {
        if (directory == null) {
            return false;
        }
        Date creationDate = directory.getDate(IptcDirectory.TAG_DATE_CREATED);
        if (creationDate != null) {
            uploadedResource.setCreationDate(creationDate);
        }
        String imageDescription = directory.getString(IptcDirectory.TAG_CAPTION);
        if (imageDescription != null) {
            uploadedResource.setName(imageDescription);
        }
        return true;
    }

    private int getMaxChunkUploadSizeBytes() {
        int wantedUploadSizeInKbB = UploadPreferences.getMaxUploadChunkSizeKb(getContext(), getPrefs());
        return 1024 * wantedUploadSizeInKbB; // 512Kb chunk size
    }
}
