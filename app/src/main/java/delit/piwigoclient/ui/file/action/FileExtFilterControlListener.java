package delit.piwigoclient.ui.file.action;

import android.content.Context;

import delit.piwigoclient.ui.file.FilterControl;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;

public class FileExtFilterControlListener<LVA extends FolderItemRecyclerViewAdapter<?, ?, FolderItem,?,?>> implements FilterControl.FilterListener {

    private final LVA listAdapter;

    public FileExtFilterControlListener(LVA adapter) {
        this.listAdapter = adapter;
    }

    @Override
    public void onFilterUnchecked(Context context, String fileExt) {
        listAdapter.removeFileTypeToShow(fileExt);
    }

    @Override
    public void onFilterChecked(Context context, String fileExt) {
        listAdapter.addFileTypeToShow(fileExt);
    }

    @Override
    public void onFiltersChanged(Context context, boolean filterHidden, boolean filterShown) {
        listAdapter.refreshContentView(context);
    }
}
