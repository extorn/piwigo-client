package delit.piwigoclient.ui.file;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.GridLayoutManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.material.button.MaterialButton;

import org.greenrobot.eventbus.EventBus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.AbstractBreadcrumbsView;
import delit.libs.ui.view.DocumentFileBreadcrumbsView;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.fragment.LongSelectableSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.TrackableRequestEvent;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static android.view.View.GONE;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class RecyclerViewDocumentFileFolderItemSelectFragment extends RecyclerViewLongSetSelectFragment<FolderItemRecyclerViewAdapter, FolderItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String TAG = "RVFolderSelFrg";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewFolderItemSelectFragment.actionStartTime";
    private DocumentFileBreadcrumbsView folderPathView;
    private Spinner folderRootFolderSpinner;
    private DocumentFileArrayAdapter folderRootsAdapter;
    private long startedActionAtTime;
    private FolderItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<Uri, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)
    private AppSettingsViewModel appSettingsViewModel;
    private ProgressIndicator progressIndicator;
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
        Uri currentFolder = getCurrentFolder();
        if(currentFolder != null) {
            // update the current folder.
            getViewPrefs().withInitialFolder(currentFolder);
        }
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_ACTION_START_TIME, startedActionAtTime);
        BundleUtils.writeMap(outState, STATE_LIST_VIEW_STATE, listViewStates);
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
            listViewStates = BundleUtils.readMap(savedInstanceState, STATE_LIST_VIEW_STATE, new LinkedHashMap<>(), Parcelable.class.getClassLoader());
        }

        startedActionAtTime = System.currentTimeMillis();

        progressIndicator = v.findViewById(R.id.progress_indicator);
        progressIndicator.setVisibility(View.GONE);

        folderRootFolderSpinner = ViewCompat.requireViewById(v, R.id.folder_root_spinner);
        folderRootFolderSpinner.setOnItemSelectedListener(new RootFolderSelectionListener());

        folderPathView = v.findViewById(R.id.folder_path);
        folderPathView.setNavigationListener(new DocumentFileNavigationListener());

        fileExtFilters = v.findViewById(R.id.file_ext_filters);

        MaterialButton folderViewRefreshButton = v.findViewById(R.id.folder_refresh_button);
        folderViewRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshCurrentFolderView();
            }
        });

        MaterialButton addRootButton = v.findViewById(R.id.add_root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addRootButton.setOnClickListener(v1 -> retrievePermissionsForUri(null));
        } else {
            addRootButton.setVisibility(GONE);
        }

        MaterialButton removeRootButton = v.findViewById(R.id.remove_root);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            removeRootButton.setOnClickListener(view -> {
                onRemoveRoot();
            });
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
            LiveData<List<UriPermissionUse>> uriPermissionsData = appSettingsViewModel.getAllForUri(treeUri);
            uriPermissionsData.observe(getViewLifecycleOwner(), new Observer<List<UriPermissionUse>>() {
                @Override
                public void onChanged(List<UriPermissionUse> permissionsHeld) {
                    uriPermissionsData.removeObserver(this);
                    if(permissionsHeld != null) {
                        List<String> consumerDescs = new ArrayList<>();
                        for (UriPermissionUse use : permissionsHeld) {
                            if (!use.consumerId.equals(UriPermissionUse.CONSUMER_ID_FILE_SELECT)
                            && !use.consumerId.equals(getViewPrefs().getSelectedUriPermissionConsumerId())) {
                                consumerDescs.add(use.localizedConsumerName);
                            }
                        }
                        if (consumerDescs.size() > 0) {
                            StringBuilder consumerListSb = new StringBuilder();
                            for (Iterator<String> iterator = consumerDescs.iterator(); iterator.hasNext(); ) {
                                String consumerDesc = iterator.next();
                                consumerListSb.append(consumerDesc);
                                if(iterator.hasNext()) {
                                    consumerListSb.append('\n');
                                }
                            }
                            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.uri_still_needed_pattern, consumerListSb));
                        } else {
                            int flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            Uri treeUri = IOUtils.getTreeUri(documentFile.getUri());
                            appSettingsViewModel.releasePersistableUriPermission(requireContext(), permissionsHeld, treeUri, flags);

                            folderRootsAdapter.remove(folderRootsAdapter.getItem(pos));
                            folderRootFolderSpinner.setSelection(0); //  calls listener because it's a definite change.
//                            DisplayUtils.selectSpinnerItemAndCallItemSelectedListener(folderRootFolderSpinner, 0);
                        }
                    }
                }
            });
        }
    }

    private void refreshCurrentFolderView() {
        getListAdapter().rebuildContentView();
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
            progressIndicator.showProgressIndicator(-1);
            updateListOfFileExtensionsForAllVisibleFiles(newFolderContent);
            filterListOfVisibleFilesInListToMatchAcceptableFileExtensions();

            recordTheCurrentListOfVisibleItemsInState(oldFolder);
        }

        @Override
        public void onPostFolderOpened(DocumentFile oldFolder, DocumentFile newFolder) {
            // scroll to the first item in the list.
            getList().scrollToPosition(0);
            buildBreadcrumbs(newFolder);
            fileExtFilters.setVisibleFilters(getListAdapter().getFileExtsInCurrentFolder());
            fileExtFilters.selectAll();

            DisplayUtils.postOnUiThread(() -> progressIndicator.hideProgressIndicator());

        }
    }

    private void recordTheCurrentListOfVisibleItemsInState(DocumentFile folder) {
        if (folder != null) {
            if (listViewStates == null) {
                listViewStates = new LinkedHashMap<>(5);
            }
            listViewStates.put(folder.getUri(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
        }
    }

    private void filterListOfVisibleFilesInListToMatchAcceptableFileExtensions() {
        getViewPrefs().withVisibleContent(fileExtFilters.getAllPossibleFilters(), getViewPrefs().getFileSortOrder());
    }

    private void updateListOfFileExtensionsForAllVisibleFiles(DocumentFile[] newFolderContent) {
        if (getViewPrefs().getVisibleFileTypes() != null) {
            if(fileExtFilters.getAllPossibleFilters() == null) {
                fileExtFilters.setAllPossibleFilters(new HashSet<>(getViewPrefs().getVisibleFileTypes()));
            }
        }
        if (fileExtFilters.getAllPossibleFilters() != null) {

            SortedSet<String> fileExtsInFolderMatchingMimeTypesWanted = getViewPrefs().getVisibleFileTypesForMimes(IOUtils.getUniqueExtAndMimeTypes(newFolderContent));
            if (fileExtsInFolderMatchingMimeTypesWanted != null) {
                // add any extra that have come from mime types now visible in this folder.
                fileExtFilters.getAllPossibleFilters().addAll(fileExtsInFolderMatchingMimeTypesWanted);
            }
        }
    }

    private void buildBreadcrumbs(DocumentFile newFolder) {
//        item = folderRootsAdapter.getItemByValue(newFolder);
//        folderRootFolderSpinner.setSelection(folderRootsAdapter.getPosition(item), false);
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
            getUiHelper().showDetailedShortMsg(R.string.alert_error, R.string.no_suitable_activity_found);
            getUiHelper().isTrackingRequest(eventId); // clear the tracked id
        }
    }

    private void retrieveFilesFromSystemPicker(@Nullable Uri uri) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, CollectionUtils.asStringArray(getViewPrefs().getVisibleMimeTypes()));
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
            getUiHelper().showDetailedShortMsg(R.string.alert_error, R.string.no_suitable_activity_found);
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

    private List<FolderItemRecyclerViewAdapter.FolderItem> processOpenDocuments(Intent resultData) {
        ClipData clipData = resultData.getClipData();
        List<FolderItemRecyclerViewAdapter.FolderItem> itemsShared = new ArrayList<>();
        if(clipData != null) {
            int items = clipData.getItemCount();
            for (int i = 0; i < items; i++) {
                Uri itemUri = clipData.getItemAt(i).getUri();
//                // convert all uris to document uris (makes things easier (possibly) - at the expense of time...).
//                if(itemUri.getAuthority().equals(MediaStore.AUTHORITY)) {
//                    itemUri = MediaStore.getDocumentUri(requireContext(), itemUri);
//                }
                FolderItemRecyclerViewAdapter.FolderItem item = new FolderItemRecyclerViewAdapter.FolderItem(itemUri);
                itemsShared.add(item);
            }

        } else if(resultData.getData() != null) {
            FolderItemRecyclerViewAdapter.FolderItem item = new FolderItemRecyclerViewAdapter.FolderItem(resultData.getData());
            itemsShared.add(item);
        } else {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
        }
        return itemsShared;
    }

    private List<FolderItemRecyclerViewAdapter.FolderItem> processOpenDocumentTree(Intent resultData) {
        if (resultData.getData() == null) {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_to_access_local_filesystem);
            return null;
        } else {
            List<FolderItemRecyclerViewAdapter.FolderItem> itemsShared = new ArrayList<>();
            // get a reference to permitted folder on the device.
            Uri permittedUri = resultData.getData();

            try {
                DocumentFile docFile = DocumentFile.fromTreeUri(requireContext(), permittedUri);
                if(docFile != null) {
                    final int takeFlags = resultData.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    appSettingsViewModel.takePersistableFileSelectionUriPermissions(requireContext(), permittedUri, takeFlags, getString(R.string.file_selection_heading));
                    itemsShared.add(new FolderItemRecyclerViewAdapter.FolderItem(permittedUri, docFile));
                } else {
                    itemsShared = processOpenDocuments(resultData);
                }
            } catch(IllegalArgumentException e) {
                // this is most likely because it is not a folder.
                itemsShared = processOpenDocuments(resultData);
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
            getListAdapter().changeFolderViewed(parent);
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

            final FolderItemRecyclerViewAdapter viewAdapter = new FolderItemRecyclerViewAdapter(requireContext(), navListener, new FolderItemRecyclerViewAdapter.MultiSelectStatusAdapter<>(), getViewPrefs());

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
                folderRootFolderSpinner.setSelection(selectRoot);
            }
            buildBreadcrumbs(getListAdapter().getActiveFolder());
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

        List<UriPermission> permissions = requireContext().getContentResolver().getPersistedUriPermissions();
        Map<String, DocumentFile> roots = new LinkedHashMap<>();
        roots.put("", null);
        roots.put(getString(R.string.system_file_selector_label), null);
        for(UriPermission perm : permissions) {
            if(perm.isWritePermission()) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(requireContext(), perm.getUri());
                roots.put(documentFile == null ? "???" : IOUtils.getFilename(documentFile), documentFile);
            }
        }

        DocumentFileArrayAdapter adapter = new DocumentFileArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, roots);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }


    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        FolderItemRecyclerViewAdapter listAdapter = getListAdapter();
        HashSet<FolderItemRecyclerViewAdapter.FolderItem> selectedItems = listAdapter.getSelectedItems();
        if (selectedItems.isEmpty() && getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            selectedItems = new HashSet<>(1);
            if(listAdapter.getActiveFolder() != null) {
                selectedItems.add(new FolderItemRecyclerViewAdapter.FolderItem(listAdapter.getActiveRootUri(), listAdapter.getActiveFolder()));
            }
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new FileSelectionCompleteEvent(getActionId(), actionTimeMillis).withFolderItems(new ArrayList<>(selectedItems)));
//        listAdapter.cancelAnyActiveFolderMediaScan();
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

        private final FolderItemRecyclerViewAdapter listAdapter;

        public FileExtFilterControlListener(FolderItemRecyclerViewAdapter adapter) {
            this.listAdapter = adapter;
        }

        @Override
        public void onFilterUnchecked(String fileExt) {
            SortedSet<String> visibleFileTypes = listAdapter.getAdapterPrefs().getVisibleFileTypes();
            visibleFileTypes.remove(fileExt);
        }

        @Override
        public void onFilterChecked(String fileExt) {
            SortedSet<String> visibleFileTypes = listAdapter.getAdapterPrefs().getVisibleFileTypes();
            visibleFileTypes.add(fileExt);
        }

        @Override
        public void onFiltersChanged(boolean filterHidden, boolean filterShown) {
            listAdapter.rebuildContentView();
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
                progressIndicator.showProgressIndicator(-1);
                DisplayUtils.postOnUiThread(() -> {
                    DocumentFileArrayAdapter adapter = (DocumentFileArrayAdapter) parent.getAdapter();
                    DocumentFile newRoot = adapter.getItemValue(position);
                    if(newRoot != null) {
                        fileExtFilters.setEnabled(true);
                        getViewPrefs().withVisibleContent(fileExtFilters.getAllPossibleFilters(), getViewPrefs().getFileSortOrder());
                        getListAdapter().resetRoot(newRoot);
                        deselectAllItems();
                        getListAdapter().setInitiallySelectedItems(); // adapter needs to be populated for this to work.
                        if(listViewStates != null) {
                            listViewStates.clear();
                        }
                    } else {
                        getViewPrefs().withVisibleContent(fileExtFilters.getAllPossibleFilters(), getViewPrefs().getFileSortOrder());
                        getListAdapter().resetRoot(newRoot);
                        deselectAllItems();
                        fileExtFilters.setEnabled(false);
                        retrieveFilesFromSystemPicker(getViewPrefs().getInitialFolder());
                    }
                    progressIndicator.hideProgressIndicator();
                });
            } else {
                // just show the empty view.
                getViewPrefs().withVisibleContent(fileExtFilters.getAllPossibleFilters(), getViewPrefs().getFileSortOrder());
                getListAdapter().resetRoot(null);
                deselectAllItems();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private static class SharedFilesIntentProcessingTask extends AsyncTask<Intent, Object, List<FolderItemRecyclerViewAdapter.FolderItem>> {

        private final WeakReference<RecyclerViewDocumentFileFolderItemSelectFragment> parentRef;

        public SharedFilesIntentProcessingTask(RecyclerViewDocumentFileFolderItemSelectFragment parent) {
             parentRef = new WeakReference<>(parent);
        }

        @Override
        protected List<FolderItemRecyclerViewAdapter.FolderItem> doInBackground(Intent[] objects) {
            Intent intent = objects[0];

            if (intent.getClipData() != null) {
                return parentRef.get().processOpenDocuments(intent);
            } else {
                return parentRef.get().processOpenDocumentTree(intent);
            }
        }

        @Override
        protected void onPostExecute(List<FolderItemRecyclerViewAdapter.FolderItem> folderItems) {
            super.onPostExecute(folderItems);

            if(folderItems.size() == 1) {
                FolderItemRecyclerViewAdapter.FolderItem item = folderItems.get(0);
                DocumentFile docFile = item.cacheDocFileFields(parentRef.get().requireContext());
                if(item.isFolder()) {
                    parentRef.get().addRootFolder(docFile);
                } else {
                    parentRef.get().getListAdapter().addItems(folderItems);
                    parentRef.get().selectAllItems();
                }
            } else {
                parentRef.get().getListAdapter().addItems(folderItems);
                parentRef.get().selectAllItems();
            }
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
            getListAdapter().changeFolderViewed(pathItemFile);
            if (listViewStates != null) {
                Iterator<Map.Entry<Uri, Parcelable>> iter = listViewStates.entrySet().iterator();
                Map.Entry<Uri, Parcelable> item;
                while (iter.hasNext()) {
                    item = iter.next();
                    if (item.getKey().equals(pathItemFile.getUri())) {
                        if (getList().getLayoutManager() != null) {
                            getList().getLayoutManager().onRestoreInstanceState(item.getValue());
                        } else {
                            Crashlytics.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
                        }
                        iter.remove();
                        while (iter.hasNext()) {
                            iter.next();
                            iter.remove();
                        }
                    }
                }
            }
        }
    }
}
