package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 03/04/18.
 */

public class SlideshowSizeUpdateEvent {
    private final int loadedResources;
    private final int totalResources;

    public SlideshowSizeUpdateEvent(int loadedResources, int totalResources) {
        this.loadedResources = loadedResources;
        this.totalResources = totalResources;
    }

    public int getLoadedResources() {
        return loadedResources;
    }

    public int getTotalResources() {
        return totalResources;
    }
}
