package delit.piwigoclient.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClassUtils {

    private static final Map<Class<?>, Class<?>> PRIMITIVES_TO_WRAPPERS;

    static {
        HashMap<Class<?>, Class<?>> map = new HashMap<>();
        map.put(boolean.class, Boolean.class);
        map.put(byte.class, Byte.class);
        map.put(char.class, Character.class);
        map.put(double.class, Double.class);
        map.put(float.class, Float.class);
        map.put(int.class, Integer.class);
        map.put(long.class, Long.class);
        map.put(short.class, Short.class);
        map.put(void.class, Void.class);
        PRIMITIVES_TO_WRAPPERS = Collections.unmodifiableMap(map);
    }

    // safe because both Long.class and long.class are of type Class<Long>
    @SuppressWarnings("unchecked")
    public static <T> Class<T> wrap(Class<T> c) {
        return c.isPrimitive() ? (Class<T>) PRIMITIVES_TO_WRAPPERS.get(c) : c;
    }
}
