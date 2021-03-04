package delit.piwigoclient.ui.album.drillDownSelect.item;

import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.business.PicassoLoader;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;

public abstract class CategoryItemViewHolder<VH extends CategoryItemViewHolder<VH,LVA,MSL>, LVA extends CategoryItemRecyclerViewAdapter<LVA,MSL,VH>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA, CategoryItemViewAdapterPreferences, CategoryItem,VH>> extends BaseViewHolder<VH,CategoryItemViewAdapterPreferences, CategoryItem, LVA,MSL> implements PicassoLoader.PictureItemImageLoaderListener {
        private ImageView iconView;
        private ResizingPicassoLoader iconViewLoader;

        public CategoryItemViewHolder(View view) {
            super(view);
        }

        public ImageView getIconView() {
            return iconView;
        }

        public ResizingPicassoLoader geticonViewLoader() {
            return iconViewLoader;
        }

        @Override
        public void redisplayOldValues(CategoryItem newItem, boolean allowItemDeletion) {
            if (!iconViewLoader.isImageLoading() && !iconViewLoader.isImageLoaded()) {
                iconViewLoader.load();
            }
        }

        public abstract void fillValues(CategoryItem newItem, boolean allowItemDeletion);

        @Override
        public void cacheViewFieldsAndConfigure(CategoryItemViewAdapterPreferences adapterPrefs) {

            super.cacheViewFieldsAndConfigure(adapterPrefs);

            iconView = itemView.findViewById(R.id.list_item_icon_thumbnail);
            iconView.setContentDescription("cat thumb");
            iconViewLoader = new ResizingPicassoLoader(getIconView(), this, 0, 0);
        }

        @Override
        public void onBeforeImageLoad(PicassoLoader loader) {
            getIconView().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageLoaded(PicassoLoader loader, boolean success) {
            getIconView().setBackgroundColor(Color.TRANSPARENT);
        }

        @Override
        public void onImageUnavailable(PicassoLoader loader, String lastLoadError) {
            getIconView().setBackgroundColor(ContextCompat.getColor(getIconView().getContext(), R.color.color_scrim_heavy));
        }

        @Override
        public boolean isDirty(CategoryItem newItem) {
        if(!super.isDirty(newItem)) {
            return getItem() != null && newItem != null && getItem().isAdminCopy() != newItem.isAdminCopy();
        }
        return true;
    }
}
