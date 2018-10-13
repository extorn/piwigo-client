package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.piwigoclient.ui.common.util.ParcelUtils;

public class CategoryItemStub implements Parcelable, Identifiable {

    public static final CategoryItemStub ROOT_GALLERY = new CategoryItemStub(CategoryItem.ROOT_ALBUM.getName(), CategoryItem.ROOT_ALBUM.getId());
    private static final CategoryItemStub ROOT_GALLERY_NON_SELECTABLE = new CategoryItemStub(CategoryItem.ROOT_ALBUM.getName(), CategoryItem.ROOT_ALBUM.getId()).markNonUserSelectable();
    private static final long serialVersionUID = -825032789826191701L;
    private final long id;
    private final String name;
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

    public CategoryItemStub(Parcel in) {
        id = in.readLong();
        name = in.readString();
        parentageChain = ParcelUtils.readLongArrayList(in, null);
        isUserSelectable = (boolean)in.readValue(null);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(name);
        ParcelUtils.writeLongArrayList(dest,parentageChain);
        dest.writeValue(isUserSelectable);
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

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<CategoryItemStub> CREATOR
            = new Parcelable.Creator<CategoryItemStub>() {
        public CategoryItemStub createFromParcel(Parcel in) {
            return new CategoryItemStub(in);
        }

        public CategoryItemStub[] newArray(int size) {
            return new CategoryItemStub[size];
        }
    };
}