package delit.piwigoclient.ui.common.util;

import android.os.Bundle;
import android.os.Parcelable;

import com.google.android.gms.common.util.ArrayUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class BundleUtils {

    public static <T extends Parcelable> HashSet<T> getHashSet(Bundle bundle, String key) {
        ArrayList<T> data = bundle.getParcelableArrayList(key);
        HashSet<T> retVal = null;
        if(data != null) {
            retVal = new HashSet<T>(data.size());
            retVal.addAll(data);
        }
        return retVal;
    }

    public static <T extends Parcelable> void putHashSet(Bundle bundle, String key, HashSet<T> data) {
        if(data != null) {
            bundle.putParcelableArrayList(key, new ArrayList<T>(data));
        }
    }


    public static HashSet<Long> getLongHashSet(Bundle bundle, String key) {
        Long[] data = ArrayUtils.toWrapperArray(bundle.getLongArray(key));
        HashSet<Long> retVal = null;
        if(data != null) {
            retVal = new HashSet<>(data.length);
            Collections.addAll(retVal, data);
        }
        return retVal;
    }

    public static void putLongHashSet(Bundle bundle, String key, Set<Long> data) {
        if(data != null) {
            long[] dataArr2 = ArrayUtils.toLongArray(data.toArray(new Long[data.size()]));
            bundle.putLongArray(key, dataArr2);
        }
    }

    public static void putFile(Bundle bundle, String key, File file) {
        if(file != null) {
            bundle.putString(key, file.getAbsolutePath());
        }
    }

    public static File getFile(Bundle bundle, String key) {
        String filePath = bundle.getString(key);
        if(filePath != null) {
            return new File(filePath);
        }
        return null;
    }

    public static HashSet<Integer> getIntHashSet(Bundle bundle, String key) {
        Integer[] data = ArrayUtils.toWrapperArray(bundle.getIntArray(key));
        HashSet<Integer> retVal = null;
        if(data != null) {
            retVal = new HashSet<>(data.length);
            Collections.addAll(retVal, data);
        }
        return retVal;
    }

    public static void putIntHashSet(Bundle bundle, String key, HashSet<Integer> data) {
        if(data != null) {
            long[] dataArr2 = ArrayUtils.toLongArray(data.toArray(new Long[data.size()]));
            bundle.putLongArray(key, dataArr2);
        }
    }

    public static ArrayList<File> getFileArrayList(Bundle bundle, String key) {
        ArrayList<String> filenames = bundle.getStringArrayList(key);
        if(filenames == null) {
            return null;
        }
        ArrayList<File> files = new ArrayList<>(filenames.size());
        for(String filename : filenames) {
            files.add(new File(filename));
        }
        return files;
    }

    public static void putFileArrayList(Bundle bundle, String key, ArrayList<File> data) {
        if(data == null) {
            return;
        }
        ArrayList<String> filenames = new ArrayList<>(data.size());
        for(File f : data) {
            filenames.add(f.getAbsolutePath());
        }
        bundle.putStringArrayList(key, filenames);
    }

    public static void putDate(Bundle bundle, String key, Date value) {
        if(value == null) {
            bundle.putLong(key, Long.MIN_VALUE);
        } else {
            bundle.putLong(key, value.getTime());
        }
    }

    public static Date getDate(Bundle bundle, String key) {
        long timeMillis = bundle.getLong(key);
        if(timeMillis == Long.MIN_VALUE) {
            return null;
        }
        return new Date(timeMillis);
    }
}
