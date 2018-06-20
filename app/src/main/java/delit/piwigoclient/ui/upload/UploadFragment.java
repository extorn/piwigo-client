package delit.piwigoclient.ui.upload;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.BiArrayAdapter;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.ArrayUtils;


/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 * Use the {@link UploadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class UploadFragment extends MyFragment implements FilesToUploadRecyclerViewAdapter.RemoveListener {

    // the fragment initialization parameters
    private static final String ARG_CURRENT_GALLERY_ID = "currentGalleryId";
    private static final String TAG = "UploadFragment";
    private static final String SAVED_STATE_CURRENT_GALLERY = "currentGallery";
    private static final String SAVED_STATE_PRIVACY_LEVEL_WANTED = "privacyLevelWanted";
    private static final String SAVED_STATE_UPLOAD_ALBUM_ID = "uploadAlbumId";
    private static final String SAVED_STATE_FILES_BEING_UPLOADED = "filesBeingUploaded";
    private static final String SAVED_STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String SAVED_SUB_CAT_NAMES_ACTION_ID = "subCategoryNamesActionId";
    private static final String ARG_SELECT_FILES_ACTION_ID = "selectFilesActionId";
    private AvailableAlbumsListAdapter availableGalleries;
    private RecyclerView filesForUploadView;
    private Button uploadFilesNowButton;
    private AppCompatSpinner selectedGallerySpinner;
    private Spinner privacyLevelSpinner;
    private CustomImageButton fileSelectButton;
    private CategoryItem currentGallery;
    private long privacyLevelWanted;
    private Long uploadToAlbumId;
    private Long uploadJobId;
    private ArrayList<File> filesForUpload = new ArrayList<>();
    private long subCategoryNamesActionId = -1;
    private FloatingActionButton retryRetrieveAlbumNamesButton;
    private CustomImageButton newGalleryButton;
    private FilesToUploadRecyclerViewAdapter filesToUploadAdapter;

    public UploadFragment() {
    }

    public static UploadFragment newInstance(long currentGalleryId, int actionId) {
        UploadFragment fragment = new UploadFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CURRENT_GALLERY_ID, currentGalleryId);
        args.putInt(ARG_SELECT_FILES_ACTION_ID, actionId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SAVED_STATE_CURRENT_GALLERY, currentGallery);
        outState.putLong(SAVED_STATE_PRIVACY_LEVEL_WANTED, privacyLevelWanted);
        outState.putLong(SAVED_STATE_UPLOAD_ALBUM_ID, uploadToAlbumId);
        outState.putSerializable(SAVED_STATE_FILES_BEING_UPLOADED, filesForUpload);
        outState.putLong(SAVED_SUB_CAT_NAMES_ACTION_ID, subCategoryNamesActionId);
        if(uploadJobId != null) {
            outState.putLong(SAVED_STATE_UPLOAD_JOB_ID, uploadJobId);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            uploadToAlbumId = getArguments().getLong(ARG_CURRENT_GALLERY_ID);
            currentGallery = CategoryItem.ROOT_ALBUM;
            if(uploadToAlbumId != currentGallery.getId()) {
                currentGallery = new CategoryItem(uploadToAlbumId);
            }
            int fileSelectAction = getArguments().getInt(ARG_SELECT_FILES_ACTION_ID);
            if(fileSelectAction >= 0) {
                getUiHelper().setTrackingRequest(fileSelectAction);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onEvent(FileSelectionCompleteEvent stickyEvent) {
        // Integer.MIN_VALUE is a special flag to allow external apps to call in and their events to always be handled.
        if(getUiHelper().isTrackingRequest(stickyEvent.getActionId()) || stickyEvent.getActionId() == Integer.MIN_VALUE) {
            EventBus.getDefault().removeStickyEvent(stickyEvent);
            updateFilesForUploadList(stickyEvent.getSelectedFiles());
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        final PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        if ((sessionDetails == null || (!sessionDetails.isAdminUser() && !sessionDetails.isUseCommunityPlugin())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_upload, container, false);

        AdView adView = view.findViewById(R.id.upload_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences viewPrefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.withShowHierachy();

        selectedGallerySpinner = view.findViewById(R.id.selected_gallery);
        availableGalleries = new AvailableAlbumsListAdapter(viewPrefs, currentGallery, getContext(), android.R.layout.simple_spinner_item);
        availableGalleries.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selectedGallerySpinner.setEnabled(false);
        selectedGallerySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                uploadToAlbumId = id;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        selectedGallerySpinner.setAdapter(availableGalleries);

        fileSelectButton = view.findViewById(R.id.select_files_for_upload_button);
        fileSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
                    String serverUri = ConnectionPreferences.getTrimmedNonNullPiwigoServerAddress(prefs, getContext());
                    getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri),new LoginResponseHandler().invokeAsync(getContext()));
                } else {
                    FileSelectionNeededEvent event = new FileSelectionNeededEvent(true, false, true);
                    ArrayList<String> allowedFileTypes = new ArrayList<>(sessionDetails.getAllowedFileTypes());
                    event.withInitialFolder(Environment.getExternalStorageDirectory().getAbsolutePath());
                    event.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.LAST_MODIFIED_DATE);
                    getUiHelper().setTrackingRequest(event.getActionId());
                    EventBus.getDefault().post(event);
                }
            }
        });

        newGalleryButton = view.findViewById(R.id.add_new_gallery_button);
        newGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CategoryItemStub selectedGallery = (CategoryItemStub)selectedGallerySpinner.getSelectedItem();
                if(selectedGallery != null) {
                    AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(selectedGallery);
                    getUiHelper().setTrackingRequest(event.getActionId());
                    EventBus.getDefault().post(event);
                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
                }
            }
        });

        filesForUploadView = view.findViewById(R.id.selected_files_for_upload);

        privacyLevelSpinner = view.findViewById(R.id.privacy_level);

        CharSequence[] privacyLevelsText = getResources().getTextArray(R.array.privacy_levels_groups_array);
        long[] privacyLevelsValues = ArrayUtils.getLongArray(getResources().getIntArray(R.array.privacy_levels_values_array));
        BiArrayAdapter<CharSequence> privacyLevelOptionsAdapter = new BiArrayAdapter(getContext(), android.R.layout.simple_spinner_item, 0, privacyLevelsText, privacyLevelsValues);
//        if(!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
//            // remove the "admin only" privacy option.
//            privacyLevelOptionsAdapter.remove(privacyLevelOptionsAdapter.getItemById(8)); // Admin ID
//        }
        // Specify the layout to use when the list of choices appears
        privacyLevelOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

// Apply the filesToUploadAdapter to the spinner
        privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);
        privacyLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                privacyLevelWanted = id;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        privacyLevelSpinner.setSelection(0);

        uploadFilesNowButton = view.findViewById(R.id.upload_files_button);
        uploadFilesNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadFiles();
            }
        });

        if (savedInstanceState != null) {
            // update view with saved data
            currentGallery = (CategoryItem) savedInstanceState.getSerializable(SAVED_STATE_CURRENT_GALLERY);
            privacyLevelWanted = savedInstanceState.getLong(SAVED_STATE_PRIVACY_LEVEL_WANTED);
            uploadToAlbumId = savedInstanceState.getLong(SAVED_STATE_UPLOAD_ALBUM_ID);
            if(savedInstanceState.containsKey(SAVED_STATE_UPLOAD_JOB_ID)) {
                uploadJobId = savedInstanceState.getLong(SAVED_STATE_UPLOAD_JOB_ID);
            }
            filesForUpload = (ArrayList) savedInstanceState.getSerializable(SAVED_STATE_FILES_BEING_UPLOADED);
            subCategoryNamesActionId = savedInstanceState.getLong(SAVED_SUB_CAT_NAMES_ACTION_ID);
        }

        retryRetrieveAlbumNamesButton = view.findViewById(R.id.upload_retryAction_actionButton);
        retryRetrieveAlbumNamesButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    invokeRetrieveSubCategoryNamesCall();
                    retryRetrieveAlbumNamesButton.setVisibility(View.GONE);
                }
                return true;
            }
        });
        retryRetrieveAlbumNamesButton.setVisibility(View.GONE);

        int position = availableGalleries.getPosition(uploadToAlbumId);
        if(position >= 0) {
            // item may not be found if it has just been deleted from the server or we have changed server we're connected to.
            selectedGallerySpinner.setSelection(position);
        }
        if(privacyLevelWanted >= 0) {
            privacyLevelSpinner.setSelection(privacyLevelOptionsAdapter.getPosition(privacyLevelWanted));
        }

        boolean showLargeFileThumbnails = prefs.getBoolean(getString(R.string.preference_data_upload_large_thumbnail_key), getResources().getBoolean(R.bool.preference_data_upload_large_thumbnail_default));
        int columnsToShow = selectBestColumnCountForScreenSize();

        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), columnsToShow);
        filesForUploadView.setLayoutManager(gridLayoutMan);

        FilesToUploadRecyclerViewAdapter filesForUploadAdapter = new FilesToUploadRecyclerViewAdapter(filesForUpload, getContext(), this);
        if(showLargeFileThumbnails) {
            filesForUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID);
        } else {
            filesForUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_LIST);
        }
        filesForUploadView.setAdapter(filesForUploadAdapter);

        updateUiUploadStatusFromJobIfRun(container.getContext(), filesForUploadAdapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // need to register here so FileSelectionComplete events always get through after the UI has been built when forked from different process
        if(!EventBus.getDefault().isRegistered(this)) {
            /* Need to wrap this call to prevent double registration when selecting files in this app from this fragment since we de-register on detach not stop which
             * is not at the same lifecycle point as this one (so events are captured when on the backstack).
             */
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // This block is to hopefully protect against a WindowManager$BadTokenException when showing a dialog as part of this call.
        if(getActivity().isDestroyed() || getActivity().isFinishing()) {
            return;
        }

        // don't do this if the activity is finished of finishing.
        if (subCategoryNamesActionId < 0) {
            invokeRetrieveSubCategoryNamesCall();
        }
    }

    private void invokeRetrieveSubCategoryNamesCall() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            subCategoryNamesActionId = addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumsAdminResponseHandler().invokeAsync(getContext()));
        } else if(sessionDetails != null && sessionDetails.isUseCommunityPlugin()) {
            final boolean recursive = true;
            subCategoryNamesActionId = addActiveServiceCall(R.string.progress_loading_albums, new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive).invokeAsync(getContext()));
        } else {
            final boolean recursive = true;
            subCategoryNamesActionId = addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive).invokeAsync(getContext()));
        }
    }

    private float getScreenWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float)dm.widthPixels / dm.xdpi;
    }

    private int getDefaultImagesColumnCount() {
        float screenWidth = getScreenWidth();
        int columnsToShow = Math.round(screenWidth - (screenWidth % 1)); // allow 1 inch per column
        return Math.max(1,columnsToShow);
    }

    private int selectBestColumnCountForScreenSize() {
        int mColumnCount = getDefaultImagesColumnCount();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mColumnCount = prefs.getInt(getString(R.string.preference_data_upload_preferredColumnsLandscape_key), mColumnCount);
        } else {
            mColumnCount = prefs.getInt(getString(R.string.preference_data_upload_preferredColumnsPortrait_key), mColumnCount);
        }
        return Math.max(1,mColumnCount);
    }

    private void updateUiUploadStatusFromJobIfRun(Context context, FilesToUploadRecyclerViewAdapter filesForUploadAdapter) {

        UploadJob uploadJob = getActiveJob(context);

        if(uploadJob != null) {
            //register the potentially completely new handler to handle the existing job messages
            getUiHelper().getPiwigoResponseListener().switchHandlerId(uploadJob.getResponseHandlerId());
            getUiHelper().updateHandlerForAllMessages();

            uploadJobId = uploadJob.getJobId();
            uploadToAlbumId = uploadJob.getUploadToCategory();

            int position = availableGalleries.getPosition(uploadToAlbumId);
            if(position >= 0 && availableGalleries.getCount() > position) {
                selectedGallerySpinner.setSelection(position);
            }

            privacyLevelWanted = uploadJob.getPrivacyLevelWanted();
            if(privacyLevelWanted >= 0) {
                privacyLevelSpinner.setSelection(((BiArrayAdapter)privacyLevelSpinner.getAdapter()).getPosition(privacyLevelWanted));
            }

            ArrayList<File> filesToBeUploaded = uploadJob.getFilesNotYetUploaded();

            filesForUploadAdapter.clear();
            filesForUploadAdapter.addAll(filesToBeUploaded);

            for (File f : filesForUploadAdapter.getFiles()) {
                int progress = uploadJob.getUploadProgress(f);
                filesForUploadAdapter.updateProgressBar(f, progress);
            }

            boolean jobIsComplete = uploadJob.isFinished();
            allowUserUploadConfiguration(uploadJob);
            if(!jobIsComplete) {
                // now register for any new messages (and pick up all messages in sequence)
                getUiHelper().handleAnyQueuedPiwigoMessages();
            }
        } else {
            allowUserUploadConfiguration(null);
        }
    }

    private void updateFilesForUploadList(ArrayList<File> filesToBeUploaded) {
        FilesToUploadRecyclerViewAdapter adapter = (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
        adapter.addAll(filesToBeUploaded);
        uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
    }

    private void uploadFiles() {

        UploadJob activeJob = null;
        if(uploadJobId != null) {
            activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        if(activeJob == null) {
            activeJob = buildNewUploadJob();
            if(activeJob != null) {
                uploadJobId = activeJob.getJobId();
            }
        }

        if(activeJob != null) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, Manifest.permission.WAKE_LOCK, getString(R.string.alert_wake_lock_permission_needed_to_keep_upload_job_running_with_screen_off));
        }
    }

    private UploadJob buildNewUploadJob() {
        FilesToUploadRecyclerViewAdapter fileListAdapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());

        if (fileListAdapter == null || uploadToAlbumId == null || uploadToAlbumId == CategoryItem.ROOT_ALBUM.getId()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
            return null;
        }

        ArrayList<File> filesForUpload = fileListAdapter.getFiles();

        int maxUploadSizeWantedThresholdMB = prefs.getInt(getString(R.string.preference_data_upload_max_filesize_mb_key), getResources().getInteger(R.integer.preference_data_upload_max_filesize_mb_default));
        final Set<File> filesForReview = new HashSet<>();
        StringBuilder filenameListStrB = new StringBuilder();
        for(File f : filesForUpload) {
            double fileLengthMB = ((double)f.length()) / 1024 / 1024;
            if(fileLengthMB > maxUploadSizeWantedThresholdMB) {
                if(filesForReview.size() > 0) {
                    filenameListStrB.append(", ");
                }
                filenameListStrB.append(f);
                filenameListStrB.append(String.format(Locale.getDefault(), "(%1$.1fMB)", fileLengthMB));
                filesForReview.add(f);
            }
        }
        if(filesForReview.size() > 0) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_files_larger_than_upload_threshold_pattern, filesForReview.size(), filenameListStrB.toString()), R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {

                final Set<File> filesToDelete = filesForReview;

                @Override
                public void onDismiss(AlertDialog dialog) {

                }

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if(Boolean.TRUE == positiveAnswer) {
                        for(File file : filesToDelete) {
                            onRemove(filesToUploadAdapter, file, false);
                        }
                    }
                }
            });
        }


        CategoryItemStub uploadToCategory = availableGalleries.getItemById(uploadToAlbumId);

        if (uploadToCategory == null) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
            return null;
        }

        long handlerId = getUiHelper().getPiwigoResponseListener().getHandlerId();
        return ForegroundPiwigoUploadService.createUploadJob(ConnectionPreferences.getActiveProfile(), filesForUpload, uploadToCategory, (int) privacyLevelWanted, handlerId);
    }

    private void allowUserUploadConfiguration(UploadJob uploadJob) {

        filesToUploadAdapter = (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
        boolean filesStillToBeUploaded = filesToUploadAdapter.getItemCount() > 0;
        boolean noJobIsYetConfigured = uploadJob == null;
        boolean jobIsFinished = uploadJob != null && uploadJob.isFinished();
        boolean jobIsRunningNow = uploadJob != null && uploadJob.isRunningNow();
        boolean jobIsSubmitted = uploadJob != null && uploadJob.isSubmitted();

        boolean jobYetToFinishUploadingFiles = filesStillToBeUploaded && (noJobIsYetConfigured || jobIsFinished || !(jobIsSubmitted || jobIsRunningNow));
        boolean jobYetToCompleteAfterUploadingFiles = !noJobIsYetConfigured && !filesStillToBeUploaded && !jobIsFinished && !jobIsRunningNow; // crashed job just loaded basically
        uploadFilesNowButton.setEnabled(jobYetToFinishUploadingFiles || jobYetToCompleteAfterUploadingFiles); // Allow restart of the job.
        fileSelectButton.setEnabled(noJobIsYetConfigured || jobIsFinished);
        newGalleryButton.setEnabled(noJobIsYetConfigured || jobIsFinished);
        selectedGallerySpinner.setEnabled(availableGalleries.getCount() > 0 && (noJobIsYetConfigured || jobIsFinished));
        privacyLevelSpinner.setEnabled(noJobIsYetConfigured || jobIsFinished);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNewGalleryCreated(AlbumCreatedEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
            uploadToAlbumId = event.getNewAlbumId();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            UploadJob activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
            boolean keepDeviceAwake = false;
            if (event.areAllPermissionsGranted()) {
                keepDeviceAwake = true;
            }
            //ensure the handler is actively listening before the job starts.
            getUiHelper().addBackgroundServiceCall(uploadJobId);
            ForegroundPiwigoUploadService.startActionRunOrReRunUploadJob(getContext(), activeJob, keepDeviceAwake);
            allowUserUploadConfiguration(activeJob);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
        if(filesForUploadView != null) {
            filesForUploadView.setAdapter(null);
        }
        if(selectedGallerySpinner != null) {
            selectedGallerySpinner.setAdapter(null);
        }
        if(privacyLevelSpinner != null) {
            privacyLevelSpinner.setAdapter(null);
        }
    }

    private void notifyUser(Context context, int titleId, String message) {
        if(getContext() == null) {
            notifyUserUploadStatus(context.getApplicationContext(), message);
        } else {
            getUiHelper().showOrQueueDialogMessage(titleId, message);
        }
    }

    private void notifyUserUploadStatus(Context ctx, String message) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx, getUiHelper().getDefaultNotificationChannelId())
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setContentTitle(ctx.getString(R.string.notification_upload_event))
                .setContentText(message)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setCategory("progress");
        } else {
            mBuilder.setCategory(Notification.CATEGORY_PROGRESS);
        }

//      Clear the last notification
        getUiHelper().clearNotification(TAG, 1);
        getUiHelper().showNotification(TAG, 1, mBuilder.build());
    }

    @Override
    public void onRemove(final FilesToUploadRecyclerViewAdapter adapter, final File itemToRemove, boolean longClick) {
        final UploadJob activeJob = getActiveJob(getContext());
        if (activeJob != null) {
            if(activeJob.isFinished()) {
                if(activeJob.uploadItemRequiresAction(itemToRemove)) {
                    String message = getString(R.string.alert_message_remove_file_server_state_incorrect);
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                        @Override
                        public void onDismiss(AlertDialog dialog) {
                        }

                        @Override
                        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                            if(Boolean.TRUE == positiveAnswer) {
                                activeJob.cancelFileUpload(itemToRemove);
                                adapter.remove(itemToRemove);
                            }
                        }
                    });
                } else {
                    // job stopped, but upload of this file never got to the server.
                    activeJob.cancelFileUpload(itemToRemove);
                    adapter.remove(itemToRemove);
                }
            } else {
                // job running.
                boolean immediatelyCancelled = activeJob.cancelFileUpload(itemToRemove);
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_message_file_upload_cancelled_pattern, itemToRemove.getName()));
                if(immediatelyCancelled) {
                    adapter.remove(itemToRemove);
                }
            }
        } else {
            if(longClick) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_delete_all_files_selected_for_upload), R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                    @Override
                    public void onDismiss(AlertDialog dialog) {

                    }

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if(Boolean.TRUE == positiveAnswer) {
                            adapter.clear();
                            uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
                        }
                    }
                });
            } else {
                adapter.remove(itemToRemove);
                uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
            }
        }
    }

    private void updateSpinnerWithNewAlbumsList(ArrayList<CategoryItemStub> albums) {

        this.availableGalleries.clear();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean autoSelectCommunityAlbum = sessionDetails != null && sessionDetails.isFullyLoggedIn() && sessionDetails.isUseCommunityPlugin();

        if (currentGallery.getId() == 0) {
            this.availableGalleries.add(CategoryItemStub.ROOT_GALLERY);
        }

        if(uploadToAlbumId == null) {
            uploadToAlbumId = currentGallery.getId();
        }

        this.availableGalleries.addAll(albums);
        int position = availableGalleries.getPosition(uploadToAlbumId);

        if(position >= 0) {
            selectedGallerySpinner.setSelection(position);
        }

        if(autoSelectCommunityAlbum) {
            for(CategoryItemStub album : albums) {
                if(album.getName().equals(sessionDetails.getUsername())) {
                    position = availableGalleries.getPosition(album);
                    selectedGallerySpinner.setSelection(position);
                    break;
                }
            }
        }
        selectedGallerySpinner.setEnabled(true);
        subCategoryNamesActionId = -1;
        UploadJob uploadJob = getActiveJob(getContext());
        allowUserUploadConfiguration(uploadJob);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new ForegroundPiwigoFileUploadResponseListener(context);
    }

    private class ForegroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener {

        ForegroundPiwigoFileUploadResponseListener(Context context) {
            super(context);
        }

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
            if(response.getMessageId() == subCategoryNamesActionId) {
                // the retrieval of the album names failed. Need to allow retry and prevent use of the UI until then.
                selectedGallerySpinner.setEnabled(false);
                newGalleryButton.setEnabled(false);
                retryRetrieveAlbumNamesButton.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
            // default insists that it is attached and active fragment
            boolean canHandle = super.canHandlePiwigoResponseNow(response);
            // now allow anything not pertaining to the only non upload action on the page at any time.
            return canHandle || (response.getMessageId() != subCategoryNamesActionId);
        }

        @Override
        protected void onRequestedFileUploadCancelComplete(Context context, File cancelledFile) {
            if(isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());
                adapter.remove(cancelledFile);
            }
            UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, uploadJobId);
            if(uploadJob.isFilePartiallyUploaded(cancelledFile)) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_partial_upload_deleted));
            }
        }

        @Override
        protected void onUploadComplete(final Context context, final UploadJob job) {
            if(isAdded()) {
                if(job.hasJobCompletedAllActionsSuccessfully() && job.isFinished()) {
                    uploadJobId = null;
                    if (UploadFragment.this.isAdded()) {
                        ForegroundPiwigoUploadService.removeJob(job);
                    }
                    HashSet<File> filesPendingApproval = job.getFilesPendingApproval();
                    if(filesPendingApproval.size() > 0) {
                        String msg = getString(R.string.alert_message_info_files_already_pending_approval_pattern, filesPendingApproval.size());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg);
                    }
                } else {
                    int errMsgResourceId = R.string.alert_message_error_uploading_start;
                    if(job.getFilesNotYetUploaded().size() == 0) {
                        errMsgResourceId = R.string.alert_message_error_uploading_end;
                    }
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_title_error_upload, getContext().getString(errMsgResourceId), R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                        @Override
                        public void onDismiss(AlertDialog dialog) {
                        }

                        @Override
                        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                            if(positiveAnswer) {
                                ForegroundPiwigoUploadService.removeJob(job);
                                ForegroundPiwigoUploadService.deleteStateFromDisk(context, job);
                                allowUserUploadConfiguration(null);
                            }
                        }
                    });
                }
                allowUserUploadConfiguration(job);
            }
            notifyUserUploadJobComplete(context, job);
        }

        @Override
        protected void onLocalFileError(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadFileLocalErrorResponse response) {
            String errorMessage;
            if (response.getError() instanceof FileNotFoundException) {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_no_longer_available_message_pattern), response.getFileForUpload().getName());
            } else {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_read_error_message_pattern), response.getFileForUpload().getName());
            }
            notifyUser(context, R.string.alert_error, errorMessage);
        }

        @Override
        protected void onPrepareUploadFailed(Context context, final PiwigoResponseBufferingHandler.PiwigoPrepareUploadFailedResponse response) {

            PiwigoResponseBufferingHandler.Response error = response.getError();
            String errorMessage = null;
            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_failed_webserver), err.getStatusCode(), err.getErrorMessage());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                errorMessage = String.format(context.getString(R.string.alert_upload_failed_webresponse), ((PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error).getRawResponse());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_failed_piwigo), err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
            }
            if(errorMessage != null) {
                notifyUser(context, R.string.alert_error, errorMessage);
            }
        }

        @Override
        protected void onFileUploadProgressUpdate(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse response) {
            if(isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());
                adapter.updateProgressBar(response.getFileForUpload(), response.getProgress());
            }
            if(response.getProgress() == 100) {
                onFileUploadComplete(context, response);
            }
        }

        private void onFileUploadComplete(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadProgressUpdateResponse response) {

            UploadJob uploadJob = getActiveJob(context);

            for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory()));

            if(isAdded()) {

                FilesToUploadRecyclerViewAdapter adapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());
                adapter.remove(response.getFileForUpload());
            }
        }

        @Override
        protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse response) {
            if(isAdded()) {
                UploadJob uploadJob = getActiveJob(context);

                if(uploadJob != null) {
                    FilesToUploadRecyclerViewAdapter adapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());
                    for (File existingFile : response.getExistingFiles()) {
                        int progress = uploadJob.getUploadProgress(existingFile);
                        adapter.updateProgressBar(existingFile, progress);
//                    adapter.remove(existingFile);
                    }
                }
            }
            String message = String.format(context.getString(R.string.alert_items_for_upload_already_exist_message_pattern), response.getExistingFiles().size());
            notifyUser(context, R.string.alert_information, message);
        }

        @Override
        protected void onChunkUploadFailed(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadFileChunkFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            File fileForUpload = response.getFileForUpload();
            String errorMessage = null;

            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), fileForUpload.getName(), err.getStatusCode(), err.getErrorMessage());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), fileForUpload.getName(), err.getRawResponse());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), fileForUpload.getName(), err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
            }
            if (errorMessage != null) {
                notifyUser(context, R.string.alert_error, errorMessage);
            }
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onGetSubGalleryNames((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) {
                onGetSubGalleries((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) response);
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }

        @Override
        protected void onAddUploadedFileToAlbumFailure(Context context, final PiwigoResponseBufferingHandler.PiwigoUploadFileAddToAlbumFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            File fileForUpload = response.getFileForUpload();
            String errorMessage = null;

            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), fileForUpload.getName(), err.getStatusCode(), err.getErrorMessage());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), fileForUpload.getName(), err.getRawResponse());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
                if ("file already exists".equals(err.getPiwigoErrorMessage())) {
                    if(isAdded()) {
                        FilesToUploadRecyclerViewAdapter adapter = ((FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter());
                        adapter.remove(fileForUpload);
                    }
                    errorMessage = String.format(context.getString(R.string.alert_error_upload_file_already_on_server_message_pattern), fileForUpload.getName());
                } else {
                    errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), fileForUpload.getName(), err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
                }
            } else  if(error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
                errorMessage = ((PiwigoResponseBufferingHandler.CustomErrorResponse)error).getErrorMessage();
            }
            if(errorMessage != null) {
                notifyUser(context, R.string.alert_error, errorMessage);
            }
        }

        protected void onGetSubGalleries(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse response) {
            updateSpinnerWithNewAlbumsList(response.getAdminList().flattenTree());
        }

        protected void onGetSubGalleryNames(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
            updateSpinnerWithNewAlbumsList(response.getAlbumNames());
        }
    }



    private void notifyUserUploadJobComplete(Context context, UploadJob job) {
        String message;
        int titleId = R.string.alert_success;

        if (!job.hasJobCompletedAllActionsSuccessfully()) {
            message = context.getString(R.string.alert_upload_partial_success);
            titleId = R.string.alert_partial_success;
        } else {
            message = context.getString(R.string.alert_upload_success);
        }
        notifyUser(context, titleId, message);
    }

    private UploadJob getActiveJob(Context context) {
        UploadJob uploadJob;
        if (uploadJobId == null) {
            uploadJob = ForegroundPiwigoUploadService.getFirstActiveForegroundJob(context);
        } else {
            uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, uploadJobId);
        }
        return uploadJob;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppLockedEvent(AppLockedEvent event) {
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }
}
