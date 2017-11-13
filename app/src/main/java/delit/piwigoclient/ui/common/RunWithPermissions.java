package delit.piwigoclient.ui.common;

import java.io.Serializable;

/**
 * Created by gareth on 14/10/17.
 */
public interface RunWithPermissions extends Serializable {
    void onPermissionGranted(int requestId);

    void onPermissionDenied(int requestId);

}
