package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryItemStub implements Serializable {

    public static final CategoryItemStub ROOT_GALLERY = new CategoryItemStub(CategoryItem.ROOT_ALBUM.getName(), CategoryItem.ROOT_ALBUM.getId());
    private static final CategoryItemStub ROOT_GALLERY_NON_SELECTABLE = new CategoryItemStub(CategoryItem.ROOT_ALBUM.getName(), CategoryItem.ROOT_ALBUM.getId()).markNonUserSelectable();
    private final String name;
    private final long id;
    private ArrayList<Long> parentageChain;
    private boolean isUserSelectable = true;

    public CategoryItemStub(String name, long id, ArrayList<Long> parentageChain) {
        this.name = name;
        this.id = id;
        this.parentageChain = parentageChain;
    }

    public CategoryItemStub(String name, long id) {
        this(name, id, new ArrayList<Long>());
    }

    public Long getParentId() {
        return parentageChain.size() == 0 ? null : parentageChain.get(parentageChain.size() - 1);
    }

    public void setParentageChain(List<Long> parentageChain, long directParent) {
        this.parentageChain = new ArrayList<>(parentageChain);
        this.parentageChain.add(directParent);
    }

    public List<Long> getParentageChain() {
        if (parentageChain == null) {
            return null;
        }
        return Collections.unmodifiableList(parentageChain);
    }

    public void setParentageChain(List<Long> parentageChain) {
        this.parentageChain = new ArrayList<>(parentageChain);
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

    public CategoryItemStub markNonUserSelectable() {
        if (this == ROOT_GALLERY) {
            return ROOT_GALLERY_NON_SELECTABLE;
        }
        this.isUserSelectable = false;
        return this;
    }

    public boolean isUserSelectable() {
        return isUserSelectable;
    }
}