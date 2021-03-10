package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageFindExistingImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesListOrphansResponseHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileFilesExistAlreadyResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadProgressUpdateResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class ExistingFilesCheckActor extends UploadActor {
    private static final String TAG = "ExistingFilesCheckActor";
    private final boolean filenamesUnique;

    public ExistingFilesCheckActor(Context context, UploadJob uploadJob, ActorListener listener, boolean filenamesUnique) {
        super(context, uploadJob, listener);
        this.filenamesUnique = filenamesUnique;
    }
    
    public boolean run() {
        UploadJob thisUploadJob = getUploadJob();

        Map<Uri,String> uniqueIdsList = getUniqueServerUnderstoodIdsForFilesWithoutServerResourcesRetrieved();

        // remove any files that already exist on the server from the upload.
        ImageFindExistingImagesResponseHandler imageFindExistingHandler = new ImageFindExistingImagesResponseHandler(uniqueIdsList.values(), filenamesUnique);
        getServerCaller().invokeWithRetries(thisUploadJob, imageFindExistingHandler, 2);

        if (imageFindExistingHandler.isSuccess()) {
            if (!retrieveAndProcessAndExistingOrphanedFiles(thisUploadJob, imageFindExistingHandler)) {
                return false;
            }

        }
        if (!imageFindExistingHandler.isSuccess()) {
            // notify the listener of the final error we received from the server
            getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
            return false;
        }
        return true;
    }

    private Map<Uri, String> getUniqueServerUnderstoodIdsForFilesWithoutServerResourcesRetrieved() {
        return getUploadJob().getFileChecksumsForServerCheck(getContext(), filenamesUnique);
    }
    private boolean retrieveAndProcessAndExistingOrphanedFiles(UploadJob thisUploadJob, ImageFindExistingImagesResponseHandler imageFindExistingHandler) {
        ArrayList<Long> orphans;
        if (PiwigoSessionDetails.isAdminUser(thisUploadJob.getConnectionPrefs())) {
            orphans = getOrphanImagesOnServer(thisUploadJob);
            if (orphans == null) {
                // there has been an error which is reported within the getOrphanImagesOnServer method.
                return true;//FIXME this cant be right.
            }
        } else {
            orphans = new ArrayList<>();
        }

        if (imageFindExistingHandler.getResponse() instanceof ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse) {
            processFindPreexistingImagesResponse(thisUploadJob, (ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse) imageFindExistingHandler.getResponse(), orphans);
        } else {
            // this is bizarre - a failure was recorded as a success!
            // notify the listener of the final error we received from the server
            getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), imageFindExistingHandler.getResponse()));
        }
        return true;
    }

    /**
     *
     * @param thisUploadJob upload job
     * @param response piwigo response listing existing images
     * @param orphans list of id for all orphans on the server
     */
    private void processFindPreexistingImagesResponse(@NonNull UploadJob thisUploadJob, @NonNull ImageFindExistingImagesResponseHandler.PiwigoFindExistingImagesResponse response, @NonNull List<Long> orphans) {
        HashMap<String, Long> preexistingItemsMap = response.getExistingImages();
        ArrayList<Uri> filesExistingOnServerAlready = new ArrayList<>();
        HashMap<Uri, Long> resourcesToRetrieve = new HashMap<>();

        // is name or md5sum used for uniqueness on this server?
        Map<Uri,String> uniqueIdsSet = getUniqueServerUnderstoodIdsForFilesWithoutServerResourcesRetrieved();

        for (Map.Entry<Uri, String> fileCheckSumEntry : uniqueIdsSet.entrySet()) {
            String uploadedFileUid = fileCheckSumEntry.getValue(); // usually MD5Sum (less chance of collision).

            if (preexistingItemsMap.containsKey(uploadedFileUid)) {
                Uri fileFoundOnServer = fileCheckSumEntry.getKey();
                Long serverResourceId = preexistingItemsMap.get(uploadedFileUid);

                // theoretically we needn't retrieve the item again if we already have it (not null), but it may have been changed by other means...
//                ResourceItem resourceItem = thisUploadJob.getUploadedFileResource(fileFoundOnServer);

                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(fileFoundOnServer);
                if(!fud.isUploadCancelled()) {
                    if (fud.isAlreadyUploadedAndConfigured()) {
                        resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                    }
                    if (fud.needsVerification()) {
                        fud.setStatusVerified();
                        getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fud.getFileUri(), fud.getOverallUploadProgress()));
                    }
                    if (fud.isReadyForConfiguration()) {
                        resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                    } else {
                        // mark this file as needing configuration (probably uploaded by ?someone else? or a different upload mechanism anyway)
                        fud.setStatusVerified();
                        filesExistingOnServerAlready.add(fileFoundOnServer);
                        resourcesToRetrieve.put(fileFoundOnServer, serverResourceId);
                        getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), fud.getFileUri(), fud.getOverallUploadProgress()));
                    }
                }
            } else {
                FileUploadDetails fud = thisUploadJob.getFileUploadDetails(fileCheckSumEntry.getKey());
                //This is a logic check of the upload job. the reset here should NEVER be invoked in the wild.
                if(fud.isStatusUnverifiedDataOnServer() && fud.getChunksAlreadyUploadedData() == null) {
                    // this isn't repairable. force clean of the server and the re-upload
                    fud.setStatusCorrupt();
                    Logging.log(Log.WARN, TAG, "Mark uploaded data corrupt. Status would infer it should be uploading or uploaded, but no chunk data available. %1$s:%2$s", fud.getFileUri(), fud.getChecksumOfFileToUpload());
                }
            }
        }

        if (filesExistingOnServerAlready.size() > 0) {
//            thisUploadJob.getFilesForUpload().removeAll(filesExistingOnServerAlready);
            getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadFileFilesExistAlreadyResponse(getNextMessageId(), filesExistingOnServerAlready));
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());


        for (Map.Entry<Uri, Long> entry : resourcesToRetrieve.entrySet()) {
            FileUploadDetails fud = thisUploadJob.getFileUploadDetails(entry.getKey());
            long imageId = entry.getValue();
            if (orphans.contains(imageId)) {
                ResourceItem item = new ResourceItem(imageId, null, null, null, null, null);
                item.setFileChecksum(uniqueIdsSet.get(entry.getKey()));
                item.setLinkedAlbums(new HashSet<>(1));
                fud.setServerResource(item);
                fud.setStatusVerified();
            } else {
                ImageGetInfoResponseHandler<ResourceItem> getImageInfoHandler = new ImageGetInfoResponseHandler<>(new ResourceItem(imageId, null, null, null, null, null));
                getServerCaller().invokeWithRetries(thisUploadJob, getImageInfoHandler, 2);
                if (getImageInfoHandler.isSuccess()) {
                    BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> rsp = (BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?>) getImageInfoHandler.getResponse();
                    fud.setServerResource(rsp.getResource());
                    fud.setStatusVerified();
                } else if (getImageInfoHandler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                    if(sessionDetails.isUseCommunityPlugin()) {
                        PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse rsp = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) getImageInfoHandler.getResponse();
                        if (rsp.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                            fud.setPendingCommunityApproval();
                            getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoUploadProgressUpdateResponse(getNextMessageId(), entry.getKey(), fud.getOverallUploadProgress()));
                        } else {
                            StatelessErrorRecordingServerCaller.logServerCallError(getContext(), getImageInfoHandler, thisUploadJob.getConnectionPrefs());
                        }
                    } else if(((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) getImageInfoHandler.getResponse()).getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                        Logging.log(Log.WARN, TAG,"Repair made to get around issue with missing server resource for uploaded file");
                        ResourceItem blankResource = new ResourceItem(imageId, fud.getFilename(getContext()), null, null, null, null);
                        fud.setServerResource(blankResource);
                        fud.setStatusVerified();
                        getListener().notifyListenersOfCustomErrorUploadingFile(getUploadJob(), fud.getFileUri(), false, getContext().getString(R.string.upload_error_server_resource_not_retrieved_repaired));
                    }
                }
            }
        }
    }

    private ArrayList<Long> getOrphanImagesOnServer(UploadJob thisUploadJob) {
        //FIXME support for more than 100 orphans.
        ImagesListOrphansResponseHandler orphanListHandler = new ImagesListOrphansResponseHandler(0, 100);
        ArrayList<Long> orphans;

        if (orphanListHandler.isMethodAvailable(getContext(), thisUploadJob.getConnectionPrefs())) {
            orphans = null;
            getServerCaller().invokeWithRetries(thisUploadJob, orphanListHandler, 2);
            if (orphanListHandler.isSuccess()) {
                ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse resp = (ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse) orphanListHandler.getResponse();
                if (resp.getTotalCount() > resp.getResources().size()) {
                    getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getContext().getString(R.string.upload_error_too_many_orphaned_files_exist_on_server))));
                    return null;
                } else {
                    orphans = resp.getResources();
                }
            } else {
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), new PiwigoResponseBufferingHandler.CustomErrorResponse(thisUploadJob.getJobId(), getContext().getString(R.string.upload_error_orphaned_file_retrieval_failed))));
            }
        } else {
            orphans = new ArrayList<>(0);
            thisUploadJob.recordError(getContext().getString(R.string.upload_error_orphaned_file_retrieval_unavailable));
        }

        return orphans;
    }
}
