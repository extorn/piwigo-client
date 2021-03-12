package delit.piwigoclient.ui.file;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.AsyncTask;
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
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.AbstractBreadcrumbsView;
import delit.libs.ui.view.DocumentFileBreadcrumbsView;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import delit.libs.util.SafeRunnable;
import delit.libs.util.progress.ProgressListener;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.LongSelectableSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;
import delit.piwigoclient.ui.file.action.FileExtFilterControlListener;
import delit.piwigoclient.ui.file.action.FileSelectionCancelledMissingPermissionsListener;
import delit.piwigoclient.ui.file.action.FolderItemSpanSizeLookup;
import delit.piwigoclient.ui.file.action.SelectedFilesUriCheckTask;
import delit.piwigoclient.ui.file.action.SharedFilesClonedIntentProcessingTask;
import delit.piwigoclient.ui.file.action.SharedFilesIntentProcessingTask;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static android.view.View.GONE;

//@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class RecyclerViewDocumentFileFolderItemSelectFragment<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>, FUIH extends FragmentUIHelper<FUIH,F>, P extends FolderItemViewAdapterPreferences<P>> extends RecyclerViewLongSetSelectFragment<F,FUIH,FolderItemRecyclerViewAdapter<?,?,FolderItem,?,?>, P, FolderItem> implements BackButtonHandler {
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
    private TextView selectedFileInfoView;

    public static <F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>, FUIH extends FragmentUIHelper<FUIH,F>, P extends FolderItemViewAdapterPreferences<P>> F newInstance(P prefs, int actionId) {
        RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P> fragment = new RecyclerViewDocumentFileFolderItemSelectFragment<>();
        fragment.setArguments(RecyclerViewDocumentFileFolderItemSelectFragment.buildArgsBundle(prefs, actionId));
        return (F) fragment;
    }

    public static <P extends FolderItemViewAdapterPreferences<P>> Bundle buildArgsBundle(P prefs, int actionId) {
        return LongSelectableSetSelectFragment.buildArgsBundle(prefs, actionId, null);
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.fragment_file_selection;
    }

    @Override
    protected P loadPreferencesFromBundle(Bundle bundle) {
        return (P) new FolderItemViewAdapterPreferences<P>(bundle);
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
        appSettingsViewModel = obtainActivityViewModel(requireActivity(), AppSettingsViewModel.class);
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

        selectedFileInfoView = v.findViewById(R.id.selected_file_info);

        MaterialButton addRootButton = v.findViewById(R.id.add_root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int perms = getNecessaryFolderPermissions();
            addRootButton.setOnClickListener(v1 -> retrievePermissionsForUri(null, perms));
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

    private int getNecessaryFolderPermissions() {
        if(!getViewPrefs().isAllowFolderSelection()) {
            return Intent.FLAG_GRANT_READ_URI_PERMISSION; // only need read permission for the folders
        } else {
            return getViewPrefs().getSelectedUriPermissionFlags();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onRemoveRoot() {
        int pos = folderRootFolderSpinner.getSelectedItemPosition();
        DocumentFile documentFile = folderRootsAdapter.getItemValue(pos);
        if(documentFile != null) {
            Uri treeUri = IOUtils.getTreeUri(documentFile.getUri());
            appSettingsViewModel.releasePersistableUriPermission(requireContext(), treeUri, UriPermissionUse.CONSUMER_ID_FILE_SELECT, true);
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
            setBackButtonHandlerEnabled(folderPathView.getBreadcrumbCount() > 1);
            getListAdapter().setInitiallySelectedItems(getContext()); // adapter needs to be populated for this to work.
            updateListOfFileExtensionsForAllVisibleFiles(getListAdapter().getFileExtsAndMimesInCurrentFolder());

            SortedSet<String> allFilters = getViewPrefs().getVisibleFileTypes();
            if(allFilters == null) {
                allFilters = new TreeSet<>();
            }
            allFilters.addAll(getViewPrefs().getFileTypesForVisibleMimes());
            allFilters.addAll(getViewPrefs().getAcceptableFileExts(getListAdapter().getFileExtsAndMimesInCurrentFolder()));

            getViewPrefs().getFileTypesForVisibleMimes();
//            fileExtFilters.setShowInactiveFilters(false);
            fileExtFilters.setAllFilters(allFilters);
            fileExtFilters.setActiveFilters(getListAdapter().getFileExtsInCurrentFolder());
            fileExtFilters.selectAll(false);
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
    private void retrievePermissionsForUri(@Nullable Uri initialUriToRequestOpened, int permissions) {
        Intent openDocTreeIntent;
        openDocTreeIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUriToRequestOpened != null) {
            openDocTreeIntent.putExtra(EXTRA_INITIAL_URI, initialUriToRequestOpened);
        }
        openDocTreeIntent.addFlags(permissions);
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
        if(getViewPrefs().isAllowFolderSelection() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            retrievePermissionsForUri(uri, getViewPrefs().getSelectedUriPermissionFlags());
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        Set<String> permittedMimeTypes = getViewPrefs().getVisibleMimeTypes();
        if(permittedMimeTypes.isEmpty()) {
            permittedMimeTypes = IOUtils.getMimeTypesFromFileExts(new HashSet<>(), getViewPrefs().getVisibleFileTypes());
        }
        intent.putExtra(Intent.EXTRA_MIME_TYPES, IOUtils.getMimeTypesIncludingFolders(permittedMimeTypes));
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
            // this is unnecessary to report since the request for files from the system selector was cancelled.
        } else {
            new SharedFilesIntentProcessingTask<>((F)this, resultData).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void processOpenDocumentsWithoutPermissions(List<FolderItem> itemsShared) {
        if(BuildConfig.PAID_VERSION) {
            new SharedFilesClonedIntentProcessingTask<>((F) this).executeNow(itemsShared);
        } else {
            // this is called from a background thread.
            DisplayUtils.runOnUiThread(() -> getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_shared_without_required_permissions_error), R.string.button_ok));
        }
    }

    @NonNull
    public List<FolderItem> processOpenDocuments(Intent resultData, ProgressListener listener) {
        ClipData clipData = resultData.getClipData();
        List<FolderItem> itemsShared = new ArrayList<>();
        boolean permissionsMissing = false;

        if(clipData != null) {
            int items = clipData.getItemCount();
            for (int i = 0; i < items; i++) {
                Uri itemUri = clipData.getItemAt(i).getUri();

                FolderItem item = new FolderItem(itemUri);
                itemsShared.add(item);
                permissionsMissing |= takePersistablePermissionsIfNeeded(resultData, item.getContentUri());
            }

        } else if(resultData.getData() != null) {
            FolderItem item = new FolderItem(resultData.getData());
            itemsShared.add(item);
            permissionsMissing = takePersistablePermissionsIfNeeded(resultData, item.getContentUri());
        } else {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            } else {
                Logging.log(Log.ERROR, TAG, "Unexpected warning about file permissions");
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem_scoped_storage);
            }
        }
        FolderItem.cacheDocumentInformation(requireContext(), itemsShared, listener);
        if(permissionsMissing) {
            //DisplayUtils.postOnUiThread(() -> getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getContext().getString(R.string.likely_file_unusable_shared_without_permissions_pattern), R.string.button_no, R.string.button_yes, new TakeCopyOfFilesActionListener(getUiHelper(), itemsShared)));
            processOpenDocumentsWithoutPermissions(itemsShared);
            return new ArrayList<>();
        }
        return itemsShared;
    }

    /**
     *
     * @param resultData
     * @param itemUri
     * @return true if a permission needed was missing.
     */
    private boolean takePersistablePermissionsIfNeeded(Intent resultData, Uri itemUri) {
        boolean allUriFlagsAreSet = true;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int desiredFlags = getViewPrefs().getSelectedUriPermissionFlags();
            //resultData.getFlags();
            int flagsProvided = IOUtils.filterToCombinedFlags(resultData.getFlags(), desiredFlags);
            allUriFlagsAreSet = flagsProvided == desiredFlags;

            if(!allUriFlagsAreSet) {
                Logging.log(Log.ERROR, TAG, "Wanted permissions %1$d but received %2$d for uri %3$s", desiredFlags, flagsProvided, itemUri);
            }

            try {
                appSettingsViewModel.takePersistableUriPermissions(requireContext(), itemUri, desiredFlags, getViewPrefs().getSelectedUriPermissionConsumerId(), getViewPrefs().getSelectedUriPermissionConsumerPurpose());
                if(DocumentFile.fromSingleUri(requireContext(), itemUri).isDirectory()) {
//                     take Read permission for file select too.
                    appSettingsViewModel.takePersistableFileSelectionUriPermissions(requireContext(), itemUri, Intent.FLAG_GRANT_READ_URI_PERMISSION, getString(R.string.uri_permission_justification_file_selection));
                }
            } catch(SecurityException e) {
                Logging.log(Log.WARN, TAG, "Unable to take persistable permissions %2$d for URI : %1$s", itemUri, desiredFlags);
//                Logging.recordException(e);
            }
        }
        return !allUriFlagsAreSet;
    }

    public List<FolderItem> processOpenDocumentTree(Intent resultData, ProgressListener listener) {
        if (resultData.getData() == null) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            } else {
                Logging.log(Log.ERROR, TAG, "Unexpected warning about file permissions");
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem_scoped_storage);
            }
            return null;
        } else {
            Context context = getContext();
            if(context == null) {
                context = getUiHelper().getAppContext();
            }
            List<FolderItem> itemsShared = new ArrayList<>();
            // get a reference to permitted folder on the device.
            Uri permittedUri = resultData.getData();

            try {
                DocumentFile docFile = DocumentFile.fromTreeUri(context, permittedUri);
                if(docFile != null) {
                    boolean permissionsMissing = takePersistablePermissionsIfNeeded(resultData, permittedUri);
                    if(!permissionsMissing) {
                        FolderItem folderItem = new FolderItem(permittedUri, docFile);
                        folderItem.cacheFields(context);
                        itemsShared.add(folderItem);
                    } else {
                        Logging.log(Log.WARN, TAG, "File shared without needed permissions: " + docFile.getUri());
                        getView().post(new SafeRunnable(() -> getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.sorry_file_unusable_as_app_shared_from_does_not_provide_necessary_permissions), R.string.button_ok)));
                    }
                    listener.onProgress(1);
                } else {
                    itemsShared = processOpenDocuments(resultData, listener);
                }
            } catch(IllegalArgumentException e) {
                // this is most likely because it is not a folder.
                DocumentFile docFile = IOUtils.getSingleDocFile(context, permittedUri);
                boolean permissionsMissing = takePersistablePermissionsIfNeeded(resultData, docFile.getUri());

                FolderItem folderItem = new FolderItem(permittedUri, docFile);
                folderItem.cacheFields(context);
                itemsShared.add(folderItem);
                listener.onProgress(1);

                if(permissionsMissing) {
                    processOpenDocumentsWithoutPermissions(itemsShared);
                    return new ArrayList<>(0);
                }
            }
            return itemsShared;
        }
    }

    @Override
    public boolean onBackButton() {
        return folderPathView.clickBreadcrumb(folderPathView.getBreadcrumbCount()-1);
    }

    private void applyNewRootsAdapter(DocumentFileArrayAdapter mappedArrayAdapter) {
        folderRootsAdapter = mappedArrayAdapter;
        folderRootFolderSpinner.setAdapter(folderRootsAdapter);
    }

    private void bindDataToView() {

        if(getListAdapter() == null) {
            applyNewRootsAdapter(buildFolderRootsAdapter());

            final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(navListener, new FolderItemSelectListener<>((F)this), getViewPrefs());
            viewAdapter.setTaskListener(new UiUpdatingProgressListener(getUiHelper().getProgressIndicator(), R.string.progress_loading_folder_content));

            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }


            // need to load this before the list adapter is added else will load from the list adapter which hasn't been inited yet!
            HashSet<Long> currentSelection = getCurrentSelection(); // loaded from saved state

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);

            fileExtFilters.setListener(new FileExtFilterControlListener<>(getListAdapter()));

            DocumentFile initialFolder = null;
            Uri lastFolderUsed = getViewPrefs().getInitialFolder();
            if(lastFolderUsed != null) {
                //Only open the last opened Uri if we have access to it.
                initialFolder = IOUtils.getDocumentFileForUriLinkedToAnAccessibleRoot(requireContext(), lastFolderUsed);
            }
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
        layoutMan.setSpanSizeLookup(new FolderItemSpanSizeLookup<>(getListAdapter(), colsOnScreen));
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
        return CollectionUtils.toCsvList(pathHierarchy, "/");
    }

    private Map<String, DocumentFile> buildDocFilesRootsMapPreKitKat() {
        List<String> rootLabels = ArrayUtils.toArrayList(new String[]{getString(R.string.folder_root_root), getString(R.string.folder_root_userdata), getString(R.string.folder_extstorage)});

        List<File> rootPaths = ArrayUtils.toArrayList(new File[]{Environment.getRootDirectory(), Environment.getDataDirectory(), requireContext().getExternalFilesDir(null)});
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
        if(!getViewPrefs().isAllowFolderSelection()) {
            roots.put(getString(R.string.system_file_selector_label), null); // allow other apps to handle file selection
        }
        return roots;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private Map<String, DocumentFile> buildDocFilesRootsMap() {
        List<UriPermission> permissions = requireContext().getContentResolver().getPersistedUriPermissions();
        Map<String, DocumentFile> roots = new LinkedHashMap<>();
        roots.put("", null);
        int requiredFolderPermissions = getNecessaryFolderPermissions();
        boolean isNeedWrite = IOUtils.needsWritePermission(requiredFolderPermissions);
        for (UriPermission perm : permissions) {
            // don't show roots we don't yet have write permission for
            if (perm.isWritePermission() || (!isNeedWrite && perm.isReadPermission())) {
                try {
                    DocumentFile documentFile = DocumentFile.fromTreeUri(requireContext(), perm.getUri());
                    String rootName;
                    if(documentFile == null) {
                        Logging.log(Log.ERROR, TAG, "FileSelector Root added is null");
                        Logging.logAnalyticEvent(requireContext(), "FileSelector Root added is null");
                        rootName = "???";
                    } else {
                        rootName = IOUtils.getDocumentFilePath(requireContext(), documentFile);
                    }
                    if(roots.containsKey(rootName)) {
                        Logging.log(Log.ERROR, TAG,"Non unique file root found");
                        Logging.logAnalyticEvent(requireContext(), "Non unique file root found");
                        do {
                            rootName += ' ';
                        }while(roots.containsKey(rootName));
                    }
                    roots.put(rootName, documentFile);
                } catch (IllegalArgumentException e) {
                    // it's a file not a folder. Ignore the error.
                }

            }
        }
        roots.put(getString(R.string.files_cached_from_other_apps), IOUtils.getSharedFilesFolder(requireContext()));
        if(!getViewPrefs().isAllowFolderSelection() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            roots.put(getString(R.string.system_file_selector_label), null);
        }
        return roots;
    }

    public void onActionFilesReadyToShareWithRequester(HashSet<FolderItem> selectedItems, boolean somePermissionsMissing) {
        if(somePermissionsMissing) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.selected_files_missing_permissions_warning_message_pattern), new FileSelectionCancelledMissingPermissionsListener<>(getUiHelper()));
        } else {
            long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
            EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFolderItems(new ArrayList<>(selectedItems)));

            // now pop this screen off the stack.
            if (isVisible()) {
                Logging.log(Log.INFO, TAG, "removing from activity immediately");
                getParentFragmentManager().popBackStackImmediate();
            }
        }
        // notify the ui that the user may use the button again.
        onSaveActionFinished();
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter<?,?,FolderItem,?,?> listAdapter = getListAdapter();
        HashSet<FolderItem> selectedItems = listAdapter.getSelectedItems();
        addCurrentFolderToListIfEmptyAndFolderSelectionOkay(selectedItems);
        new SelectedFilesUriCheckTask<>((F)this, selectedItems).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void addCurrentFolderToListIfEmptyAndFolderSelectionOkay(HashSet<FolderItem> selectedItems) {
        FolderItemRecyclerViewAdapter<?,?,FolderItem,?,?> listAdapter = getListAdapter();
        if (selectedItems.isEmpty() && (getViewPrefs().isAllowItemSelection() && getViewPrefs().isAllowFolderSelection()) && !getViewPrefs().isMultiSelectionEnabled()) {
            if(listAdapter.getActiveFolder() != null) {
                FolderItem folderItem = new FolderItem(listAdapter.getActiveRootUri(), listAdapter.getActiveFolder());

                selectedItems.add(folderItem);
            }
        }
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        // heading field not used (missing from layout)
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
    }

    @Override
    public void onClickCancelFileSelectionButton() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFiles(getViewPrefs().getInitialSelection()));
        super.onClickCancelFileSelectionButton();
    }

    private class RootFolderSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            setBackButtonHandlerEnabled(false); // will be re-enabled if needed once item select finishes.
            if(listViewStates != null) {
                listViewStates.clear();
            }
            Context ctx = parent.getContext();
            if (id > 0) {
                DisplayUtils.postOnUiThread(() -> {
                    DocumentFileArrayAdapter adapter = (DocumentFileArrayAdapter) parent.getAdapter();
                    DocumentFile newRoot = adapter.getItemValue(position);
                    if(newRoot != null) {
                        fileExtFilters.setEnabled(true);
                        
                        DocumentFile currentRoot = getListAdapter().getActiveFolder();
                        try {
                            if (currentRoot == null && getViewPrefs().getInitialFolder() != null) {
                                DocumentFile file = IOUtils.getTreeLinkedDocFile(ctx, newRoot.getUri(), getViewPrefs().getInitialFolder());
                                getListAdapter().updateContentAndRoot(ctx, newRoot, file);
                            } else {
                                getListAdapter().resetRoot(ctx, newRoot);
                            }
                        } catch(IllegalStateException e) {
                            getListAdapter().resetRoot(ctx, newRoot); // just use the current root and ignore the initial folder.
                        }
                        deselectAllItems();
                    } else {
                        getListAdapter().clearAndAddAllFileTypesToShow(getViewPrefs().getVisibleFileTypes());
                        getListAdapter().resetRoot(ctx, newRoot);
                        deselectAllItems();
                        fileExtFilters.setEnabled(false);
                        retrieveFilesFromSystemPicker(getViewPrefs().getInitialFolder());
                    }
                });
            } else {
                // just show the empty view (if we're not already).
                if(getListAdapter().getActiveFolder() != null) {
                    
                    getListAdapter().resetRoot(ctx, null);
                    deselectAllItems();
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    public View getFileExtFilters() {
        return fileExtFilters;
    }

    public void addRootFolder(@NonNull DocumentFile docFile) {
        String rootPathStr = IOUtils.getDocumentFilePath(requireContext(), docFile);
        folderRootsAdapter.add(rootPathStr, docFile);
        folderRootFolderSpinner.setSelection(folderRootsAdapter.getCount() - 1); // calls listener because it's a definite change.
//        DisplayUtils.selectSpinnerItemAndCallItemSelectedListener(folderRootFolderSpinner, folderRootsAdapter.getCount() - 1);
    }

    private class DocumentFileNavigationListener implements AbstractBreadcrumbsView.NavigationListener<DocumentFile> {
        @Override
        public void onBreadcrumbClicked(DocumentFile pathItemFile) {
            if (getListAdapter().getActiveFolder() == null) {
                return; // UI out of sync. Do nothing and be patient.
            }
            if (getListAdapter().getActiveFolder().getUri().equals(pathItemFile.getUri())) {
                getListAdapter().rebuildContentView(requireContext());
            } else {
                boolean loadedFromMemory = false;
                if (listViewStates != null) {
                    Iterator<Map.Entry<DocumentFile, List<Object>>> iterator = listViewStates.entrySet().iterator();
                    Map.Entry<DocumentFile, List<Object>> item;
                    while (iterator.hasNext()) {
                        item = iterator.next();
                        if (item.getKey().getUri().equals(pathItemFile.getUri())) {
                            loadedFromMemory = true;
                            if (getList().getLayoutManager() != null) {
                                FolderItemRecyclerViewAdapter<?,?,FolderItem,?,?> adapter = getListAdapter();
                                Objects.requireNonNull(adapter).restoreState((FolderItemRecyclerViewAdapter.SavedState) item.getValue().get(0));
                                getList().getLayoutManager().onRestoreInstanceState((Parcelable) item.getValue().get(1));

                            } else {
                                Logging.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
                            }
                            // only keep a single level below our current level in case we moved back accidentally.
                            if (iterator.hasNext()) {
                                iterator.next();
                                if (iterator.hasNext()) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                }
                if (!loadedFromMemory) {
                    getListAdapter().changeFolderViewed(requireContext(), pathItemFile);
                }
            }
        }
    }
    private static class FolderItemSelectListener<F extends RecyclerViewDocumentFileFolderItemSelectFragment<F,FUIH,P>, FUIH extends FragmentUIHelper<FUIH,F>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA,P,T,VH>, LVA extends FolderItemRecyclerViewAdapter<LVA,P,T,MSL,VH>, P extends FolderItemViewAdapterPreferences<P>, T extends FolderItem, VH extends FolderItemRecyclerViewAdapter.FolderItemViewHolder<VH,LVA,T,MSL,P>> extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA, P, T,VH> {

        private final F ownerFragment;

        public FolderItemSelectListener(F ownerFragment) {
            this.ownerFragment = ownerFragment;
        }


        @Override
        public void onItemSelectionCountChanged(LVA adapter, int itemCount) {
            super.onItemSelectionCountChanged(adapter, itemCount);
            long totalBytes = 0;
            for(T item : adapter.getSelectedItems()) {
                totalBytes += item.getFileLength();
            }
            ownerFragment.updateSelectedFileText(itemCount, totalBytes);
        }
    }

    protected void updateSelectedFileText(int itemCount, long totalBytes) {
        selectedFileInfoView.setText(getString(R.string.files_to_upload_count_label_pattern, itemCount, IOUtils.bytesToNormalizedText(totalBytes)));
    }
}
