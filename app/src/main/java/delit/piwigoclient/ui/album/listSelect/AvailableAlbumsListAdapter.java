package delit.piwigoclient.ui.album.listSelect;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.list.CustomSelectListAdapter;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

import static android.view.View.GONE;

/**
 * Created by gareth on 13/06/17.
 */

public class AvailableAlbumsListAdapter extends CustomSelectListAdapter<AlbumSelectionListAdapterPreferences, CategoryItemStub> {

    private final CategoryItem parentAlbum;
    private final @IdRes
    int txtViewId;

    public AvailableAlbumsListAdapter(AlbumSelectionListAdapterPreferences viewPrefs, CategoryItem parentAlbum, @NonNull Context context, @LayoutRes int itemLayout) {
        super(context, viewPrefs, itemLayout);
        this.txtViewId = 0;
        this.parentAlbum = parentAlbum;
    }

    public AvailableAlbumsListAdapter(AlbumSelectionListAdapterPreferences prefs, CategoryItem parentAlbum, @NonNull Context context) {
        super(context, prefs, getLayoutId(prefs), getTextFieldId(prefs));
        this.txtViewId = getTextFieldId(prefs);
        this.parentAlbum = parentAlbum;
    }

    private static @IdRes
    int getTextFieldId(AlbumSelectionListAdapterPreferences prefs) {
        return R.id.actionable_list_item_text;
    }

    private static @LayoutRes
    int getLayoutId(AlbumSelectionListAdapterPreferences prefs) {
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
                if (getPrefs().isFlattenAlbumHierarchy()) {
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

                Button button = aView.findViewById(R.id.actionable_list_item_delete_button);
                button.setVisibility(getPrefs().isAllowItemDeletion() ? View.VISIBLE : GONE);
            }
        } catch (ClassCastException e) {
            Logging.recordException(e);
            Logging.log(Log.ERROR, "AvailableAlbumsListAd", "You must supply a resource ID for a TextView");
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

}
