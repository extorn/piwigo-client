package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.libs.http.RequestParams;
import delit.piwigoclient.model.piwigo.ResourceItem;

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
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(';');
            }
        }
        return sb.toString();
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp, boolean isCached) throws JSONException {
        piwigoResource.setLinkedAlbums(linkedAlbums);
        BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse r = new BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse(getMessageId(), getPiwigoMethod(), piwigoResource, isCached);
        storeResponse(r);
    }

}