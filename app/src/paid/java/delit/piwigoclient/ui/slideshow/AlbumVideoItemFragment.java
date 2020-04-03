package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.view.View;

import com.google.android.exoplayer2.Player;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.ui.events.SlideshowItemPageFinished;

public class AlbumVideoItemFragment extends AbstractAlbumVideoItemFragment {

    @Override
    protected Player.EventListener buildNewPlayerEventListener() {
        return new PaidPlayerEventListener();
    }

    @Override
    protected void onViewCreatedAndStateLoaded(View view, Bundle savedInstanceState) {
        setPlayVideoAutomatically(isPlayVideoAutomatically() | AlbumViewPreferences.isAutoDriveSlideshow(prefs, requireContext()));
        setVideoIsPlayingWhenVisible(isVideoIsPlayingWhenVisible() |  AlbumViewPreferences.isAutoDriveSlideshow(prefs, requireContext()));
    }

    class PaidPlayerEventListener extends MyPlayerEventListener {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            super.onPlayerStateChanged(playWhenReady, playbackState);
            if(Player.STATE_ENDED == playbackState && playWhenReady) {
                int itemIdx = getPagerIndex();
                EventBus.getDefault().post(new SlideshowItemPageFinished(itemIdx));
            }
        }
    }
}
