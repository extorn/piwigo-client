package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONException;

import java.util.ArrayList;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.piwigoApi.http.RequestHandle;
import delit.piwigoclient.piwigoApi.http.RequestParams;
import delit.piwigoclient.util.VersionUtils;

public class ImagesListOrphansResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "GetResourcesRspHdlr";
    private final int pageSize;
    private final int page;
    private boolean retrieveFullList;
    private ArrayList<Long> resultContainer = null;

    private ImagesListOrphansResponseHandler(int page, int pageSize, ArrayList<Long> resultContainer) {
        this(page, pageSize);
        this.resultContainer = resultContainer;
    }

    public ImagesListOrphansResponseHandler(int page, int pageSize) {
        super("pwg.images.listOrphans", TAG);
        this.page = page;
        this.pageSize = pageSize;
    }

    public ImagesListOrphansResponseHandler(int pageSize) {
        this(0, pageSize);
        this.retrieveFullList = true;
    }

    @Override
    public String getPiwigoMethod() {
        return getPiwigoMethodOverrideIfPossible("piwigo_client.images.getOrphans");
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("page", String.valueOf(page));
        params.put("per_page", String.valueOf(pageSize));
        return params;
    }

    @Override
    public RequestHandle runCall(CachingAsyncHttpClient client, AsyncHttpResponseHandler handler) {
        if(retrieveFullList) {
            runSequenceOfNestedHandlers();
            return null;
        } else {
            return super.runCall(client, handler);
        }
    }

    @Override
    public boolean isMethodAvailable(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        boolean available = super.isMethodAvailable(context, connectionPrefs);
        if(available && "piwigo_client.images.getOrphans".equals(getPiwigoMethod())) {
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            int[] pluginVersion = VersionUtils.parseVersionString(sessionDetails.getPiwigoClientPluginVersion());
            boolean versionSupported = VersionUtils.versionExceeds(new int[]{1,0,5}, pluginVersion);
            available = versionSupported;
        }
        return available;
    }

    private void runSequenceOfNestedHandlers() {
        int nextPage = 0;
        resultContainer = new ArrayList<Long>(pageSize);
        PiwigoGetOrphansResponse nestedResponse = null;
        while(nextPage >= 0) {
            ImagesListOrphansResponseHandler nestedHandler = new ImagesListOrphansResponseHandler(nextPage, pageSize, resultContainer);
            nestedHandler.invokeAndWait(getContext(), getConnectionPrefs());
            nestedResponse = null;
            if (nestedHandler.isSuccess()) {
                nestedResponse = (PiwigoGetOrphansResponse) nestedHandler.getResponse();
                if (nestedResponse.getTotalCount() > nestedResponse.getResources().size()) {
                    nextPage = nestedResponse.getPage() + 1;
                    resultContainer.ensureCapacity(nestedResponse.getTotalCount());
                }
            } else {
                reportNestedFailure(nestedHandler);
                nextPage = -1;
            }
        }

        setError(getNestedFailure());

        if(nestedResponse != null) {
            storeResponse(new PiwigoGetOrphansResponse(getMessageId(), getPiwigoMethod(), 0, resultContainer.size(), resultContainer.size(), resultContainer));
        }
        // this is needed because we aren't calling the onSuccess method.
        resetFailureAsASuccess();
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {

        JsonObject result = rsp.getAsJsonObject();

        JsonObject pagingObj = result.get("paging").getAsJsonObject();
        int page = pagingObj.get("page").getAsInt();
        int pageSize = pagingObj.get("per_page").getAsInt();
        int count = pagingObj.get("count").getAsInt();
        int totalCount = pagingObj.get("total_count").getAsInt();
        JsonArray images = result.get("images").getAsJsonArray();

        if(resultContainer == null) {
            resultContainer = new ArrayList<>(count);
        }

        for (int i = 0; i < images.size(); i++) {
            JsonObject userObj = images.get(i).getAsJsonObject();
            long id = userObj.get("id").getAsLong();
            resultContainer.add(id);
        }

        PiwigoGetOrphansResponse r = new PiwigoGetOrphansResponse(getMessageId(), getPiwigoMethod(), page, pageSize, totalCount, resultContainer);
        storeResponse(r);
    }

    public static class PiwigoGetOrphansResponse extends PiwigoResponseBufferingHandler.BasePiwigoResponse {

        private final int page;
        private final int pageSize;
        private final int totalCount;
        private final ArrayList<Long> resourceIds;

        public PiwigoGetOrphansResponse(long messageId, String piwigoMethod, int page, int pageSize, int totalCount, ArrayList<Long> resourceIds) {
            super(messageId, piwigoMethod, true);
            this.page = page;
            this.pageSize = pageSize;
            this.totalCount = totalCount;
            this.resourceIds = resourceIds;
        }

        public int getPage() {
            return page;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getPageSize() {
            return pageSize;
        }

        public ArrayList<Long> getResources() {
            return resourceIds;
        }
    }
}
