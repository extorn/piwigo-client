package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.HashSet;

/**
 * Created by gareth on 13/09/17.
 */

public class Basket implements Serializable {

    public static final int ACTION_COPY = 1;
    public static final int ACTION_CUT = 2;

    private int action;
    private CategoryItem contentParent;
    private HashSet<ResourceItem> contents = new HashSet<>();

    public void addItem(int action, ResourceItem item, CategoryItem contentParent) {
        // only add items to be appended if the action is the same
        if(action != this.action) {
            contents.clear();
        }
        // only allow items from the same album to be added to the clipboard
        if(contents.size() > 0 && !contents.iterator().next().getParentId().equals(item.getParentId())) {
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

    public void removeItem(ResourceItem item) {
        contents.remove(item);
        if(contents.size() == 0) {
            contentParent = null;
        }
    }

    public HashSet<ResourceItem> getContents() {
        HashSet<ResourceItem> contentsCopy = new HashSet<>(contents.size());
        contentsCopy.addAll(contents);
        return contentsCopy;
    }

    public void clear() {
        contents.clear();
    }

    public long getContentParentId() {
        return contentParent == null ? -1 : contentParent.getId();
    }

    public CategoryItem getContentParent() {
        return contentParent;
    }
}
