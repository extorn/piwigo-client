package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.squareup.picasso.CustomNetworkRequestHandler;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.NetworkPolicy;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToByteArrayHandler;
import delit.piwigoclient.ui.events.BadRequestUsesRedirectionServerEvent;
import delit.piwigoclient.ui.events.BadRequestUsingHttpToHttpsServerEvent;

/**
 * Created by gareth on 18/05/17.
 */

public abstract class AbstractBaseCustomImageDownloader implements Downloader {

    private static final String TAG = "CustomImageDwnldr";
    public static final String EXIF_WANTED_URI_PARAM = "pwgCliEW";
    public static final String EXIF_WANTED_URI_FLAG = EXIF_WANTED_URI_PARAM + "=true";
    private final Context context;

    private final ConnectionPreferences.ProfilePreferences connectionPrefs;


    public AbstractBaseCustomImageDownloader(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        this.context = context;
        this.connectionPrefs = connectionPrefs;
    }

    public AbstractBaseCustomImageDownloader(Context context) {
        this(context, ConnectionPreferences.getActiveProfile());
    }

    @Override
    public Downloader.Response load(Uri uri, int networkPolicy) throws IOException {

        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(getUriString(uri));
        handler.setCallDetails(context, connectionPrefs, false);
        Looper currentLooper = Looper.myLooper();
        if (currentLooper == null || currentLooper.getThread() != Looper.getMainLooper().getThread()) {
            if (BuildConfig.DEBUG) {
                Crashlytics.log(Log.DEBUG, TAG, "Image downloader has been called on background thread for URI: " + uri);
            }
            handler.runCall(!NetworkPolicy.shouldReadFromDiskCache(networkPolicy));
        } else {
            if (BuildConfig.DEBUG) {
                // invoke a separate thread if this was called on the main thread (this won't occur when called within Picasso)
                Crashlytics.log(Log.ERROR, TAG, "Image downloader has been called on and blocked the main thread! - URI: " + uri);
            }
            handler.invokeAndWait(context, connectionPrefs);
        }

        if (!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse) handler.getResponse();

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            if ("http".equalsIgnoreCase(uri.getScheme()) && connectionPrefs.getPiwigoServerAddress(sharedPrefs, context).toLowerCase().startsWith("https://")) {
                EventBus.getDefault().post(new BadRequestUsingHttpToHttpsServerEvent(connectionPrefs, uri));
            }
            if (errorResponse.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
                EventBus.getDefault().post(new BadRequestUsesRedirectionServerEvent(connectionPrefs, uri));
            }

            final String errMsg = errorResponse.getUrl() +
                    '\n' +
                    errorResponse.getErrorMessage() +
                    '\n' +
                    errorResponse.getErrorDetail() +
                    '\n' +
                    errorResponse.getResponseBody();

            // these are going to be caught by listeners registered on a uri basis with the PicassoFactory.
            throw new CustomResponseException(errMsg, networkPolicy, errorResponse.getStatusCode());
        }
        byte[] imageData = ((PiwigoResponseBufferingHandler.UrlSuccessResponse) handler.getResponse()).getData();

        ByteArrayInputStream imageDataStream = new ByteArrayInputStream(imageData);
        int exitRotationDegrees = 0;
        if (handler.isSuccess()) {
            Metadata exifMetadata = loadExifMetadata(uri, imageDataStream);
            imageDataStream.reset();
            exitRotationDegrees = getExifRotationDegrees(exifMetadata);
        }
        return new CustomNetworkRequestHandler.DownloaderResponse(imageDataStream, false, imageData.length, exitRotationDegrees);
    }

    protected Metadata loadExifMetadata(Uri uri, InputStream imageDataStream) {
        Metadata metadata = null;
        if(imageDataStream != null) {

            // Load EXIF data.
            try {
                metadata = ImageMetadataReader.readMetadata(imageDataStream);
            } catch (ImageProcessingException e) {
                Crashlytics.logException(e);
            } catch (IOException e) {
                Crashlytics.logException(e);
            }
        }
        return metadata;
    }

    private int getExifRotationDegrees(Metadata metadata) {

        Integer orientation = null;

        if(metadata != null) {
            ExifIFD0Directory dir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (dir != null) {
                orientation = dir.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            }
        }

        if(orientation == null) {
            orientation = 0;
        }
        switch (orientation) {
            default:
            case 1:
                return 0;
            case 3:
                return 180;
            case 6:
                return 90;
            case 8:
                return 270;
        }
    }

    protected String getUriString(Uri uri) {
        String uriStr = uri.toString();
        int idx = uriStr.indexOf(EXIF_WANTED_URI_FLAG) - 1;
        if(idx > 0) {
            uriStr = uriStr.substring(0, idx);
        }
        return uriStr;
    }

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}