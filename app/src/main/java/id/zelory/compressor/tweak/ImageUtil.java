package id.zelory.compressor.tweak;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created on : June 18, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * GitHub     : https://github.com/zetbaitsu
 */
class ImageUtil {

    private ImageUtil() {

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    static File compressImage(Context c, Uri imageUri, ExifInterface exif, int reqWidth, int reqHeight, Bitmap.CompressFormat compressFormat, int quality, String destinationPath) throws IOException {
        FileOutputStream fileOutputStream = null;
        File destFolder = new File(destinationPath).getParentFile();
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }
        fileOutputStream = new FileOutputStream(destinationPath);
        // write the compressed bitmap at the destination specified by destinationPath.
        decodeSampledBitmapFromFile(c, imageUri, reqWidth, reqHeight, exif).compress(compressFormat, quality, fileOutputStream);

        return new File(destinationPath);
    }

    static File compressImage(File imageFile, int reqWidth, int reqHeight, Bitmap.CompressFormat compressFormat, int quality, String destinationPath) throws IOException {
        FileOutputStream fileOutputStream = null;
        File destFolder = new File(destinationPath).getParentFile();
        if (!destFolder.exists()) {
            destFolder.mkdirs();
        }
        try {
            fileOutputStream = new FileOutputStream(destinationPath);
            // write the compressed bitmap at the destination specified by destinationPath.
            try(FileInputStream is = new FileInputStream(imageFile)){
                decodeSampledBitmapFromFile(is, reqWidth, reqHeight, getExif(imageFile)).compress(compressFormat, quality, fileOutputStream);
            }
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        }

        return new File(destinationPath);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    protected static ExifInterface getExif(FileDescriptor fd) throws IOException {
        return new ExifInterface(fd);
    }

    protected static ExifInterface getExif(File f) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ExifInterface(f);
        } else {
            return new ExifInterface(f.getPath());
        }
    }

    private static boolean isSeekableFD(FileDescriptor fd) {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                try {
                    Os.lseek(fd, 0, OsConstants.SEEK_CUR);
                } catch (ErrnoException e) {
                    throw new IOException("Failed to seek file descriptor", e);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    static Bitmap decodeSampledBitmapFromFile(InputStream imageFile, int reqWidth, int reqHeight, ExifInterface exif) {
        // First decode with inJustDecodeBounds=true to check dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Rect paddingRect = new Rect();
        BitmapFactory.decodeStream(imageFile, paddingRect, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        Bitmap scaledBitmap = BitmapFactory.decodeStream(imageFile, paddingRect, options);

        Matrix matrix = getRotationMatrix(exif);

        scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
        return scaledBitmap;
    }

    static Bitmap decodeSampledBitmapFromFile(Context c, Uri imageUri, int reqWidth, int reqHeight, ExifInterface exif) throws IOException {
        // First decode with inJustDecodeBounds=true to check dimensions
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        Rect paddingRect = new Rect();
        try(InputStream is = c.getContentResolver().openInputStream(imageUri)){
            BitmapFactory.decodeStream(is, paddingRect, options);
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        try(InputStream is = c.getContentResolver().openInputStream(imageUri)) {
            Bitmap scaledBitmap = BitmapFactory.decodeStream(is, paddingRect, options);
            Matrix matrix = getRotationMatrix(exif);

            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
            return scaledBitmap;
        }
    }

    private static Matrix getRotationMatrix(ExifInterface exif) {
        //check the rotation of the image and display it properly
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
        Matrix matrix = new Matrix();
        if (orientation == 6) {
            matrix.postRotate(90);
        } else if (orientation == 3) {
            matrix.postRotate(180);
        } else if (orientation == 8) {
            matrix.postRotate(270);
        }
        return matrix;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
