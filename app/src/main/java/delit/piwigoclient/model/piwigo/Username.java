package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

public class Username implements Identifiable, Serializable {
    final long id;
    final String username;
    final String userType; //guest,    generic,    normal,    admin,    webmaster

    public Username(long id, String username, String userType) {
        this.id = id;
        this.username = username;
        this.userType = userType;
    }

    public String getUsername() {
        return username;
    }

    public String getUserType() {
        return userType;
    }

    public long getId() {
        return id;
    }
}