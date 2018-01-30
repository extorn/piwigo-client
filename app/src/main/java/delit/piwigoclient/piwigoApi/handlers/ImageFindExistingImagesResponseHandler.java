package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageFindExistingImagesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "ImagesExistRspHdlr";
    private final Collection<String> checksums;
    private boolean nameUnique;

    public ImageFindExistingImagesResponseHandler(Collection<String> checksums, boolean nameUnique) {
        super("pwg.images.exist", TAG);
        this.checksums = checksums;
        this.nameUnique = nameUnique;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();

        params.put("method", getPiwigoMethod());

        if (checksums != null) {
            StringBuilder sb = new StringBuilder();
            Iterator<String> iter = checksums.iterator();
            while (iter.hasNext()) {
                sb.append(iter.next());
                if (iter.hasNext()) {
                    sb.append(',');
                }
            }
            if(nameUnique) {
                params.put("filename_list", sb.toString());
            } else {
                params.put("md5sum_list", sb.toString());
            }
        }

        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        final HashMap<String, Long> preexistingItems = new HashMap<>();

        if(rsp instanceof JsonObject) {
            JsonObject results = rsp.getAsJsonObject();
            Iterator<String> resultsIter = results.keySet().iterator();
            while (resultsIter.hasNext()) {
                String key = resultsIter.next();
                JsonElement imageKeyJsonElement = results.get(key);
                if(imageKeyJsonElement != null && !imageKeyJsonElement.isJsonNull()) {
                    long imageId = imageKeyJsonElement.getAsLong();
                    preexistingItems.put(key, imageId);
                }
            }
        }
        PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse r = new PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse(getMessageId(), getPiwigoMethod(), preexistingItems);
        storeResponse(r);
    }

}