package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.util.SparseIntArray;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.squareup.picasso.CustomNetworkRequestHandler;
import com.squareup.picasso.Downloader;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToByteArrayHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.BadRequestUsesRedirectionServerEvent;
import delit.piwigoclient.ui.events.BadRequestUsingHttpToHttpsServerEvent;
import delit.piwigoclient.util.ToastUtils;

/**
 * Created by gareth on 18/05/17.
 */

public abstract class AbstractBaseCustomImageDownloader implements Downloader {

    private static final String TAG = "CustomImageDwnldr";
    public static final String EXIF_WANTED_URI_PARAM = "pwgCliEW";
    public static final String EXIF_WANTED_URI_FLAG = EXIF_WANTED_URI_PARAM + "=true";
    private final Context context;
    private final SparseIntArray errorDrawables = new SparseIntArray();
    private final ConnectionPreferences.ProfilePreferences connectionPrefs;

    public AbstractBaseCustomImageDownloader(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        this.context = context;
        this.connectionPrefs = connectionPrefs;
    }

    public AbstractBaseCustomImageDownloader(Context context) {
        this(context, ConnectionPreferences.getActiveProfile());
    }

    public AbstractBaseCustomImageDownloader addErrorDrawable(int statusCode, @DrawableRes int drawable) {
        errorDrawables.put(statusCode, drawable);
        return this;
    }

    @Override
    public Downloader.Response load(Uri uri, int networkPolicy) throws IOException {

        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(getUriString(uri));
        handler.setCallDetails(context, connectionPrefs, false);
        handler.runCall();
//        handler.invokeAndWait(context, connectionPrefs);

        if (!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse) handler.getResponse();

            StringBuilder msgBuilder= new StringBuilder();
            msgBuilder.append(errorResponse.getUrl());
            msgBuilder.append('\n');
            msgBuilder.append(errorResponse.getErrorMessage());
            msgBuilder.append('\n');
            msgBuilder.append(errorResponse.getErrorDetail());
            msgBuilder.append('\n');
            msgBuilder.append(errorResponse.getResponseBody());

            final String toastMessage = msgBuilder.toString();
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (uri.getScheme().equalsIgnoreCase("http") && connectionPrefs.getPiwigoServerAddress(sharedPrefs, context).toLowerCase().startsWith("https://")) {
                EventBus.getDefault().post(new BadRequestUsingHttpToHttpsServerEvent(connectionPrefs));
            }
            if (errorResponse.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
                EventBus.getDefault().post(new BadRequestUsesRedirectionServerEvent(connectionPrefs));
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.makeDetailedToast(context, R.string.alert_error, toastMessage, Toast.LENGTH_LONG).show();
                }
            });
            Integer drawableId = errorDrawables.get(errorResponse.getStatusCode());
            if (drawableId != null && drawableId > 0) {
                //return locked padlock image.
                Bitmap icon = PicassoFactory.getInstance().getPicassoSingleton(context).load(drawableId).get();
                return new Downloader.Response(icon, true);
            }
            return null;
//            throw new ResponseException("Error downloading " + uri.toString() + " : " + handler.getError(), networkPolicy, errorResponse.getStatusCode());
        }
        byte[] imageData = ((PiwigoResponseBufferingHandler.UrlSuccessResponse) handler.getResponse()).getData();

        ByteArrayInputStream imageDataStream = new ByteArrayInputStream(imageData);
        Metadata exifMetadata = loadExifMetadata(uri, imageDataStream);
        imageDataStream.reset();
        int exitRotationDegrees = getExifRotationDegrees(exifMetadata);
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