package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageAlterRatingResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "AlterResRatingRspHdlr";
    private final ResourceItem piwigoResource;
    private final float newRating;

    public ImageAlterRatingResponseHandler(ResourceItem piwigoResource, float newRating) {
        super("pwg.images.rate", TAG);
        this.piwigoResource = piwigoResource;
        this.newRating = newRating;
    }

    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        params.put("rate", String.valueOf(newRating));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();
        float score = (float) result.get("score").getAsDouble();
        float averageRating = (float) result.get("average").getAsDouble();
        int usersRated = result.get("count").getAsInt();
        piwigoResource.setMyRating(newRating);
        piwigoResource.setAverageRating(averageRating);
        piwigoResource.setScore(score);
        piwigoResource.setRatingsGiven(usersRated);

        PiwigoRatingAlteredResponse r = new PiwigoRatingAlteredResponse(getMessageId(), getPiwigoMethod(), piwigoResource, isCached);
        storeResponse(r);
    }

    public static class PiwigoRatingAlteredResponse extends PiwigoResponseBufferingHandler.PiwigoResourceItemResponse {
        public PiwigoRatingAlteredResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource, boolean isCached) {
            super(messageId, piwigoMethod, piwigoResource, isCached);
        }
    }
}