package delit.piwigoclient.ui.permissions;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.button.MaterialCheckboxTriState;
import delit.libs.ui.view.list.MultiSourceListAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

/**
 * Created by gareth on 22/06/17.
 */

public class AlbumSelectionListAdapter extends MultiSourceListAdapter<CategoryItemStub, AlbumSelectionListAdapterPreferences> {

    public AlbumSelectionListAdapter(ArrayList<CategoryItemStub> availableItems, AlbumSelectionListAdapterPreferences adapterPreferences) {
        super(availableItems, adapterPreferences);
    }

    public AlbumSelectionListAdapter(ArrayList<CategoryItemStub> availableItems, HashSet<Long> indirectlySelectedItems, AlbumSelectionListAdapterPreferences adapterPreferences) {
        super(availableItems, indirectlySelectedItems, adapterPreferences);
    }

    @Override
    protected int getItemViewLayoutRes() {
        if(getAdapterPrefs().isShowThumbnails()) {
            return R.layout.layout_actionable_triselect_list_item_icon_and_detail;
        } else if(getAdapterPrefs().isFlattenAlbumHierarchy()){
            return R.layout.layout_list_item_permission;
        } else {
            return R.layout.layout_actionable_triselect_list_item;
        }
    }

    @Override
    protected MaterialCheckboxTriState getAppCompatCheckboxTriState(View view) {
        if(getAdapterPrefs().isShowThumbnails()) {
//            return view.findViewById(R.id.list_item_checked);
            throw new UnsupportedOperationException("cannot currently show thumbnails in this adapter");
        }  else if(getAdapterPrefs().isFlattenAlbumHierarchy()){
            return view.findViewById(R.id.permission_status_icon);
        } else {
            throw new UnsupportedOperationException("cannot currently show hierarchical view in this adapter");
        }
    }

    @Override
    protected void setViewContentForItemDisplay(Context context, View view, CategoryItemStub item, int levelInTreeOfItem) {
        if(getAdapterPrefs().isShowThumbnails()) {
            TextView textView = view.findViewById(R.id.list_item_name);
            if(getAdapterPrefs().isFlattenAlbumHierarchy()) {
                int defaultPaddingStartDp = 8;
                int paddingStartPx = DisplayUtils.dpToPx(context, defaultPaddingStartDp + (levelInTreeOfItem * 15));
                textView.setPaddingRelative(paddingStartPx, textView.getPaddingTop(), textView.getPaddingEnd(), textView.getPaddingBottom());
            } else {
                throw new UnsupportedOperationException("cannot currently show hierarchical view in this adapter");
            }
            textView.setText(item.toString());
            ImageView thumbnailView = view.findViewById(R.id.list_item_icon_thumbnail);
            PicassoLoader loader = new ResizingPicassoLoader(thumbnailView, new ThumbnailLoadListener(), 0, 0);
//            loader.setUriToLoad(item.get);
        } else if(getAdapterPrefs().isFlattenAlbumHierarchy()){
            super.setViewContentForItemDisplay(context, view, item, levelInTreeOfItem);
        }
    }

    @Override
    public long getItemId(CategoryItemStub item) {
        return item.getId();
    }

    @Override
    public Long getItemParentId(CategoryItemStub item) {
        return item.getParentId();
    }

    private static class ThumbnailLoadListener implements PicassoLoader.PictureItemImageLoaderListener {
        @Override
        public void onBeforeImageLoad(PicassoLoader loader) {
            loader.getLoadInto().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageLoaded(PicassoLoader loader, boolean success) {
            loader.getLoadInto().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageUnavailable(PicassoLoader loader, String lastLoadError) {
            loader.getLoadInto().setBackgroundColor(ContextCompat.getColor(loader.getLoadInto().getContext(), R.color.color_scrim_heavy));
        }
    }
}
