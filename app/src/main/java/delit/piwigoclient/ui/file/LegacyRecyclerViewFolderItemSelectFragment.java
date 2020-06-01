package delit.piwigoclient.ui.file;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Log;
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
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.util.ArrayUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
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
import delit.libs.ui.view.FileBreadcrumbsView;
import delit.libs.ui.view.FlowLayout;
import delit.libs.ui.view.list.MappedArrayAdapter;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
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
    private static final String STATE_ALL_POSS_VIS_FILE_EXTS = "RecyclerViewFolderItemSelectFragment.allPossVisFileExts";
    private static final String STATE_SELECTED_VIS_FILE_EXTS = "RecyclerViewFolderItemSelectFragment.selectedVisFileExts";
    private FileBreadcrumbsView folderPathView;
    private Spinner folderRootFolderSpinner;
    private MappedArrayAdapter<String, File> folderRootsAdapter;
    private long startedActionAtTime;
    private LegacyFolderItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<String, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)
    private Set<String> allPossiblyVisibleFileExts;
    private Set<String> selectedVisibleFileExts;
    private FlowLayout fileExtFilters;

    public static LegacyRecyclerViewFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        LegacyRecyclerViewFolderItemSelectFragment fragment = new LegacyRecyclerViewFolderItemSelectFragment();
        fragment.setArguments(LegacyRecyclerViewFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
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
            listViewStates = BundleUtils.readMap(savedInstanceState, STATE_LIST_VIEW_STATE, new LinkedHashMap<String, Parcelable>(), Parcelable.class.getClassLoader());
            allPossiblyVisibleFileExts = BundleUtils.getStringHashSet(savedInstanceState, STATE_ALL_POSS_VIS_FILE_EXTS);
            selectedVisibleFileExts = BundleUtils.getStringHashSet(savedInstanceState, STATE_SELECTED_VIS_FILE_EXTS);
        }

        startedActionAtTime = System.currentTimeMillis();


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

    private View createFileExtFilterControl(String fileExt, boolean checked) {
        CheckBox fileExtControl = new CheckBox(requireContext());
        int paddingPx = DisplayUtils.dpToPx(requireContext(), 5);
        fileExtControl.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        fileExtControl.setText(fileExt);
        fileExtControl.setChecked(checked);
        fileExtControl.setOnCheckedChangeListener(new FileExtControlListener());
        return fileExtControl;
    }

    private static final class PreviouslyUploadedFilesFilter extends AsyncTask<Void, Void, Void> {

        private Application context;
        private String folderPath;
        private String serverProfileId;
        private LegacyFolderItemRecyclerViewAdapter adapter;

        public PreviouslyUploadedFilesFilter(Context context, LegacyFolderItemRecyclerViewAdapter listAdapter, String absolutePath, String serverProfileId) {
            this.context = (Application) context.getApplicationContext();
            this.adapter = listAdapter;
            this.folderPath = absolutePath;
            this.serverProfileId = serverProfileId;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            //TODO enable this for paid app only
//            // clear those files no longer existing from the database.
//            PiwigoDatabase.getInstance(context).uploadedFileDao().removeObsolete(folderPath, new File(folderPath).list());
//
//            List<String> previouslyUploadedFiles = PiwigoDatabase.getInstance(context).uploadedFileDao().loadAllFilenamesByParentPathForServerId(folderPath, serverProfileId);
//            // mark up the adapter content.
//            for (String previousUploadedFile : previouslyUploadedFiles) {
//                int itemPos = adapter.getItemPositionForFilename(previousUploadedFile);
//                adapter.removeItemByPosition(itemPos);
//            }
            return null;
        }
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

    private void buildFileExtFilterControls(Context context, File currentFolder) {

        // initialise local cached set of selected items
        if (selectedVisibleFileExts == null && allPossiblyVisibleFileExts != null) {
            selectedVisibleFileExts = new HashSet<>(allPossiblyVisibleFileExts);
        }
        // clear all the existing filters
        fileExtFilters.removeAllViews();

        LegacyFolderItemRecyclerViewAdapter listAdapter = getListAdapter();

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
                } else {
                    listAdapter.getAdapterPrefs().getVisibleFileTypes().add(fileExt);
                }
                fileExtFilters.addView(createFileExtFilterControl(fileExt, checked), layoutParams);
            }
            if (updateViewRequired) {
                listAdapter.rebuildContentView(context);
            }
        }
    }

    private class FolderItemNavigationListener implements LegacyFolderItemRecyclerViewAdapter.NavigationListener {

        @Override
        public void onPreFolderOpened(File oldFolder, File newFolder) {

            if (getViewPrefs().getVisibleFileTypes() != null) {
                if (allPossiblyVisibleFileExts == null) {
                    allPossiblyVisibleFileExts = new HashSet<>(getViewPrefs().getVisibleFileTypes());
                }
            }
            if (allPossiblyVisibleFileExts != null) {
                SortedSet<String> fileExtsInFolderMatchingMimeTypesWanted = getViewPrefs().getVisibleFileTypesForMimes(IOUtils.getUniqueExtAndMimeTypes(newFolder.listFiles()));
                if (fileExtsInFolderMatchingMimeTypesWanted != null) {
                    // add any extra that have come from mime types now visible in this folder.
                    allPossiblyVisibleFileExts.addAll(fileExtsInFolderMatchingMimeTypesWanted);
                }
            }
            // this works because the adapter uses a reference to the same preferences.
            getViewPrefs().withVisibleContent(allPossiblyVisibleFileExts, getViewPrefs().getFileSortOrder());

            if (oldFolder != null) {
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
            buildFileExtFilterControls(getContext(), newFolder);
            // get a list of all those files previously uploaded to this server and update the
            String serverProfileId = ConnectionPreferences.getActiveProfile().getProfileId(prefs, getContext());

            //TODO enable uploaded file tracking database here!

            //            new PreviouslyUploadedFilesFilter(requireContext(), getListAdapter(), newFolder.getAbsolutePath(), serverProfileId).execute();

        }
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
        buildBreadcrumbs(getListAdapter().getActiveFolder());
    }

    private void bindDataToView(Bundle savedInstanceState) {

        new DataBinderTask(this).execute();


        File activeFolder = null;
        if (savedInstanceState != null) {
            activeFolder = BundleUtils.getFile(savedInstanceState, ACTIVE_FOLDER);
        }

        if(getListAdapter() == null) {


            final LegacyFolderItemRecyclerViewAdapter viewAdapter = new LegacyFolderItemRecyclerViewAdapter(requireContext(), navListener, MediaScanner.instance(getContext()), new LegacyFolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<>(), getViewPrefs());

            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }


            // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
            HashSet<Long> currentSelection = getCurrentSelection();

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);

            // update the adapter content
            File newFolder = activeFolder;
            if(newFolder == null) {
                newFolder = getViewPrefInitialFolderAsFile();
            }
            viewAdapter.changeFolderViewed(getContext(), newFolder);

            // select the items to view.
            viewAdapter.setInitiallySelectedItems();
            viewAdapter.setSelectedItems(currentSelection);
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
            return IOUtils.getFile(getViewPrefs().getInitialFolder());
        } catch (IOException e) {
            throw new IllegalStateException("Non file Uri somehow got given as initial folder");
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

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        LegacyFolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem> selectedItems = listAdapter.getSelectedItems();
        Set<Uri> selectedUris = new HashSet<>(selectedItems.size());
        for(LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem selectionItem : selectedItems) {
            //TODO check if the content uris are better or worse to use.
//            if(selectionItem.getContentUri() != null) {
//                selectedUris.add(selectionItem.getContentUri());
//            } else {
//                selectedUris.add(Uri.fromFile(selectionItem.getFile()));
//            }
            selectedUris.add(Uri.fromFile(selectionItem.getFile()));
        }
        if (selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            selectedItems.add(new LegacyFolderItemRecyclerViewAdapter.LegacyFolderItem(listAdapter.getActiveFolder()));
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;

        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFiles(selectedUris));
        listAdapter.cancelAnyActiveFolderMediaScan(getContext());
        // now pop this screen off the stack.
        if (isVisible()) {
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private static class DataBinderTask extends AsyncTask<Void, Void, MappedArrayAdapter<String, File>> {

        WeakReference<LegacyRecyclerViewFolderItemSelectFragment> parentFragmentRef;

        DataBinderTask(LegacyRecyclerViewFolderItemSelectFragment parentFragment) {
            this.parentFragmentRef = new WeakReference<>(parentFragment);
        }

        @Override
        protected MappedArrayAdapter<String, File> doInBackground(Void... voids) {
            LegacyRecyclerViewFolderItemSelectFragment parentFragment = parentFragmentRef.get();
            if (parentFragment != null) {
                return parentFragment.buildFolderRootsAdapter();
            }
            return null;
        }

        @Override
        protected void onPostExecute(MappedArrayAdapter<String, File> mappedArrayAdapter) {
            // building this stuff is slow - lets do it async!
            LegacyRecyclerViewFolderItemSelectFragment parentFragment = parentFragmentRef.get();
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
            LegacyFolderItemRecyclerViewAdapter adapter = getListAdapter();
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
            adapter.rebuildContentView(buttonView.getContext());
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
                Crashlytics.logException(e);
                //TODO why does this occur? How?
                return 1;
            }
        }
    }

    private class RootFolderSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (id > 0) {
                MappedArrayAdapter<String, File> adapter = (MappedArrayAdapter) parent.getAdapter();
                File newRoot = adapter.getItemValue(position);
                getListAdapter().changeFolderViewed(getContext(), newRoot);
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
                                Crashlytics.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
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
