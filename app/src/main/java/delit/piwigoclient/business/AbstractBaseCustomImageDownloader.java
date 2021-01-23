package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.squareup.picasso.CustomNetworkRequestHandler;
import com.squareup.picasso.Downloader;
import com.squareup.picasso.NetworkPolicy;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.core.util.Logging;
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

        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(getUriStringEncodingPathSegments(context, uri));
        handler.setCallDetails(context, connectionPrefs, false);
        Looper currentLooper = Looper.myLooper();
        if (currentLooper == null || currentLooper.getThread() != Looper.getMainLooper().getThread()) {
            if (BuildConfig.DEBUG) {
                Logging.log(Log.DEBUG, TAG, "Image downloader has been called on background thread for URI: " + uri);
            }
            handler.runCall(!NetworkPolicy.shouldReadFromDiskCache(networkPolicy));
        } else {
            if (BuildConfig.DEBUG) {
                // invoke a separate thread if this was called on the main thread (this won't occur when called within Picasso)
                Logging.log(Log.ERROR, TAG, "Image downloader has been called on and blocked the main thread! - URI: " + uri);
            }
            handler.invokeAndWait(context, connectionPrefs);
        }

        if (!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse) handler.getResponse();

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            String serverUri = connectionPrefs.getPiwigoServerAddress(sharedPrefs, context);
            if (serverUri != null && "http".equalsIgnoreCase(uri.getScheme()) &&serverUri.toLowerCase().startsWith("https://")) {
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
                Logging.recordException(e);
            } catch (IOException e) {
                Logging.recordException(e);
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

    protected String getUriStringEncodingPathSegments(Context c, Uri uri) {

        Uri.Builder builder = uri.buildUpon().encodedPath(null);
        Set<String> queryParamIds = new HashSet<>(uri.getQueryParameterNames());

        if (queryParamIds.contains(EXIF_WANTED_URI_PARAM)) {
            builder.clearQuery();
            queryParamIds.remove(EXIF_WANTED_URI_PARAM);

            boolean piwigoFragmentAdded = false;
            boolean paramAdded = false;
            for (String param : queryParamIds) {
                List<String> paramVals = uri.getQueryParameters(param);
                if (paramVals.size() > 0) {
                    for (String paramVal : paramVals) {
                        builder.appendQueryParameter(param, paramVal);
                        paramAdded = true;
                    }
                } else if (!piwigoFragmentAdded) {
                    if (paramAdded) {
                        Bundle b = new Bundle();
                        b.putString("uri", uri.toString());
                        FirebaseAnalytics.getInstance(c).logEvent("uri_error", b);
                        Logging.log(Log.ERROR, TAG, "Corrupting uri : " + uri.toString());
                    }
                    builder.encodedQuery(param);
                    piwigoFragmentAdded = true;
                }
            }

        } else {
            List<String> pathSegments = uri.getPathSegments();
            for (int i = 0; i < pathSegments.size(); i++) {
                builder.appendEncodedPath(Uri.encode(pathSegments.get(i)));
            }
        }



        return builder.build().toString();
    }

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}