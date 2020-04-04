package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.crashlytics.android.Crashlytics;
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
import com.google.firebase.analytics.FirebaseAnalytics;
import com.wunderlist.slidinglayer.CustomSlidingLayer;
import com.wunderlist.slidinglayer.OnInteractAdapter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.business.video.CachedContent;
import delit.piwigoclient.business.video.CustomExoPlayerTimeBar;
import delit.piwigoclient.business.video.PausableLoadControl;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.business.video.RemoteDirectHttpClientBasedHttpDataSource;
import delit.piwigoclient.business.video.RemoteFileCachingDataSourceFactory;
import delit.piwigoclient.model.piwigo.AbstractBaseResourceItem;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionFinishedEvent;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.ui.model.ViewModelContainer;

public class AbstractAlbumVideoItemFragment extends SlideshowItemFragment<VideoResourceItem> {

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
        setAllowDownload(false);

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

        downloadedByteCountView = simpleExoPlayerView.findViewById(R.id.exo_downloaded);
        cachedByteCountView = simpleExoPlayerView.findViewById(R.id.exo_cached_summary);

        simpleExoPlayerView.getVideoSurfaceView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getOverlaysVisibilityControl().runWithDelay(getView());
            }
        });

        simpleExoPlayerView.getVideoSurfaceView().setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isUseCache()) {
                    stopVideoDownloadAndPlay();
                    player.stop(); // this is terminal.
                    videoPlaybackPosition = 0; // ensure it starts at the beginning again
                    configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
                }
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.alert_clear_cached_content), R.string.button_cancel, R.string.button_ok, new ClearCachedContentAction(getUiHelper()));
                return true;
            }
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
        private final CustomSlidingLayer videoMetadataContainerView;
        private int naturalHeightPx = -1;

        private CustomVidePlayerControlsVisibilityListener(View playerControlsView, CustomSlidingLayer videoMetadataContainerView) {
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

    @Override
    public void onImageDeleted(HashSet<Long> deletedItemIds) {
        super.onImageDeleted(deletedItemIds);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if(event.item.getId() == this.getModel().getId() && isVisible() && showingOutsideSlideshow) {
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    protected void configureItemContent(@Nullable View itemContentView, final VideoResourceItem model, @Nullable Bundle savedInstanceState) {
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
        if (getParentFragment() != null) {
            configureDatasourceAndPlayerRequestingPermissions(playVideoAutomatically && videoIsPlayingWhenVisible);
        }
    }

    private void clearCacheAndRestartVideo() {
        stopVideoDownloadAndPlay();
        player.stop(); // this is terminal.
        videoPlaybackPosition = 0; // ensure it starts at the beginning again
        try {
            CacheUtils.deleteCachedContent(getContext(), getModel().getFileUrl(getModel().getFullSizeFile().getName()));
            // now update stored state and UI display
            setAllowDownload(false);
            displayItemDetailsControlsBasedOnSessionState();
        } catch (IOException e) {
            Crashlytics.logException(e);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_clear_cached_content));
        }
        logStatus("Cache cleared - configure a new datasource and player - start playback? : " + videoIsPlayingWhenVisible);
        configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
    }

    @Override
    public void displayItemDetailsControlsBasedOnSessionState() {
        super.displayItemDetailsControlsBasedOnSessionState();
    }

    @Override
    protected void onDownloadItem(final VideoResourceItem model) {
        getUiHelper().setPermissionsNeededReason(PERMISSIONS_FOR_DOWNLOAD);
        getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Integer.MAX_VALUE, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_download));
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
                                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new UIHelper.QuestionResultAdapter(getUiHelper()) {
                                    @Override
                                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                        doDownloadAction(items, selectedPiwigoFilesizeName, false);
                                    }
                                });
                            } else {
                                doDownloadAction(items, selectedPiwigoFilesizeName, false);
                            }

                        }

                        @Override
                        public void onShare(Set<ResourceItem> items, String selectedPiwigoFilesizeName, Set<ResourceItem> filesUnavailableToDownload) {
                            if(filesUnavailableToDownload.size() > 0) {
                                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.files_unavailable_to_download_removed_pattern, filesUnavailableToDownload.size()), new UIHelper.QuestionResultAdapter(getUiHelper()) {
                                    @Override
                                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                        doDownloadAction(items, selectedPiwigoFilesizeName, true);
                                    }
                                });
                            } else {
                                doDownloadAction(items, selectedPiwigoFilesizeName, true);
                            }
                        }

                        private void doDownloadAction(Set<ResourceItem> items, String selectedPiwigoFilesizeName, boolean shareWithOtherAppsAfterDownload) {
                            ResourceItem item = items.iterator().next();
                            DownloadFileRequestEvent evt = new DownloadFileRequestEvent(shareWithOtherAppsAfterDownload);
                            if(item instanceof VideoResourceItem) {
                                File localCache = RemoteAsyncFileCachingDataSource.getFullyLoadedCacheFile(getContext(), Uri.parse(item.getFileUrl(item.getFullSizeFile().getName())));
                                if(localCache != null) {
                                    String downloadFilename = item.getDownloadFileName(item.getFullSizeFile());
                                    String remoteUri = item.getFileUrl(item.getFullSizeFile().getName());
                                    evt.addFileDetail(item.getName(), remoteUri, downloadFilename, localCache);
                                }
                            } else {
                                String downloadFilename = item.getDownloadFileName(item.getFile(selectedPiwigoFilesizeName));
                                String remoteUri = item.getFileUrl(selectedPiwigoFilesizeName);
                                evt.addFileDetail(item.getName(), remoteUri, downloadFilename);
                            }
                            EventBus.getDefault().post(evt);
                            EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
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
                            } else {
                                FirebaseAnalytics.getInstance(context).logEvent("NoClipMgr", null);
                            }
                            EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), item));
                        }
                    });
                    dialog.show();

                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                    EventBus.getDefault().post(new AlbumItemActionFinishedEvent(getUiHelper().getTrackedRequest(), getModel()));
                }
//                    String downloadFilename = originalVideoFilename.replaceAll(".*/", "").replaceAll("(\\.[^.]*$)", "_" + getModel().getId() + "$1");
//                    String remoteUri = getModel().getFileUrl(getModel().getFullSizeFile().getName());
//                    DownloadFileRequestEvent evt = new DownloadFileRequestEvent(false);
//                    evt.addFileDetail(getModel().getName(), remoteUri, downloadFilename, cachedVideoFile);
//                    EventBus.getDefault().post(evt);
//                    getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.alert_image_download_complete_message));
//
//                } else {
//                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
//                }
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
                boolean factorySettingsAltered = dataSourceFactory.setCachingEnabled(isUseCache());
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

    private boolean isUseCache() {
        // assume have permission until proven otherwise.
        return prefs.getBoolean(getString(R.string.preference_video_cache_enabled_key), getResources().getBoolean(R.bool.preference_video_cache_enabled_default));
    }

    private void configureDatasourceAndPlayerRequestingPermissions(final boolean startPlayback) {

        logStatus("configuring datasource factory with current cache enabled etc settings");

        boolean factorySettingsAltered = dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setCachingEnabled(isUseCache());
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
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
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
            loadControl.resumeBuffering();
            player.prepare(videoSource, false, false);
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
        if (isUseCache()) {
            long maxCacheSizeBytes = 1024 * 1024 * prefs.getInt(getString(R.string.preference_video_cache_maxsize_mb_key), getResources().getInteger(R.integer.preference_video_cache_maxsize_mb_default));
            logStatus("managing the disk cache - max size = " + IOUtils.toNormalizedText(maxCacheSizeBytes));
            try {
                CacheUtils.manageVideoCache(getContext(), maxCacheSizeBytes);
            } catch (IOException e) {
                Crashlytics.logException(e);
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
            timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            setAllowDownload(true);
            cachedVideoFile = cacheFileContent.getCachedDataFile();
            originalVideoFilename = cacheFileContent.getOriginalUri().replace(".*/", "").replace("\\?.*", "");

            if (isVisible()) {
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() != null) {
                            displayItemDetailsControlsBasedOnSessionState();
                            cachedByteCountView.setText(getString(R.string.x_of_y, IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()) ,IOUtils.toNormalizedText(cacheFileContent.getTotalBytes())));
                            timebar.invalidate();
                        }
                    }
                });
            }
        }

        @Override
        public void onRangeAdded(final CachedContent cacheFileContent, long fromVideoPosition, long toVideoPosition, long bytesAddedToRange) {
            bytesDownloaded += bytesAddedToRange;

            if (timebar.getParent() != null) {
                timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            }
            if (isVisible()) {
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() != null) {
                            downloadedByteCountView.setText(IOUtils.toNormalizedText(bytesDownloaded));
                            cachedByteCountView.setText(getString(R.string.x_of_y,IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()), IOUtils.toNormalizedText(cacheFileContent.getTotalBytes())));
                            timebar.invalidate();
                        }
                    }
                });
            }
        }

        @Override
        public void onCacheLoaded(final CachedContent cacheFileContent, long position) {
            if (timebar.getParent() != null) {
                timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
            }
            if (isVisible()) {
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() != null) {
                            downloadedByteCountView.setText(IOUtils.toNormalizedText(bytesDownloaded));
                            cachedByteCountView.setText(getString(R.string.x_of_y,IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()), IOUtils.toNormalizedText(cacheFileContent.getTotalBytes())));
                            timebar.invalidate();
                        }
                    }
                });
            }
        }

        @Override
        public void onDownload(final long bytesCachedInThisRange, final long totalBytes, final long bytesAddedToCache) {
            if (isVisible()) {
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (getContext() != null) {
                            bytesDownloaded += bytesAddedToCache;
                            downloadedByteCountView.setText(IOUtils.toNormalizedText(bytesDownloaded));
                            cachedByteCountView.setText(getString(R.string.x_of_y, IOUtils.toNormalizedText(bytesCachedInThisRange), IOUtils.toNormalizedText(totalBytes)));
                        }
                    }
                });
            }
        }
    }

    private static class ClearCachedContentAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractAlbumVideoItemFragment>> {

        public ClearCachedContentAction(FragmentUIHelper<AbstractAlbumVideoItemFragment> uiHelper) {
            super(uiHelper);
        }

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
                getUiHelper().showOrQueueDialogMessage(new UIHelper.QueuedDialogMessage(R.string.alert_error, getString(R.string.alert_server_error_pattern, err.getStatusCode(), err.getUri()), response, R.string.button_ok));
            } else if (e instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException err = (HttpDataSource.InvalidResponseCodeException) e;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_server_error_pattern, err.responseCode, getModel().getDownloadFileName(getModel().getFullSizeFile())));
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.error_unexpected_error_calling_server));
            }
        }
    }
}