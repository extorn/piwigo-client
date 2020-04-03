package delit.piwigoclient.ui.events;

public class SlideshowItemPageFinished {
    private int pagerItemIndex;

    public SlideshowItemPageFinished(int pagerIndex) {
        this.pagerItemIndex = pagerIndex;
    }

    public int getPagerItemIndex() {
        return pagerItemIndex;
    }
}
