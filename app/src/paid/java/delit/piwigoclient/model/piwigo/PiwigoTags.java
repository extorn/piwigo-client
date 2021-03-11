package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.IntRange;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import delit.libs.core.util.Logging;

/**
 * Created by gareth on 02/01/18.
 */
public class PiwigoTags<T extends Tag> extends IdentifiablePagedList<T> {

    private static final String TAG = "PiwigoTags";
    private int pageSources = 1;

    private Comparator<Tag> tagComparator = new TagComparator();

    public PiwigoTags() {
        super("Tag");
    }

    public PiwigoTags(Parcel in) {
        super(in);
        pageSources = in.readInt();
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        Logging.log(Log.ERROR, TAG, "Unable to reverse the order of the items. Why is this being attempted?");
//        throw new UnsupportedOperationException("cannot reverse the order");
        return false;
    }

    @Override
    protected void sortItems(List<T> items) {
        Collections.sort(items, tagComparator);
    }

    public int getPageSources() {
        return pageSources;
    }

    public void setPageSources(int pageSources) {
        if(getPagesLoadedIdxToSizeMap() > 0) {
            throw new IllegalStateException("cannot update the page sources after a page has been added");
        }
        this.pageSources = pageSources;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(pageSources);
    }

    public int getPagesLoadedIdxToSizeMap() {
        return super.getPagesLoadedIdxToSizeMap()/pageSources;
    }



    public boolean containsTag(String tagName) {
        for (Tag tag : getItems()) {
            if (tag.getName().equals(tagName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int addItemPage(int page, int pageSize, List<T> newItems) {
        throw new UnsupportedOperationException("use addItemPage specifying a page source indicator");
    }

    public int addItemPage(@IntRange(from = 0, to = 5) int pageSourceId, boolean preferExistingItems, int page, int pageSize, List<T> newItems) {

        int realPage = (page * pageSources) + pageSourceId;
        if (getItemCount() == 0) {
            super.addItemPage(realPage, pageSize, newItems);
        } else {
            ArrayList<T> itemsToAdd = new ArrayList<>(newItems);
            if (preferExistingItems) {
                // remove any items loaded into the store already from the new items as the admin items contain less information
                // we use the item id hence
                for (Iterator<T> iterator = itemsToAdd.iterator(); iterator.hasNext(); ) {
                    T item = iterator.next();
                    if(containsItem(item)) {
                        iterator.remove();
                    }
                }
            } else {
                // remove any items loaded into the store already by the admin page so there are no duplicates
                removeAllById(itemsToAdd);
            }
            super.addItemPage(realPage, pageSize, itemsToAdd);
        }
        return -1;
    }

    /**
     * Adds items without affecting the page load count etc
     * @param tags new items
     * @param preferExistingItems should existing items be left alone if already present
     */
    public void addRandomItems(Collection<T> tags, boolean preferExistingItems) {
        if(preferExistingItems) {
            addMissingItems(tags);
        } else {
            removeAllById(tags);
            for(T tag : tags) {
                addItem(tag);
            }
        }
    }

    private static class TagComparator implements Comparator<Tag>, Serializable {

        private static final long serialVersionUID = -3433690704683890667L;

        @Override
        public int compare(Tag o1, Tag o2) {
            // bubble tags with images to the top.
            if (o1.getPhotoCount() == 0 && o2.getPhotoCount() != 0) {
                return 1;
            }
            if (o1.getPhotoCount() != 0 && o2.getPhotoCount() == 0) {
                return -1;
            }
            // sort all tags into name order
            return o1.getName().compareTo(o2.getName());
        }
    }

    public static final Parcelable.Creator<PiwigoTags<?>> CREATOR
            = new Parcelable.Creator<PiwigoTags<?>>() {
        public PiwigoTags<?> createFromParcel(Parcel in) {
            return new PiwigoTags<>(in);
        }

        public PiwigoTags<?>[] newArray(int size) {
            return new PiwigoTags<?>[size];
        }
    };
}