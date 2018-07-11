package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 09/10/17.
 */

public class MemoryTrimmedEvent {
    private final int level;
    private boolean picassoCacheCleared;

    public MemoryTrimmedEvent(int level, boolean cacheCleared) {
        this.level = level;
        this.picassoCacheCleared = picassoCacheCleared;
    }

    public boolean isPicassoCacheCleared() {
        return picassoCacheCleared;
    }

    public int getMemoryLevel() {
        return level;
    }
}
