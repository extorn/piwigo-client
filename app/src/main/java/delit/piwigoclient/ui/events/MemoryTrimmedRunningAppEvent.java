package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 09/10/17.
 */

public class MemoryTrimmedRunningAppEvent {

    private final int level;
    private boolean picassoCacheCleared;

    public MemoryTrimmedRunningAppEvent(int level, boolean cacheCleared) {
        this.level = level;
        this.picassoCacheCleared = cacheCleared;
    }

    public boolean isPicassoCacheCleared() {
        return picassoCacheCleared;
    }

    public int getMemoryLevel() {
        return level;
    }
}
