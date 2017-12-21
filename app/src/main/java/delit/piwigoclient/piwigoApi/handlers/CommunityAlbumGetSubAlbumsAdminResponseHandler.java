package delit.piwigoclient.piwigoApi.handlers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbumAdminList;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.http.RequestParams;

public class CommunityAlbumGetSubAlbumsAdminResponseHandler extends AbstractPiwigoWsResponseHandler {

    private static final String TAG = "CommunityGetAlbumsRspHdlr";

    private final CategoryItem parentAlbum;
    private final boolean recursive;
    private final String thumbnailSize;

    public CommunityAlbumGetSubAlbumsAdminResponseHandler(CategoryItem parentAlbum, String thumbnailSize, boolean recursive) {
        super("community.categories.getList", TAG);
        this.parentAlbum = parentAlbum;
        this.recursive = recursive;
        this.thumbnailSize = thumbnailSize;
    }

    @Override
    public RequestParams buildRequestParameters() {
        RequestParams params = new RequestParams();
        params.put("method", getPiwigoMethod());
        if (!parentAlbum.isRoot()) {
            params.put("cat_id", String.valueOf(parentAlbum.getId()));
        }
        if(thumbnailSize != null) {
            params.put("thumbnail_size", thumbnailSize);
        }
        params.put("recursive", String.valueOf(recursive));
        return params;
    }

    @Override
    protected void onPiwigoSuccess(JSONObject rsp) throws JSONException {
        JSONArray categories = rsp.getJSONObject("result").getJSONArray("categories");
        ArrayList<CategoryItem> availableGalleries = new ArrayList<>(categories.length());

        SimpleDateFormat piwigoDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = (JSONObject) categories.get(i);
            long id = category.getLong("id");
            String name = category.getString("name");
            long photos = category.getLong("nb_images");
            long totalPhotos = category.getLong("total_nb_images");
            long subCategories = category.getLong("nb_categories");
            String description = category.getString("comment");
            boolean isPublic = "public".equals(category.getString("status"));
            String dateLastAlteredStr = category.getString("max_date_last");
            String thumbnail = null;
            Long representativePictureId = null;
            try {
                representativePictureId = category.getLong("representative_picture_id");
            } catch(JSONException e) {
                // no representative picture ID
            }

            Date dateLastAltered = null;
            try {
                if ("null".equals(dateLastAlteredStr)) {
                    dateLastAltered = new Date(0);
                } else {
                    dateLastAltered = piwigoDateFormat.parse(dateLastAlteredStr);
                }
            } catch (ParseException e) {
                throw new JSONException("Unable to parse date " + dateLastAlteredStr);
            }

            CategoryItem item = new CategoryItem(id, name, description, !isPublic, dateLastAltered, photos, totalPhotos, subCategories, thumbnail);
            item.setRepresentativePictureId(representativePictureId);

            if (item.getId() == parentAlbum.getId()) {
                // this album is the one we requested the load of sub albums for.
                item.setParentageChain(parentAlbum.getParentageChain());
            } else {
                item.setParentageChain(parentAlbum.getParentageChain(), parentAlbum.getId());
            }
            availableGalleries.add(item);
        }

        //TODO now go and get all the thumbnail urls for those albums.... or maybe get it on demand later?

        PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse r = new PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse(getMessageId(), getPiwigoMethod(), availableGalleries);
        storeResponse(r);
    }

}
