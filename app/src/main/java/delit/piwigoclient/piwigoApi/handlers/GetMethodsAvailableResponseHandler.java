package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

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
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        PiwigoSessionDetails currentCredentials = PiwigoSessionDetails.getInstance(getConnectionPrefs());
        currentCredentials.setMethodsAvailable(parseMethodsAvailable(rsp));
        PiwigoGetMethodsAvailableResponse r = new PiwigoGetMethodsAvailableResponse(getMessageId(), getPiwigoMethod());
        storeResponse(r);
    }

    private Set<String> parseMethodsAvailable(JsonElement rsp) {
        JsonObject result = rsp.getAsJsonObject();
        JsonArray methodsArray = result.get("methods").getAsJsonArray();
        Set<String> methodsAvailable = new HashSet<>(methodsArray.size());
        Iterator<JsonElement> methodItem = methodsArray.iterator();
        while (methodItem.hasNext()) {
            methodsAvailable.add(methodItem.next().getAsString());
        }
        return methodsAvailable;
    }

    public static class PiwigoGetMethodsAvailableResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        public PiwigoGetMethodsAvailableResponse(long messageId, String piwigoMethod) {
            super(messageId, piwigoMethod, true);
        }
    }
}