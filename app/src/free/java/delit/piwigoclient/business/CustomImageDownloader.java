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

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
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

public class CustomImageDownloader extends AbstractBaseCustomImageDownloader {

    private static final String TAG = "CustomImageDwnldr";
    public static final String EXIF_WANTED_URI_FLAG = "pwgCliEW";

    public CustomImageDownloader(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        super(context, connectionPrefs);
    }

    public CustomImageDownloader(Context context) {
        super(context);
    }

    @Override
    protected void processImageData(Uri uri, byte[] imageData) {
    }
}