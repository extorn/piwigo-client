package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.business.video.CachedContent;
import delit.piwigoclient.business.video.CustomExoPlayerTimeBar;
import delit.piwigoclient.business.video.PausableLoadControl;
import delit.piwigoclient.business.video.RemoteAsyncFileCachingDataSource;
import delit.piwigoclient.business.video.RemoteFileCachingDataSourceFactory;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.IOUtils;

public class AlbumVideoItemFragment extends SlideshowItemFragment<VideoResourceItem> {

    private static final String CURRENT_VIDEO_PLAYBACK_POSITION = "currentVideoPlaybackPosition";
    private static final String CACHED_VIDEO_FILENAME = "cachedVideoFileName";
    private static final String STATE_START_ON_RESUME = "startOnResume";
    private static final String STATE_CONTINUE_PLAYBACK = "continuePlayback";
    private static final String STATE_START_VIDEO_ON_PERMISSIONS_GRANTED = "startVideoWhenPermissionsGranted";
    private static final int PERMISSIONS_FOR_DOWNLOAD = 1;
    private static final int PERMISSIONS_FOR_CACHE = 2;
    private SimpleExoPlayer player;
    private RemoteFileCachingDataSourceFactory dataSourceFactory;
    private PausableLoadControl loadControl;
    private long seekToPosition;
    private CustomImageButton directDownloadButton;
    private boolean resetPlayerDatasource;
    private File cachedVideoFile;
    private boolean permissionToCache = true;
    private boolean startOnResume;
    private boolean continuePlayback = true;

    // No need to store this as it's used once and forgotten.
    private transient boolean startImmediately;
    private boolean startVideoWhenPermissionsGranted;
    private boolean pageIsShowing;

    public AlbumVideoItemFragment() {
    }

    public static Bundle buildArgs(VideoResourceItem galleryItem, long albumResourceItemIdx, long albumResourceItemCount, long totalResourceItemCount, boolean startPlaybackOnFragmentDisplay) {
        Bundle args = SlideshowItemFragment.buildArgs(galleryItem, albumResourceItemIdx, albumResourceItemCount, totalResourceItemCount);
        args.putBoolean(STATE_START_ON_RESUME, startPlaybackOnFragmentDisplay);
        return args;
    }

    @Override
    public void onDestroyView() {
        cleanupVideoResources();
        manageCache();
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        stopVideoDownloadAndPlay();
        super.onPause();
    }

    @Override
    public void onResume() {
        if(pageIsShowing) {
            configureDatasourceAndPlayerRequestingPermissions(startImmediately || (startOnResume && continuePlayback));
            startImmediately = false;
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_START_ON_RESUME, startOnResume);
        outState.putBoolean(STATE_CONTINUE_PLAYBACK, continuePlayback);
        outState.putBoolean(STATE_START_VIDEO_ON_PERMISSIONS_GRANTED, startVideoWhenPermissionsGranted);
        if (player != null) {
            outState.putLong(CURRENT_VIDEO_PLAYBACK_POSITION, player.getCurrentPosition());
            outState.putString(CACHED_VIDEO_FILENAME, cachedVideoFile != null ? cachedVideoFile.getAbsolutePath() : null);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            startVideoWhenPermissionsGranted = savedInstanceState.getBoolean(STATE_START_VIDEO_ON_PERMISSIONS_GRANTED);
        }
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setAllowDownload(false);
        return view;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if(getArguments() != null) {
            // if true, playback will start immediately. Otherwise user will have to push play.
            continuePlayback = getArguments().getBoolean(STATE_CONTINUE_PLAYBACK);
        }
        if (savedInstanceState != null) {
            startVideoWhenPermissionsGranted = savedInstanceState.getBoolean(STATE_START_VIDEO_ON_PERMISSIONS_GRANTED);
            seekToPosition = savedInstanceState.getLong(CURRENT_VIDEO_PLAYBACK_POSITION);
            String cachedVideoFilename = savedInstanceState.getString(CACHED_VIDEO_FILENAME);
            if(cachedVideoFilename == null) {
                cachedVideoFile = null;
            } else {
                cachedVideoFile = new File(cachedVideoFilename);
            }
            startOnResume = savedInstanceState.getBoolean(STATE_START_ON_RESUME);
            continuePlayback = savedInstanceState.getBoolean(STATE_CONTINUE_PLAYBACK);
        }
        displayItemDetailsControlsBasedOnSessionState();
    }

    @Nullable
    @Override
    public View createItemContent(LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState, final VideoResourceItem model) {
        pageIsShowing = false;
        hideProgressIndicator();

        directDownloadButton = container.findViewById(R.id.slideshow_resource_action_direct_download);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_file_download_black_24px).into(directDownloadButton);
        directDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadItem(getModel());
            }
        });

        LayoutInflater layoutInflater = getLayoutInflater();
        PlayerView simpleExoPlayerView = (PlayerView) layoutInflater.inflate(R.layout.exo_player_viewer_custom, container, false);

        CustomExoPlayerTimeBar timebar = simpleExoPlayerView.findViewById(R.id.exo_progress);

        simpleExoPlayerView.setPlayer(buildPlayer(model, timebar));

        CustomExoPlayerTouchListener customTouchListener = new CustomExoPlayerTouchListener(simpleExoPlayerView);
        simpleExoPlayerView.setOnTouchListener(customTouchListener);
        return simpleExoPlayerView;
    }

    private SimpleExoPlayer buildPlayer(VideoResourceItem model, CustomExoPlayerTimeBar timebar) {

        resetPlayerDatasource = true;
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);
// Produces DataSource instances through which media data is loaded.
        String userAgent = Util.getUserAgent(getContext(), getActivity().getApplicationContext().getPackageName());
        //DataSource.Factory dataSourceFactory = pkg AsyncHttpClientDataSourceFactory(getContext(), userAgent, bandwidthMeter);
        CustomCacheListener cacheListener = new CustomCacheListener(timebar);
        dataSourceFactory = new RemoteFileCachingDataSourceFactory(getContext(), bandwidthMeter, cacheListener, userAgent);
        PausableLoadControl.Listener loadControlPauseListener = dataSourceFactory.getLoadControlPauseListener();

        // 2. Create the player
        loadControl = new PausableLoadControl();
        loadControl.setListener(loadControlPauseListener);

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(getContext()), trackSelector, loadControl);

        return player;
    }

    @Override
    public void onPageDeselected() {
        super.onPageDeselected();
        pageIsShowing = false;
        stopVideoDownloadAndPlay();
    }

    @Override
    public void onPageSelected() {
        super.onPageSelected();
        pageIsShowing = true;
        if(isAdded()) {
            configureDatasourceAndPlayerRequestingPermissions(startImmediately || (startOnResume && continuePlayback));
            startImmediately = false;
        }
    }

    private void clearCacheAndRestartVideo() {
        continuePlayback = player.getPlayWhenReady();
        stopVideoDownloadAndPlay();
        player.stop();
        resetPlayerDatasource = true;
        seekToPosition = 0;
        try {
            CacheUtils.deleteCachedContent(getContext(), getModel().getFullSizeFile().getUrl());
            // now update stored state and UI display
            setAllowDownload(false);
            displayItemDetailsControlsBasedOnSessionState();
        } catch (IOException e) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_clear_cached_content));
        }
        configureDatasourceAndPlayerRequestingPermissions(continuePlayback);
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PermissionsWantedResponse event) {

        if (getUiHelper().completePermissionsWantedRequest(event)) {
            if(getUiHelper().getPermissionsNeededReason() == PERMISSIONS_FOR_DOWNLOAD) {
                if (event.areAllPermissionsGranted()) {
                    try {
                        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File toFile = new File(downloadsFolder, cachedVideoFile.getName());
                        IOUtils.copy(cachedVideoFile, toFile);
                        onGetResource(new PiwigoResponseBufferingHandler.UrlToFileSuccessResponse(0, getModel().getFullSizeFile().getUrl(), toFile));
                        getUiHelper().showToast(getString(R.string.alert_image_download_complete_message));
                    } catch (IOException e) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, String.format(getString(R.string.alert_error_unable_to_copy_file_from_cache_pattern), e.getMessage()));
                    }
                } else {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_download_cancelled_insufficient_permissions));
                }
            } else if(getUiHelper().getPermissionsNeededReason() == PERMISSIONS_FOR_CACHE) {
                if (event.areAllPermissionsGranted()) {
                    configurePlayer(startVideoWhenPermissionsGranted);
                } else {
                    permissionToCache = false;
                    dataSourceFactory.setCachingEnabled(false);
                    dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
                    dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));
                    configurePlayer(startVideoWhenPermissionsGranted);
                }
            } else {
                throw new IllegalStateException("Permission required for what reason?");
            }
        }
    }

    private boolean isUseCache() {
        // assume have permission until proven otherwise.
        return permissionToCache && prefs.getBoolean(getString(R.string.preference_video_cache_enabled_key), getResources().getBoolean(R.bool.preference_video_cache_enabled_default));
    }

    private void configureDatasourceAndPlayerRequestingPermissions(final boolean startPlayback) {

        dataSourceFactory.setCachingEnabled(isUseCache());
        dataSourceFactory.setRedirectsAllowed(prefs.getBoolean(getString(R.string.preference_server_connection_allow_redirects_key), getResources().getBoolean(R.bool.preference_server_connection_allow_redirects_default)));
        dataSourceFactory.setMaxRedirects(prefs.getInt(getString(R.string.preference_server_connection_max_redirects_key), getResources().getInteger(R.integer.preference_server_connection_max_redirects_default)));

        if(dataSourceFactory.isCachingEnabled()) {
            startVideoWhenPermissionsGranted = startPlayback;
            getUiHelper().setPermissionsNeededReason(PERMISSIONS_FOR_CACHE);
            getUiHelper().runWithExtraPermissions(this, Build.VERSION_CODES.BASE, Build.VERSION_CODES.KITKAT, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.alert_write_permission_needed_for_video_caching));
        } else {
            configurePlayer(startPlayback);
        }
    }

    private void configurePlayer(boolean startPlayback) {
        if(resetPlayerDatasource) {
            Uri videoUri = Uri.parse(getModel().getFullSizeFile().getUrl());
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
            if(connectionPrefs.isForceHttps(prefs, getContext()) && videoUri.getScheme().equalsIgnoreCase("http")) {
                videoUri = videoUri.buildUpon().scheme("https").build();
            }
            ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(dataSourceFactory);
            factory.setExtractorsFactory(extractorsFactory);
            ExtractorMediaSource videoSource = factory.createMediaSource(videoUri);
            if (player.getCurrentPosition() != seekToPosition && seekToPosition >= 0) {
                player.seekTo(seekToPosition);
            }
            loadControl.resumeBuffering();
            player.prepare(videoSource, false, false);
            player.setPlayWhenReady(startPlayback && pageIsShowing);
            resetPlayerDatasource = false;
        } else {
            loadControl.resumeBuffering();
            player.setPlayWhenReady(startPlayback && pageIsShowing);
        }
    }

    public void onManualResume() {
        startImmediately = continuePlayback;
    }

    private void startVideoDownloadAndPlay() {
        if(isAdded()) {
            configureDatasourceAndPlayerRequestingPermissions(continuePlayback);
        } else {
            startImmediately = true;
        }
    }

    private void stopVideoDownloadAndPlay() {
        if (player != null) {
            continuePlayback = player.getPlayWhenReady();
            seekToPosition = player.getCurrentPosition();
            player.setPlayWhenReady(false);
            loadControl.pauseBuffering();
        }
    }

    private void cleanupVideoResources() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
            dataSourceFactory = null;
        }
    }

    private void manageCache() {
        if(isUseCache()) {
            long maxCacheSizeBytes = 1024 * 1024 * prefs.getInt(getString(R.string.preference_video_cache_maxsize_mb_key), getResources().getInteger(R.integer.preference_video_cache_maxsize_mb_default));
            try {
                CacheUtils.manageVideoCache(getContext(), maxCacheSizeBytes);
            } catch (IOException e) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_tidying_video_cache));
            }
        }
    }

    private class CustomCacheListener implements RemoteAsyncFileCachingDataSource.CacheListener {

        private final CustomExoPlayerTimeBar timebar;

        public CustomCacheListener(CustomExoPlayerTimeBar timebar) {
            this.timebar = timebar;
        }

        @Override
        public void onFullyCached(final CachedContent cacheContent) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    timebar.updateCachedContent(cacheContent, cacheContent.getTotalBytes());
                    setAllowDownload(true);
                    displayItemDetailsControlsBasedOnSessionState();
                    cachedVideoFile = cacheContent.getCachedDataFile();
                }
            });
        }

        @Override
        public void onRangeAdded(final CachedContent cacheFileContent, long fromVideoPosition, long toVideoPosition) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
                }
            });
        }

        @Override
        public void onCacheLoaded(final CachedContent cacheFileContent, long position) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    timebar.updateCachedContent(cacheFileContent, cacheFileContent.getTotalBytes());
                }
            });
        }
    }

    private class CustomExoPlayerTouchListener extends CustomClickTouchListener {
        public CustomExoPlayerTouchListener(View linkedView) {
            super(linkedView);
        }

        @Override
        public void onLongClick() {
            if(!isUseCache()) {
                return;
            }
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_information, getString(R.string.alert_clear_cached_content), R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
                @Override
                public void onDismiss(AlertDialog dialog) {

                }

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if(Boolean.TRUE == positiveAnswer) {
                        clearCacheAndRestartVideo();
                    }
                }
            });
        }
    }
}