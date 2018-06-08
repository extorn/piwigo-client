package delit.piwigoclient.model.piwigo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Created by gareth on 02/01/18.
 */

public class PiwigoTags implements Serializable, IdentifiableItemStore<Tag> {

    private final ArrayList<Tag> items = new ArrayList<>();
    private int pagesLoaded = 0;

    public PiwigoTags() {
    }

    private transient final Comparator<Tag> tagComparator = new Comparator<Tag>() {

        @Override
        public int compare(Tag o1, Tag o2) {
            // bubble tags with images to the top.
            if(o1.getUsageCount() == 0 && o2.getUsageCount() != 0) {
                return 1;
            }
            if(o1.getUsageCount() != 0 && o2.getUsageCount() == 0) {
                return -1;
            }
            // sort all tags into name order
            return o1.getName().compareTo(o2.getName());
        }
    };

    public void sort() {
        Collections.sort(items, tagComparator);
    }

    public boolean containsTag(String tagName) {
        for(Tag tag : items) {
            if(tag.getName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Tag getItemByIdx(int idx) {
        return items.get(idx);
    }

    @Override
    public ArrayList<Tag> getItems() {
        return items;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public Tag getItemById(long selectedItemId) {
        for (Tag item : items) {
            if(item.getId() == selectedItemId) {
                return item;
            }
        }
        throw new IllegalArgumentException("No Tag present with id : " + selectedItemId);
    }

    public int addItemPage(boolean isAdminPage, HashSet<Tag> tags) {
        pagesLoaded++;
        if(items.size() == 0) {
            items.addAll(tags);
            return 0;
        }
        if(isAdminPage) {
            // remove any already present in the store.
            tags.removeAll(getItems());
        } else {
            // overwrite those already in the store.
            getItems().removeAll(tags);
        }
        int insertAt = items.size();
        items.addAll(tags);
        sort();
        return insertAt;
    }

    public int getPagesLoaded() {
        return pagesLoaded;
    }

    public boolean isFullyLoaded() {
        return pagesLoaded > 0;
    }

    @Override
    public void addItem(Tag newTag) {
        items.add(newTag);
    }

    @Override
    public int getItemIdx(Tag newTag) {
        return items.indexOf(newTag);
    }

    @Override
    public boolean removeAll(Collection<Tag> itemsForDeletion) {
        return items.removeAll(itemsForDeletion);
    }

    @Override
    public void remove(Tag r) {
        items.remove(r);
    }

}
