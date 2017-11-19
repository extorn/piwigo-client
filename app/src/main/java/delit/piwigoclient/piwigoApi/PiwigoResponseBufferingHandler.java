package delit.piwigoclient.piwigoApi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.User;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

/**
 * Created by gareth on 24/06/17.
 */

public class PiwigoResponseBufferingHandler {
    private static volatile PiwigoResponseBufferingHandler defaultInstance;
    private final Handler callbackHandler;
    private ConcurrentMap<Long, Response> responses = new ConcurrentSkipListMap<>();
    private ConcurrentMap<Long, Long> handlerResponseMap = new ConcurrentSkipListMap<>();
    private ConcurrentMap<Long, PiwigoResponseListener> handlers = new ConcurrentSkipListMap<>();
    private static AtomicLong nextHandlerId = new AtomicLong();

    public PiwigoResponseBufferingHandler() {
        callbackHandler = new Handler(Looper.getMainLooper());
    }

    public synchronized static long getNextHandlerId() {
        long id = nextHandlerId.incrementAndGet();
        if(id < 0) {
            id = 0;
            nextHandlerId.set(0);
        }
        return id;
    }

    /**
     * Convenience singleton for apps using a process-wide EventBus instance.
     */
    public static PiwigoResponseBufferingHandler getDefault() {
        if (defaultInstance == null) {
            synchronized (PiwigoResponseBufferingHandler.class) {
                if (defaultInstance == null) {
                    defaultInstance = new PiwigoResponseBufferingHandler();
                }
            }
        }
        return defaultInstance;
    }

    public synchronized void handleAnyQueuedMessagesForHandler(PiwigoResponseListener handler) {
        //TODO maybe its something special about the fragments in a slideshow - not having state retained perhaps? Do others work as expected?
        //TODO perhaps the handlers are not being removed from the list when they are replaced by another? Maybe the new one isn't replacing them for some reason?
        //TODO this method leaves responses unhandled by handlers... both are retained. Memory leak. Re-examine the whole lifecycle of fragments that are handlers.
        List<Long> responsesMappingsToRemove = new ArrayList<>(10);
        List<Long> handlersToRemove = new ArrayList<>(10);
        for(Map.Entry<Long,Long> handlerResponseEntry : handlerResponseMap.entrySet()) {
            if(handlerResponseEntry.getValue() == handler.getHandlerId()) { // deliberate object referemce equality.
                long responseMessageId = handlerResponseEntry.getKey();
                Response r = responses.remove(responseMessageId);
                if(r != null) {
                    if(handler.canHandlePiwigoResponseNow(r)) {
                        handler.handlePiwigoResponse(r);
                        if(r.isEndResponse()) {
                            responsesMappingsToRemove.add(responseMessageId);
                        }
                    } else {
                        // add it back to the queue.
                        responses.put(r.getMessageId(), r);
                    }
                }
            }
        }
        // check which handlers are not listening for anything else
        for(Long responseId : responsesMappingsToRemove) {
            Long thisHandlerId = handlerResponseMap.remove(responseId);
            if(!handlerResponseMap.containsValue(thisHandlerId)) {
                handlersToRemove.add(thisHandlerId);
            }
        }
        // remove all the dead handlers.
        for(Long handlerId : handlersToRemove) {
            handlers.remove(handlerId);
        }
    }

    public synchronized void preRegisterResponseHandlerForNewMessage(long currentMessageId, long newMessageId) {
        Long handlerId = handlerResponseMap.get(currentMessageId);
        if(handlerId == null) {
            throw new IllegalStateException("Unable to find handler to register for new message id");
        }
        handlerResponseMap.put(newMessageId, handlerId);
    }

    public synchronized PiwigoResponseListener registerResponseHandler(long messageId, final PiwigoResponseListener h) {
        PiwigoResponseListener oldHandler = handlers.put(h.getHandlerId(), h);
        handlerResponseMap.put(messageId, h.getHandlerId());
        final Response r = responses.remove(messageId);
        if (r != null) {
            if (r.isEndResponse()) {
                handlerResponseMap.remove(messageId);
            }
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(h.canHandlePiwigoResponseNow(r)) {
                        h.handlePiwigoResponse(r);
                    } else {
                        //Trying to replace the handler and the response back on the queue for later processing.
                        //TODO think this through more - thread safe? Think so... be sure.
                        if (r.isEndResponse()) {
                            handlerResponseMap.put(r.getMessageId(), h.getHandlerId());
                        }
                        responses.put(r.getMessageId(), r);
                    }
                }
            });
        }
        return oldHandler;
    }

    public synchronized void processResponse(final Response response) {
        final PiwigoResponseListener handler;
        final Long handlerId;
        if(response.isEndResponse()) {
            handlerId = handlerResponseMap.remove(response.getMessageId());
        } else {
            handlerId = handlerResponseMap.get(response.getMessageId());
        }
        if(handlerId != null) {
            handler = handlers.get(handlerId);
        } else {
            handler = null;
        }
        if (handler != null) {
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(handler.canHandlePiwigoResponseNow(response)) {
                        handler.handlePiwigoResponse(response);
                    } else {
                        //Trying to replace the handler and the response back on the queue for later processing.
                        //TODO think this through more - thread safe? Think so... be sure.
                        if (response.isEndResponse()) {
                            handlerResponseMap.put(response.getMessageId(), handlerId);
                        }
                        responses.put(response.getMessageId(), response);
                    }
                }
            });
        } else {
            // Allow 30 seconds grace after which this response could be expunged at any moment.
            response.setExpiresAt(System.currentTimeMillis() + 30000);
            responses.put(response.getMessageId(), response);
        }
        removeExpiredResponses();
    }

    public void removeExpiredResponses() {
        Iterator<Map.Entry<Long, Response>> iter = responses.entrySet().iterator();
        long currentTime = System.currentTimeMillis();
        while (iter.hasNext()) {
            Map.Entry<Long, Response> item = iter.next();
            if (item.getValue().getExpiresAt() < currentTime) {
                // still no handler for this...
                if(!handlerResponseMap.containsKey(item.getKey())) {
                    iter.remove();
                    Log.d("handlers", "Message expired before delivery could be made");
                }
            }
        }
    }

    public synchronized PiwigoResponseListener deRegisterResponseHandler(long messageId) {
        Long handlerId = handlerResponseMap.remove(messageId);
        if(handlerId != null) {
            return handlers.remove(handlerId);
        }
        return null;
    }

    public void replaceHandler(BasicPiwigoResponseListener newHandler) {
        handlers.put(newHandler.getHandlerId(), newHandler);
    }

    public interface PiwigoResponse extends Response {
        String getPiwigoMethod();
    }

    public interface Response {
        long getMessageId();

        boolean isEndResponse();

        long getExpiresAt();

        void setExpiresAt(long expiresAt);
    }

    public static class CustomErrorResponse extends BaseResponse implements ErrorResponse {

        private final String errorMessage;

        public CustomErrorResponse(long messageId, String errorMessage) {
            super(messageId);
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public interface PiwigoResponseListener {
        /**
         *
         * @param response
         * @return true if this response was meant for this handler.
         */
        void handlePiwigoResponse(Response response);

        boolean canHandlePiwigoResponseNow(Response response);

        long getHandlerId();
    }

    public static class PiwigoSuccessResponse extends BasePiwigoResponse {

        private final JSONObject response;

        public PiwigoSuccessResponse(long messageId, String piwigoMethod, JSONObject response) {
            super(messageId, piwigoMethod);
            this.response = response;
        }

        public JSONObject getResponse() {
            return response;
        }
    }

    /**
     * Marker interface
     */
    public interface ErrorResponse {}

    public static class PiwigoServerErrorResponse extends BasePiwigoResponse implements ErrorResponse {
        private String piwigoMethod;
        private int piwigoErrorCode;
        private String piwigoErrorMessage;

        public PiwigoServerErrorResponse(long messageId, String piwigoMethod, int piwigoErrorCode, String piwigoErrorMessage) {
            super(messageId, piwigoMethod);
            this.piwigoMethod = piwigoMethod;
            this.piwigoErrorCode = piwigoErrorCode;
            this.piwigoErrorMessage = piwigoErrorMessage;
        }

        public String getPiwigoMethod() {
            return piwigoMethod;
        }

        public int getPiwigoErrorCode() {
            return piwigoErrorCode;
        }

        public String getPiwigoErrorMessage() {
            return piwigoErrorMessage;
        }
    }

    public static class PiwigoUnexpectedReplyErrorResponse extends BasePiwigoResponse implements ErrorResponse {

        public static final short OUTCOME_SUCCESS = 2;
        public static final short OUTCOME_FAILED = 1;
        public static final short OUTCOME_UNKNOWN = 0;

        private final String rawResponse;
        private short requestOutcome;

        public PiwigoUnexpectedReplyErrorResponse(long messageId, String piwigoMethod, short requestOutcome, String rawResponse) {
            super(messageId, piwigoMethod);
            if (requestOutcome > OUTCOME_SUCCESS || requestOutcome < OUTCOME_UNKNOWN) {
                throw new IllegalArgumentException("RequestOutcome must be one of the constant values defined in " + this.getClass().getName());
            }
            this.requestOutcome = requestOutcome;
            this.rawResponse = rawResponse;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public short getRequestOutcome() {
            return requestOutcome;
        }

        public boolean requestSucceeded() {
            return requestOutcome == OUTCOME_SUCCESS;
        }

        public boolean requestFailed() {
            return requestOutcome == OUTCOME_FAILED;
        }
    }

    public static class PiwigoHttpErrorResponse extends BasePiwigoResponse implements ErrorResponse  {

        private int statusCode;
        private String errorMessage;
        private String errorDetail;

        public PiwigoHttpErrorResponse(long messageId, String piwigoMethod, int statusCode, String errorMessage, String errorDetail) {
            this(messageId, piwigoMethod, statusCode, errorMessage);
            this.errorDetail = errorDetail;
        }

        public PiwigoHttpErrorResponse(long messageId, String piwigoMethod, int statusCode, String errorMessage) {
            super(messageId, piwigoMethod);
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorDetail() {
            return errorDetail;
        }
    }

    public static class UrlCancelledResponse extends BaseUrlResponse {

        public UrlCancelledResponse(long messageId, String url) {
            super(messageId, url);
        }
    }

    public static class UrlErrorResponse extends BaseUrlResponse implements ErrorResponse  {
        private int statusCode;
        private String errorMessage;
        private String errorDetail;

        public UrlErrorResponse(long messageId, String url, int statusCode, byte[] data, String errorMessage, String errorDetail) {
            super(messageId, url);
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.errorDetail = errorDetail;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorDetail() {
            return errorDetail;
        }
    }

    public static class UrlProgressResponse extends BaseUrlResponse {
        private final int progress;

        public UrlProgressResponse(long messageId, String url, int progress) {
            super(messageId, url, false);
            this.progress = progress;
        }

        public int getProgress() {
            return progress;
        }
    }

    public static class UrlToFileSuccessResponse extends BaseUrlResponse {
        private File file;

        public UrlToFileSuccessResponse(long messageId, String url, File file) {
            super(messageId, url);
            this.file = file;
        }

        public File getFile() {
            return file;
        }
    }

    public static class UrlSuccessResponse extends BaseUrlResponse {
        private byte[] data;

        public UrlSuccessResponse(long messageId, String url, byte[] data) {
            super(messageId, url);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class BaseUrlResponse extends BaseResponse {
        private String url;

        public BaseUrlResponse(long messageId, String url) {
            super(messageId);
            this.url = url;
        }

        public BaseUrlResponse(long messageId, String url, boolean isEndRequest) {
            super(messageId, isEndRequest);
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class BasePiwigoResponse extends BaseResponse implements PiwigoResponse {
        private String piwigoMethod;

        public BasePiwigoResponse(long messageId, String piwigoMethod) {
            super(messageId);
            this.piwigoMethod = piwigoMethod;
        }

        public BasePiwigoResponse(long messageId, String piwigoMethod, boolean isEndResponse) {
            super(messageId, isEndResponse);
            this.piwigoMethod = piwigoMethod;
        }

        @Override
        public String getPiwigoMethod() {
            return piwigoMethod;
        }
    }

    public static class PiwigoSessionStatusRetrievedResponse extends BasePiwigoResponse {

        public PiwigoSessionStatusRetrievedResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoAddImageResponse extends BasePiwigoResponse {

        public PiwigoAddImageResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoFavoriteStatusResponse extends BasePiwigoResponse {

        private final ResourceItem piwigoResource;

        public PiwigoFavoriteStatusResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource) {
            super(messageId, piwigoMethod, true);
            this.piwigoResource = piwigoResource;
        }

        public ResourceItem getPiwigoResource() {
            return piwigoResource;
        }
    }

    public static class PiwigoRatingAlteredResponse extends BasePiwigoResponse {

        private final ResourceItem piwigoResource;

        public PiwigoRatingAlteredResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource) {
            super(messageId, piwigoMethod, true);
            this.piwigoResource = piwigoResource;
        }

        public ResourceItem getPiwigoResource() {
            return piwigoResource;
        }
    }


    public static class PiwigoAlbumPermissionsRetrievedResponse extends BasePiwigoResponse {

        private final CategoryItem album;

        public PiwigoAlbumPermissionsRetrievedResponse(long messageId, String piwigoMethod, CategoryItem album) {
            super(messageId, piwigoMethod, true);
            this.album = album;
        }

        public CategoryItem getAlbum() {
            return album;
        }
    }

    public static class PiwigoAlbumCreatedResponse extends BasePiwigoResponse {

        private final PiwigoGalleryDetails albumDetails;
        private final long newAlbumId;

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, long albumId) {
            super(messageId, piwigoMethod, true);
            this.albumDetails = null;
            this.newAlbumId = albumId;
        }

        public PiwigoAlbumCreatedResponse(long messageId, String piwigoMethod, PiwigoGalleryDetails albumDetails) {
            super(messageId, piwigoMethod, true);
            this.albumDetails = albumDetails;
            this.newAlbumId = albumDetails.getGalleryId();
        }

        public PiwigoGalleryDetails getAlbumDetails() {
            return albumDetails;
        }

        public long getNewAlbumId() {
            return newAlbumId;
        }
    }


    public static class PiwigoAlbumThumbnailUpdatedResponse extends BasePiwigoResponse {
        private final Long albumParentId;

        public PiwigoAlbumThumbnailUpdatedResponse(long messageId, String piwigoMethod, Long albumParentId) {
            super(messageId, piwigoMethod, true);
            this.albumParentId = albumParentId;
        }

        public Long getAlbumParentIdAltered() {
            return albumParentId;
        }
    }

    public static class PiwigoAlbumDeletedResponse extends BasePiwigoResponse {
        private long albumId;

        public PiwigoAlbumDeletedResponse(long messageId, String piwigoMethod, long albumId) {
            super(messageId, piwigoMethod, true);
            this.albumId = albumId;
        }

        public long getAlbumId() {
            return albumId;
        }
    }

    public static class PiwigoDeleteGroupResponse extends BasePiwigoResponse {
        public PiwigoDeleteGroupResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoDeleteImageResponse extends BasePiwigoResponse {
        public PiwigoDeleteImageResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoDeleteUserResponse extends BasePiwigoResponse {
        public PiwigoDeleteUserResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoOnLogoutResponse extends BasePiwigoResponse {
        public PiwigoOnLogoutResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoOnLoginResponse extends BasePiwigoResponse {
        private boolean sessionRetrieved;
        private boolean userDetailsRetrieved;

        public PiwigoOnLoginResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }

        public void setSessionRetrieved() {
            sessionRetrieved = true;
        }

        public boolean isSessionRetrieved() {
            return sessionRetrieved;
        }

        public void setUserDetailsRetrieved() {
            userDetailsRetrieved = true;
        }

        public boolean isUserDetailsRetrieved() {
            return userDetailsRetrieved;
        }
    }

    public static class PiwigoSetAlbumStatusResponse extends BasePiwigoResponse {
        public PiwigoSetAlbumStatusResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }

    public static class PiwigoAddAlbumPermissionsResponse extends BasePiwigoResponse {

        private final HashSet<Long> groupIdsAffected;
        private final HashSet<Long> userIdsAffected;

        public PiwigoAddAlbumPermissionsResponse(long messageId, String piwigoMethod, HashSet<Long> groupIdsAffected, HashSet<Long> userIdsAffected) {
            super(messageId, piwigoMethod, true);
            this.groupIdsAffected = groupIdsAffected;
            this.userIdsAffected = userIdsAffected;
        }

        public HashSet<Long> getGroupIdsAffected() {
            return groupIdsAffected;
        }

        public HashSet<Long> getUserIdsAffected() {
            return userIdsAffected;
        }
    }

    public static class PiwigoRemoveAlbumPermissionsResponse extends BasePiwigoResponse {
        private final HashSet<Long> groupIdsAffected;
        private final HashSet<Long> userIdsAffected;

        public PiwigoRemoveAlbumPermissionsResponse(long messageId, String piwigoMethod, HashSet<Long> groupIdsAffected, HashSet<Long> userIdsAffected) {
            super(messageId, piwigoMethod, true);
            this.groupIdsAffected = groupIdsAffected;
            this.userIdsAffected = userIdsAffected;
        }

        public HashSet<Long> getGroupIdsAffected() {
            return groupIdsAffected;
        }

        public HashSet<Long> getUserIdsAffected() {
            return userIdsAffected;
        }
    }

    public static class PiwigoUploadFileChunkResponse extends BasePiwigoResponse {
        private final ResourceItem uploadedResource;

        public PiwigoUploadFileChunkResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
            uploadedResource = null;
        }

        public PiwigoUploadFileChunkResponse(long messageId, String piwigoMethod, ResourceItem uploadedResource) {
            super(messageId, piwigoMethod, true);
            this.uploadedResource = uploadedResource;
        }

        public ResourceItem getUploadedResource() {
            return uploadedResource;
        }
    }


    public static class PiwigoResourceCheckRetrievedResponse<T extends ResourceItem> extends BasePiwigoResponse {

        private final T resource;
        private final Boolean fileMatches;

        public PiwigoResourceCheckRetrievedResponse(long messageId, String piwigoMethod, T resource, Boolean fileMatches) {
            super(messageId, piwigoMethod, true);
            this.resource = resource;
            this.fileMatches = fileMatches;
        }

        public Boolean getFileMatches() {
            return fileMatches;
        }

        public boolean isFileMatch() {
            return fileMatches != null && fileMatches;
        }

        public T getResource() {
            return resource;
        }
    }

    public static class PiwigoResourceInfoRetrievedResponse<T extends ResourceItem> extends BasePiwigoResponse {

        private final T resource;

        public PiwigoResourceInfoRetrievedResponse(long messageId, String piwigoMethod, T resource) {
            super(messageId, piwigoMethod, true);
            this.resource = resource;
        }

        public T getResource() {
            return resource;
        }
    }

    public static class PiwigoGetUserDetailsResponse extends PiwigoGetUsersListResponse {

        private final User selectedUser;

        public PiwigoGetUserDetailsResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<User> users) {
            super(messageId, piwigoMethod, page, pageSize, itemsOnPage, users);
            selectedUser = getUsers().remove(0);
        }

        public User getSelectedUser() {
            return selectedUser;
        }
    }

    public static class PiwigoGetUsersListResponse extends BasePiwigoResponse {
        private final ArrayList<User> users;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetUsersListResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<User> users) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.users = users;
        }

        public int getItemsOnPage() {
            return itemsOnPage;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<User> getUsers() {
            return users;
        }
    }

    public static class PiwigoGetUsernamesListResponse extends BasePiwigoResponse {
        private final ArrayList<Username> usernames;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetUsernamesListResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, ArrayList<Username> usernames) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.usernames = usernames;
        }

        public int getItemsOnPage() {
            return itemsOnPage;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<Username> getUsernames() {
            return usernames;
        }
    }

    public static class PiwigoGetGroupsListRetrievedResponse extends BasePiwigoResponse {
        private final HashSet<Group> groups;
        private final int itemsOnPage;
        private final int pageSize;
        private final int page;

        public PiwigoGetGroupsListRetrievedResponse(long messageId, String piwigoMethod, int page, int pageSize, int itemsOnPage, HashSet<Group> groups) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.itemsOnPage = itemsOnPage;
            this.groups = groups;
        }

        public int getItemsOnPage() {
            return itemsOnPage;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public HashSet<Group> getGroups() {
            return groups;
        }
    }

    public static class PiwigoFindExistingImagesResponse extends BasePiwigoResponse {

        private HashMap<String, Long> existingImages;

        public PiwigoFindExistingImagesResponse(long messageId, String piwigoMethod, HashMap<String, Long> existingImages) {
            super(messageId, piwigoMethod, true);
            this.existingImages = existingImages;
        }

        public HashMap<String, Long> getExistingImages() {
            return existingImages;
        }
    }

    public static class PiwigoGetResourcesResponse extends BasePiwigoResponse {

        private final int page;
        private final int pageSize;
        private ArrayList<GalleryItem> resources;

        public PiwigoGetResourcesResponse(long messageId, String piwigoMethod, int page, int pageSize, ArrayList<GalleryItem> resources) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.resources = resources;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<GalleryItem> getResources() {
            return resources;
        }
    }

    public static class PiwigoGetSubAlbumNamesResponse extends BasePiwigoResponse {
        private ArrayList<CategoryItemStub> albumNames;

        public PiwigoGetSubAlbumNamesResponse(long messageId, String piwigoMethod, ArrayList<CategoryItemStub> albumNames) {
            super(messageId, piwigoMethod, true);
            this.albumNames = albumNames;
        }

        public ArrayList<CategoryItemStub> getAlbumNames() {
            return albumNames;
        }
    }

    public static class PiwigoUpdateAlbumInfoResponse extends BasePiwigoResponse {
        private CategoryItem album;

        public PiwigoUpdateAlbumInfoResponse(long messageId, String piwigoMethod, CategoryItem album) {
            super(messageId, piwigoMethod, true);
            this.album = album;
        }

        public CategoryItem getAlbum() {
            return album;
        }
    }

    public static class PiwigoUpdateAlbumContentResponse extends BasePiwigoResponse {
        private CategoryItem album;

        public PiwigoUpdateAlbumContentResponse(long messageId, String piwigoMethod, CategoryItem album) {
            super(messageId, piwigoMethod, true);
            this.album = album;
        }

        public CategoryItem getAlbum() {
            return album;
        }
    }


    public static class PiwigoUserPermissionsAddedResponse extends BasePiwigoResponse {
        private final HashSet<Long> albumsForWhichPermissionAdded;
        private long userId;

        public PiwigoUserPermissionsAddedResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> albumsForWhichPermissionAdded) {
            super(messageId, piwigoMethod, true);
            this.userId = userId;
            this.albumsForWhichPermissionAdded = albumsForWhichPermissionAdded;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getAlbumsForWhichPermissionAdded() {
            return albumsForWhichPermissionAdded;
        }
    }

    public static class PiwigoGroupPermissionsAddedResponse extends BasePiwigoResponse {
        private final ArrayList<Long> albumsForWhichPermissionAdded;
        private long groupId;

        public PiwigoGroupPermissionsAddedResponse(long messageId, String piwigoMethod, long groupId, ArrayList<Long> albumsForWhichPermissionAdded) {
            super(messageId, piwigoMethod, true);
            this.groupId = groupId;
            this.albumsForWhichPermissionAdded = albumsForWhichPermissionAdded;
        }

        public long getGroupId() {
            return groupId;
        }

        public ArrayList<Long> getAlbumsForWhichPermissionAdded() {
            return albumsForWhichPermissionAdded;
        }
    }

    public static class PiwigoUserPermissionsRemovedResponse extends BasePiwigoResponse {
        private final HashSet<Long> albumsForWhichPermissionRemoved;
        private long userId;

        public PiwigoUserPermissionsRemovedResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> albumsForWhichPermissionRemoved) {
            super(messageId, piwigoMethod, true);
            this.userId = userId;
            this.albumsForWhichPermissionRemoved = albumsForWhichPermissionRemoved;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getAlbumsForWhichPermissionRemoved() {
            return albumsForWhichPermissionRemoved;
        }
    }

    public static class PiwigoGroupPermissionsRemovedResponse extends BasePiwigoResponse {
        private final ArrayList<Long> albumsForWhichPermissionRemoved;
        private long groupId;

        public PiwigoGroupPermissionsRemovedResponse(long messageId, String piwigoMethod, long groupId, ArrayList<Long> albumsForWhichPermissionRemoved) {
            super(messageId, piwigoMethod, true);
            this.groupId = groupId;
            this.albumsForWhichPermissionRemoved = albumsForWhichPermissionRemoved;
        }

        public long getGroupId() {
            return groupId;
        }

        public ArrayList<Long> getAlbumsForWhichPermissionRemoved() {
            return albumsForWhichPermissionRemoved;
        }
    }

    public static class PiwigoAddGroupResponse extends BasePiwigoResponse {
        private Group group;

        public PiwigoAddGroupResponse(long messageId, String piwigoMethod, Group group) {
            super(messageId, piwigoMethod, true);
            this.group = group;
        }

        public Group getGroup() {
            return group;
        }
    }

    public static class PiwigoGroupUpdateInfoResponse extends BasePiwigoResponse {
        private Group group;

        public PiwigoGroupUpdateInfoResponse(long messageId, String piwigoMethod, Group group) {
            super(messageId, piwigoMethod, true);
            this.group = group;
        }

        public Group getGroup() {
            return group;
        }
    }

    public static class PiwigoGroupAddMembersResponse extends BasePiwigoResponse {
        private Group group;

        public PiwigoGroupAddMembersResponse(long messageId, String piwigoMethod, Group group) {
            super(messageId, piwigoMethod, true);
            this.group = group;
        }

        public Group getGroup() {
            return group;
        }
    }

    public static class PiwigoGroupRemoveMembersResponse extends BasePiwigoResponse {
        private Group group;

        public PiwigoGroupRemoveMembersResponse(long messageId, String piwigoMethod, Group group) {
            super(messageId, piwigoMethod, true);
            this.group = group;
        }

        public Group getGroup() {
            return group;
        }
    }

    public static class PiwigoAddUserResponse extends BasePiwigoResponse {
        private User user;

        public PiwigoAddUserResponse(long messageId, String piwigoMethod, User user) {
            super(messageId, piwigoMethod, true);
            this.user = user;
        }

        public User getUser() {
            return user;
        }
    }

    public static class PiwigoUpdateUserInfoResponse extends BasePiwigoResponse {
        private User user;

        public PiwigoUpdateUserInfoResponse(long messageId, String piwigoMethod, User user) {
            super(messageId, piwigoMethod, true);
            this.user = user;
        }

        public User getUser() {
            return user;
        }
    }

    public static class PiwigoUpdateResourceInfoResponse<T extends ResourceItem> extends BasePiwigoResponse {
        private T resource;

        public PiwigoUpdateResourceInfoResponse(long messageId, String piwigoMethod, T resource) {
            super(messageId, piwigoMethod, true);
            this.resource = resource;
        }

        public T getResource() {
            return resource;
        }
    }


    public static class PiwigoGetSubAlbumsResponse extends BasePiwigoResponse {
        private ArrayList<CategoryItem> albums;

        public PiwigoGetSubAlbumsResponse(long messageId, String piwigoMethod, ArrayList<CategoryItem> albums) {
            super(messageId, piwigoMethod, true);
            this.albums = albums;
        }

        public ArrayList<CategoryItem> getAlbums() {
            return albums;
        }
    }

    public static class PiwigoUserPermissionsResponse extends BasePiwigoResponse {
        private final HashSet<Long> indirectlyAccessibleAlbumIds;
        private HashSet<Long> directlyAccessibleAlbumIds;
        private long userId;

        public PiwigoUserPermissionsResponse(long messageId, String piwigoMethod, long userId, HashSet<Long> directlyAccessibleAlbumIds, HashSet<Long> indirectlyAccessibleAlbumIds) {
            super(messageId, piwigoMethod, true);
            this.userId = userId;
            this.directlyAccessibleAlbumIds = directlyAccessibleAlbumIds;
            this.indirectlyAccessibleAlbumIds = indirectlyAccessibleAlbumIds;
        }

        public long getUserId() {
            return userId;
        }

        public HashSet<Long> getDirectlyAccessibleAlbumIds() {
            return directlyAccessibleAlbumIds;
        }

        public HashSet<Long> getIndirectlyAccessibleAlbumIds() {
            return indirectlyAccessibleAlbumIds;
        }
    }

    public static class PiwigoPrepareUploadFailedResponse extends BaseResponse {

        private final Response error;

        public PiwigoPrepareUploadFailedResponse(long jobId, Response error) {
            super(jobId, true);
            this.error = error;
        }

        public Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadProgressUpdateResponse extends BaseResponse {

        private final int progress;
        private final File fileForUpload;

        public PiwigoUploadProgressUpdateResponse(long jobId, File fileForUpload, int progress) {
            super(jobId, true);
            this.progress = progress;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public int getProgress() {
            return progress;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileLocalErrorResponse extends BaseResponse implements ErrorResponse  {

        private final Exception error;
        private final File fileForUpload;

        public PiwigoUploadFileLocalErrorResponse(long jobId, File fileForUpload, Exception error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public Exception getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileAddToAlbumFailedResponse extends BaseResponse {

        private final Response error;
        private final File fileForUpload;

        public PiwigoUploadFileAddToAlbumFailedResponse(long jobId, File fileForUpload, Response error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileChunkFailedResponse extends BaseResponse {

        private final Response error;
        private final File fileForUpload;

        public PiwigoUploadFileChunkFailedResponse(long jobId, File fileForUpload, Response error) {
            super(jobId, true);
            this.error = error;
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public Response getError() {
            return error;
        }

        public long getJobId() {
            return getMessageId();
        }
    }


    public static class PiwigoUploadFileFilesExistAlreadyResponse extends BaseResponse {

        private final ArrayList<File> existingFiles;

        public PiwigoUploadFileFilesExistAlreadyResponse(long jobId, ArrayList<File> existingFiles) {
            super(jobId, true);
            this.existingFiles = existingFiles;
        }

        public ArrayList<File> getExistingFiles() {
            return existingFiles;
        }

        public long getJobId() {
            return getMessageId();
        }
    }

    public static class PiwigoUploadFileJobCompleteResponse extends BaseResponse {

        private UploadJob job;

        public PiwigoUploadFileJobCompleteResponse(long messageId, UploadJob job) {

            super(messageId, true);
            this.job = job;
        }

        public long getJobId() {
            return getMessageId();
        }

        public UploadJob getJob() {
            return job;
        }
    }

    public static class PiwigoStartUploadFileResponse extends BaseResponse {

        private final File fileForUpload;

        public PiwigoStartUploadFileResponse(long jobId, File fileForUpload) {
            super(jobId, true);
            this.fileForUpload = fileForUpload;
        }

        public File getFileForUpload() {
            return fileForUpload;
        }

        public long getJobId() {
            return getMessageId();
        }
    }


    public static class PiwigoGroupPermissionsRetrievedResponse extends BasePiwigoResponse {
        private final HashSet<Long> allowedAlbums;
        private final HashSet<Long> groupIds;

        public PiwigoGroupPermissionsRetrievedResponse(long messageId, String piwigoMethod, HashSet<Long> groupIds, HashSet<Long> allowedAlbums) {
            super(messageId, piwigoMethod, true);
            this.groupIds = groupIds;
            this.allowedAlbums = allowedAlbums;
        }

        public long getGroupId() {
            if(groupIds.size() != 1) {
                throw new IllegalStateException("Can only use this method when there is known to be a single group id");
            }
            return groupIds.iterator().next();
        }

        public HashSet<Long> getGroupIds() {
            return groupIds;
        }

        public HashSet<Long> getAllowedAlbums() {
            return allowedAlbums;
        }
    }

    public static class FileUploadCancelledResponse extends BaseResponse {

        private File cancelledFile;
        public FileUploadCancelledResponse(long messageId, File cancelledFile) {
            super(messageId, true);
            this.cancelledFile = cancelledFile;
        }

        public File getCancelledFile() {
            return cancelledFile;
        }
    }

    public static class HttpClientsShutdownResponse extends BaseResponse {

        public HttpClientsShutdownResponse(long messageId) {
            super(messageId, true);
        }
    }

    public static class BaseResponse implements Response {

        private final long messageId;
        private final boolean isEndResponse;
        private long expiresAt;


        public BaseResponse(long messageId) {
            this(messageId, true);
        }

        public BaseResponse(long messageId, boolean isEndResponse) {
            this.messageId = messageId;
            this.isEndResponse = isEndResponse;
        }

        @Override
        public long getMessageId() {
            return messageId;
        }

        @Override
        public boolean isEndResponse() {
            return isEndResponse;
        }

        @Override
        public long getExpiresAt() {
            return expiresAt;
        }

        @Override
        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

}
