package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageFindExistingImagesResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "ImagesExistRspHdlr";
    private final Collection<String> checksums;

    public ImageFindExistingImagesResponseHandler(Collection<String> checksums) {
        super("pwg.images.exist", TAG);
        this.checksums = checksums;
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
            params.put("md5sum_list", sb.toString());
        }

        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {

        final HashMap<String, Long> preexistingItems = new HashMap<>();

        Object result = rsp.get("result");
        if(result instanceof JSONObject) {
            JSONObject results = (JSONObject)result;
            Iterator<String> resultsIter = results.keys();
            while (resultsIter.hasNext()) {
                String key = resultsIter.next();
                Long imageId = results.getLong(key);
                preexistingItems.put(key, imageId);
            }
        }
        PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse r = new PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse(getMessageId(), getPiwigoMethod(), preexistingItems);
        storeResponse(r);
    }

}