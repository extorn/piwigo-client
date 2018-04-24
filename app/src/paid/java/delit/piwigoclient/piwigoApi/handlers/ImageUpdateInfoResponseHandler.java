package delit.piwigoclient.piwigoApi.handlers;

import java.util.HashSet;
import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.http.RequestParams;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageUpdateInfoResponseHandler<T extends ResourceItem> extends BaseImageUpdateInfoResponseHandler<T> {
    public ImageUpdateInfoResponseHandler(T piwigoResource) {
        super(piwigoResource);
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = super.buildRequestParameters();
        String tagIdsCsvList = getTagIds(getPiwigoResource().getTags());
        if(tagIdsCsvList != null) {
            params.put("tag_ids", tagIdsCsvList);
        }
        return params;
    }

    private String getTagIds(HashSet<Tag> tags) {
        if(tags.size() == 0) {
            return "";
        }
        Iterator<Tag> tagIterator = tags.iterator();
        StringBuilder sb = new StringBuilder();
        sb.append(tagIterator.next().getId());
        while(tagIterator.hasNext()) {
            sb.append(',');
            sb.append(tagIterator.next().getId());
        }
        return sb.toString();
    }
}
