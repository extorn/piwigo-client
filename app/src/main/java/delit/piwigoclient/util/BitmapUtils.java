package delit.piwigoclient.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

public class BitmapUtils {
    /**
     * Crops the input image to the ratio desired (the centre of the image will remain the centre)
     * Next scales the cropped image to the desired ratio
     * Essentially this is a scale centre fit.
     * Warning: remember to recycle the old bitmap if it is no longer needed.
     * @param bm input Bitmap
     * @param newWidth desired width in px
     * @param newHeight desired height in px
     * @return a new bitmap
     */
    public static Bitmap getResizedCenterFittedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        double desiredRatio = ((double)newWidth)/newHeight;
        double xShrinkRatio = ((double)newWidth) / width;
        double yShrinkRatio = ((double)newHeight) / height;
        int cropToHeight;
        int cropToWidth;
        float scaleRatio;
        if(yShrinkRatio > xShrinkRatio) {
            // shrinking more on y axis than x axis
            cropToHeight = height;
            cropToWidth = (int)Math.rint(cropToHeight * desiredRatio);
            scaleRatio = (float) yShrinkRatio;
        } else {
            // shrinking more on x axis than y axis
            cropToWidth = width;
            cropToHeight = (int)Math.rint(cropToWidth * (1 / desiredRatio));
            scaleRatio = (float) xShrinkRatio;
        }

        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        matrix.postScale(scaleRatio, scaleRatio);

        int startX = (int) Math.rint((((double)width) / 2) - (((double)cropToWidth) / 2));
        int startY = (int) Math.rint((((double)height) / 2) - (((double)cropToHeight) / 2));

        // "RECREATE" THE NEW BITMAP
        // first, we take the maximum portion of the bitmap that fits the desired aspect ratio
        // next we scale that to the desired size.
        return Bitmap.createBitmap(
                bm, startX, startY, cropToWidth, cropToHeight, matrix, false);
    }
}
