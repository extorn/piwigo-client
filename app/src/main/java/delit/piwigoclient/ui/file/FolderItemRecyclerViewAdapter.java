package delit.piwigoclient.ui.file;

import android.content.Context;
import android.view.View;

import java.io.File;
import java.util.HashSet;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.common.recyclerview.CustomViewHolder;

public class FolderItemRecyclerViewAdapter extends BaseRecyclerViewAdapter<FolderItemViewAdapterPreferences, File, FolderItemRecyclerViewAdapter.FolderItemViewHolder> {

    private File activeFolder;

    public FolderItemRecyclerViewAdapter(File initialFolder, MultiSelectStatusListener multiSelectStatusListener, FolderItemViewAdapterPreferences folderViewPrefs) {
        super(multiSelectStatusListener, folderViewPrefs);
        activeFolder = initialFolder;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public FolderItemViewHolder buildViewHolder(View view, int viewType) {
        return null;
    }

    @Override
    protected File getItemById(Long selectedId) {
        return null;
    }

    @Override
    public int getItemPosition(File item) {
        return 0;
    }

    @Override
    protected void removeItemFromInternalStore(int idxRemoved) {

    }

    @Override
    protected void replaceItemInInternalStore(int idxToReplace, File newItem) {

    }

    @Override
    protected File getItemFromInternalStoreMatching(File item) {
        return null;
    }

    @Override
    protected void addItemToInternalStore(File item) {

    }

    @Override
    public File getItemByPosition(int position) {
        return null;
    }

    @Override
    public boolean isHolderOutOfSync(FolderItemViewHolder holder, File newItem) {
        return false;
    }

    @Override
    public HashSet<Long> getItemsSelectedButNotLoaded() {
        return null;
    }

    @Override
    public int getItemCount() {
        return 0;
    }

    protected static class FolderItemViewHolder extends CustomViewHolder<FolderItemViewAdapterPreferences, File> {

        public FolderItemViewHolder(View view) {
            super(view);
        }

        @Override
        public void fillValues(Context context, File newItem, boolean allowItemDeletion) {

        }

        @Override
        public void cacheViewFieldsAndConfigure() {

        }

        @Override
        public void setChecked(boolean checked) {

        }
    }


}
