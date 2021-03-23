package delit.piwigoclient.ui.slideshow.item;

import android.os.Bundle;
import android.view.View;

import com.google.android.exoplayer2.Player;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.business.SlideshowViewPreferences;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.SlideshowItemPageFinished;

public class AlbumVideoItemFragment<F extends AbstractAlbumVideoItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends VideoResourceItem> extends AbstractAlbumVideoItemFragment<F,FUIH,T> {

    @Override
    protected Player.EventListener buildNewPlayerEventListener() {
        return new PaidPlayerEventListener();
    }

    @Override
    protected void onViewCreatedAndStateLoaded(View view, Bundle savedInstanceState) {
        setPlayVideoAutomatically(isPlayVideoAutomatically() | SlideshowViewPreferences.isAutoDriveSlideshow(prefs, requireContext()));
        setVideoIsPlayingWhenVisible(isVideoIsPlayingWhenVisible() |  SlideshowViewPreferences.isAutoDriveSlideshow(prefs, requireContext()));
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
