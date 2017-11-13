package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class ImageSetLinkedAlbumsResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CopyResourceToAlbumRspHdlr";
    private final T piwigoResource;
    private final HashSet<Long> linkedAlbums;

    public ImageSetLinkedAlbumsResponseHandler(T piwigoResource, HashSet<Long> linkedAlbums) {
        super("pwg.images.setInfo", TAG);
        this.piwigoResource = piwigoResource;
        this.linkedAlbums = linkedAlbums;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        params.put("image_id", String.valueOf(piwigoResource.getId()));
        params.put("categories", getLinkedAlbumList(linkedAlbums));
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
        piwigoResource.setLinkedAlbums(linkedAlbums);
        PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse r = new PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

}