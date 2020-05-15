package delit.piwigoclient.ui.events.trackable;

import android.os.Parcel;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 14/10/17.
 */

public class PermissionsWantedRequestEvent extends TrackableRequestEvent {
    private final HashSet<String> permissionsWanted = new HashSet<>();
    private String justification;
    private HashSet<String> permissionsNeeded;

    public PermissionsWantedRequestEvent() {}

    public PermissionsWantedRequestEvent(Parcel in) {
        super(in);
        ParcelUtils.readStringSet(in, permissionsWanted);
        justification = ParcelUtils.readString(in);
        permissionsNeeded = ParcelUtils.readStringSet(in);
    }

    public void addPermissionNeeded(String permissionNeeded) {
        permissionsWanted.add(permissionNeeded);
    }

    public HashSet<String> getPermissionsWanted() {
        return permissionsWanted;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String[] getPermissionsNeeded() {
        return permissionsNeeded.toArray(new String[0]);
    }

    public void setPermissionsNeeded(HashSet<String> permissionsNeeded) {
        this.permissionsNeeded = permissionsNeeded;
    }

    public void setActionId(int actionId) {
        super.setActionId(actionId);
    }


    public static final Creator<PermissionsWantedRequestEvent> CREATOR = new Creator<PermissionsWantedRequestEvent>() {
        @Override
        public PermissionsWantedRequestEvent createFromParcel(Parcel in) {
            return new PermissionsWantedRequestEvent(in);
        }

        @Override
        public PermissionsWantedRequestEvent[] newArray(int size) {
            return new PermissionsWantedRequestEvent[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeStringSet(dest, permissionsWanted);
        dest.writeValue(justification);
        ParcelUtils.writeStringSet(dest, permissionsNeeded);
    }
}
