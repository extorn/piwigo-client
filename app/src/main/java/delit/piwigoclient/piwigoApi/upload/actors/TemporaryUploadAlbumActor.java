package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.HashSet;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.handlers.UploadAlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoCleanupPostUploadFailedResponse;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;
import delit.piwigoclient.ui.events.AlbumDeletedEvent;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class TemporaryUploadAlbumActor extends UploadActor {
    public TemporaryUploadAlbumActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public boolean createTemporaryUploadAlbum(UploadJob thisUploadJob) {

        long uploadAlbumId = thisUploadJob.getTemporaryUploadAlbumId();

        if (uploadAlbumId < 0) {
            // create temporary hidden album
            UploadAlbumCreateResponseHandler albumGenHandler = new UploadAlbumCreateResponseHandler(thisUploadJob.getUploadToCategory().getName(), thisUploadJob.getUploadToCategory().getId());
            getServerCaller().invokeWithRetries(thisUploadJob, albumGenHandler, 2);
            if (albumGenHandler.isSuccess()) {
                PiwigoGalleryDetails albumDetails = ((AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) albumGenHandler.getResponse()).getAlbumDetails();
                PiwigoSessionDetails currentUserDetails = PiwigoSessionDetails.getInstance(thisUploadJob.getConnectionPrefs());
                CategoryItem catItem = new CategoryItem(albumDetails.asCategoryItemStub());
                AlbumGetPermissionsResponseHandler permissionsResponseHandler = new AlbumGetPermissionsResponseHandler(catItem);
                getServerCaller().invokeWithRetries(thisUploadJob, permissionsResponseHandler, 2);
                if(permissionsResponseHandler.isSuccess()) {
                    HashSet<Long> removeGroups = CollectionUtils.getSetFromArray(catItem.getGroups());
                    HashSet<Long> removeUsers = CollectionUtils.getSetFromArray(catItem.getUsers());
                    removeUsers.retainAll(Collections.singleton(currentUserDetails.getUserId()));
                    AlbumRemovePermissionsResponseHandler removePermissionsResponseHandler = new AlbumRemovePermissionsResponseHandler(albumDetails, removeGroups, removeUsers);
                    getServerCaller().invokeWithRetries(thisUploadJob, removePermissionsResponseHandler, 2);
                    if(!removePermissionsResponseHandler.isSuccess()) {
                        getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), removePermissionsResponseHandler.getResponse()));
                    }
                } else {
                    getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), permissionsResponseHandler.getResponse()));
                }

                uploadAlbumId = albumDetails.getGalleryId();
            }
            if (!albumGenHandler.isSuccess() || uploadAlbumId < 0) {
                // notify the listener of the final error we received from the server
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), albumGenHandler.getResponse()));
                return false;
            } else {
                thisUploadJob.setTemporaryUploadAlbumId(uploadAlbumId);
            }
        }
        return true;
    }

    public boolean deleteTemporaryUploadAlbum(@NonNull UploadJob thisUploadJob) {
        if (thisUploadJob.getTemporaryUploadAlbumId() < 0 && thisUploadJob.hasNeedOfTemporaryFolder()) {
            throw new IllegalStateException("Cannot delete upload album when job is still incomplete");
        }
        // all files were uploaded successfully.
        //delete temporary hidden album
        AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(thisUploadJob.getTemporaryUploadAlbumId(), true);
        getServerCaller().invokeWithRetries(thisUploadJob, albumDelHandler, 2);
        if (!albumDelHandler.isSuccess()) {
            // notify the listener of the final error we received from the server
            getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoCleanupPostUploadFailedResponse(getNextMessageId(), albumDelHandler.getResponse()));
        } else {
            CategoryItem tmpAlbum = new CategoryItem(thisUploadJob.getTemporaryUploadAlbumId());
            tmpAlbum.setParentageChain(thisUploadJob.getUploadToCategory().getParentageChain(), thisUploadJob.getUploadToCategory().getId());
            EventBus.getDefault().post(new AlbumDeletedEvent(tmpAlbum));
            thisUploadJob.setTemporaryUploadAlbumId(-1);
        }
        return albumDelHandler.isSuccess();
    }
}
