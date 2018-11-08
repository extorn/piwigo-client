package delit.piwigoclient.ui.file;

import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.util.ArrayUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.FlowLayout;
import delit.piwigoclient.ui.common.fragment.LongSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.list.MappedArrayAdapter;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.util.IOUtils;

import static android.view.View.NO_ID;

public class RecyclerViewFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String ACTIVE_FOLDER = "RecyclerViewFolderItemSelectFragment.activeFolder";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewFolderItemSelectFragment.actionStartTime";
    private FlowLayout folderPathView;
    private Spinner spinner;
    private MappedArrayAdapter<String, File> folderRootsAdapter;
    private long startedActionAtTime;
    private FolderItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<String, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)

    public static RecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewFolderItemSelectFragment fragment = new RecyclerViewFolderItemSelectFragment();
        fragment.setArguments(RecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId) {
        Bundle args = LongSetSelectFragment.buildArgsBundle(prefs, actionId, null);
        return args;
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.fragment_file_selection_recycler_list;
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
        outState.putSerializable(STATE_LIST_VIEW_STATE, listViewStates);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        if (savedInstanceState != null) {
            startedActionAtTime = savedInstanceState.getLong(STATE_ACTION_START_TIME);
            listViewStates = BundleUtils.getSerializable(savedInstanceState, STATE_LIST_VIEW_STATE, LinkedHashMap.class);
        }

        startedActionAtTime = System.currentTimeMillis();

        folderRootsAdapter = buildFolderRootsAdapter();

        spinner = v.findViewById(R.id.folder_root_spinner);
        spinner.setAdapter(folderRootsAdapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (id > 0) {
                    MappedArrayAdapter<String, File> adapter = (MappedArrayAdapter) parent.getAdapter();
                    File newRoot = adapter.getItemValue(position);
                    getListAdapter().updateContent(newRoot);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        folderPathView = v.findViewById(R.id.folder_path);

        navListener = new FolderItemRecyclerViewAdapter.NavigationListener() {

            @Override
            public void onFolderOpened(File oldFolder, File newFolder) {

                if(oldFolder != null) {
                    if (listViewStates == null) {
                        listViewStates = new LinkedHashMap<>(5);
                    }
                    listViewStates.put(oldFolder.getAbsolutePath(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
                }
                getList().scrollToPosition(0);

                buildBreadcrumbs(newFolder);
            }
        };

        bindDataToView(savedInstanceState);
        buildBreadcrumbs(getListAdapter().getActiveFolder());


        return v;
    }

    private void buildBreadcrumbs(File newFolder) {
        String item = folderRootsAdapter.getItemByValue(newFolder);
        if (item == null) {
            // reset the selection
            if (spinner.getSelectedItemId() >= 0) {
                spinner.setAdapter(folderRootsAdapter);
            }
        } else {
            spinner.setSelection(folderRootsAdapter.getPosition(item), false);
        }

        File f = newFolder;
        folderPathView.removeAllViews();


        ArrayList<File> pathItems = new ArrayList<>();
        while (!f.getName().isEmpty()) {
            pathItems.add(0, f);
            f = f.getParentFile();
        }
        TextView pathItem = null;
        int idx = 0;
        for(final File pathItemFile : pathItems) {
            idx++;
            int lastId = NO_ID;
            if(pathItem != null) {
                lastId = pathItem.getId();
            }
            pathItem = new TextView(getContext());
            pathItem.setId(View.generateViewId());
            TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_AppCompat_Body2_Clickable);
            pathItem.setText(pathItemFile.getName());
            folderPathView.addView(pathItem);

            pathItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView tv = (TextView) v;
                    getListAdapter().updateContent(pathItemFile);
                    Iterator<Map.Entry<String, Parcelable>> iter = listViewStates.entrySet().iterator();
                    Map.Entry<String, Parcelable> item;
                    while(iter.hasNext()) {
                        item = iter.next();
                        if(item.getKey() == pathItemFile.getAbsolutePath()) {
                            getList().getLayoutManager().onRestoreInstanceState(item.getValue());
                            iter.remove();
                            while(iter.hasNext()) {
                                iter.next();
                                iter.remove();
                            }
                        }
                    }
                }
            });

            if(idx < pathItems.size()) {
                TextView pathItemSeperator = new TextView(getContext());
                TextViewCompat.setTextAppearance(pathItemSeperator, R.style.TextAppearance_AppCompat_Body2);
                pathItemSeperator.setText("/");
                pathItemSeperator.setId(View.generateViewId());
                folderPathView.addView(pathItemSeperator);
                pathItem = pathItemSeperator;
            }
        }
    }

    private void bindDataToView(Bundle savedInstanceState) {

        File activeFolder = null;
        if (savedInstanceState != null) {
            activeFolder = BundleUtils.getFile(savedInstanceState, ACTIVE_FOLDER);
        }

        if(getListAdapter() == null) {

            final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(navListener, new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<File>(), getViewPrefs());
            if (activeFolder != null) {
                viewAdapter.setActiveFolder(activeFolder);
            } else {
                // ??? can this ever occur?
            }
            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }

            viewAdapter.setInitiallySelectedItems();

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);
        }

        int colsOnScreen = Math.max(getViewPrefs().getColumnsOfFiles(), getViewPrefs().getColumnsOfFolders());
        if (getViewPrefs().getColumnsOfFiles() % getViewPrefs().getColumnsOfFolders() > 0) {
            colsOnScreen = getViewPrefs().getColumnsOfFiles() * getViewPrefs().getColumnsOfFolders();
        }
        GridLayoutManager layoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        layoutMan.setSpanSizeLookup(new SpanSizeLookup(getListAdapter(), colsOnScreen));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(getListAdapter());
    }

    @Override
    public boolean onBackButton() {
        File parent = getListAdapter().getActiveFolder().getParentFile();
        if (parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().updateContent(parent);
            return true;
        }
    }

    private MappedArrayAdapter<String, File> buildFolderRootsAdapter() {
        List<String> rootLabels = ArrayUtils.toArrayList(new String[]{getString(R.string.folder_root_root), getString(R.string.folder_root_userdata), getString(R.string.folder_extstorage)});

        List<File> rootPaths = ArrayUtils.toArrayList(new File[]{Environment.getRootDirectory(), Environment.getDataDirectory(), Environment.getExternalStorageDirectory()});
        List<String> sdCardPaths = IOUtils.getSdCardPaths(getContext(), true);
        if(sdCardPaths != null) {
            int extStorageDeviceId = 1;
            for(String path : sdCardPaths) {
                File f = new File(path);
                File[] locations = f.listFiles();
                if(locations != null && locations.length > 0) {
                    for (File location : locations) {
                        File[] folderContent = location.listFiles();
                        if (location.isDirectory() && !rootPaths.contains(location) && folderContent != null && folderContent.length > 0) {
                            rootLabels.add(getString(R.string.folder_extstorage_device_pattern, extStorageDeviceId));
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

        if (getViewPrefs().getInitialFolderAsFile() != null && !rootPaths.contains(getViewPrefs().getInitialFolderAsFile())) {
            rootPaths.add(0, getViewPrefs().getInitialFolderAsFile());
            rootLabels.add(0, getString(R.string.folder_default));
        }

        rootLabels.add(0, "");
        rootPaths.add(0, null);

        MappedArrayAdapter adapter = new MappedArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, rootLabels, rootPaths);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
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

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<File> selectedItems = listAdapter.getSelectedItems();
        if(selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            selectedItems.add(listAdapter.getActiveFolder());
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), new ArrayList<>(selectedItems), actionTimeMillis));
        // now pop this screen off the stack.
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    public void onCancelChanges() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), null, actionTimeMillis));
        super.onCancelChanges();
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int spanCount;
        private final FolderItemRecyclerViewAdapter viewAdapter;

        public SpanSizeLookup(FolderItemRecyclerViewAdapter viewAdapter, int spanCount) {
            this.viewAdapter = viewAdapter;
            this.spanCount = spanCount;
        }

        @Override
        public int getSpanSize(int position) {
            try {
                int itemType = viewAdapter.getItemViewType(position);
                switch (itemType) {
                    case FolderItemRecyclerViewAdapter.VIEW_TYPE_FOLDER:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFolders();
                    case FolderItemRecyclerViewAdapter.VIEW_TYPE_FILE:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
                    default:
                        return spanCount / viewAdapter.getAdapterPrefs().getColumnsOfFiles();
                }
            } catch (IndexOutOfBoundsException e) {
                Crashlytics.logException(e);
                //TODO why does this occur? How?
                return 1;
            }
        }
    }

}
