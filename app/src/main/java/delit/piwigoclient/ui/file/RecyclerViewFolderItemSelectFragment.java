package delit.piwigoclient.ui.file;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.LongSetSelectFragment;
import delit.piwigoclient.ui.common.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.events.trackable.FileListSelectionCompleteEvent;

public class RecyclerViewFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> {
    private static final String ACTIVE_FOLDER = "activeFolder";
    private FolderContent folderModel = new FolderContent("File", 100);
    private File activeFolder;


    public static RecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId, File initialFolder) {
        RecyclerViewFolderItemSelectFragment fragment = new RecyclerViewFolderItemSelectFragment();
        fragment.setArguments(RecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId, initialFolder));
        return fragment;
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId, File initialFolder) {
        Bundle args = LongSetSelectFragment.buildArgsBundle(prefs, actionId, null);
        args.putSerializable(ACTIVE_FOLDER, initialFolder.getAbsolutePath());
        return args;
    }

    @Override
    protected FolderItemViewAdapterPreferences createEmptyPrefs() {
        return new FolderItemViewAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ACTIVE_FOLDER, activeFolder);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        boolean editingEnabled = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        if(!editingEnabled) {
            getViewPrefs().readonly();
        }

        final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(activeFolder, new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<File>() {
//            @Override
//            public void onMultiSelectStatusChanged(boolean multiSelectEnabled) {
//
//            }
//
//            @Override
//            public void onItemSelectionCountChanged(int size) {
//
//            }
//
//            @Override
//            public void onItemDeleteRequested(File f) {
//
//            }
//
//            @Override
//            public void onItemClick(File item) {
//
//            }

            @Override
            public <A extends BaseRecyclerViewAdapter> void onItemLongClick(A adapter, File item) {
                if(item.isDirectory() && getViewPrefs().isSelectFolders()) {
                    adapter.setItemSelected(adapter.getItemId(adapter.getItemPosition(item)));
                }
            }
        }, getViewPrefs());
        if(!viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }

        setListAdapter(viewAdapter);

        RecyclerView.LayoutManager layoutMan = new LinearLayoutManager(getContext());
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(viewAdapter);

        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(layoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = folderModel.getPagesLoaded();
                if (pageToLoad == 0 || folderModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadFolderPage();
            }
        };
        scrollListener.configure(folderModel.getPagesLoaded(), folderModel.getItemCount());
        getList().addOnScrollListener(scrollListener);

        if (savedInstanceState != null) {
            activeFolder = (File) savedInstanceState.getSerializable(ACTIVE_FOLDER);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.file_selection_heading);
        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(folderModel.getPagesLoaded() == 0) {
            getListAdapter().notifyDataSetChanged();
            loadFolderPage();
        }
    }

    private void loadFolderPage() {
        List<File> folderContent = Arrays.asList(activeFolder.listFiles());
        int firstIdxAdded = folderModel.addItemPage(0, folderContent.size(), folderContent);
        HashSet<Long> selectedItemIds = getListAdapter().getSelectedItemIds();
        for (Long selectedItemId : selectedItemIds) {
            getListAdapter().setItemSelected(selectedItemId);
        }
        getListAdapter().notifyItemRangeInserted(firstIdxAdded, folderContent.size());
        onListItemLoadSuccess();
        setAppropriateComponentState();
    }

    @Override
    protected void populateListWithItems() {
        loadFolderPage();
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<File> selectedItems = listAdapter.getSelectedItems();
        EventBus.getDefault().post(new FileListSelectionCompleteEvent(getActionId(), new ArrayList<>(selectedItems)));
        // now pop this screen off the stack.
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

}
