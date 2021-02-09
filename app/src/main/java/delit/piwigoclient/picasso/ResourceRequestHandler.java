package delit.piwigoclient.picasso;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.appcompat.content.res.AppCompatResources;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

public class ResourceRequestHandler extends RequestHandler {
    private final Context context;

    public ResourceRequestHandler(Context context) {
        this.context = context;
    }

    public boolean canHandleRequest(Request data) {
        return data.resourceId != 0 || "android.resource".equals(data.uri.getScheme());
    }

    public Result load(Request data, int networkPolicy) {
        Drawable d = AppCompatResources.getDrawable(context, data.resourceId);
        return new Result(drawableToBitmap(d), Picasso.LoadedFrom.DISK);
    }


    public Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single colors bitmap will be created of 1x1 pixel
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }


}
