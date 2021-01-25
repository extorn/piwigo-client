package delit.piwigoclient.ui.util.download;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.PicassoFactory;

public abstract class FileThumbnailGenerator<P extends FileThumbnailGenerator<P>> implements Target {

    private static final String TAG = "FileThumbnailGenerator";

    private final Uri downloadedFile;
    private @NonNull final DownloadTargetLoadListener<P> listener;
    private final WeakReference<Context> contextRef;
    private final Point desiredSize;

    public interface DownloadTargetLoadListener<P extends FileThumbnailGenerator<P>> {
        void onDownloadTargetResult(P generator, boolean success);
    }

    public Uri getDownloadedFile() {
        return downloadedFile;
    }

    public FileThumbnailGenerator(@NonNull Context context, @NonNull DownloadTargetLoadListener<P> loadListener, @NonNull Uri downloadedFile, Point thumbSize) {
        this.contextRef = new WeakReference<>(context);
        this.listener = loadListener;
        this.downloadedFile = downloadedFile;
        this.desiredSize = thumbSize;
    }

    public void execute() {
        try {
            DisplayUtils.runOnUiThread(() -> PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(downloadedFile).error(R.drawable.ic_file_gray_24dp).resize(desiredSize.x, desiredSize.y).centerInside().into(this));
        } catch(RuntimeException e) {
            Logging.log(Log.ERROR, TAG, "Unexpected fatal error" );
            Logging.recordException(e);
            throw e;
        }
    }

    protected Context getContext() {
        return contextRef.get();
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Generated bitmap from : " + downloadedFile.getPath());
        }
        DisplayUtils.runOnUiThread(() -> withLoadedThumbnail(bitmap));
        listener.onDownloadTargetResult((P) this, true);
    }


    protected abstract void withLoadedThumbnail(Bitmap bitmap);
    protected abstract void withErrorThumbnail(Bitmap bitmap);

    @Override
    public void onBitmapFailed(Drawable errorDrawable) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "Failed to generate bitmap from : " + downloadedFile.getPath());
        }
        Bitmap errorBitmap = DisplayUtils.getBitmap(errorDrawable);
        DisplayUtils.runOnUiThread(() -> withErrorThumbnail(errorBitmap));
        listener.onDownloadTargetResult((P) this, false);
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
        // Don't need to do anything before loading image
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "About to generate bitmap from : " + downloadedFile.getPath());
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(obj instanceof FileThumbnailGenerator) {
            FileThumbnailGenerator<?> other = (FileThumbnailGenerator<?>) obj;
            return downloadedFile.equals(other.downloadedFile);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return downloadedFile.hashCode();
    }
}
