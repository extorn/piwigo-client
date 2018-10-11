package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

/**
 * Created by gareth on 02/01/18.
 */
public class PiwigoTags extends IdentifiablePagedList<Tag> {

    private int pageSources;

    private Comparator<Tag> tagComparator = new TagComparator();

    public PiwigoTags(int pageSources) {
        super("Username");
        this.pageSources = pageSources;
    }

    public PiwigoTags(Parcel in) {
        super(in);
        pageSources = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(pageSources);
    }

    public int getPagesLoaded() {
        return super.getPagesLoaded()/pageSources;
    }



    public boolean containsTag(String tagName) {
        for (Tag tag : getItems()) {
            if (tag.getName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    public void sort() {
        Collections.sort(getItems(), tagComparator);
    }

    @Override
    public int addItemPage(int page, int pageSize, Collection<Tag> newItems) {
        throw new UnsupportedOperationException("use addItemPage specifying a page source indicator");
    }

    public int addItemPage(@IntRange(from = 0, to = 5) int pageSourceId, boolean preferExistingItems, int page, int pageSize, Collection<Tag> newItems) {
        ArrayList<Tag> items = getItems();
        int realPage = (page * pageSources) + pageSourceId;
        if (items.size() == 0) {
            super.addItemPage(realPage, pageSize, newItems);
        } else {
            if (preferExistingItems) {
                // remove any items loaded into the store already from the new items as the admin items contain less information
                newItems.removeAll(getItems());
            } else {
                // remove any items loaded into the store already by the admin page so there are no duplicates
                items.removeAll(newItems);
            }
            super.addItemPage(realPage, pageSize, newItems);
        }
        sort();
        return -1;
    }

    /**
     * Adds items without affecting the page load count etc
     * @param tags new items
     * @param preferExistingItems should existing items be left alone if already present
     */
    public void addRandomItems(HashSet<Tag> tags, boolean preferExistingItems) {
        if(preferExistingItems) {
            tags.removeAll(getItems());
        } else {
            getItems().removeAll(tags);
        }
        getItems().addAll(tags);
    }

    private static class TagComparator implements Comparator<Tag> {

        @Override
        public int compare(Tag o1, Tag o2) {
            // bubble tags with images to the top.
            if (o1.getUsageCount() == 0 && o2.getUsageCount() != 0) {
                return 1;
            }
            if (o1.getUsageCount() != 0 && o2.getUsageCount() == 0) {
                return -1;
            }
            // sort all tags into name order
            return o1.getName().compareTo(o2.getName());
        }
    }

    public static final Parcelable.Creator<PiwigoTags> CREATOR
            = new Parcelable.Creator<PiwigoTags>() {
        public PiwigoTags createFromParcel(Parcel in) {
            return new PiwigoTags(in);
        }

        public PiwigoTags[] newArray(int size) {
            return new PiwigoTags[size];
        }
    };
}