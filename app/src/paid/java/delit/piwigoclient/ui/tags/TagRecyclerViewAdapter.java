package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Tag}
 */
public class TagRecyclerViewAdapter extends IdentifiableListViewAdapter<BaseRecyclerViewAdapterPreferences, Tag, PiwigoTags, TagRecyclerViewAdapter.TagViewHolder> {

    public TagRecyclerViewAdapter(final PiwigoTags tags, MultiSelectStatusListener multiSelectStatusListener, BaseRecyclerViewAdapterPreferences prefs) {
        super(tags, multiSelectStatusListener, prefs);
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

        public void fillValues(Context context, Tag newItem, boolean allowItemDeletion) {
            setItem(newItem);
            getTxtTitle().setText(newItem.getName());
            getDetailsTitle().setText(String.format(context.getString(R.string.tag_usage_count_pattern), newItem.getUsageCount()));
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
