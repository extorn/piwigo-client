package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        int privacyLevel = result.getInt("level");
        resourceItem.setName(result.getString("name"));
        resourceItem.setPrivacyLevel(privacyLevel);
        JSONObject rates = result.getJSONObject("rates");
        if (!rates.isNull("score")) {
            float rating = (float) rates.getDouble("score");
            resourceItem.setYourRating(rating);
        }
        if (!rates.isNull("average")) {
            float averageRating = (float) rates.getDouble("average");
            resourceItem.setAverageRating(averageRating);
        }

        String fileChecksum = result.getString("md5sum");

        resourceItem.setFileChecksum(fileChecksum);


        // we reload this because when retrieving all images for an album, it returns incorrect results.
        HashSet<Long> linkedAlbums = new HashSet<>();
        JSONArray linkedAlbumsJsonArr = result.getJSONArray("categories");
        for(int j = 0; j < linkedAlbumsJsonArr.length(); j++) {
            JSONObject catJsonObj = linkedAlbumsJsonArr.getJSONObject(j);
            linkedAlbums.add(catJsonObj.getLong("id"));
        }
        resourceItem.setLinkedAlbums(linkedAlbums);

        int usersRated = rates.getInt("count");
        resourceItem.setRatingsGiven(usersRated);

        PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse<T>(getMessageId(), getPiwigoMethod(), resourceItem);
        storeResponse(r);
    }

}