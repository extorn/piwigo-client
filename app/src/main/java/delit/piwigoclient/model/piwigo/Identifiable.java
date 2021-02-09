package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

/**
 * Created by gareth on 06/04/18.
 */

public interface Identifiable {
    long getId();

    /**
     * Ascending ID order
     * @param <T>
     */
    class IdComparator<T extends Identifiable> implements java.util.Comparator<T>, Serializable {
        private static final long serialVersionUID = 7017742204389489695L;

        @Override
        public int compare(T o1, T o2) {
            long x = o1.getId();
            long y = o2.getId();
            return Long.compare(x, y);
        }
    }
}
