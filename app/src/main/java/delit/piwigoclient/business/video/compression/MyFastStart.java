package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import delit.libs.util.IOUtils;

public class MyFastStart {

    public static boolean fastStart(Context context, Uri in) throws IOException, QtFastStart.MalformedFileException, QtFastStart.UnsupportedFileException {
        boolean ret = false;
        FileInputStream inStream = null;
        FileOutputStream outStream = null;
        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("compressed.", ".mp4", context.getExternalCacheDir());
            FileDescriptor fd = context.getContentResolver().openFileDescriptor(in, "rw").getFileDescriptor();
            inStream = new FileInputStream(fd);
            outStream = new FileOutputStream(tmpFile);
            ret = QtFastStart.fastStartImpl(inStream.getChannel(), outStream.getChannel());
            if(ret) {
                IOUtils.copyFile(context, tmpFile, in);
            }
            return ret;
        } finally {
            QtFastStart.safeClose(inStream);
            QtFastStart.safeClose(outStream);
            if (!ret) {
                if(tmpFile != null && tmpFile.exists()) {
                    if(!tmpFile.delete()) {
                        QtFastStart.printf("Error deleting tmp file");
                    }
                }
            }
        }
    }
}
