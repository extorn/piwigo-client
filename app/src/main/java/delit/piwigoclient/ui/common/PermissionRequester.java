package delit.piwigoclient.ui.common;

import java.io.Serializable;
import java.util.HashSet;

/**
 * Created by gareth on 14/10/17.
 */
interface PermissionRequester extends Serializable {
    void requestPermission(int requestId, final HashSet<String> permissionsNeeded);
}
