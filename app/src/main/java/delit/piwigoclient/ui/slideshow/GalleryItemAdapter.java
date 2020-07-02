package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

public class GalleryItemAdapter<T extends Identifiable & Parcelable, S extends ViewPager, P extends SlideshowItemFragment<? extends ResourceItem>> extends MyFragmentRecyclerPagerAdapter<P, S> {

    private static final String TAG = "GalleryItemAdapter";
    private final List<Integer> galleryResourceItems;
    private boolean shouldShowVideos;
    private ResourceContainer<T, GalleryItem> gallery;
    private Class<? extends ViewModelContainer> galleryModelClass;
    private HashMap<Long, Integer> cachedItemPositions; // id of item, against Slideshow item's position in slideshow pager

    public GalleryItemAdapter(Class<? extends ViewModelContainer> galleryModelClass, ResourceContainer<T, GalleryItem> gallery, boolean shouldShowVideos, int selectedItem, FragmentManager fm) {
        super(fm);
        this.galleryModelClass = galleryModelClass;
        this.gallery = gallery;
        galleryResourceItems = new ArrayList<>(gallery.getResourcesCount());
        this.shouldShowVideos = shouldShowVideos;
        addResourcesToIndex(0, gallery.getItems().size(), selectedItem); // use get items.size to ignore issues when hide
    }

    private void addResourcesToIndex(int fromIdx, int items, int selectedItem) {
        int toIndex = fromIdx;
        if (fromIdx == 0) {
            // need to reload all items.
            galleryResourceItems.clear();
            toIndex += gallery.getItemCount();
        }

        for (int i = fromIdx; i < toIndex; i++) {
            GalleryItem currentItem = gallery.getItemByIdx(i);
            if (!(currentItem instanceof ResourceItem)) {
//                if (gallery.getItemCount() < items) {
//                    toIndex++;
//                }
                continue;
            }
            if (!shouldShowVideos && currentItem instanceof VideoResourceItem && i != selectedItem) {
                continue;
            }
            galleryResourceItems.add(i);
        }
        if (cachedItemPositions == null) {
            cachedItemPositions = new HashMap<>(galleryResourceItems.size());
        }
    }


    public int getRawGalleryItemPosition(int slideshowPosition) {
        return galleryResourceItems.get(slideshowPosition);
    }


    @Override
    public int getItemPosition(@NonNull Object item) {
        ResourceItem model = ((SlideshowItemFragment) item).getModel();
        int fullGalleryIdx = gallery.getDisplayIdx(model);
        if(fullGalleryIdx < 0) {
            return POSITION_NONE;
        }
        int newIndexPosition = galleryResourceItems.indexOf(fullGalleryIdx);
        if (newIndexPosition < 0) {
            return POSITION_NONE;
        }
        Integer currentCachedPosition = cachedItemPositions.get(model.getId());
        if(currentCachedPosition != null && currentCachedPosition == newIndexPosition) {
            return POSITION_UNCHANGED;
        }
        cachedItemPositions.put(model.getId(), newIndexPosition);
        return newIndexPosition;
    }

    @Override
    public int getCount() {
        return galleryResourceItems.size();
    }

    private int getTotalSlideshowItems() {
        int ignoredResourceCount = gallery.getResourcesCount() - galleryResourceItems.size();
        return gallery.isFullyLoaded() ? galleryResourceItems.size() : gallery.getImgResourceCount() - ignoredResourceCount;
    }

    @Override
    public Class<? extends P> getFragmentType(int position) {
        int slideshowIdx = galleryResourceItems.get(position);
        GalleryItem galleryItem = null;
        try {
            galleryItem = gallery.getItemByIdx(slideshowIdx);
            if (galleryItem instanceof PictureResourceItem) {
                if ("gif".equalsIgnoreCase(((PictureResourceItem) galleryItem).getFileExtension())) {
                    return (Class<? extends P>) AlbumGifPictureItemFragment.class;
                } else {
                    return (Class<? extends P>) AlbumPictureItemFragment.class;
                }
            } else if (galleryItem instanceof VideoResourceItem) {
                return (Class<? extends P>) AlbumVideoItemFragment.class;
            }
            //TODO check what causes this - probably deleting all items and trying to show a header!
            throw new IllegalArgumentException("Unsupported slideshow item type at position " + position + "(" + galleryItem + " " + galleryItem.getClass().getName() + ")");
        } catch(IndexOutOfBoundsException e) {
            Logging.log(Log.ERROR, TAG, "The gallery has %1$d items. Requested item that isn't present.", gallery.getItemCount());
        }
        throw new IllegalArgumentException("Unsupported slideshow item type ("+galleryItem+") at slideshow position " + position + " and slideshow idx " + slideshowIdx + " with gallery of size " + gallery.getItemCount());
    }

    @Override
    protected P createNewItem(Class<? extends P> fragmentTypeNeeded, int position) {
        P item = instantiateItem(fragmentTypeNeeded);
        if (item == null) {
            throw new RuntimeException("unsupported gallery item.");
        }

        GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
        int totalSlideshowItems = getTotalSlideshowItems();
        if (galleryItem instanceof PictureResourceItem) {
            Bundle b = AlbumPictureItemFragment.buildArgs(galleryModelClass, gallery.getId(), galleryItem.getId(), position, galleryResourceItems.size(), totalSlideshowItems);
            item.setArguments(b);
        } else if (galleryItem instanceof VideoResourceItem) {
            Bundle args = AbstractAlbumVideoItemFragment.buildArgs(galleryModelClass, gallery.getId(), galleryItem.getId(), position, galleryResourceItems.size(), totalSlideshowItems, false);
            item.setArguments(args);
        }
        return item;
    }

    public int getSlideshowIndex(int rawCurrentGalleryItemPosition) {
        int idx = galleryResourceItems.indexOf(rawCurrentGalleryItemPosition);
        if (idx < 0) {
            Logging.log(Log.WARN, TAG, String.format(Locale.getDefault(), "Slideshow does not contain album item with index position (%1$d) (only have %2$d items available) - probably deleted it - will show first available.", rawCurrentGalleryItemPosition, galleryResourceItems.size()));
            if(galleryResourceItems.size() > 0) {
                return 0;
            }
            if (gallery.getItemCount() == 0) {
                throw new IllegalArgumentException("This slideshow is empty (empty album) and should not have been opened!");
            } else {
                throw new IllegalArgumentException("This slideshow is empty (all album content filtered) and should not have been opened!");
            }
        }
        return idx;
    }

    public void deleteGalleryItem(int fullGalleryIdx) {
        int slideshowIdxOfItemToDelete = galleryResourceItems.indexOf(fullGalleryIdx);
        deleteItem(slideshowIdxOfItemToDelete);
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        SlideshowItemFragment selectedPage = getActiveFragment(position);
        int pagerIndex = -1;
        if (selectedPage != null) {
            pagerIndex = selectedPage.getPagerIndex();
            if (pagerIndex < 0) {
                throw new RuntimeException("Error pager index invalid!");
            }
        }
        if (selectedPage == null || pagerIndex == position) {
            // this if is needed because I am calling destroy item, but this is handled within the ViewPager too... I'm not sure why I am calling it any more, but
            // things don't work if I don't.
            super.destroyItem(container, position, object);
        }
    }

    public GalleryItem getItemByPagerPosition(int position) {
        return gallery.getItemByIdx(galleryResourceItems.get(position));
    }

    private void deleteItem(int itemIdx) {
        if (itemIdx >= 0) {
            // remove the item from the list of items in the slideshow.
            galleryResourceItems.remove(itemIdx);

            // presume that the parent gallery has also been updated and adjust all items down one.
            for(int i = itemIdx; i < galleryResourceItems.size(); i++) {
                galleryResourceItems.set(i, galleryResourceItems.get(i)-1);
            }
            // now request a rebuild of the slideshow pages

            onDeleteItem(getContainer(), itemIdx);
        }
    }

    @Override
    protected void onItemDeleted(P fragment) {
        super.onItemDeleted(fragment);
        Integer cachedPosition = cachedItemPositions.remove(fragment.getModel().getId());
    }

    @Override
    public void notifyDataSetChanged() {
        EventBus.getDefault().post(new SlideshowSizeUpdateEvent(galleryResourceItems.size(), getTotalSlideshowItems()));
        super.notifyDataSetChanged();
    }

    public void setShouldShowVideos(boolean shouldShowVideos) {
        this.shouldShowVideos = shouldShowVideos;
    }

    @Override
    public void onDataAppended(int firstPositionAddedAt, int itemsAddedCount) {
        addResourcesToIndex(firstPositionAddedAt, itemsAddedCount, -1);
        notifyDataSetChanged();
    }
}
