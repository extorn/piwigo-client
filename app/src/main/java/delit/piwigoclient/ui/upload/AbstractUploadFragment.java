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
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.view.button.CustomImageButton;
import delit.libs.ui.view.list.BiArrayAdapter;
import delit.libs.util.ArrayUtils;
import delit.libs.util.CollectionUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
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
    private static final boolean ENABLE_COMPRESSION_BUTTON = false;
    private static final int TAB_IDX_SETTINGS = 1;
    private static final int TAB_IDX_FILES = 0;
    public static final String FILES_TO_UPLOAD_ADAPTER_STATE = "filesToUploadAdapter";
    private Long uploadJobId;
    private long externallyTriggeredSelectFilesActionId;
    private CategoryItemStub uploadToAlbum;
    private RecyclerView filesForUploadView;
    private Button uploadFilesNowButton;
    private Button deleteUploadJobButton;
    private TextView selectedGalleryTextView;
    private Spinner privacyLevelSpinner;
    private CustomImageButton fileSelectButton;
    private FilesToUploadRecyclerViewAdapter filesToUploadAdapter;
    private Button uploadJobStatusButton;
    private TextView uploadableFilesView;
    private CheckBox compressVideosCheckbox;
    private CheckBox allowUploadOfRawVideosIfIncompressibleCheckbox;
    private CheckBox compressImagesCheckbox;
    private ViewPager mViewPager;
    private LinearLayout compressImagesSettings;
    private LinearLayout compressVideosSettings;
    private Spinner compressVideosQualitySpinner;
    private Spinner compressVideosAudioBitrateSpinner;
    private NumberPicker compressImagesQualityNumberPicker;
    private Spinner compressImagesOutputFormatSpinner;
    private NumberPicker compressImagesMaxHeightNumberPicker;
    private NumberPicker compressImagesMaxWidthNumberPicker;
    private ProgressBar overallUploadProgressBar;

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
        if (getArguments() != null) {
            uploadToAlbum = getArguments().getParcelable(SAVED_STATE_UPLOAD_TO_ALBUM);
            if (uploadToAlbum == null) {
                uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
            }
            externallyTriggeredSelectFilesActionId = getArguments().getInt(ARG_EXTERNALLY_TRIGGERED_SELECT_FILES_ACTION_ID);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent stickyEvent) {
        if (externallyTriggeredSelectFilesActionId == stickyEvent.getActionId() || getUiHelper().isTrackingRequest(stickyEvent.getActionId())) {
            ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
            if (PiwigoSessionDetails.getInstance(activeProfile) != null) {
                EventBus.getDefault().removeStickyEvent(stickyEvent);
                mViewPager.setCurrentItem(TAB_IDX_FILES);
                Set<String> allowedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
                Iterator<FolderItemRecyclerViewAdapter.FolderItem> iter = stickyEvent.getSelectedFolderItems().iterator();
                Set<String> unsupportedExts = new HashSet<>();
                while (iter.hasNext()) {
                    FolderItemRecyclerViewAdapter.FolderItem f = iter.next();
                    String fileExt = IOUtils.getFileExt(f.getFile().getName().toLowerCase());
                    if (!allowedFileTypes.contains(fileExt)) {
                        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
                        if (mimeType == null || !mimeType.startsWith("video/")) {
                            iter.remove();
                            unsupportedExts.add(fileExt);
                        }
                    }
                }
                if (!unsupportedExts.isEmpty()) {
                    getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_error_unsupported_file_extensions_pattern, CollectionUtils.toCsvList(unsupportedExts)));
                }
                updateFilesForUploadList(stickyEvent.getSelectedFolderItems(), stickyEvent.isContentUrisPresent());
            }
            AdsManager.getInstance().showFileToUploadAdvertIfAppropriate();
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

        MediaScanner mediaScanner = MediaScanner.instance(container.getContext());

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

        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences viewPrefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.withShowHierachy();

        selectedGalleryTextView = view.findViewById(R.id.selected_gallery);
        selectedGalleryTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSelectedGalleryTextViewClick();
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
        BiArrayAdapter<String> audioBitrateAdapter = new BiArrayAdapter<>(getContext(), getResources().getStringArray(R.array.preference_data_upload_compress_videos_audio_bitrate_items),
                ArrayUtils.getLongArray(getResources().getIntArray(R.array.preference_data_upload_compress_videos_audio_bitrate_values)));
        audioBitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        compressVideosAudioBitrateSpinner.setAdapter(audioBitrateAdapter);
        int defaultAudioBitrate = UploadPreferences.getVideoCompressionAudioBitrate(getContext(), getPrefs());
        compressVideosAudioBitrateSpinner.setSelection(audioBitrateAdapter.getPosition(defaultAudioBitrate));

        compressVideosCheckbox = view.findViewById(R.id.compress_videos_button);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            compressVideosCheckbox.setVisibility(GONE);
            compressVideosSettings.setVisibility(GONE);
        } else {
            boolean compressVids = UploadPreferences.isCompressVideosByDefault(getContext(), getPrefs());
            compressVideosCheckbox.setChecked(!compressVids);// ensure the checked change listener is called!
            compressVideosCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    compressVideosSettings.setVisibility(isChecked && buttonView.isEnabled() ? VISIBLE : GONE);
//                    compressVideosSettings.setEnabled(buttonView.isEnabled());
                }
            });
            compressVideosCheckbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CompoundButton buttonView = (CompoundButton) v;
                    compressVideosSettings.setVisibility(buttonView.isChecked() && buttonView.isEnabled() ? VISIBLE : GONE);
                    DisplayUtils.toggleHierachyEnabled(compressVideosSettings, buttonView.isEnabled());
                }
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
        compressImagesCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                compressImagesSettings.setVisibility(isChecked && buttonView.isEnabled() ? VISIBLE : GONE);
//                compressImagesSettings.setEnabled(buttonView.isEnabled());
            }
        });
        compressImagesCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CompoundButton buttonView = (CompoundButton) v;
                compressImagesSettings.setVisibility(buttonView.isChecked() && buttonView.isEnabled() ? VISIBLE : GONE);
                DisplayUtils.toggleHierachyEnabled(compressImagesSettings, buttonView.isEnabled());
            }
        });
        compressImagesCheckbox.setChecked(compressPics);

        allowUploadOfRawVideosIfIncompressibleCheckbox.setChecked(UploadPreferences.isAllowUploadOfRawVideosIfIncompressible(getContext(), getPrefs()));

        uploadFilesNowButton = view.findViewById(R.id.upload_files_button);
        uploadFilesNowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadFilesNowButton.setText(R.string.upload_files_button_title);
                uploadFiles();
            }
        });

        if (BuildConfig.DEBUG && ENABLE_COMPRESSION_BUTTON) {
            Button compressVideosButton = new Button(getContext());
            compressVideosButton.setText("Compress");
            compressVideosButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    v.setEnabled(false);
                    compressVideos(v);
                }
            });
            ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.leftToRight = R.id.view_detailed_upload_status_button;
            layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            ((ConstraintLayout) uploadFilesNowButton.getParent()).addView(compressVideosButton, layoutParams);
        }

        if (filesToUploadAdapter == null) {
            filesToUploadAdapter = new FilesToUploadRecyclerViewAdapter(new ArrayList<File>(), mediaScanner, getContext(), this);
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

        updateUiUploadStatusFromJobIfRun(container.getContext(), filesToUploadAdapter);

        return view;
    }


    private void compressVideos(View linkedView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            getUiHelper().showDetailedMsg(R.string.alert_error, "Video Compression not supported on this version of android");
        } else {
            FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();
            List<File> filesForUpload = fileListAdapter.getFiles();
            if (filesForUpload.isEmpty()) {
                return;
            }
            ExoPlayerCompression.CompressionParameters compressionSettings = new ExoPlayerCompression.CompressionParameters();
            long rawVal = compressVideosQualitySpinner.getSelectedItemId();
            int audioBitrate = (int) compressVideosAudioBitrateSpinner.getSelectedItemId();
            double bpps = ((double) rawVal) / 1000;
            compressionSettings.getVideoCompressionParameters().setWantedBitRatePerPixelPerSecond(bpps);
            compressionSettings.getAudioCompressionParameters().setBitRate(audioBitrate);

            File inputVideo = filesForUpload.get(0);
            File moviesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            File outputVideo = new File(moviesFolder, "compressed_" + inputVideo.getName());
            outputVideo = IOUtils.changeFileExt(outputVideo, MimeTypeMap.getSingleton().getExtensionFromMimeType(compressionSettings.getOutputFileMimeType()));
            new ExoPlayerCompression().invokeFileCompression(getContext(), inputVideo, outputVideo, new DebugCompressionListener(getUiHelper(), linkedView), compressionSettings);
        }
    }

    private void setSpinnerSelectedItem(Spinner spinner, Object item) {
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter instanceof ArrayAdapter) {
            int itemPosition = ((ArrayAdapter) spinner.getAdapter()).getPosition(item);
            spinner.setSelection(itemPosition);
        } else {
            Crashlytics.log(Log.ERROR, TAG, "Cannot set selected spinner item - adapter is not instance of ArrayAdapter");
        }
    }

    protected boolean isImageFilesWaitingForUpload() {
        return hasFileMatchingMime("image/");
    }

    protected boolean isVideoFilesWaitingForUpload() {
        return hasFileMatchingMime("video/");
    }

    protected boolean hasFileMatchingMime(String mimePrefix) {
        List<File> files = filesToUploadAdapter.getFiles();
        MimeTypeMap mimeTypesMap = MimeTypeMap.getSingleton();
        for (File f : files) {
            String fileExt = IOUtils.getFileExt(f.getName());
            String mimeType = mimeTypesMap.getMimeTypeFromExtension(fileExt.toLowerCase());
            if (mimeType != null && mimeType.startsWith(mimePrefix)) {
                return true;
            }
        }
        return false;
    }

    private CustomImageButton getFileSelectButton() {
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
            String initialFolder = getPrefs().getString(getString(R.string.preference_data_upload_default_local_folder_key), Environment.getExternalStorageDirectory().getAbsolutePath());
            event.withInitialFolder(initialFolder);
            event.withVisibleContent(allowedFileTypes, FileSelectionNeededEvent.LAST_MODIFIED_DATE);

            Set<String> visibleMimeTypes = Collections.singleton("video/");
            event.withVisibleMimeTypes(visibleMimeTypes);

            getUiHelper().setTrackingRequest(event.getActionId());
            EventBus.getDefault().post(event);
        }
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
                FirebaseAnalytics.getInstance(getContext()).logEvent("IncompleteUserSession", b);
                getUiHelper().showDetailedMsg(R.string.alert_error, R.string.alert_user_session_no_allowed_filetypes);
                getView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        fileSelectButton.setEnabled(true);
                        requestFileSelection(null); // show all files... I hope the user is sensible!
                    }
                }, 3000);
            } else {
                requestFileSelection(sessionDetails.getAllowedFileTypes());
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
            selectedGalleryTextView.setText(uploadToAlbum.getName());

            byte privacyLevelWanted = uploadJob.getPrivacyLevelWanted();
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
                    File compressedFile = uploadJob.getCompressedFile(f);
                    if (compressedFile != null) {
                        filesForUploadAdapter.updateCompressionProgress(f, compressedFile, 100);
                    }
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
    }

    protected FilesToUploadRecyclerViewAdapter getFilesForUploadViewAdapter() {
        return (FilesToUploadRecyclerViewAdapter) filesForUploadView.getAdapter();
    }

    private CategoryItemStub getUploadToAlbum() {
        return uploadToAlbum;
    }

    protected void updateFilesForUploadList(ArrayList<FolderItemRecyclerViewAdapter.FolderItem> folderItemsToBeUploaded, boolean contentUrisPresent) {
        if (folderItemsToBeUploaded.size() > 0) {
            String lastOpenedFolder = folderItemsToBeUploaded.get(0).getFile().getParentFile().getAbsolutePath();
            getPrefs().edit().putString(getString(R.string.preference_data_upload_default_local_folder_key), lastOpenedFolder).apply();
        }
        FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
        int addedItems = 0;
        if (contentUrisPresent) {
            for (FolderItemRecyclerViewAdapter.FolderItem item : folderItemsToBeUploaded) {
                if (adapter.add(item.getFile(), item.getContentUri())) {
                    addedItems++;
                }
            }
            adapter.notifyDataSetChanged();
        } else {
            ArrayList<File> filesToBeUploaded = new ArrayList<>(folderItemsToBeUploaded.size());
            for (FolderItemRecyclerViewAdapter.FolderItem item : folderItemsToBeUploaded) {
                filesToBeUploaded.add(item.getFile());
            }
            addedItems = adapter.addAll(filesToBeUploaded).size();
        }
        int filesAlreadyPresent = folderItemsToBeUploaded.size() - addedItems;
        if (filesAlreadyPresent > 0) {
            getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.files_already_set_for_upload_skipped_pattern, filesAlreadyPresent));
        }
        uploadFilesNowButton.setEnabled(adapter.getItemCount() > 0);
        updateActiveJobActionButtonsStatus();
    }

    private Set<File> getFilesExceedingMaxDesiredUploadThreshold(List<File> filesForUpload) {
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
        buildAndSubmitNewUploadJob(false);
    }

    private void buildAndSubmitNewUploadJob(boolean filesizesChecked) {
        UploadJob activeJob = null;

        if (uploadJobId != null) {
            activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        FilesToUploadRecyclerViewAdapter fileListAdapter = getFilesForUploadViewAdapter();
        List<File> filesForUpload = fileListAdapter.getFiles();

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
            activeJob = ForegroundPiwigoUploadService.createUploadJob(ConnectionPreferences.getActiveProfile(), filesForUpload, uploadToAlbum, privacyLevelWanted, handlerId);
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

    private boolean runAreAllFilesUnderUserChosenMaxUploadThreshold(List<File> filesForUpload) {

        final Set<File> filesForReview = getFilesExceedingMaxDesiredUploadThreshold(filesForUpload);

        StringBuilder filenameListStrB = new StringBuilder();

        for (File f : filesForReview) {
            double fileLengthMB = ((double) f.length()) / 1024 / 1024;
            if (filesForReview.size() > 0) {
                filenameListStrB.append(", ");
            }
            filenameListStrB.append(f);
            filenameListStrB.append(String.format(Locale.getDefault(), "(%1$.1fMB)", fileLengthMB));
        }
        if (compressVideosCheckbox.isChecked()) {
            Iterator<File> iter = filesForReview.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(IOUtils.getFileExt(iter.next().getName()).toLowerCase());
                if (mimeType != null && mimeType.startsWith("video/")) {
                    iter.remove();
                }
            }
        }
        if (compressImagesCheckbox.isChecked()) {
            Iterator<File> iter = filesForReview.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {

                String mimeType = mimeTypeMap.getMimeTypeFromExtension(IOUtils.getFileExt(iter.next().getName()).toLowerCase());
                if (mimeType != null && mimeType.startsWith("image/")) {
                    iter.remove();
                }
            }
        }
        if (filesForReview.size() > 0) {
            getUiHelper().showOrQueueCancellableDialogQuestion(R.string.alert_warning, getString(R.string.alert_files_larger_than_upload_threshold_pattern, filesForReview.size(), filenameListStrB.toString()), R.string.button_no, R.string.button_cancel, R.string.button_yes, new FileSizeExceededAction(getUiHelper(), filesForReview));
            return false;
        }
        return true;
    }

    private boolean runIsAllFileTypesAcceptedByServerTests(List<File> filesForUpload) {
        // check for server unacceptable files.
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        Set<String> serverAcceptedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
        Set<String> fileTypesForUpload = IOUtils.getUniqueFileExts(filesForUpload);
        Set<String> unacceptableFileExts = SetUtils.difference(fileTypesForUpload, serverAcceptedFileTypes);
        if (compressVideosCheckbox.isChecked()) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && mimeType.startsWith("video/")) {
                    iter.remove();
                }
            }
        }
        if (compressImagesCheckbox.isChecked()) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && mimeType.startsWith("image/")) {
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
            if (!activeJob.isRunningNow() && activeJob.getFilesAwaitingUpload().size() == 0) {
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
                selectedGalleryTextView.setText(event.getAlbumPath(selectedAlbum));
//                selectedGalleryTextView.setText(uploadToAlbum.getName());
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

    }

    private TextView getSelectedGalleryTextView() {
        return selectedGalleryTextView;
    }

    private Button getUploadFilesNowButton() {
        return uploadFilesNowButton;
    }

    private static class OnLoginAction extends UIHelper.Action<AbstractUploadFragment, LoginResponseHandler.PiwigoOnLoginResponse> {
        @Override
        public boolean onSuccess(UIHelper<AbstractUploadFragment> uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
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
        public DeleteAllFilesSelectedAction(FragmentUIHelper<AbstractUploadFragment> uiHelper) {
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

    private static class PartialUploadFileAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {

        private final File itemToRemove;

        public PartialUploadFileAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, File itemToRemove) {
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
                    Crashlytics.log(Log.ERROR, TAG, "Attempt to alter upload job but it was null");
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
        public void onCompressionStarted() {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression started");
            startCompressionAt = System.currentTimeMillis();
        }

        @Override
        public void onCompressionError(Exception e) {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression failed");
            linkedView.setEnabled(true);
        }

        @Override
        public void onCompressionComplete() {
            uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression finished");
            linkedView.setEnabled(true);
        }

        @Override
        public void onCompressionProgress(final double compressionProgress, final long mediaDurationMs) {
            if (!DisplayUtils.isRunningOnUIThread()) {
                DisplayUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onCompressionProgress(compressionProgress, mediaDurationMs);
                    }
                });
                return;
            }
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
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nrate: %5$.02fx\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr, compressionRate), Toast.LENGTH_SHORT, 1);
            } else {
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr), Toast.LENGTH_SHORT, 1);
            }
        }
    }

    private static class UnacceptableFilesAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {

        private final Set<String> unacceptableFileExts;

        public UnacceptableFilesAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, Set<String> unacceptableFileExts) {
            super(uiHelper);
            this.unacceptableFileExts = unacceptableFileExts;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractUploadFragment fragment = getUiHelper().getParent();

            if (Boolean.TRUE == positiveAnswer) {

                List<File> unaccceptableFiles = new ArrayList<>(fragment.getFilesForUploadViewAdapter().getFiles());
                Iterator<File> iter = unaccceptableFiles.iterator();
                while (iter.hasNext()) {
                    if (!unacceptableFileExts.contains(IOUtils.getFileExt(iter.next().getName()).toLowerCase())) {
                        iter.remove();
                    }
                }
                for (File file : unaccceptableFiles) {
                    fragment.onRemove(fragment.getFilesForUploadViewAdapter(), file, false);
                }

                fragment.buildAndSubmitNewUploadJob(false);
            }
        }
    }

    private static class FileSizeExceededAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {
        private Set<File> filesToDelete;

        public FileSizeExceededAction(FragmentUIHelper<AbstractUploadFragment> uiHelper, Set<File> filesForReview) {
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
                fragment.buildAndSubmitNewUploadJob(true);
            }
        }
    }

    private static class OnDeleteJobQuestionAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractUploadFragment>> {
        public OnDeleteJobQuestionAction(FragmentUIHelper<AbstractUploadFragment> uiHelper) {
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
                ForegroundPiwigoUploadService.deleteStateFromDisk(getContext(), job, true);
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
                    if (!uploadToAlbum.isRoot() && !uploadToAlbum.isParentRoot()) {
                        fragment.getSelectedGalleryTextView().setText(fragment.getString(R.string.subAlbum_text, uploadToAlbum.getName()));
                    } else {
                        fragment.getSelectedGalleryTextView().setText(uploadToAlbum.getName());
                    }
                } else if (uploadToAlbum.isParentRoot()) {
                    uploadToAlbum = CategoryItemStub.ROOT_GALLERY;
                    fragment.getSelectedGalleryTextView().setText(uploadToAlbum.getName());
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
                UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, uploadJobId);
                updateOverallUploadProgress(uploadJob.getUploadProgress());
            }
            if (uploadJobId != null) {
                UploadJob uploadJob = ForegroundPiwigoUploadService.getActiveForegroundJob(context, uploadJobId);
                if (uploadJob.isFilePartiallyUploaded(cancelledFile)) {
                    getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_partial_upload_deleted));
                }
            } else {
                FirebaseAnalytics.getInstance(getContext()).logEvent("noJobDelFile", null);
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
                } else if (job.getAndClearWasLastRunCancelled()) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_message_upload_cancelled, context.getString(R.string.alert_message_upload_cancelled_message), R.string.button_ok);
                } else {
                    int errMsgResourceId = R.string.alert_message_error_uploading_start;
                    if (job.getFilesNotYetUploaded().size() == 0) {
                        errMsgResourceId = R.string.alert_message_error_uploading_end;
                    }
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_upload, context.getString(errMsgResourceId), R.string.button_ok);
                }
                allowUserUploadConfiguration(job);
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
            notifyUser(context, titleId, message);
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

        private void updateOverallUploadProgress(int progress) {
            overallUploadProgressBar.setProgress(progress);
            overallUploadProgressBar.setVisibility(VISIBLE);
            if (progress == 100) {
                overallUploadProgressBar.setVisibility(GONE);
            }
        }

        @Override
        protected void onFileUploadProgressUpdate(Context context, final BasePiwigoUploadService.PiwigoUploadProgressUpdateResponse response) {
            if (isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.updateUploadProgress(response.getFileForUpload(), response.getProgress());
                UploadJob activeJob = getActiveJob(context);
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
            if (isAdded()) {
                FilesToUploadRecyclerViewAdapter adapter = getFilesForUploadViewAdapter();
                adapter.updateCompressionProgress(response.getFileForUpload(), response.getCompressedFileUpload(), response.getProgress());
                updateOverallUploadProgress(getActiveJob(context).getUploadProgress());
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

            UploadJob uploadJob = getActiveJob(context);
            if (uploadJob != null) {
                updateOverallUploadProgress(uploadJob.getUploadProgress());
//                ResourceItem item = uploadJob.getUploadedFileResource(response.getFileForUpload());
//                for (Long albumParent : uploadJob.getUploadToCategoryParentage()) {
//                    EventBus.getDefault().post(new AlbumAlteredEvent(albumParent));
//                }
//                EventBus.getDefault().post(new AlbumAlteredEvent(uploadJob.getUploadToCategory(), item.getId()));

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
                    updateOverallUploadProgress(uploadJob.getUploadProgress());
                }
            }
            String message = String.format(context.getString(R.string.alert_items_for_upload_already_exist_message_pattern), response.getExistingFiles().size());
            notifyUser(context, R.string.alert_information, message);
        }

        @Override
        protected void onMessageForUser(Context context, BasePiwigoUploadService.MessageForUserResponse response) {
            notifyUser(context, R.string.alert_information, response.getMessage());
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
