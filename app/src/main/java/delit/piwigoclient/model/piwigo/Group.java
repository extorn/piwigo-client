package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

/**
 * Created by gareth on 26/06/17.
 */
public class Group implements Identifiable, Serializable {
    private long id = -1;
    private String name;
    private boolean isDefault;
    private int memberCount;

    public Group() {
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

}
