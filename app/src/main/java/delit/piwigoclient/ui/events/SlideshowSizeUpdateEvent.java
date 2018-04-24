package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 03/04/18.
 */

public class SlideshowSizeUpdateEvent {
    private final int loadedResources;
    private final long totalResources;

    public SlideshowSizeUpdateEvent(int loadedResources, long totalResources) {
        this.loadedResources = loadedResources;
        this.totalResources = totalResources;
    }

    public int getLoadedResources() {
        return loadedResources;
    }

    public long getTotalResources() {
        return totalResources;
    }
}
