package delit.piwigoclient.ui.events;

import delit.libs.ui.events.SingleUseEvent;

public class SlideshowItemRefreshRequestEvent extends SingleUseEvent {
    private final int slideshowPageIdx;

    public SlideshowItemRefreshRequestEvent(int slideshowPageIdx) {
        this.slideshowPageIdx = slideshowPageIdx;
    }

    public int getSlideshowPageIdx() {
        return slideshowPageIdx;
    }
}
