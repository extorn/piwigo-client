package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryItemStub implements Serializable {

    public static final CategoryItemStub ROOT_GALLERY = new CategoryItemStub(CategoryItem.ROOT_ALBUM.getName(), CategoryItem.ROOT_ALBUM.getId());
    private final String name;
    private final long id;
    private ArrayList<Long> parentageChain;

    public CategoryItemStub(String name,  long id) {
        this.name = name;
        this.id = id;
        parentageChain = new ArrayList<>();
    }

    public Long getParentId() {
        return parentageChain.size() == 0? null : parentageChain.get(parentageChain.size() - 1);
    }

    public void setParentageChain(List<Long> parentageChain) {
        this.parentageChain = new ArrayList<>(parentageChain);
    }

    public void setParentageChain(List<Long> parentageChain, long directParent) {
        this.parentageChain = new ArrayList<>(parentageChain);
        this.parentageChain.add(directParent);
    }

    public List<Long> getParentageChain() {
        return Collections.unmodifiableList(parentageChain);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CategoryItemStub && id == ((CategoryItemStub) obj).id;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return (int) id;
    }
}