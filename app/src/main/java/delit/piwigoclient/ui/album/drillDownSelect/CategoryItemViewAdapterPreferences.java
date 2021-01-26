package delit.piwigoclient.ui.album.drillDownSelect;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;

public class CategoryItemViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<CategoryItemViewAdapterPreferences> {

    private String connectionProfileKey;
    private CategoryItemStub initialRoot;
    private HashSet<Long> initialSelection;
    private int columns = 1;

    public CategoryItemViewAdapterPreferences() {
    }

    public CategoryItemViewAdapterPreferences withConnectionProfile(@Nullable String connectionProfileKey) {
        this.connectionProfileKey = connectionProfileKey;
        return this;
    }

    public CategoryItemViewAdapterPreferences withInitialRoot(@NonNull CategoryItemStub initialRoot) {
        this.initialRoot = initialRoot;
        return this;
    }

    public CategoryItemViewAdapterPreferences withInitialSelection(HashSet<Long> initialSelection) {
        this.initialSelection = initialSelection;
        return this;
    }

    public CategoryItemViewAdapterPreferences withColumns(int columns) {
        this.columns = columns;
        return this;
    }

    @Override
    protected String getBundleName() {
        return "CategoryItemViewAdapterPreferences";
    }

    @Override
    protected String writeContentToBundle(Bundle b) {
        super.writeContentToBundle(b);
        b.putInt("columns", columns);
        b.putParcelable("initialRoot", initialRoot);
        BundleUtils.putLongHashSet(b, "initialSelection", initialSelection);
        b.putString("connectionProfileKey", connectionProfileKey);
        return getBundleName();
    }

    @Override
    protected void readContentFromBundle(Bundle b) {
        super.readContentFromBundle(b);
        columns = b.getInt("columns");
        initialRoot = b.getParcelable("initialRoot");
        initialSelection = BundleUtils.getLongHashSet(b, "initialSelection");
        connectionProfileKey = b.getString("connectionProfileKey");
    }

    public CategoryItemStub getInitialRoot() {
        return initialRoot;
    }

    public HashSet<Long> getInitialSelection() {
        return initialSelection;
    }

    public int getColumns() {
        return columns;
    }

    public String getConnectionProfileKey() {
        return connectionProfileKey;
    }
}