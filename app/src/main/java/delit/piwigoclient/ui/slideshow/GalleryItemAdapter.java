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
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;
import delit.piwigoclient.ui.model.ViewModelContainer;

public class GalleryItemAdapter<T extends Identifiable & Parcelable, VP extends ViewPager, SIF extends SlideshowItemFragment<SIF,FUIH,ResourceItem>,FUIH extends FragmentUIHelper<FUIH,SIF>> extends MyFragmentRecyclerPagerAdapter<SIF, VP> {

    private static final String TAG = "GalleryItemAdapter";
    private final List<Integer> galleryResourceItemsFullGalleryIdx;
    private boolean shouldShowVideos;
    private final ResourceContainer<T, GalleryItem> gallery;
    private final Class<? extends ViewModelContainer> galleryModelClass;
    private HashMap<Long, Integer> cachedItemPositions; // id of item, against Slideshow item's position in slideshow pager

    public GalleryItemAdapter(Class<? extends ViewModelContainer> galleryModelClass, ResourceContainer<T, GalleryItem> gallery, boolean shouldShowVideos, int showGalleryItemIdx, FragmentManager fm) {
        super(fm);
        this.galleryModelClass = galleryModelClass;
        this.gallery = gallery;
        galleryResourceItemsFullGalleryIdx = new ArrayList<>(gallery.getResourcesCount());
        this.shouldShowVideos = shouldShowVideos;
        int firstGalleryIdxToImport = gallery.getFirstResourceIdx();
        addResourcesToIndex(firstGalleryIdxToImport, gallery.getResourcesCount(), showGalleryItemIdx); // use get items.size to ignore issues when hide
    }

    private void addResourcesToIndex(int firstGalleryIdxToImport, int maxSlideshowItemCount, int selectedItem) {
        int scanToRawIndex = gallery.getItemCount() - 1;
        if (firstGalleryIdxToImport == 0) {
            // need to reload all items.
            galleryResourceItemsFullGalleryIdx.clear();
        }
        int resourcesAddedToSlideshow = 0;
        for (int rawGalleryIdx = firstGalleryIdxToImport; rawGalleryIdx <= scanToRawIndex; rawGalleryIdx++) {
            GalleryItem currentItem = gallery.getItemByIdx(rawGalleryIdx);
            if (!(currentItem instanceof ResourceItem)) {
                // skip any child albums and other non resource items
                continue;
            }
            if(!currentItem.isFromServer()) {
                // skip any blanks, heading, etc
                continue;
            }
            if (!shouldShowVideos && currentItem instanceof VideoResourceItem && rawGalleryIdx != selectedItem) {
                continue;
            }
            galleryResourceItemsFullGalleryIdx.add(rawGalleryIdx);
            resourcesAddedToSlideshow++;
            if(resourcesAddedToSlideshow == maxSlideshowItemCount) {
                break;
            }
        }
        if (cachedItemPositions == null) {
            cachedItemPositions = new HashMap<>(galleryResourceItemsFullGalleryIdx.size());
        }
    }


    public int getFullGalleryItemIdxFromSlideshowIdx(int slideshowPosition) {
        return galleryResourceItemsFullGalleryIdx.get(slideshowPosition);
    }


    @Override
    public int getItemPosition(@NonNull Object item) {
        ResourceItem model = ((SlideshowItemFragment<?,?,?>) item).getModel();
        int fullGalleryIdx = gallery.getItemIdx(model);
        if(fullGalleryIdx < 0) {
            return POSITION_NONE;
        }
        int newIndexPosition = galleryResourceItemsFullGalleryIdx.indexOf(fullGalleryIdx);
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
        return galleryResourceItemsFullGalleryIdx.size();
    }

    private int getTotalSlideshowItems() {
        int ignoredResourceCount = gallery.getResourcesCount() - galleryResourceItemsFullGalleryIdx.size();
        return gallery.isFullyLoaded() ? galleryResourceItemsFullGalleryIdx.size() : gallery.getImgResourceCount() - ignoredResourceCount;
    }

    @Override
    public Class<? extends SIF> getFragmentType(int position) {
        int slideshowIdx = galleryResourceItemsFullGalleryIdx.get(position);
        GalleryItem galleryItem = null;
        try {
            galleryItem = gallery.getItemByIdx(slideshowIdx);
            if (galleryItem instanceof PictureResourceItem) {
                if ("gif".equalsIgnoreCase(((PictureResourceItem) galleryItem).getFileExtension())) {
                    return (Class<? extends SIF>)(Class) AlbumGifPictureItemFragment.class;
                } else {
                    return (Class<? extends SIF>)(Class) AlbumPictureItemFragment.class;
                }
            } else if (galleryItem instanceof VideoResourceItem) {
                return (Class<? extends SIF>)(Class) AlbumVideoItemFragment.class;
            }
            //TODO check what causes this - probably deleting all items and trying to show a header!
            throw new IllegalArgumentException("Unsupported slideshow item type at position " + position + "(" + galleryItem + " " + Utils.getId(galleryItem) + ")");
        } catch(IndexOutOfBoundsException e) {
            Logging.log(Log.ERROR, TAG, "The gallery has %1$d items. Requested item that isn't present.", gallery.getItemCount());
        }
        throw new IllegalArgumentException("Unsupported slideshow item type ("+galleryItem+") at slideshow position " + position + " and slideshow idx " + slideshowIdx + " with gallery of size " + gallery.getItemCount());
    }

    @Override
    protected SIF createNewItem(Class<? extends SIF> fragmentTypeNeeded, int position) {
        SIF item = instantiateItem(fragmentTypeNeeded);
        if (item == null) {
            throw new RuntimeException("unsupported gallery item.");
        }

        GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItemsFullGalleryIdx.get(position));
        int totalSlideshowItems = getTotalSlideshowItems();
        if (galleryItem instanceof PictureResourceItem) {
            Bundle b = AlbumPictureItemFragment.buildArgs(galleryModelClass, gallery.getId(), galleryItem.getId(), position, galleryResourceItemsFullGalleryIdx.size(), totalSlideshowItems);
            item.setArguments(b);
        } else if (galleryItem instanceof VideoResourceItem) {
            Bundle args = AbstractAlbumVideoItemFragment.buildArgs(galleryModelClass, gallery.getId(), galleryItem.getId(), position, galleryResourceItemsFullGalleryIdx.size(), totalSlideshowItems, false);
            item.setArguments(args);
        }
        return item;
    }

    public int getSlideshowIndexUsingFullGalleryIdx(int itemFullGalleryIdx) {
        int idx = galleryResourceItemsFullGalleryIdx.indexOf(itemFullGalleryIdx);
        if (idx < 0) {
            Logging.log(Log.WARN, TAG, String.format(Locale.getDefault(), "Slideshow does not contain album item with index position (%1$d) (only have %2$d items available) - probably deleted it - will show first available.", itemFullGalleryIdx, galleryResourceItemsFullGalleryIdx.size()));
            if(galleryResourceItemsFullGalleryIdx.size() > 0) {
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

    public void deleteGalleryItem(int adapterItemIdx) {
        deleteItem(adapterItemIdx);
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        SIF selectedPage = getActiveFragment(position);
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
        return gallery.getItemByIdx(galleryResourceItemsFullGalleryIdx.get(position));
    }

    private void deleteItem(int adapterItemIdx) {
        if (adapterItemIdx >= 0) {
            int itemFullGalleryIdx = getFullGalleryItemIdxFromSlideshowIdx(adapterItemIdx);
            // remove the item from the list of items in the slideshow.
            int expectedFullGalleryIdx = galleryResourceItemsFullGalleryIdx.remove(adapterItemIdx);
            if(itemFullGalleryIdx != expectedFullGalleryIdx) {
                Logging.log(Log.ERROR, TAG, "Cached full gallery idxs is out of sync with actual values when starting item delete");
            }

            // presume that the parent gallery has also been updated and adjust all items down one.
            int fromIdx = adapterItemIdx;
            int toIdx = galleryResourceItemsFullGalleryIdx.size() -1;
            if(gallery.isRetrieveItemsInReverseOrder()) {
                fromIdx = 0;
                toIdx = adapterItemIdx-1;
            }
            for(int i = fromIdx; i <= toIdx; i++) {
                galleryResourceItemsFullGalleryIdx.set(i, galleryResourceItemsFullGalleryIdx.get(i)-1);
            }
            // now request a rebuild of the slideshow pages
            onDeleteItem(getContainer(), adapterItemIdx);
        }
    }

    @Override
    protected void onItemDeleted(SIF fragment) {
        super.onItemDeleted(fragment);
        Integer cachedPosition = cachedItemPositions.remove(fragment.getModel().getId());
    }

    @Override
    public void notifyDataSetChanged() {
        EventBus.getDefault().post(new SlideshowSizeUpdateEvent(galleryResourceItemsFullGalleryIdx.size(), getTotalSlideshowItems()));
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

    public boolean isOutOfDate() {
        try {
            int maxAlbumIdxInSlideshow = galleryResourceItemsFullGalleryIdx.get(galleryResourceItemsFullGalleryIdx.size() - 1);
            if (maxAlbumIdxInSlideshow > gallery.getItemCount() - 1) {
                return true;
            }
            return false;
        } catch(ArrayIndexOutOfBoundsException e) {
            return true;
        }
    }
}
