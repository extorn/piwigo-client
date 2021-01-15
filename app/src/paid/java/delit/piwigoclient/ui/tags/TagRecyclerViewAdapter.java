package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.BaseViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Tag}
 */
//public class AlbumItemRecyclerViewAdapter<LVA extends delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter<LVA,T,MSL,VH,M>, T extends GalleryItem,
// MSL extends delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter,
// VH extends AlbumItemViewHolder<VH, LVA, T, MSL, M>, M extends ResourceContainer<? extends T, GalleryItem>>
// extends IdentifiableListViewAdapter<LVA, AlbumItemRecyclerViewAdapterPreferences, GalleryItem, M, VH, MSL> {
public class TagRecyclerViewAdapter<LVA extends TagRecyclerViewAdapter<LVA, MSL, VH,T>,
                                    MSL extends TagRecyclerViewAdapter.MultiSelectStatusAdapter<T>,
                                    VH extends TagRecyclerViewAdapter.TagViewHolder<VH, LVA, MSL, T>,
                                    T extends Tag>
                                    extends IdentifiableListViewAdapter<LVA, TagRecyclerViewAdapter.TagViewAdapterPreferences, T, PiwigoTags<T>, VH, MSL> {

    public TagRecyclerViewAdapter(Context context, Class<? extends ViewModelContainer> modelType, PiwigoTags<T> itemStore, MSL multiSelectStatusListener, TagViewAdapterPreferences prefs) {
        super(context, modelType, itemStore, multiSelectStatusListener, prefs);
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH)new TagViewHolder(view, this);
    }

    public static class TagViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<TagViewAdapterPreferences>{}

    public static class TagViewHolder<VH extends TagViewHolder<VH,LVA,MSL,T>, LVA extends TagRecyclerViewAdapter<LVA, MSL,VH, T>,MSL extends TagRecyclerViewAdapter.MultiSelectStatusAdapter<T>, T extends Tag> extends BaseViewHolder<VH, TagViewAdapterPreferences, T, LVA,MSL> {
        private final LVA parentAdapter;

        public TagViewHolder(View view, LVA parentAdapter) {
            super(view);
            this.parentAdapter = parentAdapter;
        }

        public void fillValues(T newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(itemView.getContext().getString(R.string.tag_usage_count_pattern), newItem.getUsageCount()));
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(parentAdapter.isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            boolean isChecked = parentAdapter.getSelectedItemIds().contains(newItem.getId());
            boolean alwaysChecked = !parentAdapter.isAllowItemDeselection(getItemId());
            getCheckBox().setChecked(isChecked);
            getCheckBox().setEnabled(parentAdapter.isEnabled());
            getCheckBox().setAlwaysChecked(alwaysChecked);
        }

    }

}
