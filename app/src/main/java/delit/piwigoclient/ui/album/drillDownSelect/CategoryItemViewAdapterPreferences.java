package delit.piwigoclient.ui.album.drillDownSelect;

import android.os.Bundle;
import androidx.annotation.NonNull;

import java.util.HashSet;

import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;

public class CategoryItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {

    private CategoryItemStub initialRoot;
    private HashSet<Long> initialSelection;
    private int columns = 1;

    public CategoryItemViewAdapterPreferences() {
    }

    public CategoryItemViewAdapterPreferences withInitialRoot(@NonNull CategoryItemStub initialRoot) {
        this.initialRoot = initialRoot;
        return this;
    }

    public CategoryItemViewAdapterPreferences withColumns(int columns) {
        this.columns = columns;
        return this;
    }

    public Bundle storeToBundle(Bundle parent) {
        Bundle b = new Bundle();
        b.putInt("columns", columns);
        b.putParcelable("initialRoot", initialRoot);
        BundleUtils.putLongHashSet(b, "initialSelection", initialSelection);
        parent.putBundle("FolderItemViewAdapterPreferences", b);
        super.storeToBundle(b);
        return parent;
    }

    public CategoryItemViewAdapterPreferences loadFromBundle(Bundle parent) {
        Bundle b = parent.getBundle("FolderItemViewAdapterPreferences");
        columns = b.getInt("columns");
        initialRoot = b.getParcelable("initialRoot");
        initialSelection = BundleUtils.getLongHashSet(b, "initialSelection");
        super.loadFromBundle(b);

        return this;
    }


    public CategoryItemStub getInitialRoot() {
        return initialRoot;
    }

    public HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    public CategoryItemViewAdapterPreferences withInitialSelection(HashSet<Long> initialSelection) {
        this.initialSelection = initialSelection;
        return this;
    }

    public int getColumns() {
        return columns;
    }
}