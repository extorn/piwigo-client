package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Identifiable;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.common.list.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;

public class GalleryItemAdapter<T extends Identifiable&Parcelable, S extends ViewPager> extends MyFragmentRecyclerPagerAdapter {

    private static final String TAG = "GalleryItemAdapter";
    private final List<Integer> galleryResourceItems;
    private boolean shouldShowVideos;
    private ResourceContainer<T, GalleryItem> gallery;
    private HashMap<Long, Integer> cachedItemPositions; // id of item, against Slideshow item's position in slideshow pager
    private S container;
    private int lastPosition = -1;

    public GalleryItemAdapter(ResourceContainer<T, GalleryItem> gallery, boolean shouldShowVideos, int selectedItem, FragmentManager fm) {
        super(fm);
        this.gallery = gallery;
        galleryResourceItems = new ArrayList<>(gallery.getResourcesCount());
        this.shouldShowVideos = shouldShowVideos;
        addResourcesToIndex(0, gallery.getItemCount(), selectedItem);
    }

    private void addResourcesToIndex(int fromIdx, int items, int selectedItem) {
        if (fromIdx == 0) {
            // need to reload all items.
            galleryResourceItems.clear();
            fromIdx = 0;
            items = gallery.getItemCount();
        }
        for (int i = fromIdx; i < fromIdx + items; i++) {
            GalleryItem currentItem = gallery.getItemByIdx(i);
            if (!(currentItem instanceof ResourceItem)) {
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
    public Class<? extends Fragment> getFragmentType(int position) {
        int slideshowIdx = galleryResourceItems.get(position);
        GalleryItem galleryItem = gallery.getItemByIdx(slideshowIdx);
        if (galleryItem instanceof PictureResourceItem) {
            if("gif".equalsIgnoreCase(((PictureResourceItem) galleryItem).getFileExtension())) {
                return AlbumGifPictureItemFragment.class;
            } else {
                return  AlbumPictureItemFragment.class;
            }
        } else if (galleryItem instanceof VideoResourceItem) {
            return AlbumVideoItemFragment.class;
        }
        throw new IllegalArgumentException("Unsupported slideshow item type at position " + position);
    }

    @Override
    public Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position) {

        GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
        SlideshowItemFragment fragment = null;
        int totalSlideshowItems = getTotalSlideshowItems();
        if (galleryItem instanceof PictureResourceItem) {
            if("gif".equalsIgnoreCase(((PictureResourceItem) galleryItem).getFileExtension())) {
                fragment = new AlbumGifPictureItemFragment();
            } else {
                fragment = new AlbumPictureItemFragment();
            }
            Bundle b = AlbumPictureItemFragment.buildArgs((PictureResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems);
            fragment.setArguments(b);
        } else if (galleryItem instanceof VideoResourceItem) {
            fragment = new AlbumVideoItemFragment();
            Bundle args = AlbumVideoItemFragment.buildArgs((VideoResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems, false);
            fragment.setArguments(args);
        }
        if (fragment != null) {
            return fragment;
        }
        //TODO handle this better.
        throw new RuntimeException("unsupported gallery item.");
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        SlideshowItemFragment fragment = (SlideshowItemFragment) super.instantiateItem(container, position);
        if (position == ((ViewPager) container).getCurrentItem()) {
            if (lastPosition >= 0 && lastPosition != position) {
                onPageDeselected(lastPosition);
            }
            lastPosition = position;
        }
        return fragment;
    }

    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        super.setPrimaryItem(container, position, object);
        SlideshowItemFragment activeFragment = ((SlideshowItemFragment)getActiveFragment(position));
        if(activeFragment == null) {
            activeFragment = (SlideshowItemFragment)instantiateItem(container, position);
//            activeFragment.onPageSelected();
        }
        activeFragment.onPageSelected();
    }

    public void onPageSelected(int position) {
        Fragment managedFragment = getActiveFragment(position);
        if (managedFragment != null) {
            // if this slideshow item still exists (not been deleted by user)
            SlideshowItemFragment selectedPage = (SlideshowItemFragment) managedFragment;
            selectedPage.onPageSelected();
        }
    }

    public void onPageDeselected(int position) {
        Fragment managedFragment = getActiveFragment(position);
        if (managedFragment != null) {
            // if this slideshow item still exists (not been deleted by user)
            SlideshowItemFragment selectedPage = (SlideshowItemFragment) managedFragment;
            selectedPage.onPageDeselected();
        }
    }

    public int getSlideshowIndex(int rawCurrentGalleryItemPosition) {
        int idx = galleryResourceItems.indexOf(rawCurrentGalleryItemPosition);
        if (idx < 0) {
            Crashlytics.log(Log.WARN, TAG, String.format("Slideshow does not contain album item with index position (%1$d) (only have %2$d items available) - probably deleted it - will show first available.", rawCurrentGalleryItemPosition, galleryResourceItems.size()));
            if(galleryResourceItems.size() > 0) {
                return 0;
            }
            throw new IllegalArgumentException("This slideshow is empty and should not have been opened!");
        }
        return idx;
    }

    public void deleteGalleryItem(int fullGalleryIdx) {
        int slideshowIdxOfItemToDelete = galleryResourceItems.indexOf(fullGalleryIdx);
        deleteItem(slideshowIdxOfItemToDelete);
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        SlideshowItemFragment selectedPage = (SlideshowItemFragment) getActiveFragment(position);
        if(selectedPage == null || selectedPage.getPagerIndex() == position) {
            // this if is needed because I am calling destroy item, but this is handled within the ViewPager too... I'm not sure why I am calling it any more, but
            // things don't work if I don't.
            super.destroyItem(container, position, object);
        }
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
    public void onDeleteItem(ViewGroup container, int position) {
        SlideshowItemFragment selectedPage = (SlideshowItemFragment) getActiveFragment(position);
        selectedPage.onPageDeselected();
        Integer cachedPosition = cachedItemPositions.remove(selectedPage.getModel().getId());

        super.onDeleteItem(container, position);
//        selectedPage = (SlideshowItemFragment) getActiveFragment(position);
//        selectedPage.onPageSelected();
    }

    //
//    public void onResume() {
//        int pageToShow = Math.max(0, getContainer().getCurrentItem());
//        if(pageToShow < galleryResourceItems.size()) {
//            Fragment selectedPage = (Fragment)instantiateItem(getContainer(), pageToShow);
//            if (selectedPage instanceof AlbumVideoItemFragment) {
//                AlbumVideoItemFragment vidFrag = (AlbumVideoItemFragment)selectedPage;
//                vidFrag.onPageSelected();
//            }
//        } else {
//            // immediately leave this screen. For whatever reason, we can't show a valid item.
//            getFragmentManager().popBackStack();
//        }
//    }

    @Override
    public void notifyDataSetChanged() {
//        if (galleryResourceItems.size() > 0) {
//            int lastLoadedIdx = galleryResourceItems.get(galleryResourceItems.size() - 1);
//            addResourcesToIndex(1 + lastLoadedIdx, -1);
//        }
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

    public S getContainer() {
        return container;
    }

    public void setContainer(S container) {
        this.container = container;
    }
}
