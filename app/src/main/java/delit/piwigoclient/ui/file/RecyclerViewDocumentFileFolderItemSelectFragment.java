package delit.piwigoclient.ui.file;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.android.material.button.MaterialButton;

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
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ProgressListener;
import delit.libs.ui.view.AbstractBreadcrumbsView;
import delit.libs.ui.view.DocumentFileBreadcrumbsView;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.LongSelectableSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static android.view.View.GONE;

//@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class RecyclerViewDocumentFileFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String TAG = "RVFolderSelFrg";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewFolderItemSelectFragment.actionStartTime";
    private DocumentFileBreadcrumbsView folderPathView;
    private Spinner folderRootFolderSpinner;
    private DocumentFileArrayAdapter folderRootsAdapter;
    private long startedActionAtTime;
    private FolderItemRecyclerViewAdapter.NavigationListener navListener;
    private SortedMap<DocumentFile, List<Object>> listViewStates; // one state for each level within the list (created and deleted on demand)
    private AppSettingsViewModel appSettingsViewModel;
    private FilterControl fileExtFilters;

    public static RecyclerViewDocumentFileFolderItemSelectFragment newInstance(FolderItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewDocumentFileFolderItemSelectFragment fragment = new RecyclerViewDocumentFileFolderItemSelectFragment();
        fragment.setArguments(RecyclerViewDocumentFileFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    public static Bundle buildArgsBundle(FolderItemViewAdapterPreferences prefs, int actionId) {
        return LongSelectableSetSelectFragment.buildArgsBundle(prefs, actionId, null);
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.fragment_file_selection;
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
        Uri currentFolder = getCurrentFolder();
        if(currentFolder != null) {
            // update the current folder.
            getViewPrefs().withInitialFolder(currentFolder);
        }
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_ACTION_START_TIME, startedActionAtTime);

    }

    private @Nullable Uri getCurrentFolder() {
        if(getListAdapter() != null) {
            DocumentFile activeFolder = getListAdapter().getActiveFolder();
            return activeFolder != null ? activeFolder.getUri() : null;
        }
        return getViewPrefs().getInitialFolder();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(getContext());
        appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
        super.onCreate(savedInstanceState);
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
        }

        startedActionAtTime = System.currentTimeMillis();

        folderRootFolderSpinner = ViewCompat.requireViewById(v, R.id.folder_root_spinner);
        folderRootFolderSpinner.setOnItemSelectedListener(new RootFolderSelectionListener());

        folderPathView = v.findViewById(R.id.folder_path);
        folderPathView.setNavigationListener(new DocumentFileNavigationListener());

        fileExtFilters = v.findViewById(R.id.file_ext_filters);

        MaterialButton addRootButton = v.findViewById(R.id.add_root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addRootButton.setOnClickListener(v1 -> retrievePermissionsForUri(null));
        } else {
            addRootButton.setVisibility(GONE);
        }

        MaterialButton removeRootButton = v.findViewById(R.id.remove_root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            removeRootButton.setOnClickListener(view -> onRemoveRoot());
        } else {
            removeRootButton.setVisibility(GONE);
        }

        navListener = new FolderItemNavigationListener();

        return v;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onRemoveRoot() {
        int pos = folderRootFolderSpinner.getSelectedItemPosition();
        DocumentFile documentFile = folderRootsAdapter.getItemValue(pos);
        if(documentFile != null) {
            Uri treeUri = IOUtils.getTreeUri(documentFile.getUri());
            appSettingsViewModel.releasePersistableUriPermission(requireContext(), treeUri, UriPermissionUse.CONSUMER_ID_FILE_SELECT);
            folderRootsAdapter.remove(folderRootsAdapter.getItem(pos));
            folderRootFolderSpinner.setSelection(0); //  calls listener because it's a definite change.
//                            DisplayUtils.selectSpinnerItemAndCallItemSelectedListener(folderRootFolderSpinner, 0);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindDataToView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private static class FolderItemTaskListener implements TaskProgressListener {

        private UIHelper uiHelper;

        public FolderItemTaskListener(UIHelper uiHelper) {
            this.uiHelper = uiHelper;
        }

        @Override
        public void onTaskProgress(double percentageComplete) {
            uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), (int) Math.rint(percentageComplete* 100));
        }

        @Override
        public void onTaskStarted() {
            uiHelper.showProgressIndicator(uiHelper.getAppContext().getString(R.string.progress_loading_folder_content), 0);
        }

        @Override
        public void onTaskFinished() {
            uiHelper.hideProgressIndicator();
        }
    }

    private class FolderItemNavigationListener implements FolderItemRecyclerViewAdapter.NavigationListener {

        @Override
        public void onPreFolderOpened(DocumentFile oldFolder, DocumentFile[] newFolderContent) {
            // need to do this before the folder is opened
            recordTheCurrentListOfVisibleItemsInState(oldFolder);
        }

        @Override
        public void onPostFolderOpened(DocumentFile oldFolder, DocumentFile newFolder) {
            // scroll to the first item in the list.
            getList().scrollToPosition(0);
            buildBreadcrumbs(newFolder);
            getListAdapter().setInitiallySelectedItems(getContext()); // adapter needs to be populated for this to work.
            updateListOfFileExtensionsForAllVisibleFiles(getListAdapter().getFileExtsAndMimesInCurrentFolder());
            fileExtFilters.setActiveFilters(getListAdapter().getFileExtsInCurrentFolder());
            fileExtFilters.selectAll(true);
        }
    }

    private void recordTheCurrentListOfVisibleItemsInState(DocumentFile folder) {
        if (folder != null) {
            if (listViewStates == null) {
                listViewStates = new TreeMap<>((o1, o2) -> {
                    int o1PathLen = Objects.requireNonNull(o1.getUri().getPath()).length();
                    int o2PathLen = Objects.requireNonNull(o2.getUri().getPath()).length();
                    return Integer.compare(o1PathLen,o2PathLen);
                });
            }
            List<Object> folderListState = new ArrayList<>();
            if(getList().getLayoutManager() != null) {
                folderListState.add(getListAdapter().saveState());
                folderListState.add(getList().getLayoutManager().onSaveInstanceState());
            }
            listViewStates.put(folder, folderListState);
        }
    }

    private void updateListOfFileExtensionsForAllVisibleFiles(Map<String,String> folderContentFileExtToMimeMap) {
        SortedSet<String> fileExts = getViewPrefs().getFileTypesForVisibleMimes();
        fileExts.addAll(folderContentFileExtToMimeMap.keySet()); // need to do this because some mime types have multiple possible file exts.
        fileExtFilters.setAllFilters(fileExts);
    }

    private void buildBreadcrumbs(DocumentFile newFolder) {
        folderPathView.populate(newFolder);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void retrievePermissionsForUri(@Nullable Uri initialUriToRequestOpened) {
        Intent openDocTreeIntent;
        openDocTreeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUriToRequestOpened != null) {
            openDocTreeIntent.putExtra(EXTRA_INITIAL_URI, initialUriToRequestOpened);
        }
        openDocTreeIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        int eventId = TrackableRequestEvent.getNextEventId();
        try {
            getUiHelper().setTrackingRequest(eventId);
            startActivityForResult(openDocTreeIntent, eventId);
        } catch(ActivityNotFoundException e) {
            getUiHelper().showDetailedShortMsg(R.string.alert_error, R.string.alert_error_no_app_available_to_handle_action);
            getUiHelper().isTrackingRequest(eventId); // clear the tracked id
        }
    }

    private void retrieveFilesFromSystemPicker(@Nullable Uri uri) {
        // change the selected root to be blank again (do it now as it takes a while)
        folderRootFolderSpinner.setSelection(0);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, CollectionUtils.asStringArray(getViewPrefs().getVisibleMimeTypes()));
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(getViewPrefs().getSelectedUriPermissionFlags());
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
//        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

        // special intent for Samsung file manager
        Intent sIntent = new Intent("com.sec.android.app.myfiles.PICK_DATA_MULTIPLE");
        // if you want any file type, you can skip next line
        sIntent.putExtra("CONTENT_TYPE", "*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        sIntent.addCategory(Intent.CATEGORY_DEFAULT);

        Intent chooserIntent;
        if (requireContext().getPackageManager().resolveActivity(sIntent, 0) != null){
            // it is device with Samsung file manager
            chooserIntent = Intent.createChooser(sIntent, getString(R.string.open_files));
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { intent});
        } else {
            chooserIntent = Intent.createChooser(intent, getString(R.string.open_files));
        }

        int eventId = TrackableRequestEvent.getNextEventId();
        try {
            getUiHelper().setTrackingRequest(eventId);
            startActivityForResult(chooserIntent, eventId);
        } catch(ActivityNotFoundException e) {
            getUiHelper().showDetailedShortMsg(R.string.alert_error, R.string.alert_error_no_app_available_to_handle_action);
            getUiHelper().isTrackingRequest(eventId); // clear the tracked id
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent resultData) {
        if(!getUiHelper().isTrackingRequest(requestCode)) {
            super.onActivityResult(requestCode, resultCode, resultData);
            return;
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
        } else {
            new SharedFilesIntentProcessingTask(this).execute(resultData);
        }
    }

    private List<FolderItemRecyclerViewAdapter.FolderItem> processOpenDocuments(Intent resultData, ProgressListener listener) {
        ClipData clipData = resultData.getClipData();
        List<FolderItemRecyclerViewAdapter.FolderItem> itemsShared = new ArrayList<>();
        if(clipData != null) {
            boolean permissionsMissing = false;
            int items = clipData.getItemCount();
            for (int i = 0; i < items; i++) {
                Uri itemUri = clipData.getItemAt(i).getUri();

                final int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    try {
                        appSettingsViewModel.takePersistableUriPermissions(requireContext(), itemUri, takeFlags, getViewPrefs().getSelectedUriPermissionConsumerId(), getViewPrefs().getSelectedUriPermissionConsumerPurpose());
                    } catch(SecurityException e) {
                        permissionsMissing = true;
                        Logging.log(Log.WARN, TAG, "Unable to take persistable permissions for URI : " + itemUri);
                        Logging.recordException(e);
                    }
                }


                FolderItemRecyclerViewAdapter.FolderItem item = new FolderItemRecyclerViewAdapter.FolderItem(itemUri);
                item.cacheFields(getContext());
                itemsShared.add(item);
                listener.onProgress((int) (100 * Math.rint((float)i) / items));
            }
            if(permissionsMissing) {
                DisplayUtils.postOnUiThread(() -> getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getContext().getString(R.string.likely_file_unusable_shared_without_permissions), R.string.button_ok));
            }

        } else if(resultData.getData() != null) {
            FolderItemRecyclerViewAdapter.FolderItem item = new FolderItemRecyclerViewAdapter.FolderItem(resultData.getData());
            item.cacheFields(getContext());
            itemsShared.add(item);
        } else {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
        }
        return itemsShared;
    }

    private List<FolderItemRecyclerViewAdapter.FolderItem> processOpenDocumentTree(Intent resultData, ProgressListener listener) {
        if (resultData.getData() == null) {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            return null;
        } else {
            Context context = getContext();
            if(context == null) {
                context = getUiHelper().getAppContext();
            }
            List<FolderItemRecyclerViewAdapter.FolderItem> itemsShared = new ArrayList<>();
            // get a reference to permitted folder on the device.
            Uri permittedUri = resultData.getData();

            try {
                DocumentFile docFile = DocumentFile.fromTreeUri(context, permittedUri);
                if(docFile != null) {
                    final int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        appSettingsViewModel.takePersistableFileSelectionUriPermissions(context, permittedUri, takeFlags, getString(R.string.file_selection_heading));
                    }
                    FolderItemRecyclerViewAdapter.FolderItem folderItem = new FolderItemRecyclerViewAdapter.FolderItem(permittedUri, docFile);
                    folderItem.cacheFields(context);
                    itemsShared.add(folderItem);
                    listener.onProgress(100);
                } else {
                    itemsShared = processOpenDocuments(resultData, listener);
                }
            } catch(IllegalArgumentException e) {
                // this is most likely because it is not a folder.
                DocumentFile docFile = DocumentFile.fromSingleUri(context, permittedUri);

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    final int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    try {
                        appSettingsViewModel.takePersistableUriPermissions(context, permittedUri, takeFlags, getViewPrefs().getSelectedUriPermissionConsumerId(), getViewPrefs().getSelectedUriPermissionConsumerPurpose());
                    } catch(SecurityException se) {
                        Logging.log(Log.WARN, TAG, "Unable to take persistable folder permissions for URI : " + permittedUri);
                        Logging.recordException(se);
                    }
                }

                FolderItemRecyclerViewAdapter.FolderItem folderItem = new FolderItemRecyclerViewAdapter.FolderItem(permittedUri, docFile);
                folderItem.cacheFields(context);
                itemsShared.add(folderItem);
                listener.onProgress(100);
            }
            return itemsShared;
        }
    }

    @Override
    public boolean onBackButton() {
//        getListAdapter().cancelAnyActiveFolderMediaScan();
        DocumentFile currentFolder = getListAdapter().getActiveFolder();
        if(currentFolder == null) {
            return false;
        }
        DocumentFile parent = currentFolder.getParentFile();
        if (parent == null) {
            return false;
        } else {
            getListAdapter().changeFolderViewed(getContext(), parent);
            return true;
        }
    }

    private void applyNewRootsAdapter(DocumentFileArrayAdapter mappedArrayAdapter) {
        folderRootsAdapter = mappedArrayAdapter;
        folderRootFolderSpinner.setAdapter(folderRootsAdapter);
    }

    private void bindDataToView() {

        if(getListAdapter() == null) {
            applyNewRootsAdapter(buildFolderRootsAdapter());

            final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(navListener, new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<FolderItemRecyclerViewAdapter.FolderItem>(), getViewPrefs());
            viewAdapter.setTaskListener(new FolderItemTaskListener(getUiHelper()));

            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }


            // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
            HashSet<Long> currentSelection = getCurrentSelection(); // loaded from saved state

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);

            fileExtFilters.setListener(new FileExtFilterControlListener(getListAdapter()));

            DocumentFile initialFolder = IOUtils.getDocumentFileForUriLinkedToAnAccessibleRoot(requireContext(), getViewPrefs().getInitialFolder());
            if (initialFolder != null) {
                // We still have access to the given Uri somehow.
                DocumentFile root = IOUtils.getRootDocFile(initialFolder);
                int selectRoot = folderRootsAdapter.getPositionByValue(root);
                buildBreadcrumbs(initialFolder); // causes issues as triggers rebuild
                folderRootFolderSpinner.setSelection(selectRoot);
            } else {
                buildBreadcrumbs(getListAdapter().getActiveFolder());
            }

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

    private DocumentFileArrayAdapter buildFolderRootsAdapter() {
        //TODO get the list of app registered permissions and use that as the basis rather than all of them which includes files etc.
        Map<String, DocumentFile> roots = null;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            roots = buildDocFilesRootsMap();
        } else {
            roots = buildDocFilesRootsMapPreKitKat();
        }

        DocumentFileArrayAdapter adapter = new DocumentFileArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roots);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private String getLabelForExtStorageLegacy(File location, String defaultName) {
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

    private Map<String, DocumentFile> buildDocFilesRootsMapPreKitKat() {
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
                            rootLabels.add(getLabelForExtStorageLegacy(location, getString(R.string.folder_extstorage_device_pattern, extStorageDeviceId)));
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

        File initialFolder = null;
        try {
            initialFolder = LegacyIOUtils.getFile(getViewPrefs().getInitialFolder());
        } catch (IOException e) {
            Logging.recordException(e);
        }
        if (initialFolder != null && !rootPaths.contains(initialFolder)) {
            rootPaths.add(0, initialFolder);
            rootLabels.add(0, getString(R.string.folder_default));
        }

        Map<String, DocumentFile> roots = new LinkedHashMap<>();
        roots.put("", null); // default option (for nothing selected).
        for(int i = 0; i < rootLabels.size(); i++) {
            roots.put(rootLabels.get(i), LegacyIOUtils.getDocFile(rootPaths.get(i)));
        }
        roots.put(getString(R.string.system_file_selector_label), null); // allow other apps to handle file selection
        return roots;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private Map<String, DocumentFile> buildDocFilesRootsMap() {
        List<UriPermission> permissions = requireContext().getContentResolver().getPersistedUriPermissions();
        Map<String, DocumentFile> roots = new LinkedHashMap<>();
        roots.put("", null);
        for (UriPermission perm : permissions) {
            if (perm.isWritePermission()) {
                try {
                    DocumentFile documentFile = DocumentFile.fromTreeUri(requireContext(), perm.getUri());
                    roots.put(documentFile == null ? "???" : IOUtils.getFilename(documentFile), documentFile);
                } catch (IllegalArgumentException e) {
                    // it's a file not a folder. Ignore the error.
                }

            }
        }
        roots.put(getString(R.string.system_file_selector_label), null);
        return roots;
    }


    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<FolderItemRecyclerViewAdapter.FolderItem> selectedItems = listAdapter.getSelectedItems();
        if (selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            if(listAdapter.getActiveFolder() != null) {
                FolderItemRecyclerViewAdapter.FolderItem folderItem = new FolderItemRecyclerViewAdapter.FolderItem(listAdapter.getActiveRootUri(), listAdapter.getActiveFolder());
                folderItem.cacheFields(getContext());
                selectedItems.add(folderItem);
            }
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFolderItems(new ArrayList<>(selectedItems)));

        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately");
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

        private final FolderItemRecyclerViewAdapter listAdapter;

        public FileExtFilterControlListener(FolderItemRecyclerViewAdapter adapter) {
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
            listAdapter.refreshContentView(context);
        }
    }

    @Override
    public void onCancelChanges() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFiles(getViewPrefs().getInitialSelection()));
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
                Logging.recordException(e);
                //TODO why does this occur? How?
                return 1;
            }
        }
    }

    private class RootFolderSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if(listViewStates != null) {
                listViewStates.clear();
            }
            if (id > 0) {
                DisplayUtils.postOnUiThread(() -> {
                    DocumentFileArrayAdapter adapter = (DocumentFileArrayAdapter) parent.getAdapter();
                    DocumentFile newRoot = adapter.getItemValue(position);
                    if(newRoot != null) {
                        fileExtFilters.setEnabled(true);
                        getViewPrefs().withVisibleContent(fileExtFilters.getAllFilters(), getViewPrefs().getFileSortOrder());
                        DocumentFile currentRoot = getListAdapter().getActiveFolder();
                        try {
                            if (currentRoot == null && getViewPrefs().getInitialFolder() != null) {
                                DocumentFile file = IOUtils.getTreeLinkedDocFile(view.getContext(), newRoot.getUri(), getViewPrefs().getInitialFolder());
                                getListAdapter().updateContentAndRoot(view.getContext(), newRoot, file);
                            } else {
                                getListAdapter().resetRoot(view.getContext(), newRoot);
                            }
                        } catch(IllegalStateException e) {
                            getListAdapter().resetRoot(view.getContext(), newRoot); // just use the current root and ignore the initial folder.
                        }
                        deselectAllItems();
                    } else {
                        getViewPrefs().withVisibleContent(fileExtFilters.getAllFilters(), getViewPrefs().getFileSortOrder());
                        getListAdapter().resetRoot(view.getContext(), newRoot);
                        deselectAllItems();
                        fileExtFilters.setEnabled(false);
                        retrieveFilesFromSystemPicker(getViewPrefs().getInitialFolder());
                    }
                });
            } else {
                // just show the empty view (if we're not already).
                if(getListAdapter().getActiveFolder() != null) {
                    getViewPrefs().withVisibleContent(fileExtFilters.getAllFilters(), getViewPrefs().getFileSortOrder());
                    getListAdapter().resetRoot(view.getContext(), null);
                    deselectAllItems();
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private static class SharedFilesIntentProcessingTask extends OwnedSafeAsyncTask<RecyclerViewDocumentFileFolderItemSelectFragment, Intent, Integer, List<FolderItemRecyclerViewAdapter.FolderItem>> implements ProgressListener {


        public SharedFilesIntentProcessingTask(RecyclerViewDocumentFileFolderItemSelectFragment parent) {
             super(parent);
             withContext(parent.requireContext());
        }

        @Override
        protected void onPreExecuteSafely() {
            super.onPreExecuteSafely();
            getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),0);
        }

        @Override
        protected List<FolderItemRecyclerViewAdapter.FolderItem> doInBackgroundSafely(Intent[] objects) {
            Intent intent = objects[0];
            if (intent.getClipData() != null) {
                return getOwner().processOpenDocuments(intent, this);
            } else {
                return getOwner().processOpenDocumentTree(intent, this);
            }
        }

        @Override
        protected void onProgressUpdateSafely(Integer... progress) {
            super.onProgressUpdateSafely(progress);
            try {
                getOwner().getUiHelper().showProgressIndicator(getOwner().getString(R.string.progress_importing_files),progress[0]);
            } catch(NullPointerException e) {
                Logging.log(Log.ERROR, TAG, "Unable to report progress. Likely not attached", e);
            }

        }

        @Override
        protected void onPostExecuteSafely(List<FolderItemRecyclerViewAdapter.FolderItem> folderItems) {
            if(folderItems != null) {
                if (folderItems.size() == 1 && folderItems.get(0).isFolder()) {
                    FolderItemRecyclerViewAdapter.FolderItem item = folderItems.get(0);
                    getOwner().addRootFolder(item.getDocumentFile());
                } else {
                    getOwner().getListAdapter().addItems(folderItems);
                    getOwner().selectAllItems();
                }
            }
            getOwner().getUiHelper().hideProgressIndicator();
            getOwner().fileExtFilters.setEnabled(true);
        }

        @Override
        public void onProgress(int percent) {
            publishProgress(percent);
        }
    }

    private void addRootFolder(@NonNull DocumentFile docFile) {
        String filename = IOUtils.getFilename(docFile);
        folderRootsAdapter.add(filename, docFile);
        folderRootFolderSpinner.setSelection(folderRootsAdapter.getCount() - 1); // calls listener because it's a definite change.
//        DisplayUtils.selectSpinnerItemAndCallItemSelectedListener(folderRootFolderSpinner, folderRootsAdapter.getCount() - 1);
    }

    private class DocumentFileNavigationListener implements AbstractBreadcrumbsView.NavigationListener<DocumentFile> {
        @Override
        public void onBreadcrumbClicked(DocumentFile pathItemFile) {
            if(getListAdapter().getActiveFolder() == null) {
                return; // UI out of sync. Do nothing and be patient.
            }
            if(getListAdapter().getActiveFolder().getUri().equals(pathItemFile.getUri())) {
                getListAdapter().rebuildContentView(getContext());
            } else {
                boolean loadedFromMemory = false;
                if(listViewStates != null) {
                    Iterator<Map.Entry<DocumentFile, List<Object>>> iterator = listViewStates.entrySet().iterator();
                    Map.Entry<DocumentFile, List<Object>> item;
                    while (iterator.hasNext()) {
                        item = iterator.next();
                        if (item.getKey().getUri().equals(pathItemFile.getUri())) {
                            loadedFromMemory = true;
                            if (getList().getLayoutManager() != null) {
                                FolderItemRecyclerViewAdapter adapter = (FolderItemRecyclerViewAdapter) getList().getAdapter();
                                Objects.requireNonNull(adapter).restoreState((FolderItemRecyclerViewAdapter.SavedState) item.getValue().get(0));
                                getList().getLayoutManager().onRestoreInstanceState((Parcelable) item.getValue().get(1));

                            } else {
                                Logging.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
                            }
                            // only keep a single level below our current level in case we moved back accidentally.
                            if(iterator.hasNext()) {
                                iterator.next();
                                if(iterator.hasNext()) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
                if(!loadedFromMemory) {
                    getListAdapter().changeFolderViewed(getContext(), pathItemFile);
                }
            }
        }
    }
}
