package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public abstract class BaseImageGetInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourceInfoRspHdlr";
    private final T resourceItem;
    private final String multimediaExtensionList;
    private boolean usingPiwigoClientOveride;

    public BaseImageGetInfoResponseHandler(T piwigoResource, String multimediaExtensionList) {
        super("pwg.images.getInfo", TAG);
        this.resourceItem = piwigoResource;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public String getPiwigoMethod() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        if(sessionDetails.isMethodAvailable("piwigo_client.images.getInfo")) {
            usingPiwigoClientOveride = true;
            return "piwigo_client.images.getInfo";
        } else {
            return super.getPiwigoMethod();
        }
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

    protected ResourceItem parseResourceItemFromJson(JsonObject resourceItemElem) throws JSONException {
        ImagesGetResponseHandler.ResourceParser resourceParser = new ImagesGetResponseHandler.ResourceParser(getContext(), multimediaExtensionList);

        ResourceItem loadedResourceItem = resourceParser.parseAndProcessResourceData(resourceItemElem);

        int privacyLevel = resourceItemElem.get("level").getAsInt();
        loadedResourceItem.setPrivacyLevel(privacyLevel);

        JsonObject rates = resourceItemElem.get("rates").getAsJsonObject();
        if (rates != null && !rates.isJsonNull()) {
            JsonElement scoreJsonElem = rates.get("score");
            if (scoreJsonElem != null && !scoreJsonElem.isJsonNull()) {
                float score = scoreJsonElem.getAsFloat();
                loadedResourceItem.setScore(score);
            }

            JsonElement ratesElem = rates.get("count");
            if (ratesElem != null && !ratesElem.isJsonNull()) {
                int usersRated = ratesElem.getAsInt();
                loadedResourceItem.setRatingsGiven(usersRated);
            }
        }

        JsonElement averageJsonElem = rates.get("average");
        if (averageJsonElem != null && !averageJsonElem.isJsonNull()) {
            float averageRating = averageJsonElem.getAsFloat();
            loadedResourceItem.setAverageRating(averageRating);
        }

        if(usingPiwigoClientOveride) {
            JsonElement yourRateElem = rates.get("my_rating");
            if (yourRateElem != null && !yourRateElem.isJsonNull()) {
                float yourRating = yourRateElem.getAsFloat();
                loadedResourceItem.setMyRating(yourRating);
            }
        }

        JsonElement checksumJsonElem = resourceItemElem.get("md5sum");
        String fileChecksum = null;
        if (checksumJsonElem != null && !checksumJsonElem.isJsonNull()) {
            fileChecksum = checksumJsonElem.getAsString();
        }

        loadedResourceItem.setFileChecksum(fileChecksum);

        if (loadedResourceItem.getClass().isAssignableFrom(resourceItem.getClass())) {
            // don't copy parentage since this isn't retrieved from the server but built locally.
            resourceItem.copyFrom(loadedResourceItem, false);
            loadedResourceItem = resourceItem;
        }

        return loadedResourceItem;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        ResourceItem loadedResourceItem = parseResourceItemFromJson(result);

        PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<>(getMessageId(), getPiwigoMethod(), (T) loadedResourceItem);
        storeResponse(r);
    }

}