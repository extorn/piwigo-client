package delit.piwigoclient.ui.upload;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.BiArrayAdapter;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.ArrayUtils;
import delit.piwigoclient.util.CollectionUtils;
import delit.piwigoclient.util.IOUtils;

import static android.view.View.GONE;


/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 * Use the {@link UploadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public abstract class AbstractUploadFragment extends MyFragment implements FilesToUploadRecyclerViewAdapter.RemoveListener {

    // the fragment initialization parameters
    private static final String TAG = "UploadFragment";
    private static final String SAVED_STATE_CURRENT_GALLERY = "currentGallery";
    private static final String SAVED_STATE_PRIVACY_LEVEL_WANTED = "privacyLevelWanted";
    private static final String SAVED_STATE_FILES_BEING_UPLOADED = "filesBeingUploaded";
    private static final String SAVED_STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID = "externallyTriggeredSelectFilesActionId";

    private RecyclerView filesForUploadView;
    private Button uploadFilesNowButton;
    private Button deleteUploadJobButton;
    private TextView selectedGallerySpinner;
    private Spinner privacyLevelSpinner;
    private CustomImageButton fileSelectButton;
    private byte privacyLevelWanted;
    private CategoryItemStub uploadToAlbum;
    private Long uploadJobId;
    private long externallyTriggeredSelectFilesActionId;
    private ArrayList<File> filesForUpload = new ArrayList<>();
    private FilesToUploadRecyclerViewAdapter filesToUploadAdapter;
    private Button uploadJobStatusButton;
    private TextView uploadableFilesView;
    private boolean compressVideosBeforeUpload;


    protected Bundle buildArgs(CategoryItemStub uploadToAlbum, int actionId) {
        Bundle args = new Bundle();
        args.putParcelable(SAVED_STATE_CURRENT_GALLERY, uploadToAlbum);
        args.putInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID, actionId);
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SAVED_STATE_CURRENT_GALLERY, uploadToAlbum);
        outState.putByte(SAVED_STATE_PRIVACY_LEVEL_WANTED, privacyLevelWanted);
        BundleUtils.putFileArrayList(outState, SAVED_STATE_FILES_BEING_UPLOADED, filesForUpload);
        if (uploadJobId != null) {
            outState.putLong(SAVED_STATE_UPLOAD_JOB_ID, uploadJobId);
        }
        outState.putLong(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID, externallyTriggeredSelectFilesActionId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            uploadToAlbum = getArguments().getParcelable(SAVED_STATE_CURRENT_GALLERY);
            if (uploadToAlbum == null) {
                uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
            }
            externallyTriggeredSelectFilesActionId = getArguments().getInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID);
            setArguments(null); // use the saved state from here.
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent stickyEvent) {
        if (externallyTriggeredSelectFilesActionId == stickyEvent.getActionId() || getUiHelper().isTrackingRequest(stickyEvent.getActionId())) {
            EventBus.getDefault().removeStickyEvent(stickyEvent);
            updateFilesForUploadList(stickyEvent.getSelectedFiles());
            AdsManager.getInstance().showFileToUploadAdvertIfAppropriate();
        }
    }

    private void addUploadingAsFieldsIfAppropriate(View v) {
        TextView uploadingAsLabelField = v.findViewById(R.id.upload_username_label);
        TextView uploadingAsField = v.findViewById(R.id.upload_username);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            uploadingAsField.setText(sessionDetails.getUsername());
            uploadingAsField.setVisibility(View.VISIBLE);
            uploadingAsLabelField.setVisibility(View.VISIBLE);
        } else {
            uploadingAsField.setVisibility(GONE);
            uploadingAsLabelField.setVisibility(GONE);
        }
    }

    @Override
    protected String buildPageHeading() {
        if (BuildConfig.DEBUG) {
            return getString(R.string.upload_page_title) + " (" + BuildConfig.FLAVOR + ')';
        } else {
            return getString(R.string.upload_page_title);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_upload, container, false);

        AdView adView = view.findViewById(R.id.upload_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(GONE);
        }

        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences viewPrefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.withShowHierachy();

        selectedGallerySpinner = view.findViewById(R.id.selected_gallery);
        selectedGallerySpinner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSelectedGallerySpinnerClick();
            }
        });

        fileSelectButton = view.findViewById(R.id.select_files_for_upload_button);
        fileSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFileSelectionWantedClick();
            }
        });

        uploadableFilesView = view.findViewById(R.id.files_uploadable_label);

        // check for login status as need to be logged in to get this information (supplied by server)
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) == null) {
            fileSelectButton.setEnabled(false);
            String serverUri = activeProfile.getPiwigoServerAddress(getPrefs(), getContext());
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), new LoginResponseHandler(), new OnLoginAction());
        } else {
            String fileTypesStr = String.format("(%1$s)", CollectionUtils.toCsvList(PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes()));
            uploadableFilesView.setText(fileTypesStr);
        }

        filesForUploadView = view.findViewById(R.id.selected_files_for_upload);

        privacyLevelSpinner = view.findViewById(R.id.privacy_level);

        CharSequence[] privacyLevelsText = getResources().getTextArray(R.array.privacy_levels_groups_array);
        long[] privacyLevelsValues = ArrayUtils.getLongArray(getResources().getIntArray(R.array.privacy_levels_values_array));
        BiArrayAdapter<CharSequence> privacyLevelOptionsAdapter = new BiArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, 0, privacyLevelsText, privacyLevelsValues);
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
                privacyLevelWanted = (byte) id;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        privacyLevelSpinner.setSelection(0);

        deleteUploadJobButton = view.findViewById(R.id.delete_upload_job_button);
        deleteUploadJobButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadFilesNowButton.setText(R.string.upload_files_button_title);
                onDeleteUploadJobButtonClick();
            }
        });

        uploadJobStatusButton = view.findViewById(R.id.view_detailed_upload_status_button);
        uploadJobStatusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUploadJobStatusButtonClick();
            }
        });


        updateActiveJobActionButtonsStatus();


        uploadFilesNowButton = view.findViewById(R.id.upload_files_button);
        uploadFilesNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadFilesNowButton.setText(R.string.upload_files_button_title);
                uploadFiles();
            }
        });

//        if (BuildConfig.DEBUG) {
//            Button compressVideosButton = new Button(getContext());
//            compressVideosButton.setText("Compress");
//            compressVideosButton.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    compressVideos();
//                }
//            });
//            ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
//            layoutParams.leftToRight = R.id.view_detailed_upload_status_button;
//            layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
//            ((ConstraintLayout) uploadFilesNowButton.getParent()).addView(compressVideosButton, layoutParams);
//        }

        if (savedInstanceState != null) {
            // update view with saved data
            uploadToAlbum = savedInstanceState.getParcelable(SAVED_STATE_CURRENT_GALLERY);
            privacyLevelWanted = savedInstanceState.getByte(SAVED_STATE_PRIVACY_LEVEL_WANTED);
            if (savedInstanceState.containsKey(SAVED_STATE_UPLOAD_JOB_ID)) {
                uploadJobId = savedInstanceState.getLong(SAVED_STATE_UPLOAD_JOB_ID);
            }
            filesForUpload = BundleUtils.getFileArrayList(savedInstanceState, SAVED_STATE_FILES_BEING_UPLOADED);
        }

        selectedGallerySpinner.setText(uploadToAlbum.getName());
        if (privacyLevelWanted >= 0) {
            privacyLevelSpinner.setSelection(privacyLevelOptionsAdapter.getPosition(privacyLevelWanted));
        }

        int columnsToShow = UploadPreferences.getColumnsOfFilesListedForUpload(prefs, getActivity());

        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), columnsToShow);
        filesForUploadView.setLayoutManager(gridLayoutMan);

        if (filesToUploadAdapter == null) {
            filesToUploadAdapter = new FilesToUploadRecyclerViewAdapter(filesForUpload, getContext(), this);
            filesToUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID);
        }
        filesForUploadView.setAdapter(filesToUploadAdapter);

        updateUiUploadStatusFromJobIfRun(container.getContext(), filesToUploadAdapter);

        return view;
    }

    private CustomImageButton getFileSelectButton() {
        return fileSelectButton;
    }


    private TextView getUploadableFilesView() {
        return uploadableFilesView;
    }

    private void compressVideos() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            getUiHelper().showDetailedMsg(R.string.alert_error, "Video Compression not supported on this version of android");
        } else {
            FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();
            ArrayList<File> filesForUpload = fileListAdapter.getFiles();
            File inputVideo = filesForUpload.get(0);
            File moviesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File outputVideo = new File(moviesFolder, "compressed_" + inputVideo.getName());
            try {
                //TODO this compressor is very basic - need to use my own.
//                new YPrestoCompressor().compressFile(getContext(), inputVideo, outputVideo, new DebugCompressionListener(getUiHelper()));
                new ExoPlayerCompression().compressFile(getContext(), inputVideo, outputVideo, new DebugCompressionListener(getUiHelper()));
            } catch (IOException e) {
                getUiHelper().showDetailedMsg(R.string.alert_error, "Error compressing video " + e.getMessage());
            }
        }
    }

    private void requestFileSelection(ArrayList<String> allowedFileTypes) {
        FileSelectionNeededEvent event = new FileSelectionNeededEvent(true, false, true);
        String initialFolder = getPrefs().getString(getString(R.string.preference_data_upload_default_local_folder_key), Environment.getExternalStorageDirectory().getAbsolutePath());
        event.withInitialFolder(initialFolder);
        event.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.LAST_MODIFIED_DATE);
        getUiHelper().setTrackingRequest(event.getActionId());
        EventBus.getDefault().post(event);
    }

    public Long getUploadJobId() {
        return uploadJobId;
    }

    private void onDeleteUploadJobButtonClick() {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, getString(R.string.alert_really_delete_upload_job), R.string.button_no, R.string.button_yes, new OnDeleteJobQuestionAction(getUiHelper()));
    }

    private void onUploadJobStatusButtonClick() {
        UploadJob uploadJob = getActiveJob(getContext());
        if (uploadJob != null) {
            EventBus.getDefault().post(new ViewJobStatusDetailsEvent(uploadJob));
        } else {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.job_not_found);
            uploadJobStatusButton.setVisibility(GONE);
        }
    }

    private void onSelectedGallerySpinnerClick() {
        HashSet<Long> selection = new HashSet<>();
        selection.add(uploadToAlbum.getId());
        ExpandingAlbumSelectionNeededEvent evt = new ExpandingAlbumSelectionNeededEvent(false, true, selection, uploadToAlbum.getParentId());
        getUiHelper().setTrackingRequest(evt.getActionId());
        EventBus.getDefault().post(evt);
    }

    private void onFileSelectionWantedClick() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            String serverUri = ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getContext());
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), new LoginResponseHandler());
        } else {
            if (sessionDetails.getAllowedFileTypes() == null) {
                fileSelectButton.setEnabled(false);
                Bundle b = new Bundle();
                sessionDetails.writeToBundle(b);
                FirebaseAnalytics.getInstance(getContext()).logEvent("IncompleteUserSession", b);
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_user_session_no_allowed_filetypes);
                getView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fileSelectButton.setEnabled(true);
                        requestFileSelection(null); // show all files... I hope the user is sensible!
                    }
                }, 5000);
            } else {
                requestFileSelection(new ArrayList<>(sessionDetails.getAllowedFileTypes()));
            }
        }
    }

    private void updateUiUploadStatusFromJobIfRun(Context context, FilesToUploadRecyclerViewAdapter filesForUploadAdapter) {

        UploadJob uploadJob = getActiveJob(context);

        if (uploadJob != null) {
            //register the potentially completely new handler to handle the existing job messages
            getUiHelper().getPiwigoResponseListener().switchHandlerId(uploadJob.getResponseHandlerId());
            getUiHelper().updateHandlerForAllMessages();

            PiwigoSessionDetails piwigoSessionDetails = PiwigoSessionDetails.getInstance(uploadJob.getConnectionPrefs());
            if (piwigoSessionDetails != null) {
                String fileTypesStr = String.format("(%1$s)", CollectionUtils.toCsvList(piwigoSessionDetails.getAllowedFileTypes()));
                uploadableFilesView.setText(fileTypesStr);
            }
            uploadJobId = uploadJob.getJobId();
            uploadToAlbum = new CategoryItemStub("???", uploadJob.getUploadToCategory());
            AlbumGetSubAlbumNamesResponseHandler hndler = new AlbumGetSubAlbumNamesResponseHandler(uploadJob.getUploadToCategory(), false);
            getUiHelper().addActionOnResponse(getUiHelper().addActiveServiceCall(hndler), new OnGetSubAlbumNamesAction());
            selectedGallerySpinner.setText(uploadToAlbum.getName());

            privacyLevelWanted = uploadJob.getPrivacyLevelWanted();
            if (privacyLevelWanted >= 0) {
                privacyLevelSpinner.setSelection(((BiArrayAdapter) privacyLevelSpinner.getAdapter()).getPosition(privacyLevelWanted));
            }

            ArrayList<File> filesToBeUploaded = uploadJob.getFilesNotYetUploaded();

            filesForUploadAdapter.clear();
            filesForUploadAdapter.addAll(filesToBeUploaded);

            for (File f : filesForUploadAdapter.getFiles()) {
                int progress = uploadJob.getUploadProgress(f);
                int compressionProgress = uploadJob.getCompressionProgress(f);
                if (compressionProgress == 100) {
                    File compressedFile = uploadJob.getCompressedFile(getContext(), f);
                    filesForUploadAdapter.updateCompressionProgress(f, compressedFile, 100);
                }
                filesForUploadAdapter.updateUploadProgress(f, progress);
            }

            boolean jobIsComplete = uploadJob.isFinished();
            allowUserUploadConfiguration(uploadJob);
            if (!jobIsComplete) {
                // now register for any new messages (and pick up all messages in sequence)
                getUiHelper().handleAnyQueuedPiwigoMessages();
            }
        } else {
            allowUserUploadConfiguration(null);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if ((sessionDetails == null || (!sessionDetails.isAdminUser() && !sessionDetails.isUseCommunityPlugin())) || isAppInReadOnlyMode()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            Log.e(TAG, "Unable to view upload fragment - removing from activity");
        }
    }

    @Override
    public void onStart() {
        addUploadingAsFieldsIfAppropriate(getView());
        super.onStart();
        // need to register here so FileSelectionComplete events always get through after the UI has been built when forked from different process
        if (!EventBus.getDefault().isRegistered(this)) {
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
        if (getActivity().isDestroyed() || getActivity().isFinishing()) {
        }
    }

    protected FilesToUploadRecyclerViewAdapter getFilesForUploadViewAdapter() {
        return (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
    }

    private CategoryItemStub getUploadToAlbum() {
        return uploadToAlbum;
    }

    protected void updateFilesForUploadList(ArrayList<File> filesToBeUploaded) {
        if (filesToBeUploaded.size() > 0) {
            String lastOpenedFolder = filesToBeUploaded.get(0).getParentFile().getAbsolutePath();
            getPrefs().edit().putString(getString(R.string.preference_data_upload_default_local_folder_key), lastOpenedFolder).apply();
        }
        FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
        List<File> addedFiles = adapter.addAll(filesToBeUploaded);
        int filesAlreadyPresent = filesToBeUploaded.size() - addedFiles.size();
        if (filesAlreadyPresent > 0) {
            getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.files_already_set_for_upload_skipped_pattern, filesAlreadyPresent));
        }
        uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
        updateActiveJobActionButtonsStatus();
    }

    private Set<File> getFilesExceedingMaxDesiredUploadThreshold() {
        int maxUploadSizeWantedThresholdMB = UploadPreferences.getMaxUploadFilesizeMb(getContext(), prefs);
        HashSet<File> retVal = new HashSet<>();
        for (File f : filesForUpload) {
            double fileLengthMB = ((double) f.length()) / 1024 / 1024;
            if (fileLengthMB > maxUploadSizeWantedThresholdMB) {
                retVal.add(f);
            }
        }
        return retVal;
    }

    private void uploadFiles() {
        buildAndSubmitNewUploadJob();
    }

    private void buildAndSubmitNewUploadJob() {
        buildAndSubmitNewUploadJob(false, false);
    }

    private void buildAndSubmitNewUploadJob(boolean filesizesChecked, boolean multimediaChecked) {
        UploadJob activeJob = null;

        if (uploadJobId != null) {
            activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        boolean userInputRequested = false;

        if (activeJob == null) {
            FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();

            if (fileListAdapter == null || uploadToAlbum == null || CategoryItemStub.ROOT_GALLERY.equals(uploadToAlbum)) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
                deleteUploadJobButton.setEnabled(true);
                return;
            }

            filesForUpload = fileListAdapter.getFiles();

            if (!filesizesChecked) {
                final Set<File> filesForReview = getFilesExceedingMaxDesiredUploadThreshold();
                StringBuilder filenameListStrB = new StringBuilder();
                for (File f : filesForReview) {
                    double fileLengthMB = ((double) f.length()) / 1024 / 1024;
                    if (filesForReview.size() > 0) {
                        filenameListStrB.append(", ");
                    }
                    filenameListStrB.append(f);
                    filenameListStrB.append(String.format(Locale.getDefault(), "(%1$.1fMB)", fileLengthMB));
                }
                if (filesForReview.size() > 0) {
                    getUiHelper().showOrQueueCancellableDialogQuestion(R.string.alert_warning, getString(R.string.alert_files_larger_than_upload_threshold_pattern, filesForReview.size(), filenameListStrB.toString()), R.string.button_no, R.string.button_cancel, R.string.button_yes, new FileSizeExceededAction(getUiHelper(), filesForReview));
                    userInputRequested = true;
                }
            }

            if (!multimediaChecked && !userInputRequested) {
                Collection<File> revisedFilesForReview = filesForUpload;//getFilesExceedingMaxDesiredUploadThreshold();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    //Compression needs minimum of API v18

                    List<String> multimediaExts = CollectionUtils.stringsFromCsvList(AlbumViewPreferences.getKnownMultimediaExtensions(getPrefs(), getContext()));
                    boolean uploadingMultimedia = false;
                    for (File f : revisedFilesForReview) {
                        if (multimediaExts.contains(IOUtils.getFileExt(f.getName()))) {
                            // uploading multimedia
                            uploadingMultimedia = true;
                            break;
                        }
                    }

                    if (uploadingMultimedia /*&& revisedFilesForReview.size() > 0*/) {
                        getUiHelper().showOrQueueCancellableDialogQuestion(R.string.alert_question_title, getString(R.string.alert_files_larger_than_upload_threshold_compress), R.string.button_no, R.string.button_cancel, R.string.button_yes, new ShouldCompressVideosAction(getUiHelper()));
                        userInputRequested = true;
                    }

                }
            }
        }

        if (!userInputRequested) {
            if (activeJob == null) {
                long handlerId = getUiHelper().getPiwigoResponseListener().getHandlerId();
                activeJob = ForegroundPiwigoUploadService.createUploadJob(ConnectionPreferences.getActiveProfile(), filesForUpload, uploadToAlbum, compressVideosBeforeUpload, privacyLevelWanted, handlerId);
            }
            submitUploadJob(activeJob);
        }
    }

    private void submitUploadJob(UploadJob activeJob) {
        uploadJobId = activeJob.getJobId();
        // the job will be submitted within the onEvent(PermissionsWantedResponse event) method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        } else {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        }
    }

    @Override
    public void onRemove(final FilesToUploadRecyclerViewAdapter adapter, final File itemToRemove, boolean longClick) {
        final UploadJob activeJob = getActiveJob(getContext());
        if (activeJob != null) {
            if (activeJob.isFinished()) {
                if (activeJob.uploadItemRequiresAction(itemToRemove)) {
                    String message = getString(R.string.alert_message_remove_file_server_state_incorrect);
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new PartialUploadFileAction(getUiHelper(), itemToRemove));
                } else {
                    // job stopped, but upload of this file never got to the server.
                    activeJob.cancelFileUpload(itemToRemove);
                    adapter.remove(itemToRemove);
                }
            } else {
                // job running.
                boolean immediatelyCancelled = activeJob.cancelFileUpload(itemToRemove);
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_file_upload_cancelled_pattern, itemToRemove.getName()));
                if (immediatelyCancelled) {
                    adapter.remove(itemToRemove);
                }
            }
            if (!activeJob.isRunningNow() && activeJob.getFilesNotYetUploaded().size() == 0) {
                // no files left to upload. Lets switch the button from upload to finish
                uploadFilesNowButton.setText(R.string.upload_files_finish_job_button_title);
            }
        } else {
            if (longClick) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_delete_all_files_selected_for_upload), R.string.button_no, R.string.button_yes, new DeleteAllFilesSelectedAction(getUiHelper()));
            } else {
                adapter.remove(itemToRemove);
                uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
                updateActiveJobActionButtonsStatus();
            }
        }
    }

    private void allowUserUploadConfiguration(UploadJob uploadJob) {

        filesToUploadAdapter = getFilesForUploadViewAdapter();
        boolean filesStillToBeUploaded = filesToUploadAdapter.getItemCount() > 0;
        boolean noJobIsYetConfigured = uploadJob == null;
        boolean jobIsFinished = uploadJob != null && uploadJob.isFinished();
        boolean jobIsRunningNow = uploadJob != null && uploadJob.isRunningNow();
        boolean jobIsSubmitted = uploadJob != null && uploadJob.isSubmitted();

        boolean jobYetToFinishUploadingFiles = filesStillToBeUploaded && (noJobIsYetConfigured || jobIsFinished || !(jobIsSubmitted || jobIsRunningNow));
        boolean jobYetToCompleteAfterUploadingFiles = !noJobIsYetConfigured && !filesStillToBeUploaded && !jobIsFinished && !jobIsRunningNow; // crashed job just loaded basically
        uploadFilesNowButton.setEnabled(jobYetToFinishUploadingFiles || jobYetToCompleteAfterUploadingFiles); // Allow restart of the job.
        updateActiveJobActionButtonsStatus();
        fileSelectButton.setEnabled(noJobIsYetConfigured || jobIsFinished);
        selectedGallerySpinner.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        privacyLevelSpinner.setEnabled(noJobIsYetConfigured || jobIsFinished);
    }

    private void updateActiveJobActionButtonsStatus() {
        if (uploadJobId != null) {
            UploadJob job = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
            if (job != null) {
                uploadJobStatusButton.setVisibility(View.VISIBLE);
                deleteUploadJobButton.setEnabled(job.hasBeenRunBefore() && !job.isRunningNow() && !job.hasJobCompletedAllActionsSuccessfully());
            } else {
                uploadJobStatusButton.setVisibility(GONE);
                deleteUploadJobButton.setEnabled(false);
            }
        } else {
            uploadJobStatusButton.setVisibility(GONE);
            deleteUploadJobButton.setEnabled(false);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            uploadToAlbum = event.getAlbumDetail().toStub();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            UploadJob activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
            if (!event.areAllPermissionsGranted()) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_required_permissions_not_granted_action_cancelled));
                return;
            }
            //ensure the handler is actively listening before the job starts.
            getUiHelper().addBackgroundServiceCall(uploadJobId);
            ForegroundPiwigoUploadService.startActionRunOrReRunUploadJob(getContext(), activeJob);
            allowUserUploadConfiguration(activeJob);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
        if (filesForUploadView != null) {
            filesForUploadView.setAdapter(null);
        }
        if (privacyLevelSpinner != null) {
            privacyLevelSpinner.setAdapter(null);
        }
    }

    private void notifyUser(Context context, int titleId, String message) {
        if (getContext() == null) {
            notifyUserUploadStatus(context.getApplicationContext(), message);
        } else {
            getUiHelper().showDetailedMsg(titleId, message);
        }
    }

    private void notifyUserUploadStatus(Context ctx, String message) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx, getUiHelper().getDefaultNotificationChannelId())
                .setContentTitle(ctx.getString(R.string.notification_upload_event))
                .setContentText(message)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
            mBuilder.setCategory("progress");
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
            mBuilder.setCategory(Notification.CATEGORY_PROGRESS);
        }


//      Clear the last notification
        getUiHelper().clearNotification(TAG, 1);
        getUiHelper().showNotification(TAG, 1, mBuilder.build());
    }

    protected void setCompressVideosBeforeUpload(boolean compress) {
        compressVideosBeforeUpload = compress;
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new ForegroundPiwigoFileUploadResponseListener(context);
    }

    private void processError(Context context, PiwigoResponseBufferingHandler.Response error) {
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
        if (errorMessage != null) {
            notifyUser(context, R.string.alert_error, errorMessage);
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            if (event.getSelectedItems() != null && event.getSelectedItems().size() > 0) {
                CategoryItem selectedAlbum = event.getSelectedItems().iterator().next();
                uploadToAlbum = selectedAlbum.toStub();
                selectedGallerySpinner.setText(uploadToAlbum.getName());
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    private void onAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {
        getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_temporary_upload_album_deleted));
    }

    private TextView getSelectedGallerySpinner() {
        return selectedGallerySpinner;
    }

    private static class OnLoginAction extends UIHelper.Action<AbstractUploadFragment, LoginResponseHandler.PiwigoOnLoginResponse> {
        @Override
        public boolean onSuccess(UIHelper<AbstractUploadFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            AbstractUploadFragment fragment = uiHelper.getParent();
            fragment.getFileSelectButton().setEnabled(true);
            ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
            String fileTypesStr = String.format("(%1$s)", CollectionUtils.toCsvList(PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes()));
            fragment.getUploadableFilesView().setText(fileTypesStr);
            return super.onSuccess(uiHelper, response);
        }
    }

    private static class DeleteAllFilesSelectedAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {
        public DeleteAllFilesSelectedAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractUploadFragment fragment = getUiHelper().getParent();
                fragment.getFilesForUploadViewAdapter().clear();
                fragment.uploadFilesNowButton.setEnabled(fragment.getFilesForUploadViewAdapter().getItemCount() > 0);
                fragment.updateActiveJobActionButtonsStatus();
            }
        }
    }

    private static class PartialUploadFileAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {

        private final File itemToRemove;

        public PartialUploadFileAction(UIHelper uiHelper, File itemToRemove) {
            super(uiHelper);
            this.itemToRemove = itemToRemove;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractUploadFragment fragment = getUiHelper().getParent();
                UploadJob activeJob = fragment.getActiveJob(getContext());
                if (activeJob != null) {
                    activeJob.cancelFileUpload(itemToRemove);
                    fragment.getFilesForUploadViewAdapter().remove(itemToRemove);
                }
            }
        }
    }

    private static class DebugCompressionListener implements ExoPlayerCompression.CompressionListener {
        private final UIHelper uiHelper;
        private final SimpleDateFormat strFormat;
        private long startCompressionAt;

        {
            strFormat = new SimpleDateFormat("HH:mm:ss");
            strFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        public DebugCompressionListener(UIHelper uiHelper) {
            this.uiHelper = uiHelper;
        }

        @Override
        public void onCompressionStarted() {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression started");
            startCompressionAt = System.currentTimeMillis();
        }

        @Override
        public void onCompressionError(Exception e) {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression failed");
        }

        @Override
        public void onCompressionComplete() {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression finished");
        }

        @Override
        public void onCompressionProgress(double compressionProgress, long mediaDurationMs) {
            AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
            File f = fragment.getFilesForUploadViewAdapter().getFiles().get(0);
            fragment.getFilesForUploadViewAdapter().updateUploadProgress(f, (int) Math.rint(compressionProgress));

            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startCompressionAt;
            long estimateTotalCompressionTime = Math.round(100 * (elapsedTime / compressionProgress));
            long endCompressionAtEstimate = startCompressionAt + estimateTotalCompressionTime;
            Date endCompressionAt = new Date(endCompressionAtEstimate);
            String remainingTimeStr = strFormat.format(new Date(endCompressionAtEstimate - currentTime));
            String elapsedCompressionTimeStr = strFormat.format(new Date(elapsedTime));

            if (mediaDurationMs > 0) {
                double compressionRate = ((double) mediaDurationMs) / elapsedTime;
                uiHelper.showDetailedShortMsg(R.string.alert_information, String.format("Video Compression\nrate: %5$.02fx\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr, compressionRate));
            } else {
                uiHelper.showDetailedShortMsg(R.string.alert_information, String.format("Video Compression\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr));
            }
        }
    }

    private static class ShouldCompressVideosAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {

        public ShouldCompressVideosAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            super.onResult(dialog, positiveAnswer);
            AbstractUploadFragment fragment = getUiHelper().getParent();
            fragment.setCompressVideosBeforeUpload(Boolean.TRUE.equals(positiveAnswer));

            if (positiveAnswer != null) {
                fragment.buildAndSubmitNewUploadJob(true, true);
            }
        }
    }

    private static class FileSizeExceededAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {
        private Set<File> filesToDelete;

        public FileSizeExceededAction(UIHelper uiHelper, Set<File> filesForReview) {
            super(uiHelper);
            this.filesToDelete = filesForReview;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();

            if (Boolean.TRUE == positiveAnswer) {
                for (File file : filesToDelete) {
                    fragment.onRemove(fragment.getFilesForUploadViewAdapter(), file, false);
                }
            }
            if (positiveAnswer != null) {
                fragment.buildAndSubmitNewUploadJob(true, false);
            }
        }
    }

    private static class OnDeleteJobQuestionAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {
        public OnDeleteJobQuestionAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();
            Long currentJobId = fragment.getUploadJobId();
            if (currentJobId == null) {
                Crashlytics.log(Log.WARN, TAG, "User attempted to delete job that was no longer exists");
                return;
            }
            UploadJob job = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), currentJobId);
            if (positiveAnswer != null && positiveAnswer && job != null) {
                if (job.getTemporaryUploadAlbum() > 0) {
                    AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(job.getTemporaryUploadAlbum());
                    getUiHelper().addNonBlockingActiveServiceCall(getContext().getString(R.string.alert_deleting_temporary_upload_album), albumDelHandler.invokeAsync(getContext(), job.getConnectionPrefs()), albumDelHandler.getTag());
                }
                ForegroundPiwigoUploadService.removeJob(job);
                ForegroundPiwigoUploadService.deleteStateFromDisk(getContext(), job);
                fragment.allowUserUploadConfiguration(null);
            }
        }
    }

    private static class ErrorUploadingQuestionAction extends UIHelper.QuestionResultAdapter<AbstractUploadFragment> {
        public ErrorUploadingQuestionAction(UIHelper uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();
            UploadJob activeJob = fragment.getActiveJob(getContext());
            if (positiveAnswer != null && positiveAnswer) {
                ForegroundPiwigoUploadService.removeJob(activeJob);
                ForegroundPiwigoUploadService.deleteStateFromDisk(getContext(), activeJob);
                fragment.allowUserUploadConfiguration(null);
            }
        }
    }

    private static class OnGetSubAlbumNamesAction extends UIHelper.Action<UploadFragment, AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse> {
        @Override
        public boolean onSuccess(UIHelper<UploadFragment> uiHelper, AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
            if (response.getAlbumNames().size() > 0) {
                AbstractUploadFragment fragment = uiHelper.getParent();
                CategoryItemStub uploadToAlbum = fragment.getUploadToAlbum();
                if (uploadToAlbum.getId() == response.getAlbumNames().get(0).getId()) {
                    uploadToAlbum = response.getAlbumNames().get(0);
                    fragment.getSelectedGallerySpinner().setText(uploadToAlbum.getName());
                } else if (uploadToAlbum.getId() == CategoryItemStub.ROOT_GALLERY.getId()) {
                    uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
                    fragment.getSelectedGallerySpinner().setText(uploadToAlbum.getName());
                }
            }
            return super.onSuccess(uiHelper, response);
        }
    }

    private class ForegroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener {

        ForegroundPiwigoFileUploadResponseListener(Context context) {
            super(context);
        }

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        protected void onRequestedFileUploadCancelComplete(Context context, File cancelledFile) {
            if (isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.remove(cancelledFile);
            }
            UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, uploadJobId);
            if (uploadJob.isFilePartiallyUploaded(cancelledFile)) {
                getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_partial_upload_deleted));
            }
        }

        @Override
        protected void onUploadComplete(final Context context, final UploadJob job) {
            if (isAdded()) {
                if (job.hasJobCompletedAllActionsSuccessfully() && job.isFinished()) {
                    uploadJobId = null;
                    if (AbstractUploadFragment.this.isAdded()) {
                        ForegroundPiwigoUploadService.removeJob(job);
                    }
                    HashSet<File> filesPendingApproval = job.getFilesPendingApproval();
                    if (filesPendingApproval.size() > 0) {
                        String msg = getString(R.string.alert_message_info_files_already_pending_approval_pattern, filesPendingApproval.size());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg);
                    }
                } else {
                    int errMsgResourceId = R.string.alert_message_error_uploading_start;
                    if (job.getFilesNotYetUploaded().size() == 0) {
                        errMsgResourceId = R.string.alert_message_error_uploading_end;
                    }
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_title_error_upload, getContext().getString(errMsgResourceId), R.string.button_no, R.string.button_yes, new ErrorUploadingQuestionAction(getUiHelper()));
                }
                allowUserUploadConfiguration(job);
            }
            notifyUserUploadJobComplete(context, job);
        }

        @Override
        protected void onLocalFileError(Context context, final BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse response) {
            String errorMessage;
            if (response.getError() instanceof FileNotFoundException) {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_no_longer_available_message_pattern), response.getFileForUpload().getName());
            } else {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_read_error_message_pattern), response.getFileForUpload().getName());
            }
            notifyUser(context, R.string.alert_error, errorMessage);
        }

        @Override
        protected void onPrepareUploadFailed(Context context, final BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response) {

            PiwigoResponseBufferingHandler.Response error = response.getError();
            processError(context, error);
        }

        @Override
        protected void onCleanupPostUploadFailed(Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            processError(context, error);
        }

        @Override
        protected void onFileUploadProgressUpdate(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
            if (isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.updateUploadProgress(response.getFileForUpload(), response.getProgress());
            }
            if (response.getProgress() == 100) {
                onFileUploadComplete(context, response);
            }
        }

        @Override
        protected void onFileCompressionProgressUpdate(Context context, BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
            if (isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.updateCompressionProgress(response.getFileForUpload(), response.getCompressedFileUpload(), response.getProgress());
            }
            if (response.getProgress() == 100) {
                onFileCompressionComplete(context, response);
            }
        }

        private void onFileCompressionComplete(Context context, final BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
            // Do nothing for now.
        }

        private void onFileUploadComplete(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {

            UploadJob uploadJob = getActiveJob(context);

            if (uploadJob != null) {
                for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
                    EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
                }
                EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory()));
            }

            if (isAdded()) {
                // somehow upload job can be null... hopefully this copes with that scenario.
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.remove(response.getFileForUpload());
            }
        }

        @Override
        protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, final BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse response) {
            if (isAdded()) {
                UploadJob uploadJob = getActiveJob(context);

                if (uploadJob != null) {
                    FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                    for (File existingFile : response.getExistingFiles()) {
                        int progress = uploadJob.getUploadProgress(existingFile);
                        adapter.updateUploadProgress(existingFile, progress);
//                    adapter.remove(existingFile);
                    }
                }
            }
            String message = String.format(context.getString(R.string.alert_items_for_upload_already_exist_message_pattern), response.getExistingFiles().size());
            notifyUser(context, R.string.alert_information, message);
        }

        @Override
        protected void onChunkUploadFailed(Context context, final BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            File fileForUpload = response.getFileForUpload();
            String errorMessage = null;

            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                String msg = err.getErrorMessage();
                if ("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(msg)) {
                    msg = err.getResponse();
                }
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), fileForUpload.getName(), err.getStatusCode(), msg);
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
            if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                onAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
            } else {
                super.onAfterHandlePiwigoResponse(response);
            }
        }

        @Override
        protected void onErrorResponse(PiwigoResponseBufferingHandler.ErrorResponse response) {
            //don't care. used to be used when retrieving album names for the spinner.
        }

        @Override
        protected void onAddUploadedFileToAlbumFailure(Context context, final BasePiwigoUploadService.PiwigoUploadFileAddToAlbumFailedResponse response) {
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
                    if (isAdded()) {
                        FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                        adapter.remove(fileForUpload);
                    }
                    errorMessage = String.format(context.getString(R.string.alert_error_upload_file_already_on_server_message_pattern), fileForUpload.getName());
                } else {
                    errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), fileForUpload.getName(), err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
                }
            } else if (error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
                errorMessage = ((PiwigoResponseBufferingHandler.CustomErrorResponse) error).getErrorMessage();
            }
            if (errorMessage != null) {
                notifyUser(context, R.string.alert_error, errorMessage);
            }
        }
    }
}
