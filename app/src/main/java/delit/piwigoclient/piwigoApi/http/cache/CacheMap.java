package delit.piwigoclient.piwigoApi.http.cache;

import java.util.LinkedHashMap;
import java.util.Map;

import cz.msebera.android.httpclient.client.cache.HttpCacheEntry;

public final class CacheMap extends LinkedHashMap<String, HttpCacheEntry> {

    private static final long serialVersionUID = -7750025207539768511L;

    private int maxEntries;

    CacheMap(final int maxEntries) {
        this(20, maxEntries);
    }

    CacheMap(final CacheMap otherMap) {
        this(otherMap.size(), otherMap.getMaxEntries());
        putAll(otherMap);
    }

    CacheMap(final int initialCapacity, final int maxEntries) {
        super(initialCapacity, 0.75f, true);
        this.maxEntries = maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(final Map.Entry<String, HttpCacheEntry> eldest) {
        return size() > this.maxEntries;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public boolean needsResize() {
        return this.size() > maxEntries;
    }

}
