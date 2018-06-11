package delit.piwigoclient.business;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.DrawableRes;

import com.squareup.picasso.Downloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToByteArrayHandler;
import delit.piwigoclient.ui.PicassoFactory;

/**
 * Created by gareth on 18/05/17.
 */

public class CustomImageDownloader implements Downloader {

    private final Context context;
    private final Map<Integer, Integer> errorDrawables = new HashMap<>();

    public CustomImageDownloader(Context context) {
        this.context = context;
    }

    public CustomImageDownloader addErrorDrawable(int statusCode, @DrawableRes int drawable) {
        errorDrawables.put(statusCode, drawable);
        return this;
    }

    @Override
    public Response load(Uri uri, int networkPolicy) throws IOException {

        String uriToCall = uri.toString();
        ImageGetToByteArrayHandler handler = new ImageGetToByteArrayHandler(uriToCall);
        handler.setCallDetails(context, ConnectionPreferences.getActiveProfile(), false);
        handler.runCall();

        if(!handler.isSuccess()) {
            PiwigoResponseBufferingHandler.UrlErrorResponse errorResponse = (PiwigoResponseBufferingHandler.UrlErrorResponse)handler.getResponse();
            Integer drawableId = errorDrawables.get(errorResponse.getStatusCode());
            if(drawableId != null) {
                //return locked padlock image.
                Bitmap icon = PicassoFactory.getInstance().getPicassoSingleton(context).load(drawableId).get();
                return new Downloader.Response(icon, true);
            }
            return null;
//            throw new ResponseException("Error downloading " + uri.toString() + " : " + handler.getError(), networkPolicy, errorResponse.getStatusCode());
        }
        byte[] imageData = ((PiwigoResponseBufferingHandler.UrlSuccessResponse)handler.getResponse()).getData();
        return new Downloader.Response(new ByteArrayInputStream(imageData), false, imageData.length);
    }

    @Override
    public void shutdown() {
        //do nothing for now (shared client).
    }
}