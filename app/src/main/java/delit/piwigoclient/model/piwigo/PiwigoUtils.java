package delit.piwigoclient.model.piwigo;

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by gareth on 07/04/18.
 */

public class PiwigoUtils {

    private PiwigoUtils() {
    }

    public static <T extends Identifiable> HashSet<Long> toSetOfIds(Collection<T> identifiables) {
        if (identifiables == null) {
            return null;
        }
        HashSet<Long> ids = new HashSet<>(identifiables.size());
        for (T identifiable : identifiables) {
            ids.add(identifiable.getId());
        }

        return ids;
    }
}
