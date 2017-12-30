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

    public ImageGetInfoResponseHandler(T piwigoResource) {
        super("pwg.images.getInfo", TAG);
        this.resourceItem = piwigoResource;
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
        int privacyLevel = result.get("level").getAsInt();
        resourceItem.setName(result.get("name").getAsString());
        resourceItem.setPrivacyLevel(privacyLevel);
        JsonObject rates = result.get("rates").getAsJsonObject();
        JsonElement scoreJsonElem = rates.get("score");
        if (scoreJsonElem != null && !scoreJsonElem.isJsonNull()) {
            float rating = scoreJsonElem.getAsFloat();
            resourceItem.setYourRating(rating);
        }
        JsonElement averageJsonElem = rates.get("average");
        if (averageJsonElem != null && !averageJsonElem.isJsonNull()) {
            float averageRating = averageJsonElem.getAsFloat();
            resourceItem.setAverageRating(averageRating);
        }

        String fileChecksum = result.get("md5sum").getAsString();

        resourceItem.setFileChecksum(fileChecksum);


        // we reload this because when retrieving all images for an album, it returns incorrect results.
        HashSet<Long> linkedAlbums = new HashSet<>();
        JsonArray linkedAlbumsJsonArr = result.get("categories").getAsJsonArray();
        for(int j = 0; j < linkedAlbumsJsonArr.size(); j++) {
            JsonObject catJsonObj = linkedAlbumsJsonArr.get(j).getAsJsonObject();
            linkedAlbums.add(catJsonObj.get("id").getAsLong());
        }
        resourceItem.setLinkedAlbums(linkedAlbums);

        int usersRated = rates.get("count").getAsInt();
        resourceItem.setRatingsGiven(usersRated);

        PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<>(getMessageId(), getPiwigoMethod(), resourceItem);
        storeResponse(r);
    }

}