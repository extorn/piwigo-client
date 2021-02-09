package delit.piwigoclient.ui.events;

public class SlideshowItemPageFinished {
    private int pagerItemIndex;
    private boolean isImmediate;

    public SlideshowItemPageFinished(int pagerIndex) {
        this.pagerItemIndex = pagerIndex;
    }

    /**
     *
     * @param pagerIndex
     * @param isImmediate this should be true if the sending of this event isn't triggered by an async event
     */
    public SlideshowItemPageFinished(int pagerIndex, boolean isImmediate) {
        this.pagerItemIndex = pagerIndex;
        this.isImmediate = isImmediate;
    }

    public int getPagerItemIndex() {
        return pagerItemIndex;
    }

    public boolean isImmediate() {
        return isImmediate;
    }
}
