/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package delit.libs.http.cache;

import android.os.Handler;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import cz.msebera.android.httpclient.annotation.ThreadSafe;
import cz.msebera.android.httpclient.client.cache.HttpCacheEntry;
import cz.msebera.android.httpclient.client.cache.HttpCacheStorage;
import cz.msebera.android.httpclient.client.cache.HttpCacheUpdateCallback;
import cz.msebera.android.httpclient.client.cache.Resource;
import cz.msebera.android.httpclient.impl.client.cache.CacheConfig;
import cz.msebera.android.httpclient.impl.client.cache.FileResource;
import cz.msebera.android.httpclient.util.Args;
import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;

/**
 * {@link HttpCacheStorage} implementation capable of deallocating resources associated with
 * the cache entries. This cache keeps track of cache entries using
 * {@link java.lang.ref.PhantomReference} and maintains a collection of all resources that
 * are no longer in use. The cache, however, does not automatically deallocates associated
 * resources by invoking {@link Resource#dispose()} method. The cache can be
 * permanently shut down using {@link #shutdown()} method. All resources associated with
 * the entries used by the cache will be deallocated.
 * <p>
 * This {@link HttpCacheStorage} implementation is intended for use with {@link FileResource}
 * and similar.
 *
 * @since 4.1
 */
@ThreadSafe
public class RestartableManagedHttpCacheStorage implements HttpCacheStorage, Closeable {

    public static final String DEFAULT_CACHE_FILENAME = "overall_cache_index.dat";
    private static final String TAG = "RestartableCache";
    public static final int MAX_EXPECTED_CACHE_SIZE = 1024 * 2048;  // 2MB
    private final File cacheFolder;
    private final ReferenceQueue<HttpCacheEntry> morque;
    private final Set<ResourceReference> resources;
    private final AtomicBoolean active;
    private String cacheFilename;
    private CacheMap entries; // not final because a resize might force a refresh
    private long unsavedUpdates;
    private long lastSavedAtMillis;
    private long minPreferredSaveInterval = 5 * 60 * 1000; // 5 minutes

    public RestartableManagedHttpCacheStorage(File cacheFolder, final CacheConfig config, Handler h) {
        this(cacheFolder, DEFAULT_CACHE_FILENAME, config, h);
    }

    public RestartableManagedHttpCacheStorage(File cacheFolder, String cacheFilename, final CacheConfig config, Handler h) {
        super();
        this.cacheFilename = cacheFilename;
        this.cacheFolder = cacheFolder;
        this.entries = loadCacheFromDisk(config.getMaxCacheEntries());
        this.morque = new ReferenceQueue<>();
        this.resources = new HashSet<>();
        this.active = new AtomicBoolean(true);
        if (cacheFolder != null) {
            new CachePersister(this, h, 30000).start();
        } else {
            new CacheTidier(this, h, 120000).start(); // run every 2 minutes
        }
    }

    public boolean isActive() {
        return active.get();
    }

    private void ensureValidState() throws IllegalStateException {
        if (!this.active.get()) {
            throw new IllegalStateException("Cache has been shut down");
        }
    }

    private void keepResourceReference(final HttpCacheEntry entry) {
        final Resource resource = entry.getResource();
        if (resource != null) {
            // Must deallocate the resource when the entry is no longer in used
            final ResourceReference ref = new ResourceReference(entry, this.morque);
            this.resources.add(ref);
        }
    }

    public void putEntry(final String url, final HttpCacheEntry entry) throws IOException {
        Args.notNull(url, "URL");
        Args.notNull(entry, "Cache entry");
        ensureValidState();
        synchronized (this) {
            this.entries.put(url, entry);
            keepResourceReference(entry);
        }
        unsavedUpdates++;
    }

    public HttpCacheEntry getEntry(final String url) throws IOException {
        Args.notNull(url, "URL");
        ensureValidState();
        synchronized (this) {
            return this.entries.get(url);
        }
    }

    public void removeEntry(final String url) throws IOException {
        Args.notNull(url, "URL");
        ensureValidState();
        synchronized (this) {
            // Cannot deallocate the associated resources immediately as the
            // cache entry may still be in use
            this.entries.remove(url);
        }
    }

    public void updateEntry(
            final String url,
            final HttpCacheUpdateCallback callback) throws IOException {
        Args.notNull(url, "URL");
        Args.notNull(callback, "Callback");
        ensureValidState();
        synchronized (this) {
            final HttpCacheEntry existing = this.entries.get(url);
            final HttpCacheEntry updated = callback.update(existing);
            this.entries.put(url, updated);
            if (existing != updated) {
                keepResourceReference(updated);
            }
        }
    }

    public boolean cacheSaveToDiskRequired() {
        return cacheFolder != null && (((double) unsavedUpdates) / entries.size() > .2 || (unsavedUpdates > 20 && (System.currentTimeMillis() - lastSavedAtMillis) > minPreferredSaveInterval));
    }

    public CacheMap loadCacheFromDisk(int maxCacheEntries) {
        synchronized (this) {
            if (cacheFolder != null) {
                File sourceFile = new File(cacheFolder, cacheFilename);
                if (sourceFile.exists()) {
                    if (sourceFile.length() > MAX_EXPECTED_CACHE_SIZE) {
                        Logging.recordException(new Exception("Cache index size larger than anticipated! - " + IOUtils.bytesToNormalizedText(sourceFile.length())));
                    }
                    entries = LegacyIOUtils.readObjectFromFile(sourceFile);

                }
            }
            if (entries != null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, String.format("Loaded %1$d cache entries from file", entries.size()));
                }
                entries.setMaxEntries(maxCacheEntries);
                if (entries.needsResize()) {
                    entries = new CacheMap(entries);
                }
            } else {
                entries = new CacheMap(maxCacheEntries);
            }
        }
        return entries;
    }

    public boolean deleteOnDiskCache() {
        if (cacheFolder == null) {
            return true;
        }
        synchronized (this) {
            File destination = new File(cacheFolder, cacheFilename);
            if (destination.exists()) {
                return destination.delete();
            } else {
                return true;
            }
        }
    }

    public void saveCacheToDisk() {
        if (cacheFolder == null) {
            return;
        }
        synchronized (this) {
            File destination = new File(cacheFolder, cacheFilename);
            boolean savedOkay = LegacyIOUtils.saveObjectToFile(destination, entries);
            if (savedOkay) {
                lastSavedAtMillis = System.currentTimeMillis();
                unsavedUpdates = 0;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, String.format("saved  %1$d cache entries to file", entries.size()));
                }
            }
            if (destination.length() > MAX_EXPECTED_CACHE_SIZE) {
                Logging.recordException(new Exception("Cache index size larger than anticipated! - " + IOUtils.bytesToNormalizedText(destination.length())));
            }
        }
    }

    public int getMaxCacheEntries() {
        return entries.getMaxEntries();
    }

    public void setMaxCacheEntries(int maxCacheEntries) {
        synchronized (this) {
            entries.setMaxEntries(maxCacheEntries);
            if (entries.needsResize()) {
                entries = new CacheMap(entries);
                saveCacheToDisk();
            }
        }
    }

    public void tidy() {
        if (this.active.get()) {
            ResourceReference ref;
            while ((ref = (ResourceReference) this.morque.poll()) != null) {
                synchronized (this) {
                    this.resources.remove(ref);
                }
                ref.getResource().dispose();
            }
        }
    }

    /**
     * Flush all items from both in-memory and on-filesystem data.
     * Leaves cache active
     */
    public void flushCache() {
        if (this.active.get()) {

            if (!deleteOnDiskCache()) {
                Logging.log(Log.WARN, TAG, "Unable to delete cache index file from disk for some reason");
            }

            synchronized (this) {
                entries.clear();
            }
            ResourceReference ref;
            while ((ref = (ResourceReference) this.morque.poll()) != null) {
                synchronized (this) {
                    this.resources.remove(ref);
                }
                ref.getResource().dispose();
            }
        }
    }

    /**
     * Clears the in-memory data, clears the on-filesystem data
     * sets the cache to be inactive
     */
    public void shutdown() {
        if (this.active.compareAndSet(true, false)) {
            synchronized (this) {

                if (!deleteOnDiskCache()) {
                    Logging.log(Log.WARN, TAG, "Unable to delete cache index file from disk for some reason");
                }

                this.entries.clear();
                for (final ResourceReference ref : this.resources) {
                    ref.getResource().dispose();
                }
                this.resources.clear();
                while (this.morque.poll() != null) {
                } // remove all items from morque
            }
        }
    }

    public void close() {
        if (this.active.compareAndSet(true, false)) {
            synchronized (this) {
                saveCacheToDisk();
                this.entries.clear();
                this.resources.clear();
                while (this.morque.poll() != null) {
                } // remove all items from morque
            }
        }
    }

    public long getEntryCount() {
        synchronized (this) {
            return entries == null ? 0 : entries.size();
        }
    }

    private static class CacheTidier extends CacheTask {
        private CacheTidier(RestartableManagedHttpCacheStorage cacheStorage, Handler h, long delayMillis) {
            super("CacheTidier - in memory cache will slowly grow and grow!", cacheStorage, h, delayMillis);
        }

        @Override
        public void runTask(RestartableManagedHttpCacheStorage cacheStorage) {
            cacheStorage.tidy();
        }
    }

    private static class CachePersister extends CacheTask {


        private CachePersister(RestartableManagedHttpCacheStorage cacheStorage, Handler h, long delayMillis) {
            super("cache persistence updater - cache will drift out of sync with disk until shutdown!", cacheStorage, h, delayMillis);
        }

        @Override
        public void runTask(RestartableManagedHttpCacheStorage cacheStorage) {
            if (cacheStorage.cacheSaveToDiskRequired()) {
                cacheStorage.saveCacheToDisk();
            }
        }
    }

    private static abstract class CacheTask implements Runnable {

        private final Handler handler;
        private final RestartableManagedHttpCacheStorage cacheStorage;
        private final long delayMillis;
        private final String tag;

        private CacheTask(String tag, RestartableManagedHttpCacheStorage cacheStorage, Handler h, long delayMillis) {
            this.tag = tag;
            this.cacheStorage = cacheStorage;
            this.handler = h;
            this.delayMillis = delayMillis;
        }

        @Override
        public final void run() {
            synchronized (cacheStorage) {
                runTask(cacheStorage);
                if (cacheStorage.isActive()) {
                    handler.removeCallbacks(this);
                    boolean posted = handler.postDelayed(this, delayMillis);
                    if (!posted && BuildConfig.DEBUG) {
                        Logging.log(Log.ERROR, TAG, "Error attempting to reschedule " + tag);
                    }
                }
            }
        }

        protected abstract void runTask(RestartableManagedHttpCacheStorage cacheStorage);

        public void start() {
            handler.postDelayed(this, delayMillis);
        }
    }

}
