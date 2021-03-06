package delit.piwigoclient.ui.upload;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.ui.view.list.BiArrayAdapter;
import delit.libs.util.ArrayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.progress.BasicProgressTracker;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.database.AppSettingsViewModel;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.CancelFileUploadEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.file.FolderItem;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;
import delit.piwigoclient.ui.upload.actions.DeleteAllFilesSelectedAction;
import delit.piwigoclient.ui.upload.actions.FileSizeExceededAction;
import delit.piwigoclient.ui.upload.actions.LoadAlbumTreeAction;
import delit.piwigoclient.ui.upload.actions.OnDeleteJobQuestionAction;
import delit.piwigoclient.ui.upload.actions.OnGetChildAlbumNamesAction;
import delit.piwigoclient.ui.upload.actions.OnLoginAction;
import delit.piwigoclient.ui.upload.actions.PartialUploadFileAction;
import delit.piwigoclient.ui.upload.actions.UnacceptableFilesAction;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;
import delit.piwigoclient.ui.upload.list.UploadDataItemViewHolder;
import delit.piwigoclient.ui.upload.list.UploadItemMultiSelectStatusAdapter;
import delit.piwigoclient.ui.upload.list.UploadItemSpanSizeLookup;
import delit.piwigoclient.ui.util.ItemSpacingDecoration;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;


/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 * Use the {@link UploadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public abstract class AbstractUploadFragment<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends MyFragment<F,FUIH> {
    // the fragment initialization parameters
    private static final String TAG = "UploadFragment";
    private static final String SAVED_STATE_UPLOAD_TO_ALBUM = "uploadToAlbum";
    private static final String SAVED_STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID = "externallyTriggeredSelectFilesActionId";
    private static final boolean ENABLE_COMPRESSION_BUTTON = false;
    private static final int TAB_IDX_SETTINGS = 1;
    private static final int TAB_IDX_FILES = 0;
    public static final String URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD = "foregroundUpload";

    private AppSettingsViewModel appSettingsViewModel;
    private Long uploadJobId;
    private long externallyTriggeredSelectFilesActionId;
    private CategoryItemStub uploadToAlbum;
    private RecyclerView filesForUploadView;
    private Button uploadFilesNowButton;
    private Button deleteUploadJobButton;
    private TextView selectedGalleryTextView;
    private TextView filesForUploadCountView;
    private Spinner privacyLevelSpinner;
    private SwitchMaterial deleteFilesAfterUploadCheckbox;
    private MaterialButton fileSelectButton;
    private FilesToUploadRecyclerViewAdapter<?,?,?> filesToUploadAdapter;
    private Button uploadJobStatusButton;
    private SwitchMaterial compressVideosCheckbox;
    private SwitchMaterial allowUploadOfRawVideosIfIncompressibleCheckbox;
    private SwitchMaterial compressImagesCheckbox;
    private ViewPager mViewPager;
    private Spinner compressVideosQualitySpinner;
    private Spinner compressVideosAudioBitrateSpinner;
    private Slider compressImagesQualityNumberPicker;
    private Spinner compressImagesOutputFormatSpinner;
    private EditText compressImagesMaxHeightNumberField;
    private EditText compressImagesMaxWidthNumberField;
    private ProgressIndicator overallUploadProgressBar;
    private FilesForUploadViewModel filesForUploadViewModel; //TODO I don't think this is required, but it's not doing any harm that I can see.
    private View compressImagesSettings;
    private View compressVideosSettings;


    protected Bundle buildArgs(CategoryItemStub uploadToAlbum, int actionId) {
        Bundle args = new Bundle();
        args.putParcelable(SAVED_STATE_UPLOAD_TO_ALBUM, uploadToAlbum);
        args.putInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID, actionId);
        return args;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
            if (uploadJobId != null) {
            outState.putLong(SAVED_STATE_UPLOAD_JOB_ID, uploadJobId);
        }
        if (filesToUploadAdapter != null) {
            filesForUploadViewModel.setFilesForUpload(filesToUploadAdapter.getUploadDataItemsModel());
//            filesToUploadAdapter.onSaveInstanceState(outState, FILES_TO_UPLOAD_ADAPTER_STATE);
        }
        outState.putParcelable(SAVED_STATE_UPLOAD_TO_ALBUM, uploadToAlbum);
        outState.putLong(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID, externallyTriggeredSelectFilesActionId);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        if(savedInstanceState == null) {
            //Set these values once the screen is back to normal (otherwise horrible JVM crash)
            // only set them if the saved instance state is null else we overwrite the user values.
            compressImagesQualityNumberPicker.setValue(((float)UploadPreferences.getImageCompressionQuality(getContext(), getPrefs())));
            compressImagesMaxHeightNumberField.setText(String.valueOf(UploadPreferences.getImageCompressionMaxHeight(getContext(), getPrefs())));
            compressImagesMaxWidthNumberField.setText(String.valueOf(UploadPreferences.getImageCompressionMaxWidth(getContext(), getPrefs())));
        }
        super.onViewStateRestored(savedInstanceState);
    }


    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent stickyEvent) {
        if (externallyTriggeredSelectFilesActionId == stickyEvent.getActionId() || getUiHelper().isTrackingRequest(stickyEvent.getActionId())) {
            if(getContext() != null) {
                EventBus.getDefault().removeStickyEvent(stickyEvent);
                UploadJob activeJob = getActiveJob(requireContext());
                if(activeJob != null && !activeJob.isStatusFinished()) {
                    if(!activeJob.isStatusRunningNow()) {
                        submitUploadJob(activeJob);
                        getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_add_files_previous_job_unfinished);
                        Logging.log(Log.INFO, TAG, "Resubmitted previously failed job as user attempted to add new files");
                    } else {
                        getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_error_unable_add_files_previous_job_still_running);
                        Logging.log(Log.INFO, TAG, "Cancelled user attempt to add files to currently running job");
                    }
                } else {
                    new FileSelectionResultProcessingTask(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, stickyEvent);
                }
            } else {
                Logging.log(Log.WARN, TAG, "Unable to handle shared files before the fragment is attached.");
            }
        }
    }

    public boolean isRawVideoUploadPermittedIfNeeded() {
        return allowUploadOfRawVideosIfIncompressibleCheckbox.isChecked();
    }

    public void onFilesForUploadTooLarge(Set<UploadDataItem> filesForReview) {
        StringBuilder filenameListStrB = new StringBuilder();
        for (UploadDataItem item : filesForReview) {
            if (filesForReview.size() > 0) {
                filenameListStrB.append(", ");
            }
            filenameListStrB.append(item.getUri().getPath());
            filenameListStrB.append(IOUtils.bytesToNormalizedText(item.getDataLength()));
        }
        getUiHelper().showOrQueueTriButtonDialogQuestion(R.string.alert_warning, getString(R.string.alert_files_larger_than_upload_threshold_pattern, filesForReview.size(), filenameListStrB.toString()), R.string.button_no, R.string.button_cancel, R.string.button_yes, new FileSizeExceededAction<>(getUiHelper(), filesForReview));
    }

    public void withFilesUnacceptableForUploadRejected(Set<String> unacceptableFileExts) {
        DisplayUtils.runOnUiThread(()-> getUiHelper().showOrQueueDialogQuestion(R.string.alert_error, getString(R.string.alert_upload_job_contains_files_server_will_not_accept_pattern, unacceptableFileExts.size()), R.string.button_no, R.string.button_yes, new UnacceptableFilesAction<>(getUiHelper(), unacceptableFileExts)));
    }

    private void addUploadingAsFieldsIfAppropriate(View v) {
        TextView uploadingAsLabelField = v.findViewById(R.id.upload_username_label);
        TextView uploadingAsField = v.findViewById(R.id.upload_username);
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails != null && sessionDetails.isLoggedInWithFullSessionDetails()) {
            uploadingAsField.setText(sessionDetails.getUsername());
            uploadingAsField.setVisibility(VISIBLE);
            uploadingAsLabelField.setVisibility(VISIBLE);
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appSettingsViewModel = obtainActivityViewModel(requireActivity(), AppSettingsViewModel.class);
        filesForUploadViewModel = obtainFragmentViewModel(this, FilesForUploadViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        invokeCallToRetrieveUploadDestination();

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_upload, container, false);

        AdView adView = view.findViewById(R.id.upload_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(GONE);
        }

        mViewPager = view.findViewById(R.id.sliding_views_tab_content);
//        mViewPager.setCurrentItem(TAB_IDX_FILES);

        AlbumSelectionListAdapterPreferences viewPrefs = new AlbumSelectionListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.setFlattenAlbumHierarchy(true);

        filesForUploadCountView = view.findViewById(R.id.files_for_upload_count_field);
        selectedGalleryTextView = view.findViewById(R.id.selected_gallery);
        // can't just use a std click listener as it first focuses the field :-(
        CustomClickTouchListener.callClickOnTouch(selectedGalleryTextView, (sgtf)->onClickSelectedGalleryTextView());

        fileSelectButton = view.findViewById(R.id.select_files_for_upload_button);
        fileSelectButton.setOnClickListener(v -> onClickFileSelectionWanted());

        Button informationForUploadButton = view.findViewById(R.id.information_for_upload_button);
        informationForUploadButton.setOnClickListener(v -> onClickInformationForUploadButton());

        // check for login status as need to be logged in to get this information (supplied by server)
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) == null) {
            fileSelectButton.setEnabled(false);
            String serverUri = activeProfile.getPiwigoServerAddress(getPrefs(), getContext());
            String callName = getString(R.string.logging_in_to_piwigo_pattern, serverUri);
            getUiHelper().invokeActiveServiceCall(callName, new LoginResponseHandler(), new OnLoginAction<>());
        }

        filesForUploadView = view.findViewById(R.id.selected_files_for_upload);

        privacyLevelSpinner = view.findViewById(R.id.privacy_level);

        CharSequence[] privacyLevelsText = getResources().getTextArray(R.array.privacy_levels_groups_array);
        long[] privacyLevelsValues = ArrayUtils.getLongArray(getResources().getIntArray(R.array.privacy_levels_values_array));
        BiArrayAdapter<CharSequence> privacyLevelOptionsAdapter = new BiArrayAdapter<>(requireContext(), privacyLevelsText, privacyLevelsValues);
//        if(!PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
//            // remove the "admin only" privacy option.
//            privacyLevelOptionsAdapter.remove(privacyLevelOptionsAdapter.getItemById(8)); // Admin ID
//        }
        // Specify the layout to use when the list of choices appears
        privacyLevelOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

// Apply the filesToUploadAdapter to the spinner
        privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);
        int defaultPrivacyLevelGroup = UploadPreferences.getDefaultPrivacyLevel(getContext(), getPrefs());
        setPrivacyLevelSpinnerSelection(defaultPrivacyLevelGroup);

        deleteUploadJobButton = view.findViewById(R.id.delete_upload_job_button);
        deleteUploadJobButton.setOnClickListener(v -> {
            onClickDeleteUploadJobButton();
        });

        uploadJobStatusButton = view.findViewById(R.id.view_detailed_upload_status_button);
        uploadJobStatusButton.setOnClickListener(v -> onClickUploadJobStatusButton());

        overallUploadProgressBar = view.findViewById(R.id.overall_upload_progress_bar);

        compressVideosSettings = view.findViewById(R.id.video_compression_options);
        compressVideosQualitySpinner = compressVideosSettings.findViewById(R.id.compress_videos_quality);
        BiArrayAdapter<String> videoQualityAdapter = new BiArrayAdapter<>(requireContext(), getResources().getStringArray(R.array.preference_data_upload_compress_videos_quality_items),
                ArrayUtils.getLongArray(getResources().getIntArray(R.array.preference_data_upload_compress_videos_quality_values)));
        videoQualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        compressVideosQualitySpinner.setAdapter(videoQualityAdapter);
        int compressionQualitySetting = UploadPreferences.getVideoCompressionQualityOption(getContext(), getPrefs());
        compressVideosQualitySpinner.setSelection(videoQualityAdapter.getPosition(compressionQualitySetting));

        compressVideosAudioBitrateSpinner = compressVideosSettings.findViewById(R.id.compress_videos_audio_bitrate);
        BiArrayAdapter<String> audioBitrateAdapter = new BiArrayAdapter<>(requireContext(), getResources().getStringArray(R.array.preference_data_upload_compress_videos_audio_bitrate_items),
                ArrayUtils.getLongArray(getResources().getIntArray(R.array.preference_data_upload_compress_videos_audio_bitrate_values)));
        audioBitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        compressVideosAudioBitrateSpinner.setAdapter(audioBitrateAdapter);
        int defaultAudioBitrate = UploadPreferences.getVideoCompressionAudioBitrate(getContext(), getPrefs());
        compressVideosAudioBitrateSpinner.setSelection(audioBitrateAdapter.getPosition(defaultAudioBitrate));

        deleteFilesAfterUploadCheckbox = view.findViewById(R.id.delete_files_after_upload_checkbox);
        deleteFilesAfterUploadCheckbox.setOnClickListener(v->{
            updateIndividualFilesSetting(getFilesForUploadViewAdapter().getUploadDataItemsModel().getUploadDataItemsReference(), false);
            // trigger a partial update.
            getFilesForUploadViewAdapter().notifyItemRangeChanged(0, getFilesForUploadViewAdapter().getItemCount(), Boolean.FALSE);
        });
        deleteFilesAfterUploadCheckbox.setChecked(UploadPreferences.isDeleteFilesAfterUploadDefault(getContext(), getPrefs()));

        compressVideosCheckbox = view.findViewById(R.id.compress_videos_button);
        if (!ExoPlayerCompression.isSupported()) {
            compressVideosCheckbox.setVisibility(GONE);
            compressVideosSettings.setVisibility(GONE);
        } else {
            compressVideosCheckbox.setOnClickListener(v -> {
                updateIndividualFilesSetting(getFilesForUploadViewAdapter().getUploadDataItemsModel().getUploadDataItemsReference(), false);
                // trigger a partial update.
                getFilesForUploadViewAdapter().notifyItemRangeChanged(0, getFilesForUploadViewAdapter().getItemCount(), Boolean.FALSE);
            });
            compressVideosCheckbox.setChecked(UploadPreferences.isCompressVideosByDefault(getContext(), getPrefs()));
        }
        allowUploadOfRawVideosIfIncompressibleCheckbox = view.findViewById(R.id.allow_upload_of_incompressible_videos_button);

        compressImagesSettings = view.findViewById(R.id.image_compression_options);
        compressImagesOutputFormatSpinner = compressImagesSettings.findViewById(R.id.compress_images_output_format);
        setSpinnerSelectedItem(compressImagesOutputFormatSpinner, UploadPreferences.getImageCompressionOutputFormat(getContext(), getPrefs()));

        compressImagesQualityNumberPicker = compressImagesSettings.findViewById(R.id.compress_images_quality);
        compressImagesQualityNumberPicker.setSaveFromParentEnabled(false);
        compressImagesQualityNumberPicker.setSaveEnabled(true);

        compressImagesMaxHeightNumberField = compressImagesSettings.findViewById(R.id.compress_images_max_height);
        compressImagesMaxHeightNumberField.setFilters(new InputFilter[]{new InputFilterMinMax(120, getResources().getInteger(R.integer.preference_data_upload_compress_images_max_height_max))});
        compressImagesMaxHeightNumberField.setSaveFromParentEnabled(false);
        compressImagesMaxHeightNumberField.setSaveEnabled(true);

        compressImagesMaxWidthNumberField = compressImagesSettings.findViewById(R.id.compress_images_max_width);
        compressImagesMaxWidthNumberField.setFilters(new InputFilter[]{new InputFilterMinMax(120, getResources().getInteger(R.integer.preference_data_upload_compress_images_max_width_max))});
        compressImagesMaxWidthNumberField.setSaveFromParentEnabled(false);
        compressImagesMaxWidthNumberField.setSaveEnabled(true);


        compressImagesCheckbox = view.findViewById(R.id.compress_images_button);
        compressImagesCheckbox.setOnClickListener(v -> {
            updateIndividualFilesSetting(getFilesForUploadViewAdapter().getUploadDataItemsModel().getUploadDataItemsReference(), false);
            // trigger a partial update.
            getFilesForUploadViewAdapter().notifyItemRangeChanged(0, getFilesForUploadViewAdapter().getItemCount(), Boolean.FALSE);
        });
        compressImagesCheckbox.setChecked(UploadPreferences.isCompressImagesByDefault(getContext(), getPrefs()));

        allowUploadOfRawVideosIfIncompressibleCheckbox.setChecked(UploadPreferences.isAllowUploadOfRawVideosIfIncompressible(getContext(), getPrefs()));

        uploadFilesNowButton = view.findViewById(R.id.upload_files_button);
        uploadFilesNowButton.setOnClickListener(v -> onClickUploadFilesButton());
        View clearIndividualOverridesButton = view.findViewById(R.id.clear_individual_overrides_button);
        clearIndividualOverridesButton.setOnClickListener((v)->onClickClearIndividualOverridesButton());

        MaterialButtonToggleGroup listModeToggleGroup = view.findViewById(R.id.toggle_list_mode_button_group);
        listModeToggleGroup.addOnButtonCheckedListener((v, buttonId, checked)->{

            if(buttonId == R.id.toggle_mode_normal) {
                filesToUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID);
                clearIndividualOverridesButton.setVisibility(GONE);
            } else {
                filesToUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_LIST);
                clearIndividualOverridesButton.setVisibility(VISIBLE);
            }
            filesToUploadAdapter.notifyDataSetChanged();
        });

        if (filesToUploadAdapter == null) {
            filesToUploadAdapter = new FilesToUploadRecyclerViewAdapter(new AdapterActionListener<>());
            filesToUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID);

//            filesToUploadAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
//                @Override
//                public void onChanged() {
//                    compressVideosCheckbox.setEnabled(isPlayableMediaFilesWaitingForUpload());
//                    compressVideosCheckbox.callOnClick();
//                    compressImagesCheckbox.setEnabled(isImageFilesWaitingForUpload());
//                    compressImagesCheckbox.callOnClick();
//                }
//            });
        }

        if (savedInstanceState != null) {
            // update view with saved data
            if (savedInstanceState.containsKey(SAVED_STATE_UPLOAD_JOB_ID)) {
                uploadJobId = savedInstanceState.getLong(SAVED_STATE_UPLOAD_JOB_ID);
            }
            // override the upload to album value (used to set clickable text field)
            uploadToAlbum = savedInstanceState.getParcelable(SAVED_STATE_UPLOAD_TO_ALBUM);
            UploadDataItemModel model = filesForUploadViewModel.getUploadDataItemModel();
            if(model != null) {
                filesToUploadAdapter.setUploadDataItemsModel(model);
            }
        }


        if (uploadToAlbum == null) {
            uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
        }

        if (!uploadToAlbum.isRoot() && !uploadToAlbum.isParentRoot()) {
            selectedGalleryTextView.setText(getString(R.string.subAlbum_text, uploadToAlbum.getName()));
        } else {
            selectedGalleryTextView.setText(uploadToAlbum.getName());
        }

        int columnsToShow = UploadPreferences.getColumnsOfFilesListedForUpload(prefs, requireActivity());

        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), columnsToShow);
        filesForUploadView.setLayoutManager(gridLayoutMan);
        gridLayoutMan.setSpanSizeLookup(new UploadItemSpanSizeLookup<>(filesToUploadAdapter, columnsToShow));
        filesForUploadView.addItemDecoration(new ItemSpacingDecoration(DisplayUtils.dpToPx(requireContext(), 4)));

        filesForUploadView.setAdapter(filesToUploadAdapter);

        updateActiveJobActionButtonsStatus();

        if (BuildConfig.DEBUG && ENABLE_COMPRESSION_BUTTON && ExoPlayerCompression.isSupported()) {
            injectCompressionControlsIntoView();
        }

        return view;
    }

    private void onClickClearIndividualOverridesButton() {
        updateIndividualFilesSetting(getFilesForUploadViewAdapter().getUploadDataItemsModel().getUploadDataItemsReference(), true);
        // trigger a partial update.
        getFilesForUploadViewAdapter().notifyItemRangeChanged(0, getFilesForUploadViewAdapter().getItemCount(), Boolean.FALSE);
    }

    protected void onClickInformationForUploadButton() {
        UploadJob job = getActiveJob(requireContext());
        ConnectionPreferences.ProfilePreferences profile;
        if(job == null) {
            profile = ConnectionPreferences.getActiveProfile();
        } else {
            profile = job.getConnectionPrefs();
        }
        UploadInformationView view = new UploadInformationView(profile);
        view.showNow(getChildFragmentManager(), UploadInformationView.class.getName());
    }

    private void invokeCallToRetrieveUploadDestination() {
        if (getArguments() != null) {
            uploadToAlbum = getArguments().getParcelable(SAVED_STATE_UPLOAD_TO_ALBUM);
            if (uploadToAlbum == null) {
                uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
                ConnectionPreferences.ResumeActionPreferences resumePrefs = getUiHelper().getResumePrefs();
                if (AbstractViewAlbumFragment.RESUME_ACTION.equals(resumePrefs.getReopenAction(requireContext()))) {
                    ArrayList<Long> albumPath = resumePrefs.getAlbumPath(requireContext());
                    if(!albumPath.isEmpty()) {
                        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
                        AlbumsGetFirstAvailableAlbumResponseHandler handler = new AlbumsGetFirstAvailableAlbumResponseHandler(albumPath, preferredAlbumThumbnailSize);
                        getUiHelper().addActionOnResponse(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, handler), new LoadAlbumTreeAction<>());
                    }
                }
            }
            externallyTriggeredSelectFilesActionId = getArguments().getInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID);
        }
    }


    private void injectCompressionControlsIntoView() {
        MaterialButton compressVideosButton = new MaterialButton(requireContext());
        compressVideosButton.setText(R.string.button_compress);

        compressVideosButton.setOnClickListener(v -> {
            v.setEnabled(false);
            FilesToUploadRecyclerViewAdapter<?,?,?> fileListAdapter = getFilesForUploadViewAdapter();
            Map<Uri,Long> filesForUpload = fileListAdapter.getFilesAndSizes();
            if (filesForUpload.isEmpty()) {
                return;
            }
            UploadJob.VideoCompressionParams compressionParams = buildVideoCompressionParams();
            if(compressionParams != null) {
                AdhocVideoCompression.compressVideos(v, compressionParams, filesForUpload, getUiHelper());
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.unable_to_compress_without_data));
            }
        });
        filesToUploadAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                compressVideosButton.setEnabled(isPlayableMediaFilesWaitingForUpload());
            }
        });
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToTop = R.id.upload_files_button;
        ((ConstraintLayout) uploadFilesNowButton.getParent()).addView(compressVideosButton, layoutParams);

    }



    private <T> void setSpinnerSelectedItem(Spinner spinner, T item) {
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter instanceof ArrayAdapter) {
            int itemPosition = ((ArrayAdapter<T>) spinner.getAdapter()).getPosition(item);
            spinner.setSelection(itemPosition);
        } else {
            Logging.log(Log.ERROR, TAG, "Cannot set selected spinner item - adapter is not instance of ArrayAdapter");
        }
    }

    private boolean isImageFilesWaitingForUpload() {
        return hasFileMatchingMime("image/*");
    }

    private boolean isPlayableMediaFilesWaitingForUpload() {
        return hasFileMatchingMime("video/*", "audio/*");
    }

    private boolean hasFileMatchingMime(String... mimeTypeFilter) {
        for (int i = 0; i < filesToUploadAdapter.getItemCount(); i++) {
            String mimeType = filesToUploadAdapter.getItemMimeType(i);
            if (null != MimeTypeFilter.matches(mimeType,mimeTypeFilter)) {
                return true;
            }
        }
        return false;
    }

    public MaterialButton getFileSelectButton() {
        return fileSelectButton;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void requestFileSelection(Set<String> allowedFileTypes) {
        if (getContext() != null) {
            // context could be null because there is a 3 second delay before the screen opens during which the user could close the app.
            FileSelectionNeededEvent event = new FileSelectionNeededEvent(true, false, true);
            String initialFolder = UploadPreferences.getDefaultLocalUploadFolder(getContext(), getPrefs());
            if(initialFolder != null) {
                event.withInitialFolder(Objects.requireNonNull(IOUtils.getLocalFileUri(initialFolder)));
            }
            event.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.LAST_MODIFIED_DATE);
            event.withSelectedUriPermissionsForConsumerId(URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
            event.setSelectedUriPermissionsForConsumerPurpose(getString(R.string.uri_permission_justification_to_upload));
            //event.requestUriReadWritePermissions();
            event.requestUriReadPermission();

            Set<String> visibleMimeTypes = new HashSet<>();
            Set<String> acceptableMimes = IOUtils.getMimeTypesFromFileExts(new HashSet<>(), allowedFileTypes);
            boolean videoSupported = IOUtils.hasMimeMatch(acceptableMimes, "video/*");
            boolean audioSupported = IOUtils.hasMimeMatch(acceptableMimes, "audio/*");
            if(videoSupported) {
                visibleMimeTypes.add("video/*");
            }
            if(videoSupported || audioSupported) {
                visibleMimeTypes.add("audio/*");
            }
            IOUtils.getMimeTypesFromFileExts(visibleMimeTypes, allowedFileTypes);
            event.withVisibleMimeTypes(visibleMimeTypes);

            getUiHelper().setTrackingRequest(event.getActionId());
            EventBus.getDefault().post(event);
        }
    }

    public Long getUploadJobId() {
        return uploadJobId;
    }

    public void setUploadJobId(Long uploadJobId) {
        this.uploadJobId = uploadJobId;
    }

    private void onClickDeleteUploadJobButton() {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, getString(R.string.alert_really_delete_upload_job), R.string.button_no, R.string.button_yes, new OnDeleteJobQuestionAction<>(getUiHelper()));
    }

    private void onClickUploadJobStatusButton() {
        UploadJob uploadJob = getActiveJob(getContext());
        if (uploadJob != null) {
            EventBus.getDefault().post(new ViewJobStatusDetailsEvent(uploadJob));
        } else {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.job_not_found);
            uploadJobStatusButton.setVisibility(GONE);
        }
    }

    private void onClickSelectedGalleryTextView() {
        HashSet<Long> selection = new HashSet<>();
        selection.add(uploadToAlbum.getId());
        ExpandingAlbumSelectionNeededEvent evt = new ExpandingAlbumSelectionNeededEvent(false, true, selection, uploadToAlbum.getParentId());
        evt.setConnectionProfileName(null);// change this if we allow upload to other servers from this page.
        getUiHelper().setTrackingRequest(evt.getActionId());
        EventBus.getDefault().post(evt);
    }

    private void onClickFileSelectionWanted() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        if (sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            String serverUri = ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getContext());
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), new LoginResponseHandler());
        } else {
            if (sessionDetails.getAllowedFileTypes() == null) {
                fileSelectButton.setEnabled(false);
                Bundle b = new Bundle();
                sessionDetails.writeToBundle(b);
                Logging.logAnalyticEvent(requireContext(),"IncompleteUserSession", b);
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_user_session_no_allowed_filetypes);
                requireView().postDelayed(() -> {
                    fileSelectButton.setEnabled(true);
                    requestFileSelection(null); // show all files... I hope the user is sensible!
                }, 3000);
            } else {
                requestFileSelection(sessionDetails.getAllowedFileTypes());
            }
        }
    }

    public void switchToUploadingFilesTab() {
        mViewPager.setCurrentItem(AbstractUploadFragment.TAB_IDX_FILES);
    }

    protected void onBeforeReloadDataFromUploadTask(UploadJob uploadJob) {
        showOverallUploadProgressIndicator(R.string.loading_please_wait,0);

        //register the potentially completely new handler to handle the existing job messages
        getUiHelper().getPiwigoResponseListener().switchHandlerId(uploadJob.getResponseHandlerId());
        getUiHelper().updateHandlerForAllMessages();

        setUploadJobId(uploadJob.getJobId());
        uploadToAlbum = new CategoryItemStub(uploadJob.getUploadToCategory().getName(), uploadJob.getUploadToCategory().getId());
        AlbumGetChildAlbumNamesResponseHandler getChildAlbumNamesHandler = new AlbumGetChildAlbumNamesResponseHandler(uploadJob.getUploadToCategory().getId(), false);
        getUiHelper().addActionOnResponse(getUiHelper().addActiveServiceCall(getChildAlbumNamesHandler), new OnGetChildAlbumNamesAction<>());
        selectedGalleryTextView.setText(uploadToAlbum.getName());

        byte privacyLevelWanted = uploadJob.getPrivacyLevelWanted();
        if (privacyLevelWanted >= 0) {
            privacyLevelSpinner.setSelection(((BiArrayAdapter<?>) privacyLevelSpinner.getAdapter()).getPosition(privacyLevelWanted));
        }
    }

    public void showOverallUploadProgressIndicator(@StringRes int progressDescText, int progress) {
        overallUploadProgressBar.showProgressIndicator(progressDescText, progress);
    }
    
    public void hideOverallUploadProgressIndicator() {
        overallUploadProgressBar.hideProgressIndicator();
    }

    public ProgressIndicator getOverallUploadProgressIndicator() {
        return overallUploadProgressBar;
    }

    private void updateUiUploadStatusFromJobIfRun(@Nullable UploadJob uploadJob) {
        if (uploadJob != null) {
            new ReloadDataFromUploadJobTask<>((F)this, uploadJob).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
            Logging.log(Log.ERROR, TAG, "Unable to view upload fragment - removing from activity");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onStart() {
        addUploadingAsFieldsIfAppropriate(requireView());
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

        FileSelectionCompleteEvent evt = EventBus.getDefault().getStickyEvent(FileSelectionCompleteEvent.class);
        if(evt != null) {
            onEvent(evt);
        }

        UploadJob job = getActiveJob(requireContext());
        if(job != null && job.hasJobCompletedAllActionsSuccessfully()) {
            Logging.log(Log.WARN, TAG, "Removing Foreground job. Incorrectly hasn't been deleted after success");
            new ForegroundJobLoadActor(requireContext()).removeJob(job, true);
            job = null;
        }
        updateUiUploadStatusFromJobIfRun(job);
    }

    @Override
    public void onPause() {
        purgeAnyUnwantedSharedFiles();
        super.onPause();
    }

    private void purgeAnyUnwantedSharedFiles() {
        DocumentFile sharedFilesFolder = IOUtils.getSharedFilesFolder(requireContext());
        int deleted = 0;
        if(sharedFilesFolder.listFiles().length > 0 && getActiveJob(requireContext()) == null) {
            for(DocumentFile oldFile : sharedFilesFolder.listFiles()) {
                if(!filesToUploadAdapter.contains(oldFile.getUri())) {
                    oldFile.delete();
                    deleted++;
                }
            }
        }
        if(deleted > 0) {
            Logging.log(Log.DEBUG, TAG, "Deleted unneeded shared files : " + deleted);
        }
    }

    public FilesToUploadRecyclerViewAdapter getFilesForUploadViewAdapter() {
        return (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
    }

    public CategoryItemStub getUploadToAlbum() {
        return uploadToAlbum;
    }

    protected void updateFilesForUploadList(List<UploadDataItem> folderItemsToBeUploaded) {
        overallUploadProgressBar.showProgressIndicator(R.string.progress_importing_files, -1);
        try {
            if (folderItemsToBeUploaded.size() > 0) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                int addedItems = 0;

                for (UploadDataItem item : folderItemsToBeUploaded) {
                    if (adapter.add(item)) {
                        addedItems++;
                    }
                }
                if (addedItems > 0) {
                    adapter.notifyDataSetChanged();
                }

                int filesAlreadyPresent = folderItemsToBeUploaded.size() - addedItems;
                if (filesAlreadyPresent > 0) {
                    // need to use the context in the UIHelper because this fragment may yet not be attached itself.
                    getUiHelper().showDetailedShortMsg(R.string.alert_information, getUiHelper().getAppContext().getString(R.string.duplicates_ignored_pattern, filesAlreadyPresent));
                }

                try {
                    updateActiveJobActionButtonsStatus();
                } catch(IllegalStateException e) {
                    Logging.log(Log.WARN,TAG, "Unable to update job action buttons without attached context");
                }
            }
            updateTotalUploadSizeField();
        } finally {
            overallUploadProgressBar.hideProgressIndicator();
        }
    }

    private void updateTotalUploadSizeField() {
        try {
            long bytesToUpload = filesToUploadAdapter.getTotalSizeOfFiles();
            filesForUploadCountView.setText(getString(R.string.files_to_upload_count_label_pattern, filesToUploadAdapter.getItemCount(), IOUtils.bytesToNormalizedText(bytesToUpload)));
        } catch(Exception e) {
            // don't let this cause an issue for anything it isn't important.
            Logging.recordException(e);
        }
    }

    protected void updateLastOpenedFolderPref(Context context, List<FolderItem> folderItemsToBeUploaded) {
        for(FolderItem item : folderItemsToBeUploaded) {
            try {
                DocumentFile documentFile = item.getDocumentFile();
                if(documentFile != null) {
                    DocumentFile folder = documentFile.getParentFile();
                    if (folder != null) { // will be null for any shared files.
                        Uri lastOpenedFolder = folder.getUri();
                        UploadPreferences.setDefaultLocalUploadFolder(context, getPrefs(), lastOpenedFolder);
                        break; // don't try again. One folder is fine.
                    }
                }
            } catch (IllegalArgumentException e) {
                // this will occur for any shared files as they're not DocumentFiles. We can safely ignore it.
            }
        }
    }

    private void onClickUploadFilesButton() {
        UploadJob job = getActiveJob(requireContext());
        if(job != null) {
            // resubmit the old job.
            submitUploadJob(job);
        } else {
            buildAndSubmitNewUploadJob();
        }
    }

    private void buildAndSubmitNewUploadJob() {
        buildAndSubmitNewUploadJob(false);
    }

    public void buildAndSubmitNewUploadJob(boolean fileSizesHaveBeenChecked) {
        FilesToUploadRecyclerViewAdapter<?,?,?> fileListAdapter = getFilesForUploadViewAdapter();
        UploadDataItemModel model = fileListAdapter.getUploadDataItemsModel();
        byte privacyLevelWanted = (byte) privacyLevelSpinner.getSelectedItemId(); // save as just bytes!
        long piwigoListenerId = getUiHelper().getPiwigoResponseListener().getHandlerId();
        boolean isDeleteFilesAfterUpload = deleteFilesAfterUploadCheckbox.isChecked();
        CreateAndSubmitUploadJobTask createAndSubmitUploadJobTask = new CreateAndSubmitUploadJobTask(this, model, uploadToAlbum, privacyLevelWanted, piwigoListenerId, fileSizesHaveBeenChecked);
        createAndSubmitUploadJobTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    protected void withNewUploadJob(UploadJob newJob) {
        mViewPager.setCurrentItem(TAB_IDX_FILES);
        submitUploadJob(newJob);
    }

    protected UploadJob.ImageCompressionParams buildImageCompressionParams() {
        if (compressImagesCheckbox.isChecked()) {
            UploadJob.ImageCompressionParams imageCompParams = new UploadJob.ImageCompressionParams();
            imageCompParams.setOutputFormat(compressImagesOutputFormatSpinner.getSelectedItem().toString());
            imageCompParams.setQuality(Float.valueOf(compressImagesQualityNumberPicker.getValue()).intValue());
            imageCompParams.setMaxHeight(Integer.parseInt(compressImagesMaxHeightNumberField.getText().toString()));
            imageCompParams.setMaxWidth(Integer.parseInt(compressImagesMaxWidthNumberField.getText().toString()));
        }
        return null;
    }

    protected @Nullable UploadJob.VideoCompressionParams buildVideoCompressionParams() {
        if (compressVideosCheckbox.isChecked()) {

            UploadJob.VideoCompressionParams vidCompParams = new UploadJob.VideoCompressionParams();
            vidCompParams.setQuality(((double) compressVideosQualitySpinner.getSelectedItemId()) / 1000);
            vidCompParams.setAudioBitrate((int) compressVideosAudioBitrateSpinner.getSelectedItemId());

            return vidCompParams;
        }
        return null;
    }

    public void onUploadJobSettingsNeeded() {
        mViewPager.setCurrentItem(TAB_IDX_SETTINGS);
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
        deleteUploadJobButton.setVisibility(VISIBLE);
    }

    



    private void submitUploadJob(UploadJob activeJob) {
        uploadJobId = activeJob.getJobId();
        // the job will be submitted within the onEvent(PermissionsWantedResponse event) method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        } else {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        }
        AdsManager.getInstance(getContext()).showFileToUploadAdvertIfAppropriate(requireActivity());
    }

    public void onUserActionDeleteFileFromUpload(@NonNull final FilesToUploadRecyclerViewAdapter adapter, @NonNull final Uri itemToRemove, boolean longClick) {
        final UploadJob activeJob = getActiveJob(getContext());
        if (activeJob != null) {
            if (!activeJob.isStatusRunningNow()) {
                activeJob.cancelFileUpload(itemToRemove);
                adapter.remove(itemToRemove);
                releaseUriPermissionsForUploadItem(itemToRemove);
                new ForegroundJobLoadActor(requireContext()).saveStateToDisk(activeJob);
                if (activeJob.getFileUploadDetails(itemToRemove).isFilePartiallyUploaded()) {
                    // job stopped, the upload of this file has been started
                    String message = getString(R.string.alert_message_remove_file_server_state_incorrect);
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_information, message, new PartialUploadFileAction<>(getUiHelper(), itemToRemove));
                }
            } else {
                // job running.
                boolean immediatelyCancelled = activeJob.cancelFileUpload(itemToRemove);
                DocumentFile fileRemoved = IOUtils.getSingleDocFile(requireContext(), itemToRemove);
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_file_upload_cancelled_pattern, fileRemoved.getName()));
                if (immediatelyCancelled) {
                    adapter.remove(itemToRemove);
                    releaseUriPermissionsForUploadItem(itemToRemove);
                }
                EventBus.getDefault().post(new CancelFileUploadEvent(activeJob.getJobId(), itemToRemove));
            }
            if (!activeJob.isStatusRunningNow() && activeJob.getFilesAwaitingUpload().size() == 0) {
                // no files left to upload. Lets switch the button from upload to finish
                uploadFilesNowButton.setText(R.string.upload_files_finish_job_button_title);
            }
        } else {
            if (longClick) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_delete_all_files_selected_for_upload), R.string.button_no, R.string.button_yes, new DeleteAllFilesSelectedAction<>(getUiHelper()));
            } else {
                adapter.remove(itemToRemove);
                releaseUriPermissionsForUploadItem(itemToRemove);
                updateActiveJobActionButtonsStatus();
                updateTotalUploadSizeField();
            }
        }
    }

    public void allowUserUploadConfiguration(@Nullable UploadJob uploadJob) {

        filesToUploadAdapter = getFilesForUploadViewAdapter();
        boolean filesStillToBeUploaded = filesToUploadAdapter.getItemCount() > 0;
        boolean noJobIsYetConfigured = uploadJob == null;
        boolean jobIsFinished = uploadJob != null && uploadJob.isStatusFinished();

        if(uploadJob == null) {
            //filesToUploadAdapter.clearUploadProgress();
            uploadFilesNowButton.setText(R.string.upload_files_button_title);
        } else {
            uploadFilesNowButton.setText(R.string.upload_files_finish_job_button_title);
        }

        compressVideosCheckbox.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        compressVideosCheckbox.callOnClick(); // set relevant fields to enabled / disabled

        compressImagesCheckbox.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        compressImagesCheckbox.callOnClick(); // set relevant fields to enabled / disabled

        compressImagesSettings.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        compressVideosSettings.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));

        deleteFilesAfterUploadCheckbox.setEnabled(noJobIsYetConfigured || jobIsFinished && !filesStillToBeUploaded);

        fileSelectButton.setEnabled(noJobIsYetConfigured || jobIsFinished && !filesStillToBeUploaded);

        fileSelectButton.setEnabled(noJobIsYetConfigured || jobIsFinished);
        selectedGalleryTextView.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        privacyLevelSpinner.setEnabled(noJobIsYetConfigured || jobIsFinished);

        updateActiveJobActionButtonsStatus(uploadJob);
    }

    private void updateActiveJobActionButtonsStatus() {
        UploadJob job = getActiveJob(requireContext());
        updateActiveJobActionButtonsStatus(job);
    }

    private void updateActiveJobActionButtonsStatus(@Nullable UploadJob job) {
        boolean essentiallyRunning = job != null && (job.isStatusSubmitted() || job.isStatusRunningNow());
        if (job != null && !essentiallyRunning && !job.hasJobCompletedAllActionsSuccessfully()) {
            uploadJobStatusButton.setVisibility(VISIBLE);
            boolean canForceDeleteJob = job.isHasRunBefore();
            deleteUploadJobButton.setVisibility(canForceDeleteJob ? VISIBLE : GONE);
            uploadFilesNowButton.setEnabled(canForceDeleteJob);
        } else {
            boolean canStartNewJob = job == null && filesToUploadAdapter.getItemCount() > 0 && !uploadToAlbum.isRoot();
            uploadFilesNowButton.setEnabled(canStartNewJob);
            uploadJobStatusButton.setVisibility(GONE);
            deleteUploadJobButton.setVisibility(GONE);
        }
    }
/* This shouldn't be needed. The listener should capture this.
    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ForegroundUploadFinishedEvent event) {
        if(getUiHelper().getPiwigoResponseListener().getParent() == null) {
            //listenerDetached
            getUiHelper().getPiwigoResponseListener().withUiHelper((F) this, getUiHelper());
            UploadJob job = getActiveJob(requireContext());
            ((ForegroundPiwigoFileUploadResponseListener<F,FUIH>)getUiHelper().getPiwigoResponseListener()).onUploadComplete(requireContext(), job);
        }
    }*/

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            uploadToAlbum = event.getAlbumDetail().toStub();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {
        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (!event.areAllPermissionsGranted()) {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_required_permissions_not_granted_action_cancelled));
                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_required_permissions_not_granted_action_cancelled_scoped_storage));
                }
                return;
            }
            submitUploadJobWithPermissions();
        }
    }

    private void submitUploadJobWithPermissions() {
        UploadJob activeJob = getActiveJob(requireContext());
        //ensure the handler is actively listening before the job starts.
        getUiHelper().addBackgroundServiceCall(activeJob.getJobId());
        ForegroundPiwigoUploadService.startActionRunOrReRunUploadJob(requireContext(), activeJob);
        allowUserUploadConfiguration(activeJob);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
//        if (filesForUploadView != null) {
//            filesForUploadView.setAdapter(null);
//        }
//        if (privacyLevelSpinner != null) {
//            privacyLevelSpinner.setAdapter(null);
//        }
    }

    protected void notifyUser(Context context, int titleId, String message) {
        if (!isAdded() || getActivity() == null) {
            notifyUserUploadStatus(context.getApplicationContext(), message);
        } else {
            getUiHelper().showDetailedMsg(titleId, message);
        }
    }

    private void notifyUserUploadStatus(Context ctx, String message) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx, getUiHelper().getLowImportanceNotificationChannelId())
                .setContentTitle(ctx.getString(R.string.notification_upload_event))
                .setContentText(message)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // this is not a vector graphic
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
            mBuilder.setCategory("progress");
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
            mBuilder.setCategory(NotificationCompat.CATEGORY_PROGRESS);
        }


//      Clear the last notification
        getUiHelper().clearNotification(TAG, 1);
        getUiHelper().showNotification(TAG, 1, mBuilder.build());
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new ForegroundPiwigoFileUploadResponseListener<>(context);
    }

    protected void processPiwigoError(Context context, PiwigoResponseBufferingHandler.Response error) {
        String errorMessage = null;
        Throwable cause;
        if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
            errorMessage = String.format(context.getString(R.string.alert_upload_failed_webserver), err.getStatusCode(), err.getErrorMessage());
            cause = err.getError(); //TODO maybe extract info from this cause too if wanted.
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
            errorMessage = String.format(context.getString(R.string.alert_upload_failed_webresponse), err.getRawResponse());
        } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
            PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
            errorMessage = String.format(context.getString(R.string.alert_upload_failed_piwigo), err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
        }
        if (errorMessage != null) {
            notifyUser(context, R.string.alert_error, errorMessage);
        }
    }

    public UploadJob getActiveJob(Context context) {
        UploadJob uploadJob;
        if (uploadJobId == null) {
            uploadJob = new ForegroundJobLoadActor(context).getFirstActiveForegroundJob();
        } else {
            uploadJob = new ForegroundJobLoadActor(context).getActiveForegroundJob(uploadJobId);
        }
        return uploadJob;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionCompleteEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            if (event.getSelectedItems() != null && event.getSelectedItems().size() > 0) {
                CategoryItem selectedAlbum = event.getSelectedItems().iterator().next();
                setUploadToAlbum(selectedAlbum.toStub());
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as app locked event rxd");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    protected void onAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {

    }

    public void setUploadToAlbum(CategoryItemStub uploadToAlbum) {
        this.uploadToAlbum =  uploadToAlbum;
        if (!uploadToAlbum.isRoot() && !uploadToAlbum.isParentRoot()) {
            getSelectedGalleryTextView().setText(getString(R.string.subAlbum_text, uploadToAlbum.getName()));
        } else {
            getSelectedGalleryTextView().setText(uploadToAlbum.getName());
        }
        updateActiveJobActionButtonsStatus();
    }

    private TextView getSelectedGalleryTextView() {
        return selectedGalleryTextView;
    }

    public Button getUploadFilesNowButton() {
        return uploadFilesNowButton;
    }

    public void onUserActionDeleteAllFilesFromUploadImmediately() {
        Map<Uri,Long> filesAndSizes = getFilesForUploadViewAdapter().getFilesAndSizes();
        Set<Uri> uris = filesAndSizes.keySet();

        UiUpdatingProgressListener progressListener = new UiUpdatingProgressListener(overallUploadProgressBar, R.string.removing_files_from_job);
        BasicProgressTracker tracker = new BasicProgressTracker("removing files from upload", 2, progressListener);
        releaseUriPermissionsForUploadItems(uris);
        tracker.incrementWorkDone(1);
        getFilesForUploadViewAdapter().removeAll(uris);
        tracker.incrementWorkDone(2);
        updateActiveJobActionButtonsStatus();
        overallUploadProgressBar.hideProgressIndicator();
        updateTotalUploadSizeField();
    }


    private void releaseUriPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            appSettingsViewModel.removeAllUriPermissionsRecords(requireContext(), URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
        }
    }

    private void releaseUriPermissionsForUploadItems(Collection<Uri> fileForUploadUris) {
        //FIXME this is called but seems to call the other one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            appSettingsViewModel.removeAllUriPermissionsRecords(requireContext(), fileForUploadUris, URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
        }
    }

    protected void releaseUriPermissionsForUploadItem(@NonNull Uri fileForUploadUri) {
        //FIXME this is called way too often  (on file finished with).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            appSettingsViewModel.removeAllUriPermissionsRecords(requireContext(), fileForUploadUri, URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
        }
    }

    public void onNotificationUploadJobSuccess(UploadJob job) {
        setUploadJobId(null);
        String message = getString(R.string.alert_upload_success);
        notifyUser(getContext(), R.string.alert_success, message);
        allowUserUploadConfiguration(null);
        updateTotalUploadSizeField();
    }

    public void onNotificationUploadJobFailure(UploadJob job) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.some_files_failed_to_upload_what_action_do_you_wish_to_take), R.string.button_review, R.string.button_retry, new OnUploadJobFailureQuestionListener<>(getUiHelper()));
        allowUserUploadConfiguration(job);
    }

    public void onAddFilesForUpload(List<UploadDataItem> folderItems) {
        hideOverallUploadProgressIndicator();
        switchToUploadingFilesTab();
        updateIndividualFilesSetting(folderItems, false);
        updateFilesForUploadList(folderItems);
    }

    private void updateIndividualFilesSetting(List<UploadDataItem> folderItems, boolean clearIndividualValues) {
        boolean compressVideos = compressVideosCheckbox.isChecked();
        boolean compressImages = compressImagesCheckbox.isChecked();
        boolean deleteAfterUpload = deleteFilesAfterUploadCheckbox.isChecked();
        boolean warnCompressionNeededForSomeFiles = false;
        for(UploadDataItem item : folderItems) {
            if(clearIndividualValues) {
                item.setDeleteAfterUpload(null);
                item.setCompressThisFile(null);
            }
            if (IOUtils.isPlayableMedia(item.getMimeType())) {
                item.setCompressThisFileByDefault(compressVideos);
            } else if (IOUtils.isImage(item.getMimeType())) {
                item.setCompressThisFileByDefault(compressImages);
            }
            item.setDeleteByDefault(deleteAfterUpload);
            if(item.isNeedsCompression() && !(item.isCompressByDefault() || item.isCompressThisFile())) {
                warnCompressionNeededForSomeFiles = true;
            }
        }
        if(warnCompressionNeededForSomeFiles) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, getString(R.string.alert_error_require_compression_pattern));
        }
    }

    public void onUserActionDeleteUploadJob() {
        UploadJob job = getActiveJob(requireContext());
        if (job == null) {
            Logging.log(Log.WARN, TAG, "User attempted to delete job that was no longer exists");
            return;
        }
        onUserActionDeleteUploadJob(job);
    }

    public void onUserActionDeleteUploadJob(@NonNull UploadJob job) {
        if (job.getTemporaryUploadAlbumId() > 0) {
            AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(job.getTemporaryUploadAlbumId(), false);
            getUiHelper().addNonBlockingActiveServiceCall(getString(R.string.alert_deleting_temporary_upload_album), albumDelHandler.invokeAsync(getContext(), job.getConnectionPrefs()), albumDelHandler.getTag());
        }
        IOUtils.deleteAllFilesSharedWithThisApp(requireContext());
        new ForegroundJobLoadActor(requireContext()).removeJob(job, true);
        filesToUploadAdapter.clear();
        uploadJobId = null;
        allowUserUploadConfiguration(null);
    }

    public void populateUiFromJob(UploadJob uploadJob) {
        setUploadToAlbum(uploadJob.getUploadToCategory());
        setPrivacyLevelSpinnerSelection(uploadJob.getPrivacyLevelWanted());
        updateTotalUploadSizeField();
        allowUserUploadConfiguration(uploadJob);
    }

    private void setPrivacyLevelSpinnerSelection(int groupPermission) {
        privacyLevelSpinner.setSelection(((BiArrayAdapter<?>) privacyLevelSpinner.getAdapter()).getPosition(groupPermission));
    }

    private static class OnUploadJobFailureQuestionListener<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public OnUploadJobFailureQuestionListener(FUIH uiHelper) {
            super(uiHelper);
        }

        protected OnUploadJobFailureQuestionListener(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                getParent().onUserActionClearUploadErrorsAndRetry();
            } else if (Boolean.FALSE == positiveAnswer) {
                getParent().onUserActionReviewUploadErrors();
            }
        }

        public static final Creator<OnUploadJobFailureQuestionListener<?,?>> CREATOR = new Creator<OnUploadJobFailureQuestionListener<?,?>>() {
            @Override
            public OnUploadJobFailureQuestionListener<?,?> createFromParcel(Parcel in) {
                return new OnUploadJobFailureQuestionListener<>(in);
            }

            @Override
            public OnUploadJobFailureQuestionListener<?,?>[] newArray(int size) {
                return new OnUploadJobFailureQuestionListener[size];
            }
        };
    }

    protected void onUserActionReviewUploadErrors() {
        // show the upload status.
        onClickUploadJobStatusButton();
    }

    protected void onUserActionClearUploadErrorsAndRetry() {
        UploadJob uploadJob = getActiveJob(requireContext());
        for(Uri file : uploadJob.getFilesRequiringRetry()) {
            getFilesForUploadViewAdapter().updateUploadStatus(file, null);
        }
        uploadJob.clearUploadErrors();
        new ForegroundJobLoadActor(requireContext()).saveStateToDisk(uploadJob);
        submitUploadJob(uploadJob);
    }

    private class AdapterActionListener<LVA extends FilesToUploadRecyclerViewAdapter<LVA,MSA,VH>, MSA extends UploadItemMultiSelectStatusAdapter<MSA, LVA,VH>, VH extends UploadDataItemViewHolder<VH, LVA,MSA>> implements FilesToUploadRecyclerViewAdapter.RemoveListener<LVA,MSA,VH> {
        @Override
        public void onRemove(LVA adapter, Uri itemToRemove, boolean longClick) {
            onUserActionDeleteFileFromUpload(adapter, itemToRemove, longClick);
        }
    }
}
