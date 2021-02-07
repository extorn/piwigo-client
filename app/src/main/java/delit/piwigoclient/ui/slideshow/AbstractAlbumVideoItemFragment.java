package delit.piwigoclient.ui.slideshow;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.slidingsheet.SlidingBottomSheet;
import delit.libs.util.IOUtils;
import delit.libs.util.SafeRunnable;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.business.video.CachedContent;
import delit.piwigoclient.business.video.CustomExoPlayerTimeBar;
import delit.piwigoclient.business.video.ExoPlayerEventAdapter;
import delit.piwigoclient.business.video.PausableLoadControl;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.business.video.RemoteDirectHttpClientBasedHttpDataSource;
import delit.piwigoclient.business.video.RemoteFileCachingDataSourceFactory;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.model.ViewModelContainer;

public class AbstractAlbumVideoItemFragment<F extends AbstractAlbumVideoItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends VideoResourceItem> extends SlideshowItemFragment<F,FUIH,T> {

    private static final String TAG = "VideoItemFragment";

    private static final String STATE_CACHED_VIDEO_FILENAME = "cachedVideoFileName";
    private static final String ARG_AND_STATE_VIDEO_PLAY_AUTOMATICALLY = "videoPlayAutomatically";
    private static final String STATE_VIDEO_IS_PLAYING = "videoIsPlayingWhenVisible";
    private static final String STATE_VIDEO_PLAYBACK_POSITION = "currentVideoPlaybackPosition";
    private static final String STATE_PERMISSION_TO_CACHE_GRANTED = "permissionToCacheToDisk";
    private static final String STATE_CACHED_VIDEO_ORIGINAL_FILENAME = "originalVideoFilename";
    private static final String ARG_AND_STATE_SHOWING_STANDALONE = "showingOutsideSlideshow";

    private static final int PERMISSIONS_FOR_DOWNLOAD = 1;
    private static final int PERMISSIONS_FOR_CACHE = 2;

    private SimpleExoPlayer player;
    private RemoteFileCachingDataSourceFactory dataSourceFactory;
    private PausableLoadControl loadControl;

    private File cachedVideoFile; // Used when downloading (copying) video to user accessible area
    private String originalVideoFilename; // Used for generating downloaded filename
    private boolean permissionToCache;

    private long videoPlaybackPosition;
    private boolean videoIsPlayingWhenVisible;
    private boolean playVideoAutomatically;

    private TextView downloadedByteCountView;
    private TextView cachedByteCountView;
    private CustomCacheListener cacheListener;
    private DefaultTrackSelector trackSelector;
    private boolean showingOutsideSlideshow;
    private View customExoPlayerInfoPanel;

    public AbstractAlbumVideoItemFragment() {
    }

    public static Bundle buildStandaloneArgs(Class<? extends ViewModelContainer> modelType, long albumId, long albumItemId, int albumResourceItemIdx, int albumResourceItemCount, int totalResourceItemCount, boolean startPlaybackOnFragmentDisplay) {
        Bundle b = buildArgs(modelType, albumId, albumItemId, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount, startPlaybackOnFragmentDisplay);
        b.putBoolean(ARG_AND_STATE_SHOWING_STANDALONE, true);
        return b;
    }

    public static Bundle buildArgs(Class<? extends ViewModelContainer> modelType, long albumId, long albumItemId, int albumResourceItemIdx, int albumResourceItemCount, int totalResourceItemCount, boolean startPlaybackOnFragmentDisplay) {
        Bundle args = SlideshowItemFragment.buildArgs(modelType, albumId, albumItemId, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount);
        args.putBoolean(ARG_AND_STATE_VIDEO_PLAY_AUTOMATICALLY, startPlaybackOnFragmentDisplay);
        return args;
    }


    @Override
    public void onDestroyView() {
        logStatus("destroy view");
        cleanupVideoResources();
        manageCache();
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        logStatus("onPause");
        stopVideoDownloadAndPlay();
        super.onPause();
    }

    @Override
    public void onResume() {
        logStatus("onResume");
        super.onResume();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
    }

    private void logStatus(String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("%1$s - %2$b : %3$s", getModel() != null ? getModel().getId() : "UNKNOWN", isPrimarySlideshowItem(), msg));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        logStatus("saving state");
        super.onSaveInstanceState(outState);
        outState.putBoolean(ARG_AND_STATE_VIDEO_PLAY_AUTOMATICALLY, playVideoAutomatically);
        outState.putBoolean(STATE_VIDEO_IS_PLAYING, videoIsPlayingWhenVisible);
        outState.putBoolean(STATE_PERMISSION_TO_CACHE_GRANTED, permissionToCache);
        outState.putString(STATE_CACHED_VIDEO_FILENAME, cachedVideoFile != null ? cachedVideoFile.getAbsolutePath() : null);
        outState.putString(STATE_CACHED_VIDEO_ORIGINAL_FILENAME, originalVideoFilename);
        outState.putLong(STATE_VIDEO_PLAYBACK_POSITION, videoPlaybackPosition);
        outState.putBoolean(ARG_AND_STATE_SHOWING_STANDALONE, showingOutsideSlideshow);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logStatus("creating all the essentials");
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        String userAgent = Util.getUserAgent(getContext(), getContext().getPackageName());
        cacheListener = new CustomCacheListener();
        dataSourceFactory = new RemoteFileCachingDataSourceFactory(getContext(), bandwidthMeter, cacheListener, cacheListener, userAgent);
        dataSourceFactory.setPerformUriPathSegmentEncoding(ConnectionPreferences.getActiveProfile().isPerformUriPathSegmentEncoding(getPrefs(), getContext()));
        PausableLoadControl.Listener loadControlPauseListener = dataSourceFactory.getLoadControlPauseListener();

        loadControl = new PausableLoadControl();
        loadControl.setListener(loadControlPauseListener);
        super.onCreate(savedInstanceState);
    }

    public void setPlayVideoAutomatically(boolean playVideoAutomatically) {
        this.playVideoAutomatically = playVideoAutomatically;
    }

    public void setVideoIsPlayingWhenVisible(boolean videoIsPlayingWhenVisible) {
        this.videoIsPlayingWhenVisible = videoIsPlayingWhenVisible;
    }

    public boolean isPlayVideoAutomatically() {
        return playVideoAutomatically;
    }

    public boolean isVideoIsPlayingWhenVisible() {
        return videoIsPlayingWhenVisible;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            logStatus("loading arguments");
            playVideoAutomatically = getArguments().getBoolean(ARG_AND_STATE_VIDEO_PLAY_AUTOMATICALLY);
            showingOutsideSlideshow = getArguments().getBoolean(ARG_AND_STATE_SHOWING_STANDALONE);
            // now initialise whether to play the video or not.
            videoIsPlayingWhenVisible = playVideoAutomatically;
        }
        if (savedInstanceState != null) {
            logStatus("loading saved state");
            // these two are only ever loaded from the args... then state. (args are deleted though atm in super.super class!)
            playVideoAutomatically = savedInstanceState.getBoolean(ARG_AND_STATE_VIDEO_PLAY_AUTOMATICALLY);
            showingOutsideSlideshow = savedInstanceState.getBoolean(ARG_AND_STATE_SHOWING_STANDALONE);

            videoIsPlayingWhenVisible = savedInstanceState.getBoolean(STATE_VIDEO_IS_PLAYING);
            permissionToCache = savedInstanceState.getBoolean(STATE_PERMISSION_TO_CACHE_GRANTED);
            videoPlaybackPosition = savedInstanceState.getLong(STATE_VIDEO_PLAYBACK_POSITION);
            String cachedVideoFilename = savedInstanceState.getString(STATE_CACHED_VIDEO_FILENAME);
            if (cachedVideoFilename == null) {
                cachedVideoFile = null;
            } else {
                cachedVideoFile = new File(cachedVideoFilename);
            }
            originalVideoFilename = savedInstanceState.getString(STATE_CACHED_VIDEO_ORIGINAL_FILENAME);
        }
        onViewCreatedAndStateLoaded(view, savedInstanceState);

        super.onViewCreated(view, savedInstanceState);
        setAllowDownload(cachedVideoFile != null);

        logStatus("view state restored");
        displayItemDetailsControlsBasedOnSessionState();
    }

    protected void onViewCreatedAndStateLoaded(View view, Bundle savedInstanceState) {
    }

    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        hideProgressIndicator();
        logStatus("Creating item content");

        View itemContentView = inflater.inflate(R.layout.exo_player_viewer_custom, container, false);
        PlayerView simpleExoPlayerView = itemContentView.findViewById(R.id.slideshow_video_player);

        customExoPlayerInfoPanel = simpleExoPlayerView.findViewById(R.id.custom_exo_info_panel);
        downloadedByteCountView = customExoPlayerInfoPanel.findViewById(R.id.exo_downloaded);
        cachedByteCountView = customExoPlayerInfoPanel.findViewById(R.id.exo_cached_summary);

        simpleExoPlayerView.getVideoSurfaceView().setOnClickListener(v -> getOverlaysVisibilityControl().runWithDelay(getView()));

        simpleExoPlayerView.getVideoSurfaceView().setOnLongClickListener(v -> {
            if (!AppPreferences.isUseVideoCache(requireContext(), prefs)) {
                stopVideoDownloadAndPlay();
                player.stop(); // this is terminal.
                videoPlaybackPosition = 0; // ensure it starts at the beginning again
                configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
            }
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.alert_clear_cached_content), R.string.button_cancel, R.string.button_ok, new ClearCachedContentAction<>(getUiHelper()));
            return true;
        });

        CustomExoPlayerTimeBar timebar = itemContentView.findViewById(R.id.exo_progress);
        cacheListener.setTimebar(timebar);

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(getContext()), trackSelector, loadControl);
        player.addListener(buildNewPlayerEventListener());
        simpleExoPlayerView.setPlayer(player);
        View exoPlayerControlsView = simpleExoPlayerView.findViewById(R.id.exo_player_controls_container);
        simpleExoPlayerView.setControllerVisibilityListener(new CustomVidePlayerControlsVisibilityListener(exoPlayerControlsView, getBottomSheet()));
        simpleExoPlayerView.showController();
        logStatus("finished created item content");
        return itemContentView;
    }

    protected Player.EventListener buildNewPlayerEventListener() {
        return new MyPlayerEventListener();
    }

    private static class CustomVidePlayerControlsVisibilityListener implements PlayerControlView.VisibilityListener {

        private final View playerControlsView;
        private final SlidingBottomSheet videoMetadataContainerView;
        private int naturalHeightPx = -1;

        private CustomVidePlayerControlsVisibilityListener(View playerControlsView, SlidingBottomSheet videoMetadataContainerView) {
            this.playerControlsView = playerControlsView;
            this.videoMetadataContainerView = videoMetadataContainerView;
        }

        @Override
        public void onVisibilityChange(int visibility) {
            if (visibility == View.VISIBLE) {
                videoMetadataContainerView.setEnabled(false);
                videoMetadataContainerView.setVisibility(View.GONE);
            } else {
                videoMetadataContainerView.setEnabled(true);
                videoMetadataContainerView.setVisibility(View.VISIBLE);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if(event.item.getId() == this.getModel().getId() && isVisible() && showingOutsideSlideshow) {
            Logging.log(Log.INFO, TAG, "removing from activity after deleted item");
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    protected void configureItemContent(@Nullable View itemContentView, final T model, @Nullable Bundle savedInstanceState) {
        super.configureItemContent(itemContentView, model, savedInstanceState);
    }

    @Override
    public void onPageDeselected() {
        if (isPrimarySlideshowItem()) {
            super.onPageDeselected();
            logStatus("transitioning to page not showing");
            stopVideoDownloadAndPlay();
        }
    }

    @Override
    protected void doOnceOnPageSelectedAndAdded() {
        super.doOnceOnPageSelectedAndAdded();
        if (getParentFragment() != null || showingOutsideSlideshow) {
            configureDatasourceAndPlayerRequestingPermissions(playVideoAutomatically && videoIsPlayingWhenVisible);
        }
    }


    protected void clearCacheAndRestartVideoUpdatingWithNewUri(String newUri) {
        clearVideoCacheAndResetUi();

        if(newUri != null) {
            ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getPreferences(null, getUiHelper().getPrefs(), getUiHelper().getAppContext());
            connPrefs.setFixPiwigoPrivacyPluginMediaUris(getUiHelper().getPrefs(), getUiHelper().getAppContext(), true);
            getModel().updateFileUri(getModel().getFullSizeFile(), newUri);
        }

        configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
    }

    private void clearVideoCacheAndResetUi() {
        stopVideoDownloadAndPlay();
        player.stop(); // this is terminal.
        videoPlaybackPosition = 0; // ensure it starts at the beginning again
        cachedVideoFile = null;
        try {
            CacheUtils.deleteCachedContent(getContext(), getModel().getFileUrl(getModel().getFullSizeFile().getName()));
            // now update stored state and UI display
            setAllowDownload(false);
            displayItemDetailsControlsBasedOnSessionState();
        } catch (IOException e) {
            Logging.recordException(e);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_clear_cached_content));
        }
        logStatus("Cache cleared - configure a new datasource and player - start playback? : " + videoIsPlayingWhenVisible);
    }

    protected void clearCacheAndRestartVideo() {
        clearVideoCacheAndResetUi();
        configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
    }

    @Override
    public void displayItemDetailsControlsBasedOnSessionState() {
        super.displayItemDetailsControlsBasedOnSessionState();
    }

    @Override
    protected void onDownloadItem(final VideoResourceItem model) {
        DocumentFile downloadFolder = AppPreferences.getAppDownloadFolder(getPrefs(), requireContext());
        String permission = IOUtils.getManifestFilePermissionsNeeded(requireContext(), downloadFolder.getUri(), IOUtils.URI_PERMISSION_READ_WRITE);
        getUiHelper().setPermissionsNeededReason(PERMISSIONS_FOR_DOWNLOAD);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, permission, getString(R.string.alert_write_permission_needed_for_download));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PermissionsWantedResponse event) {

        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if (getUiHelper().getPermissionsNeededReason() == PERMISSIONS_FOR_DOWNLOAD) {
                if (event.areAllPermissionsGranted()) {
                    //Granted
                    DownloadSelectionMultiItemDialog dialogFactory = new DownloadSelectionMultiItemDialog(getContext());
                    AlertDialog dialog = dialogFactory.buildDialog(AbstractBaseResourceItem.ResourceFile.ORIGINAL, getModel(), new DownloadSelectionMultiItemDialog.DownloadSelectionMultiItemListener() {
                        @Override
                        public void onDownload(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
                            if(filesUnavailableToDownload.size() > 0) {
                                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new SelectionContainsUnsuitableFilesQuestionResult<>(getUiHelper(), items, selectedPiwigoFilesizeName));
                            } else {
                                new BaseDownloadQuestionResult<>(getUiHelper()).doDownloadAction(items, selectedPiwigoFilesizeName, false);
                            }

                        }

                        @Override
                        public void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
                            if(filesUnavailableToDownload.size() > 0) {
                                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new OnFilesUnavailableToDownloadQuestionResult<>(getUiHelper(), items, selectedPiwigoFilesizeName));
                            } else {
                                new BaseDownloadQuestionResult<>(getUiHelper()).doDownloadAction(items, selectedPiwigoFilesizeName, true);
                            }
                        }

                        @Override
                        public void onCopyLink(Context context, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
                            ResourceItem item = items.iterator().next();
                            String resourceName = item.getName();
                            ResourceItem.ResourceFile resourceFile = item.getFile(selectedPiwigoFilesizeName);
                            Uri uri = Uri.parse(item.getFileUrl(resourceFile.getName()));
                            ClipboardManager mgr = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if(mgr != null) {
                                ClipData clipData = ClipData.newRawUri(context.getString(R.string.download_link_clipboard_data_desc, resourceName), uri);
                                mgr.setPrimaryClip(clipData);
                                getUiHelper().showShortMsg(R.string.copied_to_clipboard);
                            } else {
                                Logging.logAnalyticEvent(context,"NoClipMgr", null);
                            }
                            EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
                        }
                    });
                    dialog.show();

                } else {
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                    } else {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions_scoped_storage));
                    }
                    EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), getModel()));
                }
            } else if (getUiHelper().getPermissionsNeededReason() == PERMISSIONS_FOR_CACHE) {
                if (event.areAllPermissionsGranted()) {
                    permissionToCache = true;
                    logStatus("All permissions granted - configure player now!");
                } else {
                    logStatus("Not all permissions granted - tweak datasource factory settings and configure player now!");
                    permissionToCache = false;
                    if (isPrimarySlideshowItem()) {
                        getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(R.string.video_caching_disabled_warning));
                    }
                }
                boolean factorySettingsAltered = dataSourceFactory.setCachingEnabled(AppPreferences.isUseVideoCache(requireContext(), prefs));
                if (factorySettingsAltered) {
                    logStatus("Need to create a new datasource (video playback stopped and data load paused)");
                    player.setPlayWhenReady(false);
                    loadControl.pauseBuffering();
                    player.stop();
                }
                dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
                dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));
                configurePlayer(videoIsPlayingWhenVisible);
            } else {
                throw new IllegalStateException("Permission required for what reason?");
            }
        }
    }

    private void configureDatasourceAndPlayerRequestingPermissions(final boolean startPlayback) {

        logStatus("configuring datasource factory with current cache enabled etc settings");

        boolean factorySettingsAltered = dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setCachingEnabled(AppPreferences.isUseVideoCache(requireContext(), prefs));
        if (factorySettingsAltered) {
            logStatus("Need to create a new datasource");
            stopVideoDownloadAndPlay();
            if (player != null) {
                player.stop();
            }
        }
        videoIsPlayingWhenVisible = startPlayback;

        if (dataSourceFactory.isCachingEnabled()) {
            logStatus("configuring datasource and player - caching enabled - check permissions first");
            getUiHelper().setPermissionsNeededReason(PERMISSIONS_FOR_CACHE);
            Uri videoCacheFolder = Uri.fromFile(CacheUtils.getBasicCacheFolder(requireContext()));
            String permission = IOUtils.getManifestFilePermissionsNeeded(requireContext(), videoCacheFolder, IOUtils.URI_PERMISSION_READ_WRITE);
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.Q, permission, getString(R.string.alert_write_permission_needed_for_video_caching));
        } else {
            logStatus("configuring datasource and player - no caching enabled - do now!");
            configurePlayer(videoIsPlayingWhenVisible);
            if (isPrimarySlideshowItem()) {
                getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(R.string.video_caching_disabled_warning));
            }
        }
    }

    private void configurePlayer(boolean startPlaybackImmediatelyIfVisibleToUser) {

        if (player.getPlaybackState() == Player.STATE_IDLE) {
            logStatus("configuring the player with a brand new datasource from factory");
            Uri videoUri = Uri.parse(getModel().getFileUrl(getModel().getFullSizeFile().getName()));
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if (connectionPrefs.isForceHttps(prefs, getContext()) && "http".equalsIgnoreCase(videoUri.getScheme())) {
                videoUri = videoUri.buildUpon().scheme("https").build();
            }
            ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(dataSourceFactory);
            factory.setExtractorsFactory(extractorsFactory);
            ExtractorMediaSource videoSource = factory.createMediaSource(videoUri);
            if (player.getCurrentPosition() != videoPlaybackPosition && videoPlaybackPosition >= 0) {
                logStatus("moving playback position to last position (" + player.getCurrentPosition() + ")");
                player.seekTo(videoPlaybackPosition);
            }
            logStatus("resuming buffering - in case paused");
            player.prepare(videoSource, false, false);
            try {
                loadControl.resumeBuffering();
            } catch (IllegalStateException e) {
                Logging.log(Log.ERROR, TAG, e.getMessage());
                Logging.recordException(e);
            }
            player.addListener(new ExoPlayerEventAdapter() {
                @Override
                public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                    if(playbackState == Player.STATE_ENDED) {
                        // need to create a new extractor media source as exoplayer does not expect them to be reused after STATE_ENDED
                        player.seekTo(0);
                        player.setPlayWhenReady(false);
                        Uri videoUri = Uri.parse(getModel().getFileUrl(getModel().getFullSizeFile().getName()));
                        ExtractorMediaSource videoSource = factory.createMediaSource(videoUri);
                        player.prepare(videoSource, false, false);
                        loadControl.resumeBuffering();
                    }
                }
            });
        } else {
            logStatus("configuring player with old datasource - resuming buffering if paused");
            loadControl.resumeBuffering();
        }
        player.setPlayWhenReady(startPlaybackImmediatelyIfVisibleToUser && isPrimarySlideshowItem());
    }

    private void stopVideoDownloadAndPlay() {
        if (player != null && player.getPlaybackState() != Player.STATE_IDLE) {
            videoIsPlayingWhenVisible = player.getPlayWhenReady();
            videoPlaybackPosition = player.getCurrentPosition();
            player.setPlayWhenReady(false);
            loadControl.pauseBuffering();
            logStatus("video playback stopped and data load paused");
        }
    }

    private void cleanupVideoResources() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
            logStatus("player and data source factory cleaned up (need to clean up datasource manually?)");
        }
    }

    private void manageCache() {
        if (AppPreferences.isUseVideoCache(requireContext(), prefs)) {
            long maxCacheSizeBytes = 1024 * 1024 * AppPreferences.getVideoCacheSizeMb(prefs, requireContext());
            logStatus("managing the disk cache - max size = " + IOUtils.bytesToNormalizedText(maxCacheSizeBytes));
            try {
                CacheUtils.manageVideoCache(getContext(), maxCacheSizeBytes);
            } catch (IOException e) {
                Logging.recordException(e);
                getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_tidying_video_cache));
            }
        }
    }

    private class CustomCacheListener implements RemoteAsyncFileCachingDataSource.CacheListener, RemoteDirectHttpClientBasedHttpDataSource.DownloadListener {

        private CustomExoPlayerTimeBar timebar;
        private long bytesDownloaded;

        public CustomCacheListener() {
        }

        public void setTimebar(CustomExoPlayerTimeBar timebar) {
            this.timebar = timebar;
        }

        @Override
        public void onFullyCached(final CachedContent cacheFileContent) {
            customExoPlayerInfoPanel.postDelayed(() -> customExoPlayerInfoPanel.setVisibility(View.INVISIBLE), 5000);
            timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            cachedVideoFile = cacheFileContent.getCachedDataFile();
            setAllowDownload(cachedVideoFile != null);

            originalVideoFilename = cacheFileContent.getOriginalUri().replace(".*/", "").replace("\\?.*", "");

            if (isVisible()) {
                getView().post(new SafeRunnable(() -> {
                    if (getContext() != null) {
                        displayItemDetailsControlsBasedOnSessionState();
                        cachedByteCountView.setText(getString(R.string.x_of_y, IOUtils.bytesToNormalizedText(cacheFileContent.getCachedBytes()) ,IOUtils.bytesToNormalizedText(cacheFileContent.getTotalBytes())));
                        timebar.invalidate();
                    }
                }));
            }
        }

        @Override
        public void onRangeAdded(final CachedContent cacheFileContent, long fromVideoPosition, long toVideoPosition, long bytesAddedToRange) {

            bytesDownloaded += bytesAddedToRange;

            if (timebar.getParent() != null) {
                timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            }
            if (isVisible()) {
                getView().post(new SafeRunnable(() -> {
                    if (getContext() != null) {
                        customExoPlayerInfoPanel.setVisibility(View.VISIBLE);
                        downloadedByteCountView.setText(IOUtils.bytesToNormalizedText(bytesDownloaded));
                        cachedByteCountView.setText(getString(R.string.x_of_y,IOUtils.bytesToNormalizedText(cacheFileContent.getCachedBytes()), IOUtils.bytesToNormalizedText(cacheFileContent.getTotalBytes())));
                        timebar.invalidate();
                    }
                }));
            }
        }

        @Override
        public void onCacheLoaded(final CachedContent cacheFileContent, long position) {
            if (timebar.getParent() != null) {
                timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            }
            if (isVisible()) {
                getView().post(new SafeRunnable(() -> {
                    if (getContext() != null) {
                        if(cacheFileContent.isComplete()) {
                            customExoPlayerInfoPanel.postDelayed(() -> customExoPlayerInfoPanel.setVisibility(View.INVISIBLE), 5000);
                        }
                        customExoPlayerInfoPanel.setVisibility(View.VISIBLE);
                        downloadedByteCountView.setText(IOUtils.bytesToNormalizedText(bytesDownloaded));
                        cachedByteCountView.setText(getString(R.string.x_of_y,IOUtils.bytesToNormalizedText(cacheFileContent.getCachedBytes()), IOUtils.bytesToNormalizedText(cacheFileContent.getTotalBytes())));
                        timebar.invalidate();
                    }
                }));
            }
        }

        @Override
        public void onDownload(final long bytesCachedInThisRange, final long totalBytes, final long bytesAddedToCache) {
            if (isVisible()) {
                getView().post(new SafeRunnable(() -> {
                    if (getContext() != null) {
                        bytesDownloaded += bytesAddedToCache;
                        downloadedByteCountView.setText(IOUtils.bytesToNormalizedText(bytesDownloaded));
                        cachedByteCountView.setText(getString(R.string.x_of_y, IOUtils.bytesToNormalizedText(bytesCachedInThisRange), IOUtils.bytesToNormalizedText(totalBytes)));
                    }
                }));
            }
        }
    }

    private static class ClearCachedContentAction<F extends AbstractAlbumVideoItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends VideoResourceItem> extends UIHelper.QuestionResultAdapter<FUIH,F> implements Parcelable {

        public ClearCachedContentAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected ClearCachedContentAction(Parcel in) {
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

        public static final Creator<ClearCachedContentAction<?,?,?>> CREATOR = new Creator<ClearCachedContentAction<?,?,?>>() {
            @Override
            public ClearCachedContentAction<?,?,?> createFromParcel(Parcel in) {
                return new ClearCachedContentAction<>(in);
            }

            @Override
            public ClearCachedContentAction<?,?,?>[] newArray(int size) {
                return new ClearCachedContentAction[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().getParent().clearCacheAndRestartVideo();
            }
        }
    }

    protected class MyPlayerEventListener extends Player.DefaultEventListener {

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if(Player.STATE_ENDED == playbackState && playWhenReady) {
                player.stop();
                player.seekTo(0); // get ready to play again.
            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Throwable e = error;
            while (e != null) {
                if (e instanceof RemoteAsyncFileCachingDataSource.HttpIOException || e instanceof HttpDataSource.InvalidResponseCodeException) {
                    break;
                }
                e = e.getCause();
            }
            if (e instanceof RemoteAsyncFileCachingDataSource.HttpIOException) {
                RemoteAsyncFileCachingDataSource.HttpIOException err = (RemoteAsyncFileCachingDataSource.HttpIOException) e;
                String response = null;
                try {
                    response = new String(err.getResponseData());
                } catch (RuntimeException e1) {
                    // do nothing.
                }

                boolean handledError = false;
                if(err.getStatusCode() == 404) {
                    ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getPreferences(null, getPrefs(), requireContext());
                    String basePiwigoUri = connPrefs.getPiwigoServerAddress(getPrefs(), requireContext());
                    String resourceUri = err.getUri();
                    AlbumGetImagesBasicResponseHandler.MultimediaUriMatcherUtil multimediaUriMatcherUtil = new AlbumGetImagesBasicResponseHandler.MultimediaUriMatcherUtil(basePiwigoUri, resourceUri);
                    if (multimediaUriMatcherUtil.matchesUri()) {
                        // should always match, but we should check.
                        if (multimediaUriMatcherUtil.isPathMissingResourceId()
                                && !connPrefs.isFixPiwigoPrivacyPluginMediaUris(getPrefs(), getContext())) {
                            String customMessage = getString(R.string.try_enabling_privacy_plugin_media_uris);
                            handledError = true;
                            String fixedUri = multimediaUriMatcherUtil.ensurePathContainsResourceId(getModel().getId());
                            getUiHelper().showOrQueueDialogQuestion(R.string.alert_error, getString(R.string.alert_server_error_pattern, err.getStatusCode(), customMessage),
                                                                   R.string.button_no, R.string.button_ok, new Fixable404ErrorActionListener<>(getUiHelper(), fixedUri));
                        }
                    }
                }
                if(!handledError) {
                    getUiHelper().showOrQueueDialogMessage(new UIHelper.QueuedDialogMessage<>(R.string.alert_error, getString(R.string.alert_server_error_pattern, err.getStatusCode(), err.getUri()), response, R.string.button_ok));
                }
            } else if (e instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException err = (HttpDataSource.InvalidResponseCodeException) e;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_server_error_pattern, err.responseCode, getModel().getDownloadFileName(getModel().getFullSizeFile())));
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.error_unexpected_error_calling_server));
            }
        }
    }

    private static class OnFilesUnavailableToDownloadQuestionResult<F extends AbstractAlbumVideoItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends VideoResourceItem> extends BaseDownloadQuestionResult<F,FUIH,T> {


        private final Set<ResourceItem> items;
        private final String selectedPiwigoFilesizeName;



        public OnFilesUnavailableToDownloadQuestionResult(FUIH uiHelper, Set<ResourceItem> items, String selectedPiwigoFilesizeName) {
            super(uiHelper);
            this.items = items;
            this.selectedPiwigoFilesizeName = selectedPiwigoFilesizeName;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            doDownloadAction(items, selectedPiwigoFilesizeName, true);
        }
    }


    private static class Fixable404ErrorActionListener<F extends AbstractAlbumVideoItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends VideoResourceItem> extends UIHelper.QuestionResultAdapter<FUIH, F> implements Parcelable {
        private final String fixedUri;

        public Fixable404ErrorActionListener(FUIH uiHelper, String fixedUri) {
            super(uiHelper);
            this.fixedUri = fixedUri;
        }

        protected Fixable404ErrorActionListener(Parcel in) {
            super(in);
            fixedUri = in.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(fixedUri);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Fixable404ErrorActionListener<?,?,?>> CREATOR = new Creator<Fixable404ErrorActionListener<?,?,?>>() {
            @Override
            public Fixable404ErrorActionListener<?,?,?> createFromParcel(Parcel in) {
                return new Fixable404ErrorActionListener<>(in);
            }

            @Override
            public Fixable404ErrorActionListener<?,?,?>[] newArray(int size) {
                return new Fixable404ErrorActionListener[size];
            }
        };

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if(Boolean.TRUE.equals(positiveAnswer)) {
                getParent().clearCacheAndRestartVideoUpdatingWithNewUri(fixedUri);
            }
        }

    }

}