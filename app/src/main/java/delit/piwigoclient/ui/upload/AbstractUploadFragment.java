package delit.piwigoclient.ui.upload;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import delit.libs.core.util.Logging;
import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomClickTouchListener;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.ui.view.list.BiArrayAdapter;
import delit.libs.util.ArrayUtils;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.Md5SumUtils;
import delit.libs.util.SetUtils;
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
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.piwigoApi.upload.BasePiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.ViewJobStatusDetailsEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;


/**
 * A simple {@link Fragment} subclass.
 * to handle interaction events.
 * Use the {@link UploadFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public abstract class AbstractUploadFragment extends MyFragment implements FilesToUploadRecyclerViewAdapter.RemoveListener {
    // the fragment initialization parameters
    private static final String TAG = "UploadFragment";
    private static final String SAVED_STATE_UPLOAD_TO_ALBUM = "uploadToAlbum";
    private static final String SAVED_STATE_UPLOAD_JOB_ID = "uploadJobId";
    private static final String ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID = "externallyTriggeredSelectFilesActionId";
    private static final boolean ENABLE_COMPRESSION_BUTTON = true;
    private static final int TAB_IDX_SETTINGS = 1;
    private static final int TAB_IDX_FILES = 0;
    private static final String FILES_TO_UPLOAD_ADAPTER_STATE = "filesToUploadAdapter";
    private static final String URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD = "foregroundUpload";

    private AppSettingsViewModel appSettingsViewModel;
    private Long uploadJobId;
    private long externallyTriggeredSelectFilesActionId;
    private CategoryItemStub uploadToAlbum;
    private RecyclerView filesForUploadView;
    private Button uploadFilesNowButton;
    private Button deleteUploadJobButton;
    private TextView selectedGalleryTextView;
    private Spinner privacyLevelSpinner;
    private SwitchMaterial deleteFilesAfterUploadCheckbox;
    private MaterialButton fileSelectButton;
    private FilesToUploadRecyclerViewAdapter filesToUploadAdapter;
    private Button uploadJobStatusButton;
    private TextView uploadableFilesView;
    private SwitchMaterial compressVideosCheckbox;
    private SwitchMaterial allowUploadOfRawVideosIfIncompressibleCheckbox;
    private SwitchMaterial compressImagesCheckbox;
    private ViewPager mViewPager;
    private LinearLayout compressImagesSettings;
    private LinearLayout compressVideosSettings;
    private Spinner compressVideosQualitySpinner;
    private Spinner compressVideosAudioBitrateSpinner;
    private NumberPicker compressImagesQualityNumberPicker;
    private Spinner compressImagesOutputFormatSpinner;
    private NumberPicker compressImagesMaxHeightNumberPicker;
    private NumberPicker compressImagesMaxWidthNumberPicker;
    private ProgressIndicator overallUploadProgressBar;


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
            filesToUploadAdapter.onSaveInstanceState(outState, FILES_TO_UPLOAD_ADAPTER_STATE);
        }
        outState.putParcelable(SAVED_STATE_UPLOAD_TO_ALBUM, uploadToAlbum);
        outState.putLong(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID, externallyTriggeredSelectFilesActionId);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewModelStoreOwner viewModelProvider = DisplayUtils.getViewModelStoreOwner(getContext());
        appSettingsViewModel = new ViewModelProvider(viewModelProvider).get(AppSettingsViewModel.class);
        if (getArguments() != null) {
            uploadToAlbum = getArguments().getParcelable(SAVED_STATE_UPLOAD_TO_ALBUM);
            if (uploadToAlbum == null) {
                uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
                SharedPreferences resumePrefs = getUiHelper().getResumePrefs();
                if (AbstractViewAlbumFragment.RESUME_ACTION.equals(resumePrefs.getString("reopenAction", null))) {
                    ArrayList<Long> albumPath = CollectionUtils.longsFromCsvList(resumePrefs.getString("reopenAlbumPath", null));
                    if(!albumPath.isEmpty()) {
                        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
                        AlbumsGetFirstAvailableAlbumResponseHandler handler = new AlbumsGetFirstAvailableAlbumResponseHandler(albumPath, preferredAlbumThumbnailSize);
                        getUiHelper().addActionOnResponse(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, handler), new LoadAlbumTreeAction());
                    }
                }
            }
            externallyTriggeredSelectFilesActionId = getArguments().getInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent stickyEvent) {
        if (externallyTriggeredSelectFilesActionId == stickyEvent.getActionId() || getUiHelper().isTrackingRequest(stickyEvent.getActionId())) {
            new SharedFilesIntentProcessingTask(this).execute(stickyEvent);
        }
    }

    private static class SharedFilesIntentProcessingTask extends OwnedSafeAsyncTask<AbstractUploadFragment, FileSelectionCompleteEvent, Object, List<FilesToUploadRecyclerViewAdapter.UploadDataItem>> {

        SharedFilesIntentProcessingTask(AbstractUploadFragment parent) {
            super(parent);
            withContext(parent.requireContext());
        }

        @Override
        protected void onPreExecuteSafely() {
            getOwner().overallUploadProgressBar.showProgressIndicator(0);
        }


        @Override
        protected List<FilesToUploadRecyclerViewAdapter.UploadDataItem> doInBackgroundSafely(FileSelectionCompleteEvent[] objects) {

            FileSelectionCompleteEvent event = objects[0];
            ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
            if (PiwigoSessionDetails.getInstance(activeProfile) != null) {
                EventBus.getDefault().removeStickyEvent(event);

                Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
                int itemCount = event.getSelectedFolderItems().size();
                Iterator<FolderItemRecyclerViewAdapter.FolderItem> iter = event.getSelectedFolderItems().iterator();
                Set<String> unsupportedExts = new HashSet<>();
                int currentItem = 0;
                while (iter.hasNext()) {
                    currentItem++;
                    FolderItemRecyclerViewAdapter.FolderItem f = iter.next();
                    if(f.getDocumentFile() == null) {
                        try {
                            f.getExt();
                        } catch (IllegalStateException e) {
                            if(!f.withMediaStoreCachedFields(getContext())) {
                                f.cacheDocFileFields(getContext());
                            }
                        }
                    }
                    if (!allowedFileTypes.contains(f.getExt())) {
                        String mimeType = f.getMime();
                        if (mimeType == null || !MimeTypeFilter.matches(mimeType, "video/*")) {
                            iter.remove();
                            unsupportedExts.add(f.getExt());
                        }
                    }
                    int currentProgress = (int)Math.round(((0.5 * currentItem) / itemCount) * 100);
                    getOwner().overallUploadProgressBar.post(() -> getOwner().overallUploadProgressBar.showProgressIndicator(R.string.adding_files, currentProgress));
                }
                if (!unsupportedExts.isEmpty()) {
                    getOwner().getUiHelper().showDetailedMsg(R.string.alert_information, getOwner().getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedExts)));
                }
            }

            int itemCount = event.getSelectedFolderItems().size();
            int currentItem = 0;
            ArrayList<FilesToUploadRecyclerViewAdapter.UploadDataItem> uploadDataItems = new ArrayList<>(event.getSelectedFolderItems().size());
            for (FolderItemRecyclerViewAdapter.FolderItem f : event.getSelectedFolderItems()) {
                currentItem++;
                if(BuildConfig.DEBUG) {
                    Log.e(TAG, "Upload Fragment Passed URI: " + f.getContentUri());
                }
                FilesToUploadRecyclerViewAdapter.UploadDataItem item = new FilesToUploadRecyclerViewAdapter.UploadDataItem(f.getContentUri(), f.getName(), f.getMime());
                try {
                    item.calculateDataHashCode(getContext());
                    uploadDataItems.add(item);
                } catch (Md5SumUtils.Md5SumException e) {
                    Logging.recordException(e);
                }
                int currentProgress = (int)Math.round((0.5 + ((0.5 * currentItem) / itemCount)) * 100);
                getOwner().overallUploadProgressBar.post(() -> getOwner().overallUploadProgressBar.showProgressIndicator(R.string.calculating_file_checksums, currentProgress));
            }
            getOwner().updateLastOpenedFolderPref(getContext(), event.getSelectedFolderItems());
            return uploadDataItems;
        }

        @Override
        protected void onPostExecuteSafely(List<FilesToUploadRecyclerViewAdapter.UploadDataItem> folderItems) {
            getOwner().overallUploadProgressBar.setVisibility(View.GONE);
            getOwner().mViewPager.setCurrentItem(TAB_IDX_FILES);
            getOwner().updateFilesForUploadList(folderItems);
        }
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

        mViewPager = view.findViewById(R.id.sliding_views_tab_content);
//        mViewPager.setCurrentItem(TAB_IDX_FILES);

        AlbumSelectionListAdapterPreferences viewPrefs = new AlbumSelectionListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.setFlattenAlbumHierarchy(true);

        selectedGalleryTextView = view.findViewById(R.id.selected_gallery);
        selectedGalleryTextView.setOnClickListener(v -> onSelectedGalleryTextViewClick());
        // can't just use a std click listener as it first focuses the field :-(
        CustomClickTouchListener.callClickOnTouch(selectedGalleryTextView);

        fileSelectButton = view.findViewById(R.id.select_files_for_upload_button);
        fileSelectButton.setOnClickListener(v -> onFileSelectionWantedClick());

        uploadableFilesView = view.findViewById(R.id.files_uploadable_label);

        // check for login status as need to be logged in to get this information (supplied by server)
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        if (PiwigoSessionDetails.getInstance(activeProfile) == null) {
            fileSelectButton.setEnabled(false);
            String serverUri = activeProfile.getPiwigoServerAddress(getPrefs(), getContext());
            getUiHelper().invokeActiveServiceCall(getString(R.string.logging_in_to_piwigo_pattern, serverUri), new LoginResponseHandler(), new OnLoginAction());
        } else {
            String list = CollectionUtils.toCsvList(PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes());
            String fileTypesStr = String.format("(%1$s)", list == null ? " * " : list);
            uploadableFilesView.setText(fileTypesStr);
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
        privacyLevelSpinner.setSelection(((BiArrayAdapter) privacyLevelSpinner.getAdapter()).getPosition(defaultPrivacyLevelGroup));

        deleteUploadJobButton = view.findViewById(R.id.delete_upload_job_button);
        deleteUploadJobButton.setOnClickListener(v -> {
            uploadFilesNowButton.setText(R.string.upload_files_button_title);
            onDeleteUploadJobButtonClick();
        });

        uploadJobStatusButton = view.findViewById(R.id.view_detailed_upload_status_button);
        uploadJobStatusButton.setOnClickListener(v -> onUploadJobStatusButtonClick());


        updateActiveJobActionButtonsStatus();

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
        deleteFilesAfterUploadCheckbox.setChecked(UploadPreferences.isDeleteFilesAfterUploadDefault(getContext(), getPrefs()));

        compressVideosCheckbox = view.findViewById(R.id.compress_videos_button);
        if (!ExoPlayerCompression.isSupported()) {
            compressVideosCheckbox.setVisibility(GONE);
            compressVideosSettings.setVisibility(GONE);
        } else {
            boolean compressVids = UploadPreferences.isCompressVideosByDefault(getContext(), getPrefs());
            compressVideosCheckbox.setChecked(!compressVids);// ensure the checked change listener is called!
            compressVideosCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                compressVideosSettings.setVisibility(isChecked && buttonView.isEnabled() ? VISIBLE : GONE);
//                    compressVideosSettings.setEnabled(buttonView.isEnabled());
            });
            compressVideosCheckbox.setOnClickListener(v -> {
                CompoundButton buttonView = (CompoundButton) v;
                compressVideosSettings.setVisibility(buttonView.isChecked() && buttonView.isEnabled() ? VISIBLE : GONE);
                DisplayUtils.toggleHierachyEnabled(compressVideosSettings, buttonView.isEnabled());
            });
            compressVideosCheckbox.setChecked(compressVids);
        }
        allowUploadOfRawVideosIfIncompressibleCheckbox = view.findViewById(R.id.allow_upload_of_incompressible_videos_button);

        compressImagesSettings = view.findViewById(R.id.image_compression_options);
        compressImagesOutputFormatSpinner = compressImagesSettings.findViewById(R.id.compress_images_output_format);
        setSpinnerSelectedItem(compressImagesOutputFormatSpinner, UploadPreferences.getImageCompressionOutputFormat(getContext(), getPrefs()));

        compressImagesQualityNumberPicker = compressImagesSettings.findViewById(R.id.compress_images_quality);
        compressImagesQualityNumberPicker.setMinValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_quality_min));
        compressImagesQualityNumberPicker.setMaxValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_quality_max));
        compressImagesQualityNumberPicker.setValue(UploadPreferences.getImageCompressionQuality(getContext(), getPrefs()));

        compressImagesMaxHeightNumberPicker = compressImagesSettings.findViewById(R.id.compress_images_max_height);
        compressImagesMaxHeightNumberPicker.setMinValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_max_height_min));
        compressImagesMaxHeightNumberPicker.setMaxValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_max_height_max));
        compressImagesMaxHeightNumberPicker.setValue(UploadPreferences.getImageCompressionMaxHeight(getContext(), getPrefs()));

        compressImagesMaxWidthNumberPicker = compressImagesSettings.findViewById(R.id.compress_images_max_width);
        compressImagesMaxWidthNumberPicker.setMinValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_max_width_min));
        compressImagesMaxWidthNumberPicker.setMaxValue(getResources().getInteger(R.integer.preference_data_upload_compress_images_max_width_max));
        compressImagesMaxWidthNumberPicker.setValue(UploadPreferences.getImageCompressionMaxWidth(getContext(), getPrefs()));

        compressImagesCheckbox = view.findViewById(R.id.compress_images_button);
        boolean compressPics = UploadPreferences.isCompressImagesByDefault(getContext(), getPrefs());
        compressImagesCheckbox.setChecked(!compressPics);// ensure the checked change listener is called!
        compressImagesCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            compressImagesSettings.setVisibility(isChecked && buttonView.isEnabled() ? VISIBLE : GONE);
//                compressImagesSettings.setEnabled(buttonView.isEnabled());
        });
        compressImagesCheckbox.setOnClickListener(v -> {
            CompoundButton buttonView = (CompoundButton) v;
            compressImagesSettings.setVisibility(buttonView.isChecked() && buttonView.isEnabled() ? VISIBLE : GONE);
            DisplayUtils.toggleHierachyEnabled(compressImagesSettings, buttonView.isEnabled());
        });
        compressImagesCheckbox.setChecked(compressPics);

        allowUploadOfRawVideosIfIncompressibleCheckbox.setChecked(UploadPreferences.isAllowUploadOfRawVideosIfIncompressible(getContext(), getPrefs()));

        uploadFilesNowButton = view.findViewById(R.id.upload_files_button);
        uploadFilesNowButton.setOnClickListener(v -> {
            uploadFilesNowButton.setText(R.string.upload_files_button_title);
            uploadFiles();
        });

        if (filesToUploadAdapter == null) {
            filesToUploadAdapter = new FilesToUploadRecyclerViewAdapter(new ArrayList<>(), this);
            filesToUploadAdapter.setViewType(FilesToUploadRecyclerViewAdapter.VIEW_TYPE_GRID);

            filesToUploadAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    compressVideosCheckbox.setEnabled(isVideoFilesWaitingForUpload());
                    compressVideosCheckbox.callOnClick();
                    compressImagesCheckbox.setEnabled(isImageFilesWaitingForUpload());
                    compressImagesCheckbox.callOnClick();
                }
            });
        }

        if (savedInstanceState != null) {
            // update view with saved data
            if (savedInstanceState.containsKey(SAVED_STATE_UPLOAD_JOB_ID)) {
                uploadJobId = savedInstanceState.getLong(SAVED_STATE_UPLOAD_JOB_ID);
            }
            // override the upload to album value (used to set clickable text field)
            uploadToAlbum = savedInstanceState.getParcelable(SAVED_STATE_UPLOAD_TO_ALBUM);
            filesToUploadAdapter.onRestoreInstanceState(savedInstanceState, FILES_TO_UPLOAD_ADAPTER_STATE);
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

        filesForUploadView.setAdapter(filesToUploadAdapter);

        updateUiUploadStatusFromJobIfRun(container.getContext());

        if (BuildConfig.DEBUG && ENABLE_COMPRESSION_BUTTON && ExoPlayerCompression.isSupported()) {

            injectCompressionControlsIntoView();

        }

        return view;
    }

    private void injectCompressionControlsIntoView() {
        MaterialButton compressVideosButton = new MaterialButton(requireContext());
        compressVideosButton.setText("Compress");
        compressVideosButton.setOnClickListener(v -> {
            v.setEnabled(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                compressVideos(v);
            }
        });
        filesToUploadAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                compressVideosButton.setEnabled(isVideoFilesWaitingForUpload());
            }
        });
        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomToTop = R.id.upload_files_button;
        ((ConstraintLayout) uploadFilesNowButton.getParent()).addView(compressVideosButton, layoutParams);

        Objects.requireNonNull(filesForUploadView.getAdapter()).registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                compressVideosButton.setEnabled(filesForUploadView.getAdapter().getItemCount() > 0);
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void compressVideos(View linkedView) {
        FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();
        List<Uri> filesForUpload = fileListAdapter.getFiles();
        if (filesForUpload.isEmpty()) {
            return;
        }
        for(Uri fileForCompression : filesForUpload) {
            ExoPlayerCompression.CompressionParameters compressionSettings = new ExoPlayerCompression.CompressionParameters();
            long rawVal = compressVideosQualitySpinner.getSelectedItemId();
            int audioBitrate = (int) compressVideosAudioBitrateSpinner.getSelectedItemId();
            double bpps = ((double) rawVal) / 1000;
            compressionSettings.setAddVideoTrack(rawVal != 0);
            compressionSettings.setAddAudioTrack(audioBitrate != 0);
            compressionSettings.getVideoCompressionParameters().setWantedBitRatePerPixelPerSecond(bpps);
            compressionSettings.getAudioCompressionParameters().setBitRate(audioBitrate);
//                compressionSettings.disableFastStart();


            DocumentFile moviesFolder = DocumentFile.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES));
            DocumentFile outputVideo;
            int i = 0;
            DocumentFile inputDocFile = DocumentFile.fromSingleUri(requireContext(), fileForCompression);
            String compressedFileExt = compressionSettings.getOutputFileExt();
            String outputFilenameSuffix = IOUtils.getFileNameWithoutExt(inputDocFile.getName()) + '.' + compressedFileExt;
            do {
                i++;
                outputVideo = moviesFolder.findFile("compressed_" + i + outputFilenameSuffix);
                if(outputVideo == null) {
                    outputVideo = moviesFolder.createFile(compressionSettings.getOutputFileMimeType(null), "compressed_" + i + outputFilenameSuffix);
                    break;
                }
            } while (true);

            new ExoPlayerCompression().invokeFileCompression(getContext(), fileForCompression, outputVideo.getUri(), new DebugCompressionListener(getUiHelper(), linkedView), compressionSettings);
        }
    }

    private void setSpinnerSelectedItem(Spinner spinner, Object item) {
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter instanceof ArrayAdapter) {
            int itemPosition = ((ArrayAdapter) spinner.getAdapter()).getPosition(item);
            spinner.setSelection(itemPosition);
        } else {
            Logging.log(Log.ERROR, TAG, "Cannot set selected spinner item - adapter is not instance of ArrayAdapter");
        }
    }

    private boolean isImageFilesWaitingForUpload() {
        return hasFileMatchingMime("image/*");
    }

    private boolean isVideoFilesWaitingForUpload() {
        return hasFileMatchingMime("video/*");
    }

    private boolean hasFileMatchingMime(String mimeTypeFilter) {
        for (int i = 0; i < filesToUploadAdapter.getItemCount(); i++) {
            String mimeType = filesToUploadAdapter.getItemMimeType(i);
            if (MimeTypeFilter.matches(mimeType,mimeTypeFilter)) {
                return true;
            }
        }
        return false;
    }

    private MaterialButton getFileSelectButton() {
        return fileSelectButton;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private TextView getUploadableFilesView() {
        return uploadableFilesView;
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
            //        event.withSelectedUriPermissionsForConsumerId(getUriPermissionsKey()); Not currently needed as permissions are transient.

            Set<String> visibleMimeTypes = new HashSet<>();
            visibleMimeTypes.add("video/*");
            MimeTypeMap map = MimeTypeMap.getSingleton();
            for(String fileExt : allowedFileTypes) {
                String mimeType = map.getMimeTypeFromExtension(fileExt.toLowerCase());
                if(mimeType == null) {
                    if(fileExt.equals("webmv")) {
                        mimeType = "video/webm";
                    }
                }
                if(mimeType != null) {
                    visibleMimeTypes.add(mimeType);
                } else {
                    Logging.log(Log.WARN, TAG, "Unrecognised file extension - no mime type found : " + fileExt);
                }
            }
            event.withVisibleMimeTypes(visibleMimeTypes);
            event.withSelectedUriPermissionsForConsumerId(URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
            event.setSelectedUriPermissionsForConsumerPurpose(getString(R.string.uri_permission_justification_to_upload));
            event.requestUriReadWritePermissions();

            getUiHelper().setTrackingRequest(event.getActionId());
            EventBus.getDefault().post(event);
        }
    }

    private Long getUploadJobId() {
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

    private void onSelectedGalleryTextViewClick() {
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
                FirebaseAnalytics.getInstance(requireContext()).logEvent("IncompleteUserSession", b);
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

    private static class ReloadDataFromUploadJobTask extends OwnedSafeAsyncTask<AbstractUploadFragment, Void, Object, List<FilesToUploadRecyclerViewAdapter.UploadDataItem>> {

        private UploadJob uploadJob;

        ReloadDataFromUploadJobTask(AbstractUploadFragment parent, UploadJob job) {
            super(parent);
            withContext(parent.requireContext());
            this.uploadJob = job;
        }

        FragmentUIHelper getUiHelper() {
            return getOwner().getUiHelper();
        }

        @Override
        protected void onPreExecuteSafely() {
            super.onPreExecuteSafely();
            getOwner().overallUploadProgressBar.showProgressIndicator(R.string.loading_please_wait,0);

            //register the potentially completely new handler to handle the existing job messages
            getUiHelper().getPiwigoResponseListener().switchHandlerId(uploadJob.getResponseHandlerId());
            getUiHelper().updateHandlerForAllMessages();

            PiwigoSessionDetails piwigoSessionDetails = PiwigoSessionDetails.getInstance(uploadJob.getConnectionPrefs());
            if (piwigoSessionDetails != null) {
                String fileTypesStr = String.format("(%1$s)", CollectionUtils.toCsvList(piwigoSessionDetails.getAllowedFileTypes()));
                getOwner().uploadableFilesView.setText(fileTypesStr);
            }
            getOwner().uploadJobId = uploadJob.getJobId();
            getOwner().uploadToAlbum = new CategoryItemStub("???", uploadJob.getUploadToCategory());
            AlbumGetSubAlbumNamesResponseHandler hndler = new AlbumGetSubAlbumNamesResponseHandler(uploadJob.getUploadToCategory(), false);
            getUiHelper().addActionOnResponse(getUiHelper().addActiveServiceCall(hndler), new OnGetSubAlbumNamesAction());
            getOwner().selectedGalleryTextView.setText(getOwner().uploadToAlbum.getName());

            byte privacyLevelWanted = uploadJob.getPrivacyLevelWanted();
            if (privacyLevelWanted >= 0) {
                getOwner().privacyLevelSpinner.setSelection(((BiArrayAdapter) getOwner().privacyLevelSpinner.getAdapter()).getPosition(privacyLevelWanted));
            }
        }

        @Override
        protected List<FilesToUploadRecyclerViewAdapter.UploadDataItem> doInBackgroundSafely(Void... nothing) {
            int itemCount = uploadJob.getFilesNotYetUploaded().size();
            List<FilesToUploadRecyclerViewAdapter.UploadDataItem> itemsToBeUploaded = new ArrayList<>(itemCount);
            int currentItem = 0;
            for(Uri toUpload : uploadJob.getFilesNotYetUploaded()) {
                currentItem++;
                // this recalculates the hash-codes - maybe unnecessary, but the file could have been altered since added to the job
                itemsToBeUploaded.add(new FilesToUploadRecyclerViewAdapter.UploadDataItem(toUpload, null, null));
                int currentProgress = (int)Math.round((((double)currentItem) / itemCount) * 100);
                getOwner().overallUploadProgressBar.post(() -> getOwner().overallUploadProgressBar.showProgressIndicator(R.string.loading_please_wait,currentProgress));
            }
            return itemsToBeUploaded;
        }

        @Override
        protected void onPostExecuteSafely(List<FilesToUploadRecyclerViewAdapter.UploadDataItem> itemsToBeUploaded) {
            FilesToUploadRecyclerViewAdapter adapter = getOwner().filesToUploadAdapter;
            adapter.clear();
            adapter.addAll(itemsToBeUploaded);

            for (Uri f : adapter.getFiles()) {
                int progress = uploadJob.getUploadProgress(f);
                int compressionProgress = uploadJob.getCompressionProgress(f);
                if (compressionProgress == 100) {
                    Uri compressedFile = uploadJob.getCompressedFile(f);
                    if (compressedFile != null) {
                        adapter.updateCompressionProgress(f, compressedFile, 100);
                    }
                }
                adapter.updateUploadProgress(f, progress);
            }

            boolean jobIsComplete = uploadJob.isFinished();
            getOwner().allowUserUploadConfiguration(uploadJob);
            if (!jobIsComplete) {
                // now register for any new messages (and pick up all messages in sequence)
                getUiHelper().handleAnyQueuedPiwigoMessages();
            } else {
                // reset status ready for next job
                BasePiwigoUploadService.removeJob(uploadJob);
                getOwner().uploadJobId = null;
            }

            getOwner().overallUploadProgressBar.hideProgressIndicator();
        }
    }

    private void updateUiUploadStatusFromJobIfRun(Context context) {
        UploadJob uploadJob = getActiveJob(context);
        if (uploadJob != null) {
            new ReloadDataFromUploadJobTask(this, uploadJob).execute();
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
            getParentFragmentManager().popBackStack();
            Log.e(TAG, "Unable to view upload fragment - removing from activity");
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
    }

    protected FilesToUploadRecyclerViewAdapter getFilesForUploadViewAdapter() {
        return (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
    }

    private CategoryItemStub getUploadToAlbum() {
        return uploadToAlbum;
    }

    protected void updateFilesForUploadList(List<FilesToUploadRecyclerViewAdapter.UploadDataItem> folderItemsToBeUploaded) {
        if (folderItemsToBeUploaded.size() > 0) {
            FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
            int addedItems = 0;

            for (FilesToUploadRecyclerViewAdapter.UploadDataItem item : folderItemsToBeUploaded) {
                if (adapter.add(item)) {
                    addedItems++;
                }
            }
            if(addedItems > 0) {
                adapter.notifyDataSetChanged();
            }

            int filesAlreadyPresent = folderItemsToBeUploaded.size() - addedItems;
            if (filesAlreadyPresent > 0) {
                getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.duplicates_ignored_pattern, filesAlreadyPresent));
            }
            uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
            updateActiveJobActionButtonsStatus();
        }
    }

    private void updateLastOpenedFolderPref(Context context, List<FolderItemRecyclerViewAdapter.FolderItem> folderItemsToBeUploaded) {
        for(FolderItemRecyclerViewAdapter.FolderItem item : folderItemsToBeUploaded) {
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

    private Map<Uri,Double> getFilesExceedingMaxDesiredUploadThreshold(List<Uri> filesForUpload) {
        int maxUploadSizeWantedThresholdMB = UploadPreferences.getMaxUploadFilesizeMb(getContext(), prefs);
        HashMap<Uri, Double> retVal = new HashMap<>();
        for (Uri f : filesForUpload) {
            Double fileLengthMB = BigDecimal.valueOf(IOUtils.getFilesize(requireContext(), f)).divide(BigDecimal.valueOf(1024* 1024), BigDecimal.ROUND_HALF_EVEN).doubleValue();
            if (fileLengthMB > maxUploadSizeWantedThresholdMB) {
                retVal.put(f, fileLengthMB);
            }
        }
        return retVal;
    }

    private void uploadFiles() {
        buildAndSubmitNewUploadJob();
    }

    private void buildAndSubmitNewUploadJob() {
        buildAndSubmitNewUploadJob(false);
    }

    private void buildAndSubmitNewUploadJob(boolean filesizesChecked) {
        UploadJob activeJob = null;

        if (uploadJobId != null) {
            activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();
        List<Uri> filesForUpload = fileListAdapter.getFiles();

        if (activeJob == null) {

            if (uploadToAlbum == null || CategoryItemStub.ROOT_GALLERY.equals(uploadToAlbum)) {
                mViewPager.setCurrentItem(TAB_IDX_SETTINGS);
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_please_select_upload_album));
                deleteUploadJobButton.setVisibility(VISIBLE);
                return;
            }

            if (!runIsAllFileTypesAcceptedByServerTests(filesForUpload)) {
                return; // no, they aren't
            }

            if (!filesizesChecked) {

                if (!runAreAllFilesUnderUserChosenMaxUploadThreshold(filesForUpload)) {
                    return; // no, they aren't
                }
            }
        }

        if (activeJob == null) {
            byte privacyLevelWanted = (byte) privacyLevelSpinner.getSelectedItemId(); // save as just bytes!
            long handlerId = getUiHelper().getPiwigoResponseListener().getHandlerId();
            boolean isDeleteFilesAfterUpload = deleteFilesAfterUploadCheckbox.isChecked();
            activeJob = ForegroundPiwigoUploadService.createUploadJob(ConnectionPreferences.getActiveProfile(), filesForUpload, uploadToAlbum, privacyLevelWanted, handlerId, isDeleteFilesAfterUpload);
            activeJob.withContext(requireContext());
            if (compressVideosCheckbox.isChecked()) {
                UploadJob.VideoCompressionParams vidCompParams = new UploadJob.VideoCompressionParams();
                vidCompParams.setQuality(((double) compressVideosQualitySpinner.getSelectedItemId()) / 1000);
                vidCompParams.setAudioBitrate((int) compressVideosAudioBitrateSpinner.getSelectedItemId());
                activeJob.setVideoCompressionParams(vidCompParams);
                activeJob.setAllowUploadOfRawVideosIfIncompressible(allowUploadOfRawVideosIfIncompressibleCheckbox.isChecked());
            }
            if (compressImagesCheckbox.isChecked()) {
                UploadJob.ImageCompressionParams imageCompParams = new UploadJob.ImageCompressionParams();
                imageCompParams.setOutputFormat(compressImagesOutputFormatSpinner.getSelectedItem().toString());
                imageCompParams.setQuality(compressImagesQualityNumberPicker.getValue());
                imageCompParams.setMaxHeight(compressImagesMaxHeightNumberPicker.getValue());
                imageCompParams.setMaxWidth(compressImagesMaxWidthNumberPicker.getValue());
                activeJob.setImageCompressionParams(imageCompParams);
            }
        }
        mViewPager.setCurrentItem(TAB_IDX_FILES);
        submitUploadJob(activeJob);
    }

    private boolean runAreAllFilesUnderUserChosenMaxUploadThreshold(List<Uri> filesForUpload) {

        final Map<Uri,Double> filesForReview = getFilesExceedingMaxDesiredUploadThreshold(filesForUpload);

        StringBuilder filenameListStrB = new StringBuilder();
        Set<Uri> keysToRemove = new HashSet<>();
        for (Map.Entry<Uri,Double> f : filesForReview.entrySet()) {
            if (compressVideosCheckbox.isChecked() && MimeTypeFilter.matches(IOUtils.getMimeType(requireContext(), f.getKey()), "video/*")) {
                keysToRemove.add(f.getKey());
                continue;
            }
            if (compressImagesCheckbox.isChecked() && MimeTypeFilter.matches(IOUtils.getMimeType(requireContext(), f.getKey()), "image/*")) {
                keysToRemove.add(f.getKey());
                continue;
            }
            double fileLengthMB = f.getValue();
            if (filesForReview.size() > 0) {
                filenameListStrB.append(", ");
            }
            filenameListStrB.append(f.getKey().getPath());
            filenameListStrB.append(String.format(Locale.getDefault(), "(%1$.1fMB)", fileLengthMB));
        }
        for(Uri uri : keysToRemove) {
            filesForReview.remove(uri);
        }
        if (filesForReview.size() > 0) {
            getUiHelper().showOrQueueCancellableDialogQuestion(R.string.alert_warning, getString(R.string.alert_files_larger_than_upload_threshold_pattern, filesForReview.size(), filenameListStrB.toString()), R.string.button_no, R.string.button_cancel, R.string.button_yes, new FileSizeExceededAction(getUiHelper(), filesForReview.keySet()));
            return false;
        }
        return true;
    }

    private boolean runIsAllFileTypesAcceptedByServerTests(List<Uri> filesForUpload) {
        // check for server unacceptable files.
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        Set<String> serverAcceptedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
        Set<String> fileTypesForUpload = IOUtils.getUniqueFileExts(getContext(), filesForUpload);
        Set<String> unacceptableFileExts = SetUtils.difference(fileTypesForUpload, serverAcceptedFileTypes);
        if (compressVideosCheckbox.isChecked()) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && MimeTypeFilter.matches(mimeType,"video/*")) {
                    iter.remove();
                }
            }
        }
        if (compressImagesCheckbox.isChecked()) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && MimeTypeFilter.matches(mimeType,"image/*")) {
                    iter.remove();
                }
            }
        }

        if (!unacceptableFileExts.isEmpty()) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_error, getString(R.string.alert_upload_job_contains_files_server_will_not_accept_pattern, unacceptableFileExts.size()), R.string.button_cancel, R.string.button_yes, new UnacceptableFilesAction(getUiHelper(), unacceptableFileExts));
            return false;
        }
        return true;
    }

    private void submitUploadJob(UploadJob activeJob) {
        uploadJobId = activeJob.getJobId();
        // the job will be submitted within the onEvent(PermissionsWantedResponse event) method.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        } else {
            getUiHelper().runWithExtraPermissions(this, Build.VERSION.SDK_INT, Build.VERSION.SDK_INT, new String[]{Manifest.permission.WAKE_LOCK}, getString(R.string.alert_foreground_service_and_wake_lock_permission_needed_to_start_upload));
        }
        AdsManager.getInstance().showFileToUploadAdvertIfAppropriate();
    }

    @Override
    public void onRemove(final FilesToUploadRecyclerViewAdapter adapter, final Uri itemToRemove, boolean longClick) {
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
                    releaseUriPermissionsForUploadItem(itemToRemove);
                }
            } else {
                // job running.
                boolean immediatelyCancelled = activeJob.cancelFileUpload(itemToRemove);
                DocumentFile fileRemoved = DocumentFile.fromSingleUri(requireContext(), itemToRemove);
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_file_upload_cancelled_pattern, fileRemoved.getName()));
                if (immediatelyCancelled) {
                    adapter.remove(itemToRemove);
                    releaseUriPermissionsForUploadItem(itemToRemove);
                }
            }
            if (!activeJob.isRunningNow() && activeJob.getFilesAwaitingUpload().size() == 0) {
                // no files left to upload. Lets switch the button from upload to finish
                uploadFilesNowButton.setText(R.string.upload_files_finish_job_button_title);
            }
        } else {
            if (longClick) {
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_delete_all_files_selected_for_upload), R.string.button_no, R.string.button_yes, new DeleteAllFilesSelectedAction(getUiHelper()));
            } else {
                adapter.remove(itemToRemove);
                releaseUriPermissionsForUploadItem(itemToRemove);
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
        compressVideosCheckbox.setEnabled((noJobIsYetConfigured || jobIsFinished) && isVideoFilesWaitingForUpload());
        compressVideosCheckbox.callOnClick();
        compressImagesCheckbox.setEnabled((noJobIsYetConfigured || jobIsFinished) && isImageFilesWaitingForUpload());
        compressImagesCheckbox.callOnClick();
        updateActiveJobActionButtonsStatus();
        fileSelectButton.setEnabled(noJobIsYetConfigured || jobIsFinished);
        selectedGalleryTextView.setEnabled(noJobIsYetConfigured || (jobIsFinished && !filesStillToBeUploaded));
        privacyLevelSpinner.setEnabled(noJobIsYetConfigured || jobIsFinished);
    }

    private void updateActiveJobActionButtonsStatus() {
        if (uploadJobId != null) {
            UploadJob job = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
            if (job != null) {
                uploadJobStatusButton.setVisibility(VISIBLE);
                boolean canForceDeleteJob = job.hasBeenRunBefore() && !job.isRunningNow() && !job.hasJobCompletedAllActionsSuccessfully();
                deleteUploadJobButton.setVisibility(canForceDeleteJob ? VISIBLE : GONE);
            } else {
                uploadJobStatusButton.setVisibility(GONE);
                deleteUploadJobButton.setVisibility(GONE);
            }
        } else {
            uploadJobStatusButton.setVisibility(GONE);
            deleteUploadJobButton.setVisibility(GONE);
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
            ForegroundPiwigoUploadService.startActionRunOrReRunUploadJob(requireContext(), activeJob);
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
        if (!isAdded() || getActivity() == null) {
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

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new ForegroundPiwigoFileUploadResponseListener(context);
    }

    private void processError(Context context, PiwigoResponseBufferingHandler.Response error) {
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
                setUploadToAlbum(selectedAlbum.toStub());
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isVisible()) {
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    private void onAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {

    }

    private static class LoadAlbumTreeAction extends UIHelper.Action<FragmentUIHelper<AbstractUploadFragment>, AbstractUploadFragment, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse> {
        private static final long serialVersionUID = -6619401114043943514L;

        @Override
        public boolean onSuccess(FragmentUIHelper<AbstractUploadFragment> uiHelper, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse response) {
            getActionParent(uiHelper).setUploadToAlbum(response.getDeepestAlbumOnDesiredPath().toStub());
            return true; // to close the progress indicator
        }
    }

    private void setUploadToAlbum(CategoryItemStub uploadToAlbum) {
        this.uploadToAlbum =  uploadToAlbum;
        if (!uploadToAlbum.isRoot() && !uploadToAlbum.isParentRoot()) {
            getSelectedGalleryTextView().setText(getString(R.string.subAlbum_text, uploadToAlbum.getName()));
        } else {
            getSelectedGalleryTextView().setText(uploadToAlbum.getName());
        }

    }

    private TextView getSelectedGalleryTextView() {
        return selectedGalleryTextView;
    }

    private Button getUploadFilesNowButton() {
        return uploadFilesNowButton;
    }

    private static class OnLoginAction extends UIHelper.Action<FragmentUIHelper<AbstractUploadFragment>, AbstractUploadFragment, LoginResponseHandler.PiwigoOnLoginResponse> {
        private static final long serialVersionUID = 7834616791801869130L;

        @Override
        public boolean onSuccess(FragmentUIHelper<AbstractUploadFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
            AbstractUploadFragment fragment = uiHelper.getParent();
            fragment.getFileSelectButton().setEnabled(true);
            ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
            String fileTypesStr = String.format("(%1$s)", CollectionUtils.toCsvList(PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes()));
            fragment.getUploadableFilesView().setText(fileTypesStr);
            FileSelectionCompleteEvent evt = EventBus.getDefault().getStickyEvent(FileSelectionCompleteEvent.class);
            if (evt != null) {
                fragment.onEvent(evt);
            }
            return super.onSuccess(uiHelper, response);
        }
    }

    private static class DeleteAllFilesSelectedAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {
        private static final long serialVersionUID = -160459487188984144L;

        DeleteAllFilesSelectedAction(FragmentUIHelper<AbstractUploadFragment> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractUploadFragment fragment = getUiHelper().getParent();
                List<Uri> uris = fragment.getFilesForUploadViewAdapter().getFiles();
                for(Uri uri : uris) {
                    fragment.getFilesForUploadViewAdapter().remove(uri);
                    fragment.releaseUriPermissionsForUploadItem(uri);
                }
                fragment.uploadFilesNowButton.setEnabled(fragment.getFilesForUploadViewAdapter().getItemCount() > 0);
                fragment.updateActiveJobActionButtonsStatus();
            }
        }
    }

    private static class PartialUploadFileAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {

        private static final long serialVersionUID = -5416665561415768505L;
        private final Uri itemToRemove;

        PartialUploadFileAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, Uri itemToRemove) {
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
                    int countFilesNeedingServerAction = activeJob.getFilesAwaitingUpload().size();
                    if (countFilesNeedingServerAction == 0) {
                        // no files left to upload. Lets switch the button from upload to finish
                        fragment.getUploadFilesNowButton().setText(R.string.upload_files_finish_job_button_title);
                    }
                } else {
                    Logging.log(Log.ERROR, TAG, "Attempt to alter upload job but it was null");
                }
            }
        }
    }

    private static class DebugCompressionListener implements ExoPlayerCompression.CompressionListener {
        private final UIHelper uiHelper;
        private final SimpleDateFormat strFormat;
        private long startCompressionAt;
        private View linkedView;

        {
            strFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            strFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        public DebugCompressionListener(UIHelper uiHelper, View linkedView) {
            this.uiHelper = uiHelper;
            this.linkedView = linkedView;
        }

        @Override
        public void onCompressionStarted(Uri inputFile, Uri outputFile) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression started");
                startCompressionAt = System.currentTimeMillis();
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                linkedView.setEnabled(false);
            });

        }

        @Override
        public void onCompressionError(Uri inputFile, Uri outputFile, Exception e) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression failed");
                linkedView.setEnabled(true);
                Logging.log(Log.ERROR, TAG, "Video Compression failed");
                Logging.recordException(e);
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                IOUtils.delete(uiHelper.getAppContext(), outputFile);
            });

        }

        @Override
        public void onCompressionComplete(Uri inputFile, Uri outputFile) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression finished");
                linkedView.setEnabled(true);
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                IOUtils.addFileToMediaStore(uiHelper.getAppContext(), outputFile);
            });
        }

        @Override
        public void onCompressionProgress(Uri inputFile, Uri outputFile, final double compressionProgress, final long mediaDurationMs) {
            if (!DisplayUtils.isRunningOnUIThread()) {
                DisplayUtils.runOnUiThread(() -> onCompressionProgress(inputFile, outputFile, compressionProgress, mediaDurationMs));
                return;
            }
            AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
            FilesToUploadRecyclerViewAdapter adapter = fragment.getFilesForUploadViewAdapter();
            if(adapter != null) {
                adapter.updateCompressionProgress(inputFile, outputFile, (int) Math.rint(compressionProgress));
            }
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startCompressionAt;
            long estimateTotalCompressionTime = Math.round(100 * (elapsedTime / compressionProgress));
            long endCompressionAtEstimate = startCompressionAt + estimateTotalCompressionTime;
            Date endCompressionAt = new Date(endCompressionAtEstimate);
            String remainingTimeStr = strFormat.format(new Date(endCompressionAtEstimate - currentTime));
            String elapsedCompressionTimeStr = strFormat.format(new Date(elapsedTime));

            if (mediaDurationMs > 0) {
                double timeProcessed = mediaDurationMs * (compressionProgress / 100);
                double compressionRate = timeProcessed / elapsedTime;
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nrate: %5$.02fx\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr, compressionRate), Toast.LENGTH_SHORT, 1);
            } else {
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr), Toast.LENGTH_SHORT, 1);
            }
        }
    }

    private static class UnacceptableFilesAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {

        private static final long serialVersionUID = -6658551916072891874L;
        private final Set<String> unacceptableFileExts;

        UnacceptableFilesAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, Set<String> unacceptableFileExts) {
            super(uiHelper);
            this.unacceptableFileExts = unacceptableFileExts;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();

            if (Boolean.TRUE == positiveAnswer) {

                List<Uri> unaccceptableFiles = new ArrayList<>(fragment.getFilesForUploadViewAdapter().getFiles());
                Iterator<Uri> iter = unaccceptableFiles.iterator();
                while (iter.hasNext()) {
                    if (!unacceptableFileExts.contains(IOUtils.getFileExt(getContext(), iter.next()).toLowerCase())) {
                        iter.remove();
                    }
                }
                for (Uri file : unaccceptableFiles) {
                    fragment.onRemove(fragment.getFilesForUploadViewAdapter(), file, false);
                }

                fragment.buildAndSubmitNewUploadJob(false);
            }
        }
    }

    private static class FileSizeExceededAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {
        private static final long serialVersionUID = 7104690861610172716L;
        private Set<Uri> filesToDelete;

        public FileSizeExceededAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, Set<Uri> filesForReview) {
            super(uiHelper);
            this.filesToDelete = filesForReview;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();

            if (Boolean.TRUE == positiveAnswer) {
                for (Uri file : filesToDelete) {
                    fragment.onRemove(fragment.getFilesForUploadViewAdapter(), file, false);
                }
            }
            if (positiveAnswer != null) {
                fragment.buildAndSubmitNewUploadJob(true);
            }
        }
    }

    private static class OnDeleteJobQuestionAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {
        private static final long serialVersionUID = 9093460171761189408L;

        OnDeleteJobQuestionAction(FragmentUIHelper<AbstractUploadFragment> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();
            Long currentJobId = fragment.getUploadJobId();
            if (currentJobId == null) {
                Logging.log(Log.WARN, TAG, "User attempted to delete job that was no longer exists");
                return;
            }
            UploadJob job = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), currentJobId);
            if (positiveAnswer != null && positiveAnswer && job != null) {
                if (job.getTemporaryUploadAlbum() > 0) {
                    AlbumDeleteResponseHandler albumDelHandler = new AlbumDeleteResponseHandler(job.getTemporaryUploadAlbum());
                    getUiHelper().addNonBlockingActiveServiceCall(getContext().getString(R.string.alert_deleting_temporary_upload_album), albumDelHandler.invokeAsync(getContext(), job.getConnectionPrefs()), albumDelHandler.getTag());
                }
                ForegroundPiwigoUploadService.removeJob(job);
                ForegroundPiwigoUploadService.deleteStateFromDisk(getContext(), job, true);
                fragment.allowUserUploadConfiguration(null);
            }
        }
    }

    private static class OnGetSubAlbumNamesAction extends UIHelper.Action<FragmentUIHelper<UploadFragment>, UploadFragment, AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse> {
        private static final long serialVersionUID = -2486104874719308423L;

        @Override
        public boolean onSuccess(FragmentUIHelper<UploadFragment> uiHelper, AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
            if (response.getAlbumNames().size() > 0) {
                AbstractUploadFragment fragment = uiHelper.getParent();
                CategoryItemStub uploadToAlbum = fragment.getUploadToAlbum();
                if (uploadToAlbum.getId() == response.getAlbumNames().get(0).getId()) {
                    uploadToAlbum = response.getAlbumNames().get(0);
                    fragment.setUploadToAlbum(uploadToAlbum);

                } else if (uploadToAlbum.isParentRoot()) {
                    fragment.setUploadToAlbum(CategoryItemStub.ROOT_GALLERY);
                }
            }
            return super.onSuccess(uiHelper, response);
        }
    }

    private static class ForegroundPiwigoFileUploadResponseListener extends PiwigoFileUploadResponseListener<AbstractUploadFragment> {

        ForegroundPiwigoFileUploadResponseListener(Context context) {
            super(context);
        }

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent() != null && getParent().isVisible()) {
                getParent().updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        protected void onRequestedFileUploadCancelComplete(Context context, Uri cancelledFile) {
            if (getParent() != null && getParent().isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                adapter.remove(cancelledFile);
                getParent().releaseUriPermissionsForUploadItem(cancelledFile);
                UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, getParent().uploadJobId);
                updateOverallUploadProgress(uploadJob.getUploadProgress());
            }
            if (getParent() != null && getParent().uploadJobId != null) {
                if (getParent() != null && getParent().isAdded()) {
                    UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, getParent().uploadJobId);
                    if (uploadJob.isFilePartiallyUploaded(cancelledFile)) {
                        getParent().getUiHelper().showDetailedMsg(R.string.alert_warning, getParent().getString(R.string.alert_partial_upload_deleted));
                    }
                }
            } else {
                FirebaseAnalytics.getInstance(getParent().requireContext()).logEvent("noJobDelFile", null);
            }
        }

        @Override
        protected void onUploadComplete(final Context context, final UploadJob job) {
            if (getParent() != null && getParent().isAdded()) {
                job.withContext(getParent().getContext());
                if (job.hasJobCompletedAllActionsSuccessfully() && job.isFinished()) {
                    getParent().uploadJobId = null;
                    ForegroundPiwigoUploadService.removeJob(job);
                    HashSet<Uri> filesPendingApproval = job.getFilesPendingApproval();
                    if (filesPendingApproval.size() > 0) {
                        String msg = getParent().getString(R.string.alert_message_info_files_already_pending_approval_pattern, filesPendingApproval.size());
                        getParent().getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, msg);
                    }
                } else if (job.getAndClearWasLastRunCancelled()) {
                    getParent().getUiHelper().showOrQueueDialogMessage(R.string.alert_message_upload_cancelled, context.getString(R.string.alert_message_upload_cancelled_message), R.string.button_ok);
                } else {
                    int errMsgResourceId = R.string.alert_message_error_uploading_start;
                    if (job.getFilesNotYetUploaded().size() == 0) {
                        errMsgResourceId = R.string.alert_message_error_uploading_end;
                    }
                    getParent().getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_upload, context.getString(errMsgResourceId), R.string.button_ok);
                }
                getParent().allowUserUploadConfiguration(job);
                updateOverallUploadProgress(job.getUploadProgress());
            }
            // ensure the album view is refreshed if visible (to remove temp upload album).
            for (Long albumParent : job.getUploadToCategoryParentage()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(job.getUploadToCategory()));
            // notify the user the upload has finished.
            notifyUserUploadJobComplete(context, job);
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
            getParent().notifyUser(context, titleId, message);
        }

        @Override
        protected void onLocalFileError(Context context, final BasePiwigoUploadService.PiwigoUploadFileLocalErrorResponse response) {
            String errorMessage;
            String cause = response.getError().getMessage();
            Uri fileForUploadUri = response.getFileForUpload();
            DocumentFile fileForUpload = DocumentFile.fromSingleUri(context, fileForUploadUri);
            String uploadFilename = fileForUpload == null ? null : fileForUpload.getName();
            if (response.getError() instanceof FileNotFoundException) {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_no_longer_available_message_pattern), uploadFilename,fileForUploadUri);
            } else if (response.getError() instanceof ExoPlaybackException) {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_compression_error_message_pattern), uploadFilename, fileForUploadUri);
            } else {
                errorMessage = String.format(context.getString(R.string.alert_error_upload_file_read_error_message_pattern), uploadFilename, fileForUploadUri);
            }
            //TODO show the user the full cause perhaps
            getParent().notifyUser(context, R.string.alert_error, errorMessage);
        }

        @Override
        protected void onPrepareUploadFailed(Context context, final BasePiwigoUploadService.PiwigoPrepareUploadFailedResponse response) {

            PiwigoResponseBufferingHandler.Response error = response.getError();
            getParent().processError(context, error);
        }

        @Override
        protected void onCleanupPostUploadFailed(Context context, BasePiwigoUploadService.PiwigoCleanupPostUploadFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            getParent().processError(context, error);
        }

        private void updateOverallUploadProgress(int progress) {
            getParent().overallUploadProgressBar.showProgressIndicator(R.string.uploading_progress_bar_message, progress);
            if (progress == 100) {
                getParent().overallUploadProgressBar.hideProgressIndicator();
            }
        }

        @Override
        protected void onFileUploadProgressUpdate(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
            if (getParent() != null && getParent().isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                adapter.updateUploadProgress(response.getFileForUpload(), response.getProgress());
                UploadJob activeJob = getParent().getActiveJob(context);
                if (activeJob != null) {
                    updateOverallUploadProgress(activeJob.getUploadProgress());
                }
            }
            if (response.getProgress() == 100) {
                onFileUploadComplete(context, response);
            }
        }

        @Override
        protected void onFileCompressionProgressUpdate(Context context, BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
            if (getParent() != null && getParent().isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                adapter.updateCompressionProgress(response.getFileForUpload(), response.getCompressedFileUpload(), response.getProgress());
                updateOverallUploadProgress(getParent().getActiveJob(context).getUploadProgress());
            }
            if (response.getProgress() == 100) {
                onFileCompressionComplete(context, response);
            }
        }

        private void onFileCompressionComplete(Context context, final BasePiwigoUploadService.PiwigoVideoCompressionProgressUpdateResponse response) {
            // Do nothing for now.
        }

        private void onFileUploadComplete(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {


            //TODO This causes lots of server calls and is really unnecessary! Refresh once at the end

            UploadJob uploadJob = getParent().getActiveJob(context);
            if (uploadJob != null) {
                updateOverallUploadProgress(uploadJob.getUploadProgress());
//                ResourceItem item = uploadJob.getUploadedFileResource(response.getFileForUpload());
//                for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
//                    EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
//                }
//                EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory(), item.getId()));

            }

            if (getParent() != null && getParent().isAdded()) {
                // somehow upload job can be null... hopefully this copes with that scenario.
                FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                adapter.remove(response.getFileForUpload());
                getParent().releaseUriPermissionsForUploadItem(response.getFileForUpload());
            }
        }

        @Override
        protected void onFilesSelectedForUploadAlreadyExistOnServer(Context context, final BasePiwigoUploadService.PiwigoUploadFileFilesExistAlreadyResponse response) {
            if (getParent() != null && getParent().isAdded()) {
                UploadJob uploadJob = getParent().getActiveJob(context);

                if (uploadJob != null) {
                    FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                    for (Uri existingFile : response.getExistingFiles()) {
                        int progress = uploadJob.getUploadProgress(existingFile);
                        adapter.updateUploadProgress(existingFile, progress);
//                    adapter.remove(existingFile);
                    }
                    updateOverallUploadProgress(uploadJob.getUploadProgress());
                }
            }
            String message = String.format(context.getString(R.string.alert_items_for_upload_already_exist_message_pattern), response.getExistingFiles().size());
            getParent().notifyUser(context, R.string.alert_information, message);
        }

        @Override
        protected void onMessageForUser(Context context, BasePiwigoUploadService.MessageForUserResponse response) {
            getParent().notifyUser(context, R.string.alert_information, response.getMessage());
        }

        @Override
        protected void onChunkUploadFailed(Context context, final BasePiwigoUploadService.PiwigoUploadFileChunkFailedResponse response) {
            PiwigoResponseBufferingHandler.Response error = response.getError();
            Uri fileForUpload = response.getFileForUpload();
            String errorMessage = null;

            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                String msg = err.getErrorMessage();
                if ("java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(msg)) {
                    msg = err.getResponse();
                }
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), fileForUpload, err.getStatusCode(), msg);
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), fileForUpload, err.getRawResponse());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), fileForUpload, err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
            }
            if (errorMessage != null) {
                getParent().notifyUser(context, R.string.alert_error, errorMessage);
            }
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                getParent().onAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
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
            Uri fileForUploadUri = response.getFileForUpload();
            DocumentFile fileForUpload = DocumentFile.fromSingleUri(context, fileForUploadUri);
            String errorMessage = null;
            String uploadFilename = fileForUpload == null ? "" : fileForUpload.getName();

            if (error instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) error);
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webserver_message_pattern), uploadFilename, err.getStatusCode(), err.getErrorMessage());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse err = (PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) error;
                errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_webresponse_message_pattern), uploadFilename, err.getRawResponse());
            } else if (error instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoServerErrorResponse err = ((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) error);
                if ("file already exists".equals(err.getPiwigoErrorMessage())) {
                    if (getParent() != null && getParent().isAdded()) {
                        FilesToUploadRecyclerViewAdapter adapter = getParent().getFilesForUploadViewAdapter();
                        adapter.remove(fileForUploadUri);
                        getParent().releaseUriPermissionsForUploadItem(fileForUploadUri);
                    }
                    errorMessage = String.format(context.getString(R.string.alert_error_upload_file_already_on_server_message_pattern), uploadFilename);
                } else {
                    errorMessage = String.format(context.getString(R.string.alert_upload_file_failed_piwigo_message_pattern), uploadFilename, err.getPiwigoErrorCode(), err.getPiwigoErrorMessage());
                }
            } else if (error instanceof PiwigoResponseBufferingHandler.CustomErrorResponse) {
                errorMessage = ((PiwigoResponseBufferingHandler.CustomErrorResponse) error).getErrorMessage();
            }
            if (errorMessage != null) {
                getParent().notifyUser(context, R.string.alert_error, errorMessage);
            }
        }
    }

    private void releaseUriPermissionsForUploadItem(Uri fileForUploadUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            appSettingsViewModel.releasePersistableUriPermission(requireContext(), fileForUploadUri, URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD);
        }
    }
}
