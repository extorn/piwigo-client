package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoPrepareUploadFailedResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class PrepareForUploadActor extends UploadActor {

    private ArrayList<CategoryItemStub> availableAlbumsOnServer;

    public PrepareForUploadActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public ArrayList<CategoryItemStub> getAvailableAlbumsOnServer() {
        return availableAlbumsOnServer;
    }

    public boolean run() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getUploadJob().getConnectionPrefs());
        if (sessionDetails == null) {
            LoginResponseHandler handler = new LoginResponseHandler();
            getServerCaller().invokeWithRetries(getUploadJob(), handler, 2);
            if (handler.isSuccess()) {
                sessionDetails = PiwigoSessionDetails.getInstance(getUploadJob().getConnectionPrefs());
            } else {
                getListener().recordAndPostNewResponse(getUploadJob(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                return false;
            }
            if (sessionDetails == null) {
                Bundle b = new Bundle();
                b.putString("location", "upload - get login");
                Logging.logAnalyticEvent(getContext(), "SessionNull", b);
                getListener().recordAndPostNewResponse(getUploadJob(), new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
                return false;
            }
        }
        availableAlbumsOnServer = retrieveListOfAlbumsOnServer(getUploadJob(), sessionDetails);
        if (availableAlbumsOnServer == null) {
            //try again. This is really important.
            availableAlbumsOnServer = retrieveListOfAlbumsOnServer(getUploadJob(), sessionDetails);
        }
        if (availableAlbumsOnServer == null) {
            // This is fatal really. It is necessary for a resilient upload. Stop the upload.
            return false;
        }

        if (getUploadJob().getTemporaryUploadAlbumId() > 0) {
            // check it still exists (ensure one is created again if not) - could have been deleted by a user manually.
            boolean tempAlbumExists = PiwigoUtils.containsItemWithId(availableAlbumsOnServer, getUploadJob().getTemporaryUploadAlbumId());
            if (!tempAlbumExists) {
                // allow a new one to be created and tracked
                getUploadJob().setTemporaryUploadAlbumId(-1);
            }
        }
        return true;
    }

    private ArrayList<CategoryItemStub> retrieveListOfAlbumsOnServer(UploadJob thisUploadJob, PiwigoSessionDetails sessionDetails) {
        if (sessionDetails.isAdminUser()) {
            AlbumGetChildAlbumsAdminResponseHandler handler = new AlbumGetChildAlbumsAdminResponseHandler();
            getServerCaller().invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse rsp = (AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) handler.getResponse();
                return rsp.getAdminList().flattenTree();
            } else {
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else if (sessionDetails.isUseCommunityPlugin() && sessionDetails.isCommunityApiAvailable()) {
            final boolean recursive = true;
            CommunityGetChildAlbumNamesResponseHandler handler = new CommunityGetChildAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive);
            getServerCaller().invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                CommunityGetChildAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse rsp = (CommunityGetChildAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        } else {
            AlbumGetChildAlbumNamesResponseHandler handler = new AlbumGetChildAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, true);
            getServerCaller().invokeWithRetries(thisUploadJob, handler, 2);
            if (handler.isSuccess()) {
                AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse rsp = (AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) handler.getResponse();
                return rsp.getAlbumNames();
            } else {
                getListener().recordAndPostNewResponse(thisUploadJob, new PiwigoPrepareUploadFailedResponse(getNextMessageId(), handler.getResponse()));
            }
        }
        return null;
    }
}
