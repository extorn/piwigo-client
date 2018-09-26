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

import com.squareup.picasso.Downloader;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToByteArrayHandler;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.BadRequestUsesRedirectionServerEvent;
import delit.piwigoclient.ui.events.BadRequestUsingHttpToHttpsServerEvent;

/**
 * Created by gareth on 18/05/17.
 */

public abstract class AbstractBaseCustomImageDownloader implements Downloader {

    private static final String TAG = "CustomImageDwnldr";
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
    public Response load(Uri uri, int networkPolicy) throws IOException {

        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(getUriString(uri));
        handler.setCallDetails(context, connectionPrefs, false);
        handler.runCall();

        if (!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse) handler.getResponse();
            final String toastMessage = errorResponse.getUrl() + '\n' + errorResponse.getErrorMessage() + '\n' + errorResponse.getErrorDetail() + '\n' + errorResponse.getResponseBody();
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
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
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

        processImageData(uri, imageData);

        return new Downloader.Response(new ByteArrayInputStream(imageData), false, imageData.length);
    }

    protected String getUriString(Uri uri) {
        return uri.toString();
    }

    protected abstract void processImageData(Uri uri, byte[] imageData);

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}