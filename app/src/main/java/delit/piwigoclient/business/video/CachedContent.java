package delit.piwigoclient.business.video;

import android.support.annotation.NonNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * Created by gareth on 30/06/17.
 */

public class CachedContent implements Serializable {

    private static final long serialVersionUID = -5756180868306113659L;
    private String cacheDataFilename;
    private Date lastAccessed;
    private ArrayList<SerializableRange> cachedRanges = new ArrayList<>();
    private long totalBytes;
    private transient File persistTo;

    public void setCacheDataFilename(String cacheDataFilename) {
        this.cacheDataFilename = cacheDataFilename;
    }

    public CachedContent() {
        lastAccessed = new Date();
    }

    public String getCacheDataFilename() {
        return cacheDataFilename;
    }

    public File getCachedDataFile() {
        return new File(persistTo.getParent(), cacheDataFilename);
    }

    public void addRange(long lower, long upper) {
        lastAccessed = new Date();
        boolean merged = false;
        for(SerializableRange range : cachedRanges) {
            if(range.mergeWithRange(lower, upper)) {
                merged = true;
            }
        }
        if(!merged) {
            cachedRanges.add(new SerializableRange(lower, upper));
        }
        Collections.sort(cachedRanges);

        for(int i = 0; i < cachedRanges.size() - 1; i++) {
            SerializableRange first = cachedRanges.get(i);
            SerializableRange second = cachedRanges.get(i+1);
            if(second.lower <= first.upper + 1) {
                if(first.mergeWithRange(second)) {
                    cachedRanges.remove(i+1);
                    i--; // check the next item with this merged one.
                }
            }
        }
    }

    public Date getLastAccessed() {
        return lastAccessed;
    }

    public SerializableRange getRangeContaining(long value) {
        lastAccessed = new Date();
        for(SerializableRange range : cachedRanges) {
            if(range.contains(value)) {
                return range;
            }
        }
        return null;
    }

    public boolean isComplete() {
        if(cachedRanges.size() == 1) {
            SerializableRange range = cachedRanges.get(0);
            return range.bytes == totalBytes;
        }
        return false;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setPersistTo(File persistTo) {
        this.persistTo = persistTo;
    }

    public File getPersistTo() {
        return persistTo;
    }

    /**
     * Call if the cache data file didn't contain the data for whatever reason - i.e. out of sync.
     */
    public void clear() {
        cachedRanges.clear();
        totalBytes = 0;
    }

    public class SerializableRange implements Serializable, Comparable<SerializableRange> {

        private static final long serialVersionUID = -2497952683249597933L;
        private long upper;
        private long lower;
        private long bytes;

        private SerializableRange(long lower, long upper) {
            this.lower = lower;
            this.upper = upper;
            bytes = upper - lower;
        }

        public long getBytes() {
            return bytes;
        }

        public boolean contains(long value) {
            return value < upper && value >= lower;
        }

        @Override
        public int compareTo(@NonNull SerializableRange o) {
            long difference = this.lower - o.lower;
            return difference > 0 ? 1 : difference < 0 ? -1 : 0;
        }

        public boolean mergeWithRange(SerializableRange other) {
            return mergeWithRange(other.lower, other.upper);
        }

        public boolean mergeWithRange(long lower, long upper) {
            boolean merged = false;
            if((this.upper >= lower - 1 && this.lower <= lower)
                    || (upper + 1 >= this.lower && lower <= this.lower)) {
                // can merge
                this.lower = Math.min(this.lower, lower);
                this.upper = Math.max(this.upper, upper);
                this.bytes = this.upper - this.lower;
                merged = true;
            }
            return merged;
        }

        public long available(long position) {
            return (this.upper - position);
        }
    }
}
