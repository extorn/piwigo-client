package delit.piwigoclient.piwigoApi.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONException;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.ResourceItem;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageGetInfoResponseHandler<T extends ResourceItem> extends BaseImageGetInfoResponseHandler<T> {
    public ImageGetInfoResponseHandler(T piwigoResource, String multimediaExtensionList) {
        super(piwigoResource, multimediaExtensionList);
    }
}
