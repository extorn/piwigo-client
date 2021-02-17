package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

public class GetMethodsAvailableResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetMethodsRspHdlr";

    public GetMethodsAvailableResponseHandler() {
        super("reflection.getMethodList", TAG);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        PiwigoSessionDetails currentCredentials = getPiwigoSessionDetails();
        currentCredentials.setMethodsAvailable(parseMethodsAvailable(rsp));
        PiwigoGetMethodsAvailableResponse r = new PiwigoGetMethodsAvailableResponse(getMessageId(), getPiwigoMethod(), isCached);
        storeResponse(r);
    }

    private Set<String> parseMethodsAvailable(JsonElement rsp) {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray methodsArray = result.get("methods").getAsJsonArray();
        Set<String> methodsAvailable = new HashSet<>(methodsArray.size());
        for (JsonElement jsonElement : methodsArray) {
            methodsAvailable.add(jsonElement.getAsString());
        }
        return methodsAvailable;
    }

    public static class PiwigoGetMethodsAvailableResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        public PiwigoGetMethodsAvailableResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
        }
    }

    public boolean isUseHttpGet() {
        return true;
    }
}