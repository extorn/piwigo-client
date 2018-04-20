package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.squareup.picasso.Downloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToByteArrayHandler;

/**
 * Created by gareth on 18/05/17.
 */

public class CustomImageDownloader implements Downloader {

    private final Context context;
    private final SharedPreferences prefs;

    public CustomImageDownloader(Context context) {
        this.context = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    @Override
    public Response load(Uri uri, int networkPolicy) throws IOException {

        String uriToCall = uri.toString();
        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(uriToCall);
        String piwigoServerUrl = ConnectionPreferences.getPiwigoServerAddress(prefs, context);
        boolean asyncMode = false;
        handler.setCallDetails(context, piwigoServerUrl, asyncMode);
        handler.runCall();

        if(!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse)handler.getResponse();
            throw new ResponseException("Error downloading " + uri.toString() + " : " + handler.getError(), networkPolicy, errorResponse.getStatusCode());
        }
        byte[] imageData = ((PiwigoResponseBufferingHandler.UrlSuccessResponse)handler.getResponse()).getData();
        return new Downloader.Response(new ByteArrayInputStream(imageData), false, imageData.length);
    }

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}