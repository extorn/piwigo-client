package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Created by gareth on 26/03/18.
 */

public class MyPicasso extends Picasso {

    private List<RequestHandler> myRequestHandlers;

    MyPicasso(Context context, Dispatcher dispatcher, Cache cache, Listener listener, RequestTransformer requestTransformer, List<RequestHandler> extraRequestHandlers, Stats stats, Bitmap.Config defaultBitmapConfig, boolean indicatorsEnabled, boolean loggingEnabled) {
        super(context, dispatcher, cache, listener, requestTransformer, extraRequestHandlers, stats, defaultBitmapConfig, indicatorsEnabled, loggingEnabled);
    }

    @Override
    List<RequestHandler> getRequestHandlers() {
        if(myRequestHandlers == null) {
            myRequestHandlers = super.getRequestHandlers();
            myRequestHandlers = Collections.unmodifiableList(myRequestHandlers.subList(1, myRequestHandlers.size()));
        }
        return myRequestHandlers;
    }

    /** Fluent API for creating {@link Picasso} instances. */
    @SuppressWarnings("UnusedDeclaration") // Public API.
    public static class Builder {
        private final Context context;
        private Downloader downloader;
        private ExecutorService service;
        private Cache cache;
        private Listener listener;
        private RequestTransformer transformer;
        private List<RequestHandler> requestHandlers;
        private Bitmap.Config defaultBitmapConfig;

        private boolean indicatorsEnabled;
        private boolean loggingEnabled;

        /** Start building a new {@link Picasso} instance. */
        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }

        /**
         * Specify the default {@link Bitmap.Config} used when decoding images. This can be overridden
         * on a per-request basis using {@link RequestCreator#config(Bitmap.Config) config(..)}.
         */
        public Builder defaultBitmapConfig(Bitmap.Config bitmapConfig) {
            if (bitmapConfig == null) {
                throw new IllegalArgumentException("Bitmap config must not be null.");
            }
            this.defaultBitmapConfig = bitmapConfig;
            return this;
        }

        /** Specify the {@link Downloader} that will be used for downloading images. */
        public Builder downloader(Downloader downloader) {
            if (downloader == null) {
                throw new IllegalArgumentException("Downloader must not be null.");
            }
            if (this.downloader != null) {
                throw new IllegalStateException("Downloader already set.");
            }
            this.downloader = downloader;
            return this;
        }

        /**
         * Specify the executor service for loading images in the background.
         * <p>
         * Note: Calling {@link Picasso#shutdown() shutdown()} will not shutdown supplied executors.
         */
        public Builder executor(ExecutorService executorService) {
            if (executorService == null) {
                throw new IllegalArgumentException("Executor service must not be null.");
            }
            if (this.service != null) {
                throw new IllegalStateException("Executor service already set.");
            }
            this.service = executorService;
            return this;
        }

        /** Specify the memory cache used for the most recent images. */
        public Builder memoryCache(Cache memoryCache) {
            if (memoryCache == null) {
                throw new IllegalArgumentException("Memory cache must not be null.");
            }
            if (this.cache != null) {
                throw new IllegalStateException("Memory cache already set.");
            }
            this.cache = memoryCache;
            return this;
        }

        /** Specify a listener for interesting events. */
        public Builder listener(Listener listener) {
            if (listener == null) {
                throw new IllegalArgumentException("Listener must not be null.");
            }
            if (this.listener != null) {
                throw new IllegalStateException("Listener already set.");
            }
            this.listener = listener;
            return this;
        }

        /**
         * Specify a transformer for all incoming requests.
         * <p>
         * <b>NOTE:</b> This is a beta feature. The API is subject to change in a backwards incompatible
         * way at any time.
         */
        public Builder requestTransformer(RequestTransformer transformer) {
            if (transformer == null) {
                throw new IllegalArgumentException("Transformer must not be null.");
            }
            if (this.transformer != null) {
                throw new IllegalStateException("Transformer already set.");
            }
            this.transformer = transformer;
            return this;
        }

        /** Register a {@link RequestHandler}. */
        public Builder addRequestHandler(RequestHandler requestHandler) {
            if (requestHandler == null) {
                throw new IllegalArgumentException("RequestHandler must not be null.");
            }
            if (requestHandlers == null) {
                requestHandlers = new ArrayList<RequestHandler>();
            }
            if (requestHandlers.contains(requestHandler)) {
                throw new IllegalStateException("RequestHandler already registered.");
            }
            requestHandlers.add(requestHandler);
            return this;
        }

        /**
         * @deprecated Use {@link #indicatorsEnabled(boolean)} instead.
         * Whether debugging is enabled or not.
         */
        @Deprecated public Builder debugging(boolean debugging) {
            return indicatorsEnabled(debugging);
        }

        /** Toggle whether to display debug indicators on images. */
        public Builder indicatorsEnabled(boolean enabled) {
            this.indicatorsEnabled = enabled;
            return this;
        }

        /**
         * Toggle whether debug logging is enabled.
         * <p>
         * <b>WARNING:</b> Enabling this will result in excessive object allocation. This should be only
         * be used for debugging purposes. Do NOT pass {@code BuildConfig.DEBUG}.
         */
        public Builder loggingEnabled(boolean enabled) {
            this.loggingEnabled = enabled;
            return this;
        }

        /** Create the {@link Picasso} instance. */
        public MyPicasso build() {
            Context context = this.context;

            if (downloader == null) {
                downloader = Utils.createDefaultDownloader(context);
            }
            if (cache == null) {
                cache = new LruCache(context);
            }
            if (service == null) {
                service = new PicassoExecutorService();
            }
            if (transformer == null) {
                transformer = RequestTransformer.IDENTITY;
            }

            Stats stats = new Stats(cache);

            Dispatcher dispatcher = new Dispatcher(context, service, HANDLER, downloader, cache, stats);

            return new MyPicasso(context, dispatcher, cache, listener, transformer, requestHandlers, stats,
                    defaultBitmapConfig, indicatorsEnabled, loggingEnabled);
        }
    }

}
