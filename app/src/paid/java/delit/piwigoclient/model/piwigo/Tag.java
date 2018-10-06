package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by gareth on 26/06/17.
 */
public class Tag implements Identifiable, Serializable {
    private static final long serialVersionUID = -1146223606759760399L;
    private long id = -1;
    private String name;
    private int usageCount;
    private Date lastModified;

    public Tag(String name) {
        this.name = name;
    }

    public Tag(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Tag(long id, String name, int usageCount, Date lastModified) {
        this.id = id;
        this.name = name;
        this.usageCount = usageCount;
        this.lastModified = lastModified;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public Date getLastModified() {
        return lastModified;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Tag)) {
            return false;
        }
        if(((Tag) other).id == -1 && this.id == -1) {
            return ((Tag) other).name.equals(this.name);
        }
        return ((Tag) other).id == this.id;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }
}
