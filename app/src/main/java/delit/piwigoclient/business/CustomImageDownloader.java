package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.squareup.picasso.Downloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import delit.piwigoclient.R;
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
    public Response load(Uri uri, boolean loadFromCache) throws IOException {

        //TODO enable caching of images.
        String uriToCall = uri.toString();
        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(uriToCall);
        String piwigoServerUrl = prefs.getString(context.getString(R.string.preference_piwigo_server_address_key), null);
        boolean asyncMode = false;
        handler.setCallDetails(context, piwigoServerUrl, asyncMode);
        handler.runCall();

        if(!handler.isSuccess()) {
            throw new ResponseException("Error downloading " + uri.toString() + " : " + handler.getError());
        }
        byte[] imageData = ((PiwigoResponseBufferingHandler.UrlSuccessResponse)handler.getResponse()).getData();
        return new Downloader.Response(new ByteArrayInputStream(imageData), false, imageData.length);
    }

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}