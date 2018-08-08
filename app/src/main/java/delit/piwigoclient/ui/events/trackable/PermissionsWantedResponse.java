package delit.piwigoclient.ui.events.trackable;

import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by gareth on 13/10/17.
 */

public class PermissionsWantedResponse extends TrackableResponseEvent {
    private HashMap<String, Boolean> permissionsResults;

    public PermissionsWantedResponse(int actionId, String[] permissions, int[] grantResults) {
        super(actionId);
        setPermissions(permissions, grantResults);
    }

    private void setPermissions(String[] permissions, int[] results) {
        permissionsResults = new HashMap<>(permissions.length);

        for (int i = 0; i < permissions.length; i++) {

            if (results.length > i) {
                permissionsResults.put(permissions[i], results[i] == PackageManager.PERMISSION_GRANTED);
            } else {
                permissionsResults.put(permissions[i], Boolean.FALSE);
            }
        }
    }

    public boolean isPermissionGranted(String permission) {
        Boolean result = permissionsResults.get(permission);
        if (result == null) {
            throw new IllegalArgumentException("Permission was not originally requested : " + permission);
        }
        return result;
    }

    public void addAllPermissionsAlreadyHaveFromRequest(PermissionsWantedRequestEvent request) {
        for (String permissionWanted : request.getPermissionsWanted()) {
            if (!permissionsResults.containsKey(permissionWanted)) {
                permissionsResults.put(permissionWanted, Boolean.TRUE);
            }
        }
    }

    public boolean areAllPermissionsGranted() {
        boolean result = true;
        for (Map.Entry<String, Boolean> entry : permissionsResults.entrySet()) {
            result &= entry.getValue();
        }
        return result;
    }
}
