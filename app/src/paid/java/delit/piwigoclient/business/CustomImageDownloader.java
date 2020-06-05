package delit.piwigoclient.business;

import android.content.Context;
import android.net.Uri;

import com.drew.metadata.Metadata;
import com.squareup.picasso.BaseLruExifCache;

import org.greenrobot.eventbus.EventBus;

import java.io.InputStream;

import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;

/**
 * Created by gareth on 18/05/17.
 */

public class CustomImageDownloader extends AbstractBaseCustomImageDownloader {

    private static final String TAG = "CustomImageDwnldr";

    public CustomImageDownloader(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        super(context, connectionPrefs);
    }

    public CustomImageDownloader(Context context) {
        super(context);
    }

    @Override
    protected Metadata loadExifMetadata(Uri uri, InputStream imageDataStream) {
        Metadata metadata = super.loadExifMetadata(uri, imageDataStream);
        String exifWantedStr = uri.getQueryParameter(EXIF_WANTED_URI_PARAM);
        boolean exifEventWanted = Boolean.parseBoolean(exifWantedStr);
        if(metadata != null && exifEventWanted) {
            BaseLruExifCache<Metadata> cache = PicassoFactory.getInstance().getPicassoSingleton().getCache();
            String uriStr = uri.toString();
            cache.setMetadata(uriStr, metadata);
            EventBus.getDefault().post(new ExifDataRetrievedEvent(uriStr, metadata));
        }
        return metadata;
    }
}