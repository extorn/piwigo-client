package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import delit.libs.util.IOUtils;

public class MyFastStart {

    public static boolean fastStart(Context context, Uri in) throws IOException, QtFastStart.MalformedFileException, QtFastStart.UnsupportedFileException {
        boolean ret;
        File tmpFile;
        try(ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(in, "r")) {
            String fileExt = IOUtils.getFileExt(context, in);
            tmpFile = File.createTempFile("compressed.", fileExt, context.getExternalCacheDir());
            FileDescriptor fd = pfd.getFileDescriptor();
            try(FileInputStream inStream = new FileInputStream(fd); FileOutputStream outStream = new FileOutputStream(tmpFile)){
                ret = QtFastStart.fastStartImpl(inStream.getChannel(), outStream.getChannel());
            }
        }
        if(ret) {
            IOUtils.copyFile(context, tmpFile, in);
        }
        if (!ret) {
            if(tmpFile.exists()) {
                if(!tmpFile.delete()) {
                    QtFastStart.printf("Error deleting tmp file");
                }
            }
        }
        return ret;
    }
}
