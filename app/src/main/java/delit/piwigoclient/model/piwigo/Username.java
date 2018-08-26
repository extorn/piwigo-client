package delit.piwigoclient.model.piwigo;

import java.io.Serializable;

public class Username implements Identifiable, Serializable {
    private static final long serialVersionUID = -135397513975209201L;
    private final long id;
    private final String username;
    private final String userType; //guest,    generic,    normal,    admin,    webmaster

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