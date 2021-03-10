package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;
import id.zelory.compressor.tweak.Compressor;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class ImageCompressor {

    private final Context context;
    private final static String TAG = "JobImgCompressor";

    public interface ImageCompressorListener {
        void onError(long jobId, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse);

        void onCompressionSuccess(DocumentFile compressedFile);
    }

    public ImageCompressor(Context context) {
        this.context = context;
    }

    public DocumentFile compressImage(UploadJob uploadJob, Uri rawImage, ImageCompressorListener listener) {

        DocumentFile outputPhoto = null;

        try {
            UploadJob.ImageCompressionParams compressionParams = uploadJob.getImageCompressionParams();
            String format = compressionParams.getOutputFormat();
            Bitmap.CompressFormat outputFormat;
            if ("jpeg".equals(format)) {
                outputFormat = Bitmap.CompressFormat.JPEG;
            } else if ("webp".equals(format)) {
                outputFormat = Bitmap.CompressFormat.WEBP;
            } else if ("png".equals(format)) {
                outputFormat = Bitmap.CompressFormat.PNG;
            } else {
                throw new IllegalArgumentException("Unsupported image compression format : " + format);
            }
            String mimeType = "image/" + format;
            outputPhoto = uploadJob.buildCompressedFile(context, rawImage, mimeType);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;

            Bitmap bitmap = null;
            int imgWidth;
            int imgHeight;
            try (InputStream is = context.getContentResolver().openInputStream(rawImage)) {
                bitmap = BitmapFactory.decodeStream(is, null, options);
                imgWidth = options.outWidth;
                imgHeight = options.outHeight;
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }

            int maxWidth = compressionParams.getMaxWidth();
            int maxHeight = compressionParams.getMaxHeight();

            if (maxWidth < 0) {
                maxWidth = imgWidth;
            }
            if (maxHeight < 0) {
                maxHeight = imgHeight;
            }

            ExifInterface originalExifData;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                FileDescriptor fd = context.getContentResolver().openFileDescriptor(rawImage, "r").getFileDescriptor();
                originalExifData = new ExifInterface(fd);
                outputPhoto = DocumentFile.fromFile(new Compressor(context)
                        .setMaxHeight(maxHeight)
                        .setMaxWidth(maxWidth)
                        .setQuality(uploadJob.getImageCompressionParams().getQuality())
                        .setCompressFormat(outputFormat)
                        .setDestinationDirectoryPath(outputPhoto.getParentFile().getUri().getPath())
                        .compressToFile(context, rawImage, originalExifData, outputPhoto.getName()));

            } else {
                outputPhoto = DocumentFile.fromFile(new Compressor(context)
                        .setMaxHeight(maxHeight)
                        .setMaxWidth(maxWidth)
                        .setQuality(uploadJob.getImageCompressionParams().getQuality())
                        .setCompressFormat(outputFormat)
                        .setDestinationDirectoryPath(outputPhoto.getParentFile().getUri().getPath())
                        .compressToFile(new File(rawImage.getPath()), outputPhoto.getName()));
                originalExifData = new ExifInterface(rawImage.getPath());
            }

            if (outputFormat == Bitmap.CompressFormat.JPEG) {
                ExifInterface newExifData = new ExifInterface(outputPhoto.getUri().getPath());
                Field[] fields = ExifInterface.class.getFields();
                try {
                    for (Field f : fields) {
                        if (f.getName().startsWith("TAG_")) {
                            Object fieldVal = f.get(originalExifData);
                            if (fieldVal != null) {
                                String tagName = fieldVal.toString();
                                String tagValue = originalExifData.getAttribute(tagName);
                                if (tagValue != null) {
                                    newExifData.setAttribute(tagName, tagValue);
                                }
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new IOException("Unable to migrate EXIF information");
                }
                newExifData.saveAttributes();
            }
        } catch (IOException e) {
            if (outputPhoto != null && outputPhoto.exists()) {
                if (!outputPhoto.delete()) {
                    IOUtils.onFileDeleteFailed(TAG, outputPhoto, "compressed image - post compression");
                }
            }
            Logging.recordException(e);
            //TODO add option to block upload of raw images if compression fails
            listener.onError(uploadJob.getJobId(), new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawImage, false, e));
            return null;
        }
        listener.onCompressionSuccess(outputPhoto);
        return outputPhoto;
    }
}
