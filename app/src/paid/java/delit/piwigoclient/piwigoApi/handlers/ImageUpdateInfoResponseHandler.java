package delit.piwigoclient.piwigoApi.handlers;

import java.util.HashSet;
import java.util.Iterator;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;
import delit.libs.http.RequestParams;

/**
 * Created by gareth on 07/04/18.
 */
public class ImageUpdateInfoResponseHandler<T extends ResourceItem> extends BaseImageUpdateInfoResponseHandler<T> {
    private final boolean updateTags;

    /**
     * Dont update tags if you've got temporary tag ids present - they'll be dropped silently!
     * @param piwigoResource
     * @param updateTags
     */
    public ImageUpdateInfoResponseHandler(T piwigoResource, boolean updateTags) {
        super(piwigoResource);
        this.updateTags = updateTags;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = super.buildRequestParameters();
        String tagIdsCsvList = getTagIds(getPiwigoResource().getTags());
        if(tagIdsCsvList != null && updateTags) {
            params.put("tag_ids", tagIdsCsvList);
        }
        return params;
    }

    private String getTagIds(HashSet<Tag> tags) {
        if(tags == null || tags.size() == 0) {
            return "";
        }
        Iterator<Tag> tagIterator = tags.iterator();
        StringBuilder sb = new StringBuilder();
        sb.append(tagIterator.next().getId());
        while(tagIterator.hasNext()) {
            long tagId = tagIterator.next().getId();
            if(tagId >= 0) {
                sb.append(',');
                sb.append(tagId);
            }
        }
        return sb.toString();
    }
}
