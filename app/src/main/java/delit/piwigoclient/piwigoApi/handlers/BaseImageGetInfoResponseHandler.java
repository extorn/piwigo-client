package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Set;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public abstract class BaseImageGetInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourceInfoRspHdlr";
    private final T resourceItem;
    private final Set<String> multimediaExtensionList;
    private boolean usingPiwigoClientOveride;
    private String piwigoMethodToUse;

    public BaseImageGetInfoResponseHandler(T piwigoResource, Set<String> multimediaExtensionList) {
        super("pwg.images.getInfo", TAG);
        this.resourceItem = piwigoResource;
        this.multimediaExtensionList = multimediaExtensionList;
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.images.getInfo");
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

    public boolean isUsingPiwigoClientOveride() {
        return usingPiwigoClientOveride;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        JsonObject result = rsp.getAsJsonObject();

        AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser resourceParser = buildResourceParser(multimediaExtensionList, usingPiwigoClientOveride);
        
        ResourceItem loadedResourceItem = resourceParser.parseAndProcessResourceData(result);

        if (loadedResourceItem.getClass().isAssignableFrom(resourceItem.getClass())) {
            // don't copy parentage since this isn't retrieved from the server but built locally.
            resourceItem.copyFrom(loadedResourceItem, false);
            loadedResourceItem = resourceItem;
        }

        PiwigoResourceInfoRetrievedResponse<T> r = new PiwigoResourceInfoRetrievedResponse<>(getMessageId(), getPiwigoMethod(), (T) loadedResourceItem, isCached);
        storeResponse(r);
    }

    protected abstract AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser buildResourceParser(Set<String> multimediaExtensionList, boolean usingPiwigoClientOveride);
    
    public static abstract class BaseImageGetInfoResourceParser extends AlbumGetImagesBasicResponseHandler.BasicCategoryImageResourceParser {

        private final boolean usingPiwigoClientOveride;

        public BaseImageGetInfoResourceParser(Set<String> multimediaExtensionList, String basePiwigoUrl, boolean usingPiwigoClientOveride) {
            super(multimediaExtensionList, basePiwigoUrl);
            this.usingPiwigoClientOveride = usingPiwigoClientOveride;
        }

        @Override
        public ResourceItem parseAndProcessResourceData(JsonObject image) throws JSONException {
            ResourceItem resourceItem = super.parseAndProcessResourceData(image);

            byte privacyLevel = image.get("level").getAsByte();
            resourceItem.setPrivacyLevel(privacyLevel);

            JsonObject rates = image.get("rates").getAsJsonObject();
            if (rates != null && !rates.isJsonNull()) {
                JsonElement scoreJsonElem = rates.get("score");
                if (scoreJsonElem != null && !scoreJsonElem.isJsonNull()) {
                    float score = scoreJsonElem.getAsFloat();
                    resourceItem.setScore(score);
                }

                JsonElement ratesElem = rates.get("count");
                if (ratesElem != null && !ratesElem.isJsonNull()) {
                    int usersRated = ratesElem.getAsInt();
                    resourceItem.setRatingsGiven(usersRated);
                }

                JsonElement averageJsonElem = rates.get("average");
                if (averageJsonElem != null && !averageJsonElem.isJsonNull()) {
                    float averageRating = averageJsonElem.getAsFloat();
                    resourceItem.setAverageRating(averageRating);
                }

                if(usingPiwigoClientOveride) {
                    JsonElement yourRateElem = rates.get("my_rating");
                    if (yourRateElem != null && !yourRateElem.isJsonNull()) {
                        float yourRating = yourRateElem.getAsFloat();
                        resourceItem.setMyRating(yourRating);
                    }
                }
            }

            JsonElement checksumJsonElem = image.get("md5sum");
            String fileChecksum = null;
            if (checksumJsonElem != null && !checksumJsonElem.isJsonNull()) {
                fileChecksum = checksumJsonElem.getAsString();
            }

            resourceItem.setFileChecksum(fileChecksum);
            resourceItem.markResourceDetailUpdated();

            // Also available: added_by, rotation, download_counter, lastmodified, date_metadata_update, hit, filesize
            //TODO use rotation???
            
            return resourceItem;
        }
    }

    public static class PiwigoResourceInfoRetrievedResponse<T extends ResourceItem> extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final T resource;

        public PiwigoResourceInfoRetrievedResponse(long messageId, String piwigoMethod, T resource, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.resource = resource;
        }

        public T getResource() {
            return resource;
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}