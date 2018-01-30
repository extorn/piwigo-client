package delit.piwigoclient.ui.slideshow;

import android.Manifest;
import android.app.AlertDialog;
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
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import delit.piwigoclient.R;
import delit.piwigoclient.business.video.CacheUtils;
import delit.piwigoclient.business.video.CustomHttpDataSourceFactory;
import delit.piwigoclient.business.video.HttpClientBasedHttpDataSource;
import delit.piwigoclient.business.video.PausableLoadControl;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.CustomClickTouchListener;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.trackable.PermissionsWantedResponse;
import delit.piwigoclient.util.IOUtils;

public class AlbumVideoItemFragment extends SlideshowItemFragment<VideoResourceItem> {

    public static final String CURRENT_VIDEO_PLAYBACK_POSITION = "currentVideoPlaybackPosition";
    private static final String CACHED_VIDEO_FILENAME = "cachedVideoFileName";
    private static final String STATE_START_ON_RESUME = "startOnResume";
    private static final String STATE_CONTINUE_PLAYBACK = "continuePlayback";
    private static final String STATE_START_VIDEO_ON_PERMISSIONS_GRANTED = "startVideoWhenPermissionsGranted";
    private static final int PERMISSIONS_FOR_DOWNLOAD = 1;
    private static final int PERMISSIONS_FOR_CACHE = 2;
    private SimpleExoPlayer player;
    private CustomHttpDataSourceFactory dataSourceFactory;
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

    public AlbumVideoItemFragment() {
            setAllowDownload(false);
    }

    public static AlbumVideoItemFragment newInstance(VideoResourceItem galleryItem, boolean startPlaybackOnFragmentDisplay) {
        AlbumVideoItemFragment fragment = new AlbumVideoItemFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_GALLERY_ITEM, galleryItem);
        args.putBoolean(STATE_START_ON_RESUME, startPlaybackOnFragmentDisplay);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDetach() {
        cleanupVideoResources();
        manageCache();
        super.onDetach();
    }

    @Override
    public void onPause() {
        stopVideoDownloadAndPlay();
        super.onPause();
    }

    @Override
    public void onResume() {
        configureDatasourceAndPlayerRequestingPermissions(startImmediately || (startOnResume && continuePlayback));
        startImmediately = false;
        super.onResume();
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

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
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

        hideProgressIndicator();

        directDownloadButton = container.findViewById(R.id.slideshow_resource_action_direct_download);
        directDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDownloadItem(getModel());
            }
        });

        if(player == null) {
            buildPlayer(model);
        }

        SimpleExoPlayerView simpleExoPlayerView = new SimpleExoPlayerView(getContext());

        simpleExoPlayerView.setPlayer(player);

        simpleExoPlayerView.setOnTouchListener(new CustomClickTouchListener(getContext()) {
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
        });
        return simpleExoPlayerView;
    }

    private SimpleExoPlayer buildPlayer(VideoResourceItem model) {

        resetPlayerDatasource = true;
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);
// Produces DataSource instances through which media data is loaded.
        String userAgent = Util.getUserAgent(getContext(), getActivity().getApplicationContext().getPackageName());
        //DataSource.Factory dataSourceFactory = pkg AsyncHttpClientDataSourceFactory(getContext(), userAgent, bandwidthMeter);
        dataSourceFactory = new CustomHttpDataSourceFactory(getContext(), userAgent, bandwidthMeter, new HttpClientBasedHttpDataSource.CacheListener() {

            @Override
            public void onFullyCached(final File cacheContent) {
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        setAllowDownload(true);
                        displayItemDetailsControlsBasedOnSessionState();
                        cachedVideoFile = cacheContent;
                    }
                });
            }
        });

        // 2. Create the player
        loadControl = new PausableLoadControl();
        loadControl.setSrcUri(model.getFullSizeFile().getUrl());

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(getContext()), trackSelector, loadControl);
        return player;
    }

    @Override
    public void onPageDeselected() {
        super.onPageDeselected();
        stopVideoDownloadAndPlay();
    }

    @Override
    public void onPageSelected() {
        super.onPageSelected();
        startVideoDownloadAndPlay();
    }

    private void clearCacheAndRestartVideo() {
        continuePlayback = player.getPlayWhenReady();
        stopVideoDownloadAndPlay();
        player.stop();
        resetPlayerDatasource = true;
        seekToPosition = 0;
        while(player.getPlaybackState() != Player.STATE_READY) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // do nothing.
            }
        }
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
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_image_download_title, getString(R.string.alert_image_download_complete_message));
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
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            ExtractorMediaSource videoSource = new ExtractorMediaSource(Uri.parse(getModel().getFullSizeFile().getUrl()),
                    dataSourceFactory, extractorsFactory, null, null);
            if (player.getCurrentPosition() != seekToPosition && seekToPosition >= 0) {
                player.seekTo(seekToPosition);
            }
            loadControl.resumeBuffering();
            player.prepare(videoSource, false, false);
            player.setPlayWhenReady(startPlayback);
            resetPlayerDatasource = false;
        } else {
            loadControl.resumeBuffering();
            player.setPlayWhenReady(startPlayback);
        }
    }

    public void onManualResume() {
        startImmediately = continuePlayback;
    }

    public void startVideoDownloadAndPlay() {
        if(isAdded()) {
            configureDatasourceAndPlayerRequestingPermissions(continuePlayback);
        } else {
            startImmediately = true;
        }
    }

    public void stopVideoDownloadAndPlay() {
        if (player != null) {
            continuePlayback = player.getPlayWhenReady();
            seekToPosition = player.getCurrentPosition();
            player.setPlayWhenReady(false);
            loadControl.pauseBuffering();
        }
    }

    public void cleanupVideoResources() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
            dataSourceFactory = null;
        }
    }

    public void manageCache() {
        if(isUseCache()) {
            long maxCacheSizeBytes = 1024 * 1024 * prefs.getInt(getString(R.string.preference_video_cache_maxsize_mb_key), getResources().getInteger(R.integer.preference_video_cache_maxsize_mb_default));
            try {
                CacheUtils.manageVideoCache(getContext(), maxCacheSizeBytes);
            } catch (IOException e) {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_tidying_video_cache));
            }
        }
    }
}