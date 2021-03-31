package delit.piwigoclient.business;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.util.ArrayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.R;

public class AppPreferences extends BaseAppPreferences {
    private static final String TAG = "AppPrefs";

}
