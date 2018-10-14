package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

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
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.IOUtils;

public class AlbumVideoItemFragment extends SlideshowItemFragment<VideoResourceItem> {

    private static final String TAG = "VideoItemFragment";

    private static final String STATE_CACHED_VIDEO_FILENAME = "cachedVideoFileName";
    private static final String STATE_VIDEO_PLAY_AUTOMATICALLY = "videoPlayAutomatically";
    private static final String STATE_VIDEO_IS_PLAYING = "videoIsPlayingWhenVisible";
    private static final String STATE_VIDEO_PLAYBACK_POSITION = "currentVideoPlaybackPosition";
    private static final String STATE_PERMISSION_TO_CACHE_GRANTED = "permissionToCacheToDisk";
    private static final String STATE_CACHED_VIDEO_ORIGINAL_FILENAME = "originalVideoFilename";
    private static final String STATE_PAGE_IS_SHOWING = "pageIsShowing";

    private static final int PERMISSIONS_FOR_DOWNLOAD = 1;
    private static final int PERMISSIONS_FOR_CACHE = 2;

    private SimpleExoPlayer player;
    private RemoteFileCachingDataSourceFactory dataSourceFactory;
    private PausableLoadControl loadControl;
    private CustomImageButton directDownloadButton;

    private File cachedVideoFile; // Used when downloading (copying) video to user accessible area
    private String originalVideoFilename; // Used for generating downloaded filename
    private boolean permissionToCache;

    private long videoPlaybackPosition;
    private boolean videoIsPlayingWhenVisible;
    private boolean playVideoAutomatically;

    private boolean pageIsShowing;

    private TextView downloadedByteCountView;
    private TextView cachedByteCountView;
    private PlayerView simpleExoPlayerView;
    private CustomCacheListener cacheListener;
    private DefaultTrackSelector trackSelector;

    public AlbumVideoItemFragment() {
    }

    public static Bundle buildArgs(VideoResourceItem galleryItem, int albumResourceItemIdx, int albumResourceItemCount, int totalResourceItemCount, boolean startPlaybackOnFragmentDisplay) {
        Bundle args = SlideshowItemFragment.buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount);
        args.putBoolean(STATE_VIDEO_PLAY_AUTOMATICALLY, startPlaybackOnFragmentDisplay);
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
        if (pageIsShowing) {
            configureDatasourceAndPlayerRequestingPermissions(playVideoAutomatically && videoIsPlayingWhenVisible);
        }
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
            Log.d(TAG, String.format("%1$s - %2$b : %3$s", getModel() != null ? getModel().getId() : "UNKNOWN", pageIsShowing, msg));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        logStatus("saving state");
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_VIDEO_PLAY_AUTOMATICALLY, playVideoAutomatically);
        outState.putBoolean(STATE_VIDEO_IS_PLAYING, videoIsPlayingWhenVisible);
        outState.putBoolean(STATE_PERMISSION_TO_CACHE_GRANTED, permissionToCache);
        outState.putString(STATE_CACHED_VIDEO_FILENAME, cachedVideoFile != null ? cachedVideoFile.getAbsolutePath() : null);
        outState.putString(STATE_CACHED_VIDEO_ORIGINAL_FILENAME, originalVideoFilename);
        outState.putBoolean(STATE_PAGE_IS_SHOWING, pageIsShowing);
        outState.putLong(STATE_VIDEO_PLAYBACK_POSITION, videoPlaybackPosition);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        logStatus("creating all the essentials");
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        String userAgent = Util.getUserAgent(getContext(), getActivity().getApplicationContext().getPackageName());
        cacheListener = new CustomCacheListener();
        dataSourceFactory = new RemoteFileCachingDataSourceFactory(getContext(), bandwidthMeter, cacheListener, cacheListener, userAgent);
        PausableLoadControl.Listener loadControlPauseListener = dataSourceFactory.getLoadControlPauseListener();

        loadControl = new PausableLoadControl();
        loadControl.setListener(loadControlPauseListener);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);
        setAllowDownload(false);

        if (getArguments() != null) {
            logStatus("loading arguments");
            playVideoAutomatically = getArguments().getBoolean(STATE_VIDEO_PLAY_AUTOMATICALLY);
            videoIsPlayingWhenVisible = playVideoAutomatically;
        }
        if (savedInstanceState != null) {
            logStatus("loading saved state");
            playVideoAutomatically = savedInstanceState.getBoolean(STATE_VIDEO_PLAY_AUTOMATICALLY);
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
            pageIsShowing = savedInstanceState.getBoolean(STATE_PAGE_IS_SHOWING);
        }
        logStatus("view state restored");
        displayItemDetailsControlsBasedOnSessionState();
    }

    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        hideProgressIndicator();
        logStatus("Creating item content");
        directDownloadButton = container.findViewById(R.id.slideshow_resource_action_direct_download);

        View view = inflater.inflate(R.layout.exo_player_viewer_custom, container, false);
        simpleExoPlayerView = view.findViewById(R.id.slideshow_video_player);

        downloadedByteCountView = simpleExoPlayerView.findViewById(R.id.exo_downloaded);
        cachedByteCountView = simpleExoPlayerView.findViewById(R.id.exo_cached_summary);


        CustomExoPlayerTouchListener customTouchListener = new CustomExoPlayerTouchListener(simpleExoPlayerView);
        simpleExoPlayerView.setOnTouchListener(customTouchListener);
        logStatus("finished created item content");
        return view;
    }

    @Override
    protected void configureItemContent(@Nullable View itemContentView, final VideoResourceItem model, @Nullable Bundle savedInstanceState) {
        super.configureItemContent(itemContentView, model, savedInstanceState);
        directDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadItem(model);
            }
        });

        CustomExoPlayerTimeBar timebar = itemContentView.findViewById(R.id.exo_progress);
        cacheListener.setTimebar(timebar);

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(getContext()), trackSelector, loadControl);

        simpleExoPlayerView.setPlayer(player);
    }

    @Override
    public void onPageDeselected() {
        if (!pageIsShowing) {
            return;
        }
        logStatus("transitioning to page not showing");
        super.onPageDeselected();
        pageIsShowing = false;
        stopVideoDownloadAndPlay();
    }

    @Override
    public void onPageSelected() {
        if (pageIsShowing) {
            return;
        }
        super.onPageSelected();
        pageIsShowing = true;
        logStatus("page selected");
        if (isAdded()) {
            configureDatasourceAndPlayerRequestingPermissions(playVideoAutomatically && videoIsPlayingWhenVisible);
        }
    }

    private void clearCacheAndRestartVideo() {
        stopVideoDownloadAndPlay();
        player.stop(); // this is terminal.
        videoPlaybackPosition = 0; // ensure it starts at the beginning again
        try {
            CacheUtils.deleteCachedContent(getContext(), getModel().getFullSizeFile().getUrl());
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
        directDownloadButton.setVisibility(isAllowDownload() ? View.VISIBLE : View.GONE);
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
                    try {
                        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File toFile = new File(downloadsFolder, originalVideoFilename.replaceAll(".*/", "").replaceAll("(\\.[^.]*$)", "_" + getModel().getId() + "$1"));
                        IOUtils.copy(cachedVideoFile, toFile);
                        onGetResource(new PiwigoResponseBufferingHandler.UrlToFileSuccessResponse(0, getModel().getFullSizeFile().getUrl(), toFile));
                        getUiHelper().showToast(getString(R.string.alert_image_download_complete_message));
                    } catch (IOException e) {
                        Crashlytics.logException(e);
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, String.format(getString(R.string.alert_error_unable_to_copy_file_from_cache_pattern), e.getMessage()));
                    }
                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                }
            } else if (getUiHelper().getPermissionsNeededReason() == PERMISSIONS_FOR_CACHE) {
                if (event.areAllPermissionsGranted()) {
                    permissionToCache = true;
                    logStatus("All permissions granted - configure player now!");
                } else {
                    logStatus("Not all permissions granted - tweak datasource factory settings and configure player now!");
                    permissionToCache = false;
                    if (pageIsShowing) {
                        getUiHelper().showToast(R.string.video_caching_disabled_warning);
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

        logStatus("configuring datasource factory with current cache enabled etc setttings");

        boolean factorySettingsAltered = dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));
        factorySettingsAltered |= dataSourceFactory.setCachingEnabled(isUseCache());
        if (factorySettingsAltered) {
            logStatus("Need to create a new datasource");
            stopVideoDownloadAndPlay();
            player.stop();
        }
        videoIsPlayingWhenVisible = startPlayback;

        if (dataSourceFactory.isCachingEnabled()) {
            logStatus("configuring datasource and player - caching enabled - check permissions first");
            getUiHelper().setPermissionsNeededReason(PERMISSIONS_FOR_CACHE);
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
        } else {
            logStatus("configuring datasource and player - no caching enabled - do now!");
            configurePlayer(videoIsPlayingWhenVisible);
            if (pageIsShowing) {
                getUiHelper().showToast(R.string.video_caching_disabled_warning);
            }
        }
    }

    private void configurePlayer(boolean startPlaybackImmediatelyIfVisibleToUser) {

        if (player.getPlaybackState() == Player.STATE_IDLE) {
            logStatus("configuring the player with a brand new datasource from factory");
            Uri videoUri = Uri.parse(getModel().getFullSizeFile().getUrl());
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if (connectionPrefs.isForceHttps(prefs, getContext()) && videoUri.getScheme().equalsIgnoreCase("http")) {
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
        player.setPlayWhenReady(startPlaybackImmediatelyIfVisibleToUser && pageIsShowing);
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
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_tidying_video_cache));
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
                            cachedByteCountView.setText(IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()) + " / " + IOUtils.toNormalizedText(cacheFileContent.getTotalBytes()));
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
                            cachedByteCountView.setText(IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()) + " / " + IOUtils.toNormalizedText(cacheFileContent.getTotalBytes()));
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
                            cachedByteCountView.setText(IOUtils.toNormalizedText(cacheFileContent.getCachedBytes()) + " / " + IOUtils.toNormalizedText(cacheFileContent.getTotalBytes()));
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
                            cachedByteCountView.setText(IOUtils.toNormalizedText(bytesCachedInThisRange) + " / " + IOUtils.toNormalizedText(totalBytes));
                        }
                    }
                });
            }
        }
    }

    private class CustomExoPlayerTouchListener extends CustomClickTouchListener {
        public CustomExoPlayerTouchListener(View linkedView) {
            super(linkedView);
        }

        @Override
        public void onLongClick() {
            if (!isUseCache()) {
                stopVideoDownloadAndPlay();
                player.stop(); // this is terminal.
                videoPlaybackPosition = 0; // ensure it starts at the beginning again
                configureDatasourceAndPlayerRequestingPermissions(videoIsPlayingWhenVisible);
            }
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.alert_clear_cached_content), R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if (Boolean.TRUE == positiveAnswer) {
                        clearCacheAndRestartVideo();
                    }
                }
            });
        }
    }
}