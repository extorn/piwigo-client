package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageGetInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourceInfoRspHdlr";
    private T resourceItem;
    private final String multimediaExtensionList;

    public ImageGetInfoResponseHandler(T piwigoResource, String multimediaExtensionList) {
        super("pwg.images.getInfo", TAG);
        this.resourceItem = piwigoResource;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(resourceItem.getId()));
        params.put("comments_page", "0");
        params.put("comments_per_page", "0");
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        ImagesGetResponseHandler.ResourceParser resourceParser = new ImagesGetResponseHandler.ResourceParser(getContext(), multimediaExtensionList);

        ResourceItem loadedResourceItem = resourceParser.parseAndProcessResourceData(result);

        int privacyLevel = result.get("level").getAsInt();
        loadedResourceItem.setPrivacyLevel(privacyLevel);

        JsonObject rates = result.get("rates").getAsJsonObject();
        if(rates != null && !rates.isJsonNull()) {
            JsonElement scoreJsonElem = rates.get("score");
            if (scoreJsonElem != null && !scoreJsonElem.isJsonNull()) {
                float rating = scoreJsonElem.getAsFloat();
                loadedResourceItem.setYourRating(rating);
            }

            JsonElement ratesElem = rates.get("count");
            if(ratesElem != null && !ratesElem.isJsonNull()) {
                int usersRated = ratesElem.getAsInt();
                loadedResourceItem.setRatingsGiven(usersRated);
            }
        }

        JsonElement averageJsonElem = rates.get("average");
        if (averageJsonElem != null && !averageJsonElem.isJsonNull()) {
            float averageRating = averageJsonElem.getAsFloat();
            loadedResourceItem.setAverageRating(averageRating);
        }

        JsonElement checksumJsonElem = result.get("md5sum");
        String fileChecksum = null;
        if(checksumJsonElem != null && !checksumJsonElem.isJsonNull()) {
            fileChecksum = checksumJsonElem.getAsString();
        }

        loadedResourceItem.setFileChecksum(fileChecksum);

        if(loadedResourceItem.getClass().isAssignableFrom(resourceItem.getClass())) {
            resourceItem.copyFrom(loadedResourceItem);
            loadedResourceItem = resourceItem;
        }

        PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<>(getMessageId(), getPiwigoMethod(), (T)loadedResourceItem);
        storeResponse(r);
    }

}