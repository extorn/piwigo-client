package delit.piwigoclient.ui.tags;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.ui.common.recyclerview.BaseViewHolder;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Tag}
 */
public class TagRecyclerViewAdapter extends IdentifiableListViewAdapter<Tag, PiwigoTags, TagRecyclerViewAdapter.TagViewHolder> {

    public TagRecyclerViewAdapter(final PiwigoTags tags, MultiSelectStatusListener multiSelectStatusListener, boolean captureActionClicks) {
        super(tags, multiSelectStatusListener, captureActionClicks);
        setAllowItemDeletion(false);
    }



    @Override
    public TagViewHolder buildViewHolder(View view) {
        return new TagViewHolder(view);
    }

    public class TagViewHolder extends BaseViewHolder<Tag> {

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
            getCheckBox().setVisibility(isCaptureActionClicks() ? View.VISIBLE : View.GONE);
            getCheckBox().setChecked(getSelectedItemIds().contains(newItem.getId()));
            getCheckBox().setEnabled(isEnabled());
            getCheckBox().setAlwaysChecked(!isAllowItemDeselection(getItemId()));
        }
    }

}
