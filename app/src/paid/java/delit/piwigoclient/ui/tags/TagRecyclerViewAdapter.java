package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
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
public class TagRecyclerViewAdapter extends IdentifiableListViewAdapter<BaseRecyclerViewAdapterPreferences, Tag, PiwigoTags, TagRecyclerViewAdapter.TagViewHolder, BaseRecyclerViewAdapter.MultiSelectStatusListener<Tag>> {

    public TagRecyclerViewAdapter(Context context, Class<? extends ViewModelContainer> modelType, final PiwigoTags tags, MultiSelectStatusListener<Tag> multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(context, modelType, tags, multiSelectStatusListener, prefs);
    }



    @NonNull
    @Override
    public TagViewHolder buildViewHolder(View view, int viewType) {
        return new TagViewHolder(view);
    }

    public class TagViewHolder extends BaseViewHolder<BaseRecyclerViewAdapterPreferences, Tag> {

        public TagViewHolder(View view) {
            super(view);
        }

        public void fillValues(Tag newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(itemView.getContext().getString(R.string.tag_usage_count_pattern), newItem.getUsageCount()));
            if (!allowItemDeletion) {
                getDeleteButton().setVisibility(View.GONE);
            }
            getCheckBox().setVisibility(isMultiSelectionAllowed() ? View.VISIBLE : View.GONE);
            boolean isChecked = getSelectedItemIds().contains(newItem.getId());
            boolean alwaysChecked = !isAllowItemDeselection(getItemId());
            getCheckBox().setChecked(isChecked);
            getCheckBox().setEnabled(isEnabled());
            getCheckBox().setAlwaysChecked(alwaysChecked);
        }
    }

}
