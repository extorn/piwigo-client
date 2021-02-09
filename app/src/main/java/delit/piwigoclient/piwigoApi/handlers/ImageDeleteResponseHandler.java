package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.HashSet;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class ImageDeleteResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "DeleteImageRspHdlr";
    private final HashSet<T> items;

    public ImageDeleteResponseHandler(HashSet<Long> itemIds, HashSet<T> selectedItems) {
        super("pwg.images.delete", TAG);
        this.items = selectedItems;
    }

    public ImageDeleteResponseHandler(T item) {
        super("pwg.images.delete", TAG);
        this.items = new HashSet<>();
        items.add(item);
    }

    @Override
    public RequestParams buildRequestParameters() {

        //TODO this will give an unusual error if the user is not logged in.... better way?

        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if (items.size() == 1) {
            params.put("image_id", String.valueOf(items.iterator().next().getId()));
        } else if (items.size() > 1) {
            for (T item : items) {
                params.add("image_id[]", String.valueOf(item.getId()));
            }
        }
        params.put("pwg_token", getPwgSessionToken());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoDeleteImageResponse r = new PiwigoDeleteImageResponse(getMessageId(), getPiwigoMethod(), items, isCached);
        storeResponse(r);
    }

    public static class PiwigoDeleteImageResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final HashSet<? extends ResourceItem> deletedItems;

        public PiwigoDeleteImageResponse(long messageId, String piwigoMethod, HashSet<? extends ResourceItem> deletedItems, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.deletedItems = deletedItems;
        }

        public HashSet<? extends ResourceItem> getDeletedItems() {
            return deletedItems;
        }
    }
}