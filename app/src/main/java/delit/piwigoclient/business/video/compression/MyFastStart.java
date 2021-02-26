package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;

public class MyFastStart {

    private static final String TAG = "MyFastStart";

    public static boolean fastStart(Context context, Uri inUri) throws IOException, QtFastStart.MalformedFileException, QtFastStart.UnsupportedFileException {
        boolean ret;
        File tmpFile;
        try(ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(inUri, "r")) {
            String fileExt = IOUtils.getFileExt(context, inUri);
            tmpFile = File.createTempFile("faststart.", fileExt, context.getExternalCacheDir());
            FileDescriptor fd = pfd.getFileDescriptor();
            try(FileInputStream inStream = new FileInputStream(fd); FileOutputStream outStream = new FileOutputStream(tmpFile)){
                ret = QtFastStart.fastStartImpl(inStream.getChannel(), outStream.getChannel());
            }
        }
        if(ret) {
            IOUtils.copyFile(context, tmpFile, inUri);
        }
        if(tmpFile.exists()) {
            if(!tmpFile.delete()) {
                Logging.log(Log.ERROR, TAG, "Error deleting tmp file created by faststart");
            }
        }
        return ret;
    }
}
