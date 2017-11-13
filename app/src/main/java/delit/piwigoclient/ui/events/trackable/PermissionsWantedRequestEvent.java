package delit.piwigoclient.ui.events.trackable;

import java.util.HashSet;

/**
 * Created by gareth on 14/10/17.
 */

public class PermissionsWantedRequestEvent extends TrackableRequestEvent {
    private String justification;
    private String permissionNeeded;
    private HashSet<String> permissionsWanted = new HashSet<>();
    private HashSet<String> permissionsNeeded;

    public void addPermissionNeeded(String permissionNeeded) {
        permissionsWanted.add(permissionNeeded);
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public HashSet<String> getPermissionsWanted() {
        return permissionsWanted;
    }

    public String getJustification() {
        return justification;
    }

    public void setPermissionsNeeded(HashSet<String> permissionsNeeded) {
        this.permissionsNeeded = permissionsNeeded;
    }

    public String[] getPermissionsNeeded() {
        return permissionsNeeded.toArray(new String[permissionsNeeded.size()]);
    }

    public void setActionId(int actionId) {
        super.setActionId(actionId);
    }
}
