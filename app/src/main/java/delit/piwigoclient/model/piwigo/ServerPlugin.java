package delit.piwigoclient.model.piwigo;

public class ServerPlugin {
    private String id;
    private String name;
    private String version;
    private String state;
    private String description;

    public ServerPlugin(String id, String name, String version, String state, String description) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.state = state;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return "active".equals(state);
    }
}
