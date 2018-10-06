package delit.piwigoclient.business;

import android.content.Context;
import android.net.Uri;

import com.crashlytics.android.Crashlytics;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.squareup.picasso.BaseLruExifCache;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;

/**
 * Created by gareth on 18/05/17.
 */

public class CustomImageDownloader extends AbstractBaseCustomImageDownloader {

    private static final String TAG = "CustomImageDwnldr";
    public static final String EXIF_WANTED_URI_PARAM = "pwgCliEW";
    public static final String EXIF_WANTED_URI_FLAG = EXIF_WANTED_URI_PARAM + "=true";

    public CustomImageDownloader(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        super(context, connectionPrefs);
    }

    public CustomImageDownloader(Context context) {
        super(context);
    }

    @Override
    protected void processImageData(Uri uri, byte[] imageData) {
        String exifWantedStr = uri.getQueryParameter(EXIF_WANTED_URI_PARAM);
        boolean exifEventWanted = Boolean.valueOf(exifWantedStr);
        if(exifEventWanted) {
            // Load EXIF data.
            try {
                Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageData));
                BaseLruExifCache cache = PicassoFactory.getInstance().getPicassoSingleton().getCache();
                String uriStr = uri.toString();
                cache.setMetadata(uriStr, metadata);
                EventBus.getDefault().post(new ExifDataRetrievedEvent(uriStr, metadata));
            } catch (ImageProcessingException e) {
                Crashlytics.logException(e);
            } catch (IOException e) {
                Crashlytics.logException(e);
            }
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
}