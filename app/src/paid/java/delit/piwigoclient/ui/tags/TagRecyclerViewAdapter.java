package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.os.Bundle;
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
public class TagRecyclerViewAdapter<LVA extends TagRecyclerViewAdapter<LVA, MSL, VH>,
                                    MSL extends TagRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA,TagRecyclerViewAdapter.TagViewAdapterPreferences,Tag,VH>,
                                    VH extends TagRecyclerViewAdapter.TagViewHolder<VH, LVA, MSL>>
                                    extends IdentifiableListViewAdapter<LVA, TagRecyclerViewAdapter.TagViewAdapterPreferences, Tag, PiwigoTags<Tag>, VH, MSL> {

    public TagRecyclerViewAdapter(Context context, Class<? extends ViewModelContainer> modelType, PiwigoTags<Tag> itemStore, MSL multiSelectStatusListener, TagViewAdapterPreferences prefs) {
        super(context, modelType, itemStore, multiSelectStatusListener, prefs);
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        return (VH)new TagViewHolder(view, this);
    }

    public static class TagViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<TagViewAdapterPreferences>{

        public TagViewAdapterPreferences(){}

        public TagViewAdapterPreferences(Bundle bundle) {
            loadFromBundle(bundle);
        }

        public TagViewAdapterPreferences(boolean allowEditing, boolean allowMultiSelect, boolean initialSelectionLocked) {
            selectable(allowMultiSelect, initialSelectionLocked);
            setAllowItemAddition(true);
            if(!allowEditing) {
                readonly();
            }
        }

        @Override
        protected String getBundleName() {
            return "TagViewAdapterPreferences";
        }
    }

    public static class TagViewHolder<VH extends TagViewHolder<VH,LVA,MSL>, LVA extends TagRecyclerViewAdapter<LVA, MSL,VH>,MSL extends TagRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA,TagRecyclerViewAdapter.TagViewAdapterPreferences,Tag,VH>> extends BaseViewHolder<VH, TagViewAdapterPreferences, Tag, LVA,MSL> {
        private final LVA parentAdapter;

        public TagViewHolder(View view, LVA parentAdapter) {
            super(view);
            this.parentAdapter = parentAdapter;
        }


        public void fillValues(Tag newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(itemView.getContext().getString(R.string.tag_usage_count_pattern), newItem.getPhotoCount()));
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
