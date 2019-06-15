package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageDeleteResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteImageRspHdlr";
    private final HashSet<T> items;
    private final HashSet<Long> itemIds;

    public ImageDeleteResponseHandler(HashSet<Long> itemIds, HashSet<T> selectedItems) {
        super("pwg.images.delete", TAG);
        this.itemIds = itemIds;
        this.items = selectedItems;
    }

    public ImageDeleteResponseHandler(T item) {
        super("pwg.images.delete", TAG);
        this.itemIds = new HashSet<>();
        this.items = new HashSet<>();
        items.add(item);
        itemIds.add(item.getId());
    }

    @Override
    public RequestParams buildRequestParameters() {
        String sessionToken = "";
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            sessionToken = sessionDetails.getSessionToken();
        }
        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if (itemIds.size() == 1) {
            params.put("image_id", String.valueOf(itemIds.iterator().next()));
        } else if (itemIds.size() > 1) {
            for (Long itemId : itemIds) {
                params.add("image_id[]", String.valueOf(itemId));
            }
        }
        params.put("pwg_token", sessionToken);
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoDeleteImageResponse r = new PiwigoDeleteImageResponse(getMessageId(), getPiwigoMethod(), itemIds, items, isCached);
        storeResponse(r);
    }

    public static class PiwigoDeleteImageResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final HashSet<Long> deletedItemIds;
        private final HashSet<? extends ResourceItem> deletedItems;

        public PiwigoDeleteImageResponse(long messageId, String piwigoMethod, HashSet<Long> itemIds, HashSet<? extends ResourceItem> deletedItems, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.deletedItemIds = itemIds;
            this.deletedItems = deletedItems;
        }

        public HashSet<? extends ResourceItem> getDeletedItems() {
            return deletedItems;
        }

        public HashSet<Long> getDeletedItemIds() {
            return deletedItemIds;
        }
    }
}