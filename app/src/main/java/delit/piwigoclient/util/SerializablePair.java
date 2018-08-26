package delit.piwigoclient.util;

import android.support.v4.util.Pair;

import java.io.Serializable;

/**
 * Created by gareth on 09/10/17.
 */

public class SerializablePair<T extends Serializable, S extends Serializable> extends Pair<T, S> implements Serializable {
    private static final long serialVersionUID = -5448324655943992896L;

    /**
     * Constructor for a Pair.
     *
     * @param first  the first object in the Pair
     * @param second the second object in the pair
     */
    public SerializablePair(T first, S second) {
        super(first, second);
    }
}
