package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;
import java.util.HashSet;

import delit.piwigoclient.ui.common.util.ParcelUtils;

public class User implements Identifiable, Parcelable {
    private long id = -1;
    private String email;
    private String username;
    private String userType; //guest,    generic,    normal,    admin,    webmaster
    private boolean highDefinitionEnabled;
    private Date lastVisit;
    private HashSet<Long> groups;
    private int privacyLevel; // 8 4 2 1 0
    private String password;

    public User() {
    }

    public User(Parcel in) {
        id = in.readLong();
        email = in.readString();
        username = in.readString();
        userType = in.readString();
        highDefinitionEnabled = ParcelUtils.readValue(in,null, boolean.class);
        lastVisit = ParcelUtils.readDate(in);
        groups = ParcelUtils.readLongSet(in, null);
        privacyLevel = in.readInt();
        password = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(email);
        dest.writeString(username);
        dest.writeString(userType);
        dest.writeValue(highDefinitionEnabled);
        ParcelUtils.writeDate(dest, lastVisit);
        ParcelUtils.writeLongSet(dest, groups);
        dest.writeInt(privacyLevel);
        dest.writeString(password);
    }

    public User(long id, String username, String userType, int privacyLevel, String email, boolean highDefinitionEnabled, Date lastVisit) {
        this.id = id;
        this.username = username;
        this.userType = userType;
        this.privacyLevel = privacyLevel;
        this.email = email;
        this.highDefinitionEnabled = highDefinitionEnabled;
        this.lastVisit = lastVisit;
    }

    public HashSet<Long> getGroups() {
        return groups;
    }

    public void setGroups(HashSet<Long> groups) {
        this.groups = groups;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isHighDefinitionEnabled() {
        return highDefinitionEnabled;
    }

    public void setHighDefinitionEnabled(boolean highDefinitionEnabled) {
        this.highDefinitionEnabled = highDefinitionEnabled;
    }

    public Date getLastVisit() {
        return lastVisit;
    }

    public void setLastVisit(Date lastVisit) {
        this.lastVisit = lastVisit;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getPrivacyLevel() {
        return privacyLevel;
    }

    public void setPrivacyLevel(int privacyLevel) {
        this.privacyLevel = privacyLevel;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<User> CREATOR
            = new Parcelable.Creator<User>() {
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        public User[] newArray(int size) {
            return new User[size];
        }
    };
}