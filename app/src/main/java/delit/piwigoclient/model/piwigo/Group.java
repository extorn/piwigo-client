package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import delit.piwigoclient.ui.common.util.ParcelUtils;

/**
 * Created by gareth on 26/06/17.
 */
public class Group implements Identifiable, Parcelable {
    private long id = -1;
    private String name;
    private boolean isDefault;
    private int memberCount;

    public Group() {
    }

    public Group(Parcel in) {
        id = in.readLong();
        name = ParcelUtils.readString(in);
        isDefault = ParcelUtils.readBool(in);
        memberCount = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeValue(name);
        ParcelUtils.writeBool(dest, isDefault);
        dest.writeInt(memberCount);
    }

    public Group(long id, String name, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
    }

    public Group(long id, String name, boolean isDefault, int memberCount) {
        this.id = id;
        this.name = name;
        this.isDefault = isDefault;
        this.memberCount = memberCount;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Group> CREATOR
            = new Parcelable.Creator<Group>() {
        public Group createFromParcel(Parcel in) {
            return new Group(in);
        }

        public Group[] newArray(int size) {
            return new Group[size];
        }
    };
}
