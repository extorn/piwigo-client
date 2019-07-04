package delit.piwigoclient.ui.file;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.util.ArrayUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.view.FlowLayout;
import delit.libs.ui.view.list.MappedArrayAdapter;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.fragment.LongSelectableSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;

import static android.view.View.NO_ID;

public class RecyclerViewFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String ACTIVE_FOLDER = "RecyclerViewFolderItemSelectFragment.activeFolder";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewFolderItemSelectFragment.actionStartTime";
    private static final String STATE_ALL_POSS_VIS_FILE_EXTS = "RecyclerViewFolderItemSelectFragment.allPossVisFileExts";
    private static final String STATE_SELECTED_VIS_FILE_EXTS = "RecyclerViewFolderItemSelectFragment.selectedVisFileExts";
    private FlowLayout folderPathView;
    private Spinner folderRootFolderSpinner;
    private MappedArrayAdapter<String, File> folderRootsAdapter;
    private long startedActionAtTime;
    private FolderItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<String, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)
    private Set<String> allPossiblyVisibleFileExts;
    private Set<String> selectedVisibleFileExts;
    private FlowLayout fileExtFilters;

    public static RecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewFolderItemSelectFragment fragment = new RecyclerViewFolderItemSelectFragment();
        fragment.setArguments(RecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId) {
        Bundle args = LongSelectableSetSelectFragment.buildArgsBundle(prefs, actionId, null);
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
        BundleUtils.writeMap(outState, STATE_LIST_VIEW_STATE, listViewStates);
        BundleUtils.putStringSet(outState, STATE_ALL_POSS_VIS_FILE_EXTS, allPossiblyVisibleFileExts);
        BundleUtils.putStringSet(outState, STATE_SELECTED_VIS_FILE_EXTS, selectedVisibleFileExts);
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
            listViewStates = BundleUtils.readMap(savedInstanceState, STATE_LIST_VIEW_STATE, new LinkedHashMap(), Parcelable.class.getClassLoader());
            allPossiblyVisibleFileExts = BundleUtils.getStringHashSet(savedInstanceState, STATE_ALL_POSS_VIS_FILE_EXTS);
            selectedVisibleFileExts = BundleUtils.getStringHashSet(savedInstanceState, STATE_SELECTED_VIS_FILE_EXTS);
        }

        startedActionAtTime = System.currentTimeMillis();


        folderRootFolderSpinner = v.findViewById(R.id.folder_root_spinner);
        folderRootFolderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (id > 0) {
                    MappedArrayAdapter<String, File> adapter = (MappedArrayAdapter) parent.getAdapter();
                    File newRoot = adapter.getItemValue(position);
                    getListAdapter().changeFolderViewed(newRoot);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        folderPathView = v.findViewById(R.id.folder_path);

        fileExtFilters = v.findViewById(R.id.file_ext_filters);

        navListener = new FolderItemRecyclerViewAdapter.NavigationListener() {

            @Override
            public void onPreFolderOpened(File oldFolder, File newFolder) {

                // this works because the adapter uses a reference to the same preferences.
                getViewPrefs().withVisibleContent(allPossiblyVisibleFileExts, getViewPrefs().getFileSortOrder());

                if(oldFolder != null) {
                    if (listViewStates == null) {
                        listViewStates = new LinkedHashMap<>(5);
                    }
                    listViewStates.put(oldFolder.getAbsolutePath(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
                }
                getList().scrollToPosition(0);

                buildBreadcrumbs(newFolder);
            }

            @Override
            public void onPostFolderOpened(File oldFolder, File newFolder) {
                buildFileExtFilterControls(newFolder);
            }
        };

        return v;
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

    private void buildFileExtFilterControls(File currentFolder) {
        if (allPossiblyVisibleFileExts == null && getViewPrefs().getVisibleFileTypes() != null) {
            allPossiblyVisibleFileExts = new HashSet<>(getViewPrefs().getVisibleFileTypes());
        }


        // initialise local cached set of selected items
        if (selectedVisibleFileExts == null && allPossiblyVisibleFileExts != null) {
            selectedVisibleFileExts = new HashSet<>(allPossiblyVisibleFileExts);
        }
        // clear all the existing filters
        fileExtFilters.removeAllViews();

        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();

        // get a list of all unique file extensions in the current folder
        SortedSet<String> visibleFileExts = listAdapter.getFileExtsInCurrentFolder();

        // for each file type visible, show a checkbox and set it according to out local model
        if (visibleFileExts != null && selectedVisibleFileExts != null) {
            boolean updateViewRequired = false;
            for (String fileExt : visibleFileExts) {
                FlowLayout.LayoutParams layoutParams = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT, FlowLayout.LayoutParams.WRAP_CONTENT);
                boolean checked = selectedVisibleFileExts.contains(fileExt);
                if (!checked) {
                    updateViewRequired = true;
                    listAdapter.getAdapterPrefs().getVisibleFileTypes().remove(fileExt);
                } else if (!listAdapter.getAdapterPrefs().getVisibleFileTypes().contains(fileExt)) {
                    listAdapter.getAdapterPrefs().getVisibleFileTypes().add(fileExt);
                }
                fileExtFilters.addView(createFileExtFilterControl(fileExt, checked), layoutParams);
            }
            if (updateViewRequired) {
                listAdapter.rebuildContentView();
            }
        }
    }

    private View createFileExtFilterControl(String fileExt, boolean checked) {
        CheckBox fileExtControl = new CheckBox(getContext());
        int paddingPx = DisplayUtils.dpToPx(getContext(), 5);
        fileExtControl.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        fileExtControl.setText(fileExt);
        fileExtControl.setChecked(checked);
        fileExtControl.setOnCheckedChangeListener(new FileExtControlListener());
        return fileExtControl;
    }

    private void buildBreadcrumbs(File newFolder) {
        if (folderRootsAdapter == null) {
            return;
            // still building the ui
        }
        String item = folderRootsAdapter.getItemByValue(newFolder);
        if (item == null) {
            // reset the selection
            if (folderRootFolderSpinner.getSelectedItemId() >= 0) {
                folderRootFolderSpinner.setAdapter(folderRootsAdapter);
            }
        } else {
            folderRootFolderSpinner.setSelection(folderRootsAdapter.getPosition(item), false);
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

        int paddingPx = DisplayUtils.dpToPx(getContext(), 3);

        for(final File pathItemFile : pathItems) {
            idx++;
            int lastId = NO_ID;
            if(pathItem != null) {
                lastId = pathItem.getId();
            }
            pathItem = new TextView(getContext());
            pathItem.setId(View.generateViewId());
            TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_AppCompat_Body2_Clickable);
            pathItem.setPaddingRelative(0, 0, 0, 0);
            pathItem.setText(pathItemFile.getName());
            folderPathView.addView(pathItem);

            pathItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView tv = (TextView) v;
                    getListAdapter().changeFolderViewed(pathItemFile);
                    if(listViewStates != null) {
                        Iterator<Map.Entry<String, Parcelable>> iter = listViewStates.entrySet().iterator();
                        Map.Entry<String, Parcelable> item;
                        while (iter.hasNext()) {
                            item = iter.next();
                            if (item.getKey().equals(pathItemFile.getAbsolutePath())) {
                                getList().getLayoutManager().onRestoreInstanceState(item.getValue());
                                iter.remove();
                                while (iter.hasNext()) {
                                    iter.next();
                                    iter.remove();
                                }
                            }
                        }
                    }
                }
            });

            if(idx < pathItems.size()) {
                TextView pathItemSeperator = new TextView(getContext());
                TextViewCompat.setTextAppearance(pathItemSeperator, R.style.TextAppearance_AppCompat_Body2);
                pathItemSeperator.setText("/");
                pathItemSeperator.setPaddingRelative(paddingPx, 0, paddingPx, 0);
                pathItemSeperator.setId(View.generateViewId());
                folderPathView.addView(pathItemSeperator);
                pathItem = pathItemSeperator;
            }
        }
    }

    @Override
    public boolean onBackButton() {
        File parent = getListAdapter().getActiveFolder().getParentFile();
        if (parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().changeFolderViewed(parent);
            return true;
        }
    }

    private void applyNewRootsAdapter(MappedArrayAdapter<String, File> mappedArrayAdapter) {
        folderRootsAdapter = mappedArrayAdapter;
        folderRootFolderSpinner.setAdapter(folderRootsAdapter);
        buildBreadcrumbs(getListAdapter().getActiveFolder());
    }

    private void bindDataToView(Bundle savedInstanceState) {

        new DataBinderTask(this).execute();


        File activeFolder = null;
        if (savedInstanceState != null) {
            activeFolder = BundleUtils.getFile(savedInstanceState, ACTIVE_FOLDER);
        }

        if(getListAdapter() == null) {


            final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(navListener, MediaScanner.instance(getContext()), new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<File>(), getViewPrefs());

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

            // update the adapter content
            viewAdapter.changeFolderViewed(getViewPrefs().getInitialFolderAsFile());
        }

        // call this here to ensure page reformats if orientation changes for example.
        getViewPrefs().withColumnsOfFiles(OtherPreferences.getFileSelectorColumnsOfFiles(getPrefs(), getActivity()));
        getViewPrefs().withColumnsOfFolders(OtherPreferences.getFileSelectorColumnsOfFolders(getPrefs(), getActivity()));

        int colsOnScreen = Math.max(getViewPrefs().getColumnsOfFiles(), getViewPrefs().getColumnsOfFolders());
        if (getViewPrefs().getColumnsOfFiles() % getViewPrefs().getColumnsOfFolders() > 0) {
            colsOnScreen = getViewPrefs().getColumnsOfFiles() * getViewPrefs().getColumnsOfFolders();
        }
        GridLayoutManager layoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        layoutMan.setSpanSizeLookup(new SpanSizeLookup(getListAdapter(), colsOnScreen));
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(getListAdapter());
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

        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Context is null while attempting to build list adapter");
        }

        MappedArrayAdapter<String, File> adapter = new MappedArrayAdapter<>(context, android.R.layout.simple_spinner_item, rootLabels, rootPaths);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<File> selectedItems = listAdapter.getSelectedItems();
        if (selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            selectedItems.add(listAdapter.getActiveFolder());
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), new ArrayList<>(selectedItems), actionTimeMillis));
        // now pop this screen off the stack.
        if (isVisible() && getFragmentManager() != null) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    private static class DataBinderTask extends AsyncTask<Void, Void, MappedArrayAdapter<String, File>> {

        WeakReference<RecyclerViewFolderItemSelectFragment> parentFragmentRef;

        DataBinderTask(RecyclerViewFolderItemSelectFragment parentFragment) {
            this.parentFragmentRef = new WeakReference<>(parentFragment);
        }

        @Override
        protected MappedArrayAdapter<String, File> doInBackground(Void... voids) {
            RecyclerViewFolderItemSelectFragment parentFragment = parentFragmentRef.get();
            if (parentFragment != null) {
                return parentFragment.buildFolderRootsAdapter();
            }
            return null;
        }

        @Override
        protected void onPostExecute(MappedArrayAdapter<String, File> mappedArrayAdapter) {
            // building this stuff is slow - lets do it async!
            RecyclerViewFolderItemSelectFragment parentFragment = parentFragmentRef.get();
            if (parentFragment != null && mappedArrayAdapter != null) {
                parentFragment.applyNewRootsAdapter(mappedArrayAdapter);
            }
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

    private class FileExtControlListener implements CompoundButton.OnCheckedChangeListener {

        public FileExtControlListener() {
            //TODO maybe add the ListAdapter here and use that.
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            String fileExt = buttonView.getText().toString();
            FolderItemRecyclerViewAdapter adapter = getListAdapter();
            SortedSet<String> visibleFileTypes = adapter.getAdapterPrefs().getVisibleFileTypes();
            if (isChecked) {
                // local model used when rebuilding each folder view
                selectedVisibleFileExts.add(fileExt);
                // current folder view
                visibleFileTypes.add(fileExt);
            } else {
                // local model used when rebuilding each folder view
                selectedVisibleFileExts.remove(fileExt);
                // current folder view
                visibleFileTypes.remove(fileExt);
            }
            adapter.rebuildContentView();
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
