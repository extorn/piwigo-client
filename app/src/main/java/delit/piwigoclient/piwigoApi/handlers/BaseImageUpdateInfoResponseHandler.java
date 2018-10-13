package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonElement;

import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Set;

import delit.piwigoclient.model.piwigo.PiwigoJsonResponse;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public abstract class BaseImageUpdateInfoResponseHandler<T extends ResourceItem> extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "UpdateResourceInfoRspHdlr";
    private final T piwigoResource;

    public BaseImageUpdateInfoResponseHandler(T piwigoResource) {
        super("pwg.images.setInfo", TAG);
        this.piwigoResource = piwigoResource;
    }

    public T getPiwigoResource() {
        return piwigoResource;
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
        if (piwigoResource.getCreationDate() != null) {
            SimpleDateFormat dateTimeOriginalFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
            params.put("date_creation", dateTimeOriginalFormat.format(piwigoResource.getCreationDate()));
        }
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
    protected void onPiwigoFailure(PiwigoJsonResponse rsp) throws JSONException {
        /*
         * OK 200
         * <error><item>You are not allowed to delete tags</item></error>
         * <info>Tags updated</info>
         *
         * <error><item>You are not allowed to delete tags</item></error>
         * <info>Tags updated</info>
         *
         * <rsp stat="ok"><script/></rsp> (image not found)
         */
        super.onPiwigoFailure(rsp);
    }

    @Override
    protected void onPiwigoSuccess(JsonElement rsp) throws JSONException {
        piwigoResource.markResourceDetailUpdated();
        PiwigoUpdateResourceInfoResponse<T> r = new PiwigoUpdateResourceInfoResponse<>(getMessageId(), getPiwigoMethod(), piwigoResource);
        storeResponse(r);
    }

    public static class PiwigoUpdateResourceInfoResponse<T extends ResourceItem> extends PiwigoResponseBufferingHandler.PiwigoResourceItemResponse {
        public PiwigoUpdateResourceInfoResponse(long messageId, String piwigoMethod, ResourceItem piwigoResource) {
            super(messageId, piwigoMethod, piwigoResource);
        }
    }
}