package delit.libs.ui.util;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;

import delit.libs.BuildConfig;
import delit.libs.core.util.Logging;
import delit.libs.util.ArrayUtils;

public class BundleUtils {

    private static final String TAG = "BundleUtils";

    public static <T extends Serializable> T getSerializable(Bundle bundle, String key, Class<T> clazz) {
        try {
            Serializable val = bundle.getSerializable(key);
            if (val == null) {
                return null;
            }
            if (clazz.isInstance(val)) {
                return clazz.cast(val);
            }
            throw new IllegalStateException("Looking for object of type " + clazz.getName() + " but was " + val.getClass().getName());
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static HashSet<String> getStringHashSet(Bundle bundle, String key) {
        return getStringSet(bundle, key, null);
    }

    /**
     * @param bundle
     * @param key
     * @param dest   Set to insert the data into
     * @param <T>    Set type
     * @return T if dest is not null, HashSet if destination set is null and key is in the bundle, otherwise null
     */
    public static @Nullable
    <T extends Set<String>> T getStringSet(Bundle bundle, String key, @Nullable T dest) {
        try {
            ArrayList<String> data = bundle.getStringArrayList(key);
            Set<String> retVal = dest;
            if (data != null) {
                if (retVal == null) {
                    retVal = new HashSet<>(data.size());
                }
                retVal.addAll(data);
            }
            return (T) retVal;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static void putStringSet(Bundle bundle, String key, Set<String> data) {
        if (data != null) {
            bundle.putStringArrayList(key, new ArrayList<>(data));
        }
    }

    public static <T extends Parcelable> HashSet<T> getHashSet(Bundle bundle, String key) {
        try {
            ArrayList<T> data = bundle.getParcelableArrayList(key);
            HashSet<T> retVal = null;
            if (data != null) {
                retVal = new HashSet<>(data.size());
                retVal.addAll(data);
            }
            return retVal;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static HashSet<Long> getLongHashSet(Bundle bundle, String key) {
        return getLongHashSet(bundle, key, null);
    }

    public static <T extends Set<Long>> T getLongHashSet(Bundle bundle, String key, T dest) {
        try {
            Long[] data = ArrayUtils.wrap(bundle.getLongArray(key));
            T retVal = null;
            if (data != null) {
                if(retVal == null) {
                    retVal = (T) new HashSet<Long>(data.length);
                } else {
                    retVal = dest;
                }
                Collections.addAll(retVal, data);
            }
            return retVal;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static void putLongHashSet(Bundle bundle, String key, Set<Long> data) {
        if(data != null) {
            long[] dataArr2 = ArrayUtils.unwrapLongs(data);
            bundle.putLongArray(key, dataArr2);
        }
    }

    public static void putFile(Bundle bundle, String key, File file) {
        if(file != null) {
            bundle.putString(key, file.getAbsolutePath());
        }
    }

    public static File getFile(Bundle bundle, String key) {
        try {
            String filePath = bundle.getString(key);
            if (filePath != null) {
                return new File(filePath);
            }
            return null;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static HashSet<Integer> getIntHashSet(Bundle bundle, String key) {
        try {
            Integer[] data = ArrayUtils.wrap(bundle.getIntArray(key));
            HashSet<Integer> retVal = null;
            if (data != null) {
                retVal = new HashSet<>(data.length);
                Collections.addAll(retVal, data);
            }
            return retVal;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static void putIntHashSet(Bundle bundle, String key, HashSet<Integer> data) {
        if(data != null) {
            int[] dataArr2 = ArrayUtils.unwrapInts(data);
            bundle.putIntArray(key, dataArr2);
        }
    }

    public static void putDate(Bundle bundle, String key, Date value) {
        if(value == null) {
            bundle.putLong(key, Long.MIN_VALUE);
        } else {
            bundle.putLong(key, value.getTime());
        }
    }

    public static Date getDate(Bundle bundle, String key) {
        try {
            long timeMillis = bundle.getLong(key);
            if (timeMillis == Long.MIN_VALUE) {
                return null;
            }
            return new Date(timeMillis);
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return null;
        }
    }

    public static <S, T, P extends Map<S, T>> P readMap(Bundle bundle, String key, P mapToFill, ClassLoader loader) {
        try {
            Bundle b = bundle.getBundle(key);
            if (b == null) {
                return mapToFill;
            }
            int len = b.getInt("bytes");
            byte[] dataBytes = b.getByteArray("data");
            if(dataBytes != null) {
                Parcel p = Parcel.obtain();
                try {
                    p.unmarshall(dataBytes, 0, len);
                    p.setDataPosition(0);
                    return ParcelUtils.readMap(p, mapToFill, loader);
                } finally {
                    p.recycle();
                }
            }
            return mapToFill;
        } catch (RuntimeException e) {
            Logging.recordException(e);
            return mapToFill;
        }
    }

    public static <S, T> HashMap<S, T> readMap(Bundle bundle, String key, ClassLoader loader) {
        return readMap(bundle, key, new HashMap<>(), loader);
    }

    /**
     * S and T must be supported by Parcel - writeValue / readValue
     * @param bundle
     * @param key
     * @param data
     * @param <S>
     * @param <T>
     */
    public static <S,T> void writeMap(Bundle bundle, String key, Map<S, T> data) {
        if(data == null) {
            return;
        }
        Parcel p = Parcel.obtain();
        byte[] parcelBytes;
        try {
            ParcelUtils.writeMap(p, data);
            parcelBytes = p.marshall();
        } finally {
            p.recycle();
        }
        Bundle b = new Bundle();
        b.putInt("bytes", parcelBytes.length);
        b.putByteArray("data", parcelBytes);
        bundle.putBundle(key, b);
    }

    public static void logSizeVerbose(String bundleId, Bundle bundle) {
        if(!BuildConfig.DEBUG) {
            Logging.log(Log.WARN, TAG, "Inadvertently left debug code in production code");
            return;
        }

        for (String bundleItemKey : bundle.keySet()) {
            Parcel parcel = Parcel.obtain();
            try {
                Object item = bundle.get(bundleItemKey);
                parcel.writeValue(item);
                int sizeInBytes = parcel.dataSize(); // This is what you want to check
                Log.v("BundleUtils", String.format("BundleSize(%1$s:%2$s) %3$.02fKb", bundleId, bundleItemKey, ((double) sizeInBytes) / 1024));
            } finally {
                parcel.recycle();
            }
        }
        logSize(bundleId, bundle);
    }

    public static void logSize(String bundleId, Bundle bundle) {
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(bundle);
            int sizeInBytes = parcel.dataSize(); // This is what you want to check
            Log.v("BundleUtils", String.format("BundleSize(%1$s) %2$.02fKb", bundleId, ((double) sizeInBytes) / 1024));
        } finally {
            parcel.recycle();
        }
    }

    public static void putClass(Bundle dest, String key, Class<?> clazz) {
        if(clazz != null) {
            dest.putString(key, clazz.getName());
        }
    }

    public static <T> Class<T> getClass(Bundle bundle, String key) {
        String classname = bundle.getString(key);
        if(classname == null) {
            return null;
        }
        try {
            return (Class<T>) Class.forName(classname);
        } catch (ClassNotFoundException e) {
            Logging.log(Log.ERROR, "BundleUtils", "expected class not found : " + classname);
        }
        return null;
    }

    public static <T extends Parcelable> Queue<T> readQueue(@NonNull Bundle in, @NonNull String key, @NonNull Queue<T> queue) {
        try {
            Parcelable[] queueData = in.getParcelableArray(key);
            if (queueData != null) {
                for (Parcelable p : queueData) {
                    queue.add((T) p);
                }
            }
        } catch (RuntimeException e) {
            Logging.recordException(e);
        }
        return queue;
    }

    public static void writeQueue(@NonNull Bundle dest, @NonNull String key, @Nullable Queue<? extends Parcelable> queue) {
        if(queue != null) {
            Parcelable[] queueData = queue.toArray(new Parcelable[0]);
            dest.putParcelableArray(key, queueData);
        }
    }

    public static <T extends Parcelable> void putSortedSet(@NonNull Bundle dest, @NonNull String key, @Nullable SortedSet<T> set) {
        putSet(dest, key, set);
    }

    public static <T extends Parcelable> void putSet(@NonNull Bundle dest, @NonNull String key, @Nullable Set<T> set) {
        if(set != null) {
            Parcelable[] queueData = set.toArray(new Parcelable[0]);
            dest.putParcelableArray(key, queueData);
        }
    }

    public static <T extends Parcelable> SortedSet<T> readSortedSet(@NonNull Bundle in, @NonNull String key, @NonNull SortedSet<T> set) {
        return readSet(in, key, set);
    }

    public static <T extends Parcelable, S extends Set<T>> S readSet(@NonNull Bundle in, @NonNull String key, @NonNull S set) {
        try {
            Parcelable[] data = in.getParcelableArray(key);
            if (data != null) {
                for (Parcelable p : data) {
                    set.add((T) p);
                }
            }
        } catch (RuntimeException e) {
            Logging.recordException(e);
        }
        return set;
    }
}
