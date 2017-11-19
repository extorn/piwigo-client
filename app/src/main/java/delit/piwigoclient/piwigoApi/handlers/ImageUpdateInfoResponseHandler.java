package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageUpdateInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateResourceInfoRspHdlr";
    private final T piwigoResource;

    public ImageUpdateInfoResponseHandler(T piwigoResource) {
        super("pwg.images.setInfo", TAG);
        this.piwigoResource = piwigoResource;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        params.put("name", piwigoResource.getName());
        params.put("comment", piwigoResource.getDescription());
        params.put("level", String.valueOf(piwigoResource.getPrivacyLevel()));
        params.put("single_value_mode", "replace");
        params.put("categories", getLinkedAlbumList(piwigoResource.getLinkedAlbums()));
        params.put("multiple_value_mode", "replace");
        return params;
    }

    private String getLinkedAlbumList(Set<Long> linkedAlbums) {
        StringBuilder sb = new StringBuilder();
        Iterator<Long> iter = linkedAlbums.iterator();
        while(iter.hasNext()) {
            sb.append(iter.next());
            if(iter.hasNext()) {
                sb.append(';');
            }
        }
        return sb.toString();
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse<T> r = new PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse<>(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

}