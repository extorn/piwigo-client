package delit.piwigoclient.ui.upgrade;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FilenameFilter;

import delit.libs.util.IOUtils;

public class PreferenceMigrator371 extends PreferenceMigrator {

    public PreferenceMigrator371() {
        super(371);
    }

    @Override
    protected void upgradePreferences(Context context, SharedPreferences prefs, SharedPreferences.Editor editor) {
        // No prefs as such. Just clean a folder
        deleteTmpFastStartFiles(context);
    }

    private void deleteTmpFastStartFiles(Context context) {
        File f = context.getExternalCacheDir();
        File[] files = f.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("compressed.");
            }
        });
        for(File tmpFile : files) {
            IOUtils.delete(context, Uri.fromFile(tmpFile));
        }
    }


}
