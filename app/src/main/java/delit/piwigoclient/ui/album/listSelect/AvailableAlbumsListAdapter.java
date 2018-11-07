package delit.piwigoclient.ui.album.listSelect;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.list.CustomSelectListAdapter;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.util.DisplayUtils;

import static android.view.View.GONE;

/**
 * Created by gareth on 13/06/17.
 */

public class AvailableAlbumsListAdapter extends CustomSelectListAdapter<AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences, CategoryItemStub> {

    private final CategoryItem parentAlbum;
    private final @IdRes
    int txtViewId;

    public AvailableAlbumsListAdapter(AvailableAlbumsListAdapterPreferences viewPrefs, CategoryItem parentAlbum, @NonNull Context context, @LayoutRes int itemLayout) {
        super(context, viewPrefs, itemLayout);
        this.txtViewId = 0;
        this.parentAlbum = parentAlbum;
    }

    public AvailableAlbumsListAdapter(AvailableAlbumsListAdapterPreferences prefs, CategoryItem parentAlbum, @NonNull Context context) {
        super(context, prefs, getLayoutId(prefs), getTextFieldId(prefs));
        this.txtViewId = getTextFieldId(prefs);
        this.parentAlbum = parentAlbum;
    }

    private static @IdRes
    int getTextFieldId(AvailableAlbumsListAdapterPreferences prefs) {
        return R.id.actionable_list_item_text;
    }

    private static @LayoutRes
    int getLayoutId(AvailableAlbumsListAdapterPreferences prefs) {
        return prefs.isMultiSelectionEnabled() ? R.layout.layout_actionable_simple_triselect_list_item : R.layout.layout_actionable_simple_select_list_item;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return super.isEnabled(position) && getItem(position).isUserSelectable();
    }

    @NonNull
    @Override
    public View getView(int position, View view, @NonNull ViewGroup parent) {
        View v = super.getView(position, view, parent);
        if (v instanceof TextView) {
            v.setPadding(0, 0, 0, 0);
        }
        return v;
    }

    @Override
    protected void setViewData(int position, View aView, boolean isDropdown) {
        try {
            boolean isCustomView = false;
            TextView textView;
            if (aView instanceof TextView) {
                textView = (TextView) aView;
            } else {
                textView = aView.findViewById(txtViewId);
                isCustomView = true;
            }
            final CategoryItemStub item = getItem(position);
            if (parentAlbum.getId() != item.getId() || position > 0) {
                // only display items that are not the root.
                int paddingStart = 0;
                if (getPrefs().isShowHierachy()) {
                    int treeDepth = getDepth(item);
                    paddingStart = 15 * treeDepth;
                }
                int defaultPaddingStartDp = 8;
                paddingStart = DisplayUtils.dpToPx(getContext(), defaultPaddingStartDp + paddingStart);
                textView.setPaddingRelative(paddingStart, textView.getPaddingTop(), textView.getPaddingEnd(), textView.getPaddingBottom());
            }
            if (isCustomView) {
                CompoundButton checkboxTriState = aView.findViewById(R.id.actionable_list_item_checked);
                checkboxTriState.setEnabled(getPrefs().isEnabled());

                CustomImageButton button = aView.findViewById(R.id.actionable_list_item_delete_button);
                button.setVisibility(getPrefs().isAllowItemDeletion() ? View.VISIBLE : GONE);
            }
        } catch (ClassCastException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e("AvailableAlbumsListAd", "You must supply a resource ID for a TextView");
            }
            throw new IllegalStateException(
                    "AvailableAlbumsListAdapter requires the resource ID to be a TextView", e);
        }
    }

    @Override
    protected Long getItemId(CategoryItemStub item) {
        return item.getId();
    }

    private int getDepth(@NonNull CategoryItemStub item) {
        CategoryItemStub thisItem = item;
        int pos = getPosition(thisItem.getId());
        int depth = 0;
        while (pos >= 0) {
            pos = getPosition(thisItem.getParentId());
            if (pos >= 0) {
                depth++;
                thisItem = getItem(pos);
            }
        }
        return depth;
    }

    public static class AvailableAlbumsListAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
        private boolean showHierachy;
        private boolean allowRootAlbumSelection;

        public AvailableAlbumsListAdapterPreferences withShowHierachy() {
            showHierachy = true;
            return this;
        }

        public AvailableAlbumsListAdapterPreferences withRootAlbumSelectionAllowed() {
            allowRootAlbumSelection = true;
            return this;
        }

        public boolean isShowHierachy() {
            return showHierachy;
        }

        @Override
        public Bundle storeToBundle(Bundle parent) {
            Bundle b = new Bundle();
            b.putBoolean("showHierachy", showHierachy);
            parent.putBundle("AvailableAlbumsListAdapterPreferences", b);
            super.storeToBundle(b);
            return parent;
        }

        @Override
        public AvailableAlbumsListAdapterPreferences loadFromBundle(Bundle parent) {
            Bundle b = parent.getBundle("AvailableAlbumsListAdapterPreferences");
            showHierachy = b.getBoolean("showHierachy");
            super.loadFromBundle(b);
            return this;
        }

        public boolean isAllowRootAlbumSelection() {
            return allowRootAlbumSelection;
        }
    }
}
