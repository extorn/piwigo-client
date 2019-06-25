package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.ArrayList;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.libs.http.RequestParams;

public class FavoritesGetListResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "FavGetListRspHndlr";
    private final int page;
    private final int pageSize;


    public FavoritesGetListResponseHandler(int page, int pageSize) {
        super("piwigo_client.favorites.getList", TAG);
        this.page = page;
        this.pageSize = pageSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {

        ArrayList<Long> items = new ArrayList<>();
        JsonObject result = rsp.getAsJsonObject();
        JsonArray images = null;
        if(result.has("images")) {
            images = result.get("images").getAsJsonArray();
        }
        if(images != null) {
            for (int i = 0; i < images.size(); i++) {
                JsonObject image = (JsonObject) images.get(i);
                items.add(image.get("id").getAsLong());
            }
        }

        PiwigoFavoriteListResponse r = new PiwigoFavoriteListResponse(getMessageId(), getPiwigoMethod(), page, pageSize, items, isCached);
        storeResponse(r);
    }

    public static class PiwigoFavoriteListResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {
        private final ArrayList<Long> imageIds;
        private final int pageSize;
        private final int page;

        public PiwigoFavoriteListResponse(long messageId, String piwigoMethod, int page, int pageSize, ArrayList<Long> imageIds, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.page = page;
            this.pageSize = pageSize;
            this.imageIds = imageIds;
        }

        public int getPage() {
            return page;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<Long> getImageIds() {
            return imageIds;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}