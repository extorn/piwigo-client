package delit.piwigoclient.ui.file;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.gms.common.util.ArrayUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.view.FileBreadcrumbsView;
import delit.libs.ui.view.list.MappedArrayAdapter;
import delit.libs.util.CollectionUtils;
import delit.libs.util.LegacyIOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.fragment.LongSelectableSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;

public class LegacyRecyclerViewFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<LegacyFolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String TAG = "RVFolderSelFrg";
    private static final String ACTIVE_FOLDER = "RecyclerViewFolderItemSelectFragment.activeFolder";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewFolderItemSelectFragment.actionStartTime";
    private FileBreadcrumbsView folderPathView;
    private Spinner folderRootFolderSpinner;
    private MappedArrayAdapter<String, File> folderRootsAdapter;
    private long startedActionAtTime;
    private LegacyFolderItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<String, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)
//    private ProgressIndicator progressIndicator;
    private FilterControl fileExtFilters;

    public static LegacyRecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        LegacyRecyclerViewFolderItemSelectFragment fragment = new LegacyRecyclerViewFolderItemSelectFragment();
        fragment.setArguments(LegacyRecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        MediaScanner.instance(getContext()).close();
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId) {
        return LongSelectableSetSelectFragment.buildArgsBundle(prefs, actionId, null);
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.fragment_file_selection_legacy;
    }

    @Override
    protected FolderItemViewAdapterPreferences createEmptyPrefs() {
        return new FolderItemViewAdapterPreferences();
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return isAppInReadOnlyMode(); // Non admin users can alter this since this may be for another profile entirely.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putFile(outState, ACTIVE_FOLDER, getListAdapter().getActiveFolder());
        outState.putLong(STATE_ACTION_START_TIME, startedActionAtTime);
        BundleUtils.writeMap(outState, STATE_LIST_VIEW_STATE, listViewStates);
    }

    @Override
    public void onAttach(Context context) {
        MediaScanner.instance(getContext());
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }
        TextView pageTitle = Objects.requireNonNull(v).findViewById(R.id.page_title);
        if(getViewPrefs().isAllowFileSelection()) {
            pageTitle.setText(R.string.files_selection_title);
        } else if(getViewPrefs().isAllowFolderSelection()) {
            pageTitle.setText(R.string.folder_selection_title);
        }

        if (savedInstanceState != null) {
            startedActionAtTime = savedInstanceState.getLong(STATE_ACTION_START_TIME);
            listViewStates = BundleUtils.readMap(savedInstanceState, STATE_LIST_VIEW_STATE, new LinkedHashMap<>(), Parcelable.class.getClassLoader());
        }

        startedActionAtTime = System.currentTimeMillis();

//        progressIndicator = v.findViewById(R.id.progress_indicator);
//        progressIndicator.setVisibility(View.GONE);

        folderRootFolderSpinner = ViewCompat.requireViewById(v, R.id.folder_root_spinner);
        folderRootFolderSpinner.setOnItemSelectedListener(new RootFolderSelectionListener());

        folderPathView = v.findViewById(R.id.folder_path);
        folderPathView.setNavigationListener(new FileNavigationListener());
        fileExtFilters = v.findViewById(R.id.file_ext_filters);

        navListener = new FolderItemNavigationListener();

        return v;
    }

    private void refreshCurrentFolderView(Context context) {
        getListAdapter().rebuildContentView(context);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindDataToView(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private class FolderItemNavigationListener implements LegacyFolderItemRecyclerViewAdapter.NavigationListener {

        @Override
        public void onPreFolderOpened(File oldFolder, File newFolder) {
            // need to do this before the folder is opened
            //DisplayUtils.postOnUiThread(() -> progressIndicator.showProgressIndicator(-1));
            recordTheCurrentListOfVisibleItemsInState(oldFolder);
        }

        @Override
        public void onPostFolderOpened(File oldFolder, File newFolder) {
            // scroll to the first item in the list.
            getList().scrollToPosition(0);
            buildBreadcrumbs(newFolder);
            getListAdapter().setInitiallySelectedItems(); // adapter needs to be populated for this to work.
            updateListOfFileExtensionsForAllVisibleFiles(getListAdapter().getFileExtsAndMimesInCurrentFolder());
            fileExtFilters.setActiveFilters(getListAdapter().getFileExtsInCurrentFolder());
            fileExtFilters.selectAll();

//            DisplayUtils.postOnUiThread(() -> progressIndicator.hideProgressIndicator());


        }
    }

    private void recordTheCurrentListOfVisibleItemsInState(File folder) {
        if (folder != null) {
            if (listViewStates == null) {
                listViewStates = new LinkedHashMap<>(5);
            }
            listViewStates.put(folder.getAbsolutePath(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
        }
    }

    private void updateListOfFileExtensionsForAllVisibleFiles(Map<String,String> folderContentFileExtToMimeMap) {
        if (getViewPrefs().getVisibleFileTypes() != null) {
            if(fileExtFilters.getAllFilters() == null) {
                fileExtFilters.setAllFilters(new TreeSet<>(getViewPrefs().getVisibleFileTypes()));
            }
        }
        if (fileExtFilters.getAllFilters() != null) {

            SortedSet<String> fileExtsInFolderMatchingMimeTypesWanted = getViewPrefs().getVisibleFileTypesForMimes(folderContentFileExtToMimeMap);
            if (fileExtsInFolderMatchingMimeTypesWanted != null) {
                // add any extra that have come from mime types now visible in this folder.
                fileExtFilters.getAllFilters().addAll(fileExtsInFolderMatchingMimeTypesWanted);
            }
        }
    }

    private void buildBreadcrumbs(File newFolder) {
        folderPathView.populate(newFolder);
    }

    @Override
    public boolean onBackButton() {
        getListAdapter().cancelAnyActiveFolderMediaScan(getContext());
        File parent = getListAdapter().getActiveFolder().getParentFile();
        if (parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().changeFolderViewed(getContext(), parent);
            return true;
        }
    }

    private void applyNewRootsAdapter(MappedArrayAdapter<String, File> mappedArrayAdapter) {
        folderRootsAdapter = mappedArrayAdapter;
        folderRootFolderSpinner.setAdapter(folderRootsAdapter);
    }

    private void bindDataToView(Bundle savedInstanceState) {

        File activeFolder = null;
        if (savedInstanceState != null) {
            activeFolder = BundleUtils.getFile(savedInstanceState, ACTIVE_FOLDER);
        }

        if(getListAdapter() == null) {
            applyNewRootsAdapter(buildFolderRootsAdapter());

            final LegacyFolderItemRecyclerViewAdapter viewAdapter = new LegacyFolderItemRecyclerViewAdapter(requireContext(), navListener, MediaScanner.instance(getContext()), new LegacyFolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem>(), getViewPrefs());

            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }


            // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
            HashSet<Long> currentSelection = getCurrentSelection();

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);

            fileExtFilters.setListener(new FileExtFilterControlListener(getListAdapter()));

            // update the adapter content
            File newFolder = activeFolder;
            if(newFolder == null) {
                newFolder = getViewPrefInitialFolderAsFile();
            }
            if(newFolder != null) {
                File f = newFolder;
                int rootIdx = -1;
                while(f != null && rootIdx < 0) {
                    rootIdx = folderRootsAdapter.getPositionByValue(f);
                    f = f.getParentFile();
                }
                folderRootFolderSpinner.setSelection(rootIdx);
            }
//            viewAdapter.changeFolderViewed(getContext(), newFolder);
            buildBreadcrumbs(getListAdapter().getActiveFolder());

            // select the items to view.
//            viewAdapter.setInitiallySelectedItems();
//            viewAdapter.setSelectedItems(currentSelection);
        }

        // call this here to ensure page reformats if orientation changes for example.
        getViewPrefs().withColumnsOfFiles(OtherPreferences.getFileSelectorColumnsOfFiles(getPrefs(), requireActivity()));
        getViewPrefs().withColumnsOfFolders(OtherPreferences.getFileSelectorColumnsOfFolders(getPrefs(), requireActivity()));

        int colsOnScreen = Math.max(getViewPrefs().getColumnsOfFiles(), getViewPrefs().getColumnsOfFolders());
        if (getViewPrefs().getColumnsOfFiles() % getViewPrefs().getColumnsOfFolders() > 0) {
            colsOnScreen = getViewPrefs().getColumnsOfFiles() * getViewPrefs().getColumnsOfFolders();
        }
        GridLayoutManager layoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        layoutMan.setSpanSizeLookup(new SpanSizeLookup(getListAdapter(), colsOnScreen));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(getListAdapter());
    }

    private File getViewPrefInitialFolderAsFile() {
        try {
            return LegacyIOUtils.getFile(getViewPrefs().getInitialFolder());
        } catch (IOException e) {
            throw new IllegalStateException("Non file Uri somehow got given as initial folder");
        }
    }

    private MappedArrayAdapter<String, File> buildFolderRootsAdapter() {
        List<String> rootLabels = ArrayUtils.toArrayList(new String[]{getString(R.string.folder_root_root), getString(R.string.folder_root_userdata), getString(R.string.folder_extstorage)});

        List<File> rootPaths = ArrayUtils.toArrayList(new File[]{Environment.getRootDirectory(), Environment.getDataDirectory(), Environment.getExternalStorageDirectory()});
        List<String> sdCardPaths = LegacyIOUtils.getSdCardPaths(getContext(), true);
        if(sdCardPaths != null) {
            int extStorageDeviceId = 1;
            for(String path : sdCardPaths) {
                File f = new File(path);
                File[] locations = f.listFiles();
                if(locations != null && locations.length > 0) {
                    for (File location : locations) {
                        File[] folderContent = location.listFiles();
                        if (location.isDirectory() && !rootPaths.contains(location) && folderContent != null && folderContent.length > 0) {
                            rootLabels.add(getLabelForExtStorage(location, getString(R.string.folder_extstorage_device_pattern, extStorageDeviceId)));
                            rootPaths.add(location);
                            extStorageDeviceId++;
                        }
                    }
                } else {
                    File[] folderContent = f.listFiles();
                    if (!rootPaths.contains(f)  && folderContent != null && folderContent.length > 0) {
                        rootLabels.add(getString(R.string.folder_extstorage_device_pattern, extStorageDeviceId));
                        rootPaths.add(f);
                        extStorageDeviceId++;
                    }
                }
            }
        }

        File initialFolder = getViewPrefInitialFolderAsFile();
        if (initialFolder != null && !rootPaths.contains(initialFolder)) {
            rootPaths.add(0, initialFolder);
            rootLabels.add(0, getString(R.string.folder_default));
        }

        rootLabels.add(0, "");
        rootPaths.add(0, null);

        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Context is null while attempting to build list adapter");
        }

        MappedArrayAdapter<String, File> adapter = new MappedArrayAdapter<>(context, android.R.layout.simple_spinner_item, rootLabels, rootPaths);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private String getLabelForExtStorage(File location, String defaultName) {
        File f = location;
        List<String> pathHierarchy = new ArrayList<>();
        while(f != null && !f.getName().equals("mnt")) {
            pathHierarchy.add(f.getName());
            f = f.getParentFile();
        }
        Collections.reverse(pathHierarchy);
        StringBuilder sb = new StringBuilder();
        return CollectionUtils.toCsvList(pathHierarchy, "/");

    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        LegacyFolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem> selectedItems = listAdapter.getSelectedItems();
        Set<Uri> selectedUris = new HashSet<>(selectedItems.size());
        for(LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem selectionItem : selectedItems) {
            selectedUris.add(Uri.fromFile(selectionItem.getFile()));
        }
        if (selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            if(listAdapter.getActiveFolder() != null) {
                selectedItems.add(new LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem(listAdapter.getActiveFolder()));
            }
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFiles(selectedUris));
        listAdapter.cancelAnyActiveFolderMediaScan(getContext());
        // now pop this screen off the stack.
        if (isVisible()) {
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        // heading field not used (missing from layout)
//        headingField.setText(R.string.file_selection_heading);
//        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
    }

    private static class FileExtFilterControlListener implements FilterControl.FilterListener {

        private final LegacyFolderItemRecyclerViewAdapter listAdapter;

        public FileExtFilterControlListener(@NonNull LegacyFolderItemRecyclerViewAdapter adapter) {
            this.listAdapter = adapter;
        }

        @Override
        public void onFilterUnchecked(Context context, String fileExt) {
            SortedSet<String> visibleFileTypes = listAdapter.getAdapterPrefs().getVisibleFileTypes();
            visibleFileTypes.remove(fileExt);
        }

        @Override
        public void onFilterChecked(Context context, String fileExt) {
            SortedSet<String> visibleFileTypes = listAdapter.getAdapterPrefs().getVisibleFileTypes();
            visibleFileTypes.add(fileExt);
        }

        @Override
        public void onFiltersChanged(Context context, boolean filterHidden, boolean filterShown) {
            listAdapter.rebuildContentView(context);
        }
    }

    @Override
    public void onCancelChanges() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis));
        getListAdapter().cancelAnyActiveFolderMediaScan(getContext());
        super.onCancelChanges();
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int spanCount;
        private final LegacyFolderItemRecyclerViewAdapter viewAdapter;

        public SpanSizeLookup(LegacyFolderItemRecyclerViewAdapter viewAdapter, int spanCount) {
            this.viewAdapter = viewAdapter;
            this.spanCount = spanCount;
        }

        @Override
        public int getSpanSize(int position) {
            try {
                int itemType = viewAdapter.getItemViewType(position);
                switch (itemType) {
                    case LegacyFolderItemRecyclerViewAdapter.VIEW_TYPE_FOLDER:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFolders();
                    case LegacyFolderItemRecyclerViewAdapter.VIEW_TYPE_FILE:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
                    default:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
                }
            } catch (IndexOutOfBoundsException e) {
                Logging.recordException(e);
                //TODO why does this occur? How?
                return 1;
            }
        }
    }

    private class RootFolderSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (id > 0) {
                //progressIndicator.showProgressIndicator(-1);
                MappedArrayAdapter<String, File> adapter = (MappedArrayAdapter) parent.getAdapter();
                File newRoot = adapter.getItemValue(position);
                getListAdapter().changeFolderViewed(getContext(), newRoot);
                //progressIndicator.hideProgressIndicator();
            } else {
                // just show the empty view.
                if(fileExtFilters.getAllFilters() != null) {
                    getViewPrefs().withVisibleContent(fileExtFilters.getAllFilters(), getViewPrefs().getFileSortOrder());
                }
                getListAdapter().changeFolderViewed(getContext(), null);
                deselectAllItems();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    class FileNavigationListener implements FileBreadcrumbsView.NavigationListener<File> {

        @Override
        public void onBreadcrumbClicked(File pathItemFile) {
            File activeFolder = getListAdapter().getActiveFolder();
            if (!pathItemFile.equals(activeFolder)) {
                getListAdapter().cancelAnyActiveFolderMediaScan(getContext());
            }
            if(pathItemFile.equals(getListAdapter().getActiveFolder())) {
                getListAdapter().rebuildContentView(getContext());
            } else {
                getListAdapter().changeFolderViewed(getContext(), pathItemFile);
                if (listViewStates != null) {
                    Iterator<Map.Entry<String, Parcelable>> iterator = listViewStates.entrySet().iterator();
                    Map.Entry<String, Parcelable> item;
                    while (iterator.hasNext()) {
                        item = iterator.next();
                        if (item.getKey().equals(pathItemFile.getAbsolutePath())) {
                            if (getList().getLayoutManager() != null) {
                                getList().getLayoutManager().onRestoreInstanceState(item.getValue());
                            } else {
                                Logging.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
                            }
                            iterator.remove();
                            while (iterator.hasNext()) {
                                iterator.next();
                                iterator.remove();
                            }
                        }
                    }
                }
            }
        }
    }
}
