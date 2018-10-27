package delit.piwigoclient.ui.common.util;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.util.ArrayUtils;

public class ParcelUtils {
    public static <T extends Parcelable> HashSet<T> readHashSet(@NonNull Parcel in, ClassLoader loader) {
        ArrayList<T> store = (ArrayList<T>) in.readValue(loader);
        if(store != null) {
            HashSet retVal = new HashSet<T>(store);
            retVal.addAll(store);
            return retVal;
        }
        return null;
    }

    public static void writeSet(@NonNull Parcel out, HashSet<? extends Parcelable> data) {
        if(data == null) {
            out.writeValue(null);
        } else {
            out.writeValue(new ArrayList<>(data));
        }
    }

    public static void writeIntSet(@NonNull Parcel dest, Set<Integer> data) {
        int[] rawData = null;
        if(data != null) {
            rawData = ArrayUtils.unwrapInts(data);
        }
        dest.writeIntArray(rawData);
    }
    
    public static HashSet<Integer> readIntHashSet(@NonNull Parcel in, ClassLoader loader) {
        int[] data = (int[]) in.readValue(loader);
        HashSet<Integer> wrapper = new HashSet<Integer>(data.length);
        if(data != null) {
            Collections.addAll(wrapper, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return wrapper;
    }

    public static Set<Integer> readIntSet(@NonNull Parcel in, @NonNull Set<Integer> destSet, ClassLoader loader) {
        int[] data = (int[]) in.readValue(loader);
        if(data != null) {
            Collections.addAll(destSet, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return destSet;
    }

    public static void writeLongSet(Parcel dest, HashSet<Long> data) {
        ArrayList<Long> dataWrapper = null;
        if(data != null) {
            dataWrapper = new ArrayList<>(data);
        }
        writeLongArrayList(dest, dataWrapper);
    }

    public static HashSet<Long> readLongSet(Parcel in, ClassLoader loader) {
        ArrayList<Long> dataWrapper = readLongArrayList(in, loader);
        HashSet<Long> wrapper = new HashSet<>(dataWrapper.size());
        if(dataWrapper != null) {
            wrapper = new HashSet<>(wrapper.size());
            wrapper.addAll(dataWrapper);
        }
        return wrapper;
    }

    public static ArrayList<Long> readLongArrayList(Parcel in, ClassLoader loader) {
        return (ArrayList<Long>) in.readValue(loader);
    }

    public static void writeLongArrayList(Parcel out, ArrayList<Long> parentageChain) {
        out.writeValue(parentageChain);
    }

    public static Date readDate(Parcel in) {
        Date date = null;
        long time = in.readLong();
        if(time != Long.MIN_VALUE) {
            date = new Date(time);
        }
        return date;
    }

    public static void writeDate(Parcel out, Date data) {
        out.writeLong(data==null?Long.MIN_VALUE:data.getTime());
    }

    public static void writeStringSet(Parcel dest, HashSet<String> entries) {
        dest.writeList(new ArrayList<>(entries));
    }

    public static HashSet<String> readStringSet(Parcel source, ClassLoader loader) {
        List<String> rawData = (List<String>) source.readValue(loader);
        HashSet<String> data = null;
        if(rawData != null) {
            data = new HashSet<>(rawData);
        }
        return data;
    }
//
//    public static <T extends Parcelable,S extends Parcelable> void writeMap(Parcel dest, HashMap<T ,ArrayList<S>> data) {
//        if(data == null) {
//            dest.writeBundle(null);
//            return;
//        }
//        Bundle b = new Bundle();
//        ArrayList<Parcelable> keys = new ArrayList<>(data.size());
//        ArrayList<ArrayList<? extends Parcelable>> values = new ArrayList<>(data.size());
//        for(Map.Entry<T,ArrayList<S>> entry : data.entrySet()) {
//            keys.add(entry.getKey());
//            values.add(entry.getValue());
//        }
//        b.putParcelableArrayList("keys", keys);
//        int i = 0;
//        for(ArrayList al : values) {
//            b.putParcelableArrayList("values_"+(i++), al);
//        }
//        dest.writeBundle(b);
//    }
//
//    public static <T extends Parcelable,S extends Parcelable> HashMap<T ,ArrayList<S>> readMap(Parcel in) {
//        Bundle b = in.readBundle();
//        if(b == null) {
//            return null;
//        }
//        ArrayList<T> keys = b.getParcelableArrayList("keys");
//        HashMap<T, ArrayList<S>> retVal = new HashMap<>(keys.size());
//        int i = 0;
//        for(Parcelable key : keys) {
//            retVal.put(keys.get(i), (ArrayList<S>) b.get("values_"+(i++)));
//        }
//        return retVal;
//    }

    public static <S, T> void writeMap(Parcel p, Map<S, T> data) {
        ArrayList<S> keys = new ArrayList<>(data.size());
        ArrayList<T> values = new ArrayList<>(data.size());
        for(Map.Entry<S,T> entry : data.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }
        p.writeValue(keys);
        p.writeValue(values);
    }

    public static <S, T> HashMap<S,T> readMap(Parcel p, ClassLoader loader) {
        ArrayList<S> keys = (ArrayList<S>) p.readValue(loader);
        ArrayList<T> values = (ArrayList<T>) p.readValue(loader);
        HashMap<S,T> map = new HashMap<>(keys.size());
        for(int i = 0; i < values.size(); i++) {
            map.put(keys.get(i), values.get(i));
        }
        return map;
    }

    public static <T extends Parcelable> ArrayList<T> readTypedList(Parcel in, Parcelable.Creator<T> creator) {
        ArrayList<T> data = new ArrayList<T>();
        in.readTypedList(data, creator);
        return data;
    }
}
