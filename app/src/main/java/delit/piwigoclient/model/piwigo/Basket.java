package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;

/**
 * Created by gareth on 13/09/17.
 */

public class Basket implements Parcelable {

    public static final int ACTION_COPY = 1;
    public static final int ACTION_CUT = 2;
    private final HashSet<ResourceItem> contents;
    private int action;
    private CategoryItem contentParent;

    public Basket() {
        contents = new HashSet<>();
    }

    public Basket(Parcel in) {
        contents = ParcelUtils.readHashSet(in, getClass().getClassLoader());
        action = in.readInt();
        contentParent = in.readParcelable(getClass().getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ParcelUtils.writeSet(dest, contents);
        dest.writeInt(action);
        dest.writeParcelable(contentParent, flags);
    }

    public void addItem(int action, ResourceItem item, CategoryItem contentParent) {
        // only add items to be appended if the action is the same
        if (action != this.action) {
            contents.clear();
        }
        // only allow items from the same album to be added to the clipboard
        if (contents.size() > 0 && !contents.iterator().next().getParentId().equals(item.getParentId())) {
            contents.clear();
        }

        this.action = action;
        contents.add(item);
        this.contentParent = contentParent;
    }

    public int getAction() {
        return action;
    }

    public int getItemCount() {
        return contents.size();
    }

    public boolean removeItem(ResourceItem item) {
        boolean altered = contents.remove(item);
        if (contents.size() == 0) {
            contentParent = null;
        }
        return altered;
    }

    public HashSet<ResourceItem> getContents() {
        HashSet<ResourceItem> contentsCopy = new HashSet<>(contents.size());
        contentsCopy.addAll(contents);
        return contentsCopy;
    }

    public void clear() {
        contents.clear();
        contentParent = null;
    }

    public long getContentParentId() {
        return contentParent == null ? -1 : contentParent.getId();
    }

    public CategoryItem getContentParent() {
        return contentParent;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<Basket> CREATOR
            = new Parcelable.Creator<Basket>() {
        public Basket createFromParcel(Parcel in) {
            return new Basket(in);
        }

        public Basket[] newArray(int size) {
            return new Basket[size];
        }
    };
}
