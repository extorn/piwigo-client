package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

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
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONObject result = rsp.getJSONObject("result");
        float rating = (float) result.getDouble("score");
        float averageRating = (float) result.getDouble("average");
        int usersRated = result.getInt("count");
        piwigoResource.setYourRating(rating);
        piwigoResource.setAverageRating(averageRating);
        piwigoResource.setRatingsGiven(usersRated);

        PiwigoResponseBufferingHandler.PiwigoRatingAlteredResponse r = new PiwigoResponseBufferingHandler.PiwigoRatingAlteredResponse(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

}