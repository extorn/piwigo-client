package delit.piwigoclient.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.appcompat.content.res.AppCompatResources;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;

public class ResourceRequestHandler extends RequestHandler {
    private static final String TAG = "ResourceRequestHandler";
    private final Context context;

    public ResourceRequestHandler(Context context) {
        this.context = context;
    }

    public boolean canHandleRequest(Request data) {
        return data.resourceId != 0 || "android.resource".equals(data.uri.getScheme());
    }


    protected Context getContext() {
        return context;
    }

    public Result load(Request data, int networkPolicy) {
        Drawable d = AppCompatResources.getDrawable(context, data.resourceId);
        return new Result(drawableToBitmap(d, data.targetWidth, data.targetHeight), Picasso.LoadedFrom.DISK);
    }


    public Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        int bitmapWidth = drawable.getIntrinsicWidth();
        int bitmapHeight = drawable.getIntrinsicHeight();
        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            Bundle b = new Bundle();
            b.putString("drawable", drawable.toString());
            b.putString("tag", Utils.getId(this));
            Logging.logAnalyticEventIfPossible("1x1 bitmap created", b);
            bitmapHeight = 1;
            bitmapWidth = 1;
        } else if(width < bitmapWidth || height < bitmapHeight && width > 0 && height > 0) {
            // this might be the case if the aspect is different, not just if desire smaller.
            bitmapWidth = width;
            bitmapHeight = height;
        }
        bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


}
