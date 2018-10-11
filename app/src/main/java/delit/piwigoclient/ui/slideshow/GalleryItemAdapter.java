package delit.piwigoclient.ui.slideshow;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.ViewGroup;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
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

    private final List<Integer> galleryResourceItems;
    private boolean shouldShowVideos;
    private ResourceContainer<T, GalleryItem> gallery;
    private S container;
    private int lastPosition = -1;

    public GalleryItemAdapter(ResourceContainer<T, GalleryItem> gallery, boolean shouldShowVideos, int selectedItem, FragmentManager fm) {
        super(fm);
        this.gallery = gallery;
        galleryResourceItems = new ArrayList<>(gallery.getResourcesCount());
        this.shouldShowVideos = shouldShowVideos;
        addResourcesToIndex(0, selectedItem);
    }

    private void addResourcesToIndex(int fromIdx, int selectedItem) {
        for (int i = fromIdx; i < gallery.getItemCount(); i++) {
            if (!(gallery.getItemByIdx(i) instanceof ResourceItem)) {
                continue;
            }
            if (!shouldShowVideos && gallery.getItemByIdx(i) instanceof VideoResourceItem && i != selectedItem) {
                continue;
            }
            galleryResourceItems.add(i);
        }
    }


    public int getRawGalleryItemPosition(int slideshowPosition) {
        return galleryResourceItems.get(slideshowPosition);
    }


    @Override
    public int getItemPosition(@NonNull Object item) {
        ResourceItem model = ((SlideshowItemFragment) item).getModel();
        int fullGalleryIdx = gallery.getItemIdx(model);
        int newIndexPosition = galleryResourceItems.indexOf(fullGalleryIdx);
        if (newIndexPosition < 0) {
            return POSITION_NONE;
        }
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
            return AlbumPictureItemFragment.class;
        } else if (galleryItem instanceof VideoResourceItem) {
            return AlbumVideoItemFragment.class;
        }
        throw new IllegalArgumentException("Unsupported slideshow item type at position " + position);
    }

    @Override
    protected void bindDataToFragment(Fragment fragment, int position) {
        GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
        int totalSlideshowItems = getTotalSlideshowItems();

        if (galleryItem instanceof PictureResourceItem) {
            fragment.setArguments(SlideshowItemFragment.buildArgs((PictureResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems));
        } else if (galleryItem instanceof VideoResourceItem) {
            fragment.setArguments(AlbumVideoItemFragment.buildArgs((VideoResourceItem) galleryItem, position, galleryResourceItems.size(), totalSlideshowItems, false));
        }
    }

    @Override
    public Fragment createNewItem(Class<? extends Fragment> fragmentTypeNeeded, int position) {

        GalleryItem galleryItem = gallery.getItemByIdx(galleryResourceItems.get(position));
        SlideshowItemFragment fragment = null;
        int totalSlideshowItems = getTotalSlideshowItems();
        if (galleryItem instanceof PictureResourceItem) {
            fragment = new AlbumPictureItemFragment();
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
        if (fragment != null && position == ((ViewPager) container).getCurrentItem()) {
            if (lastPosition >= 0) {
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
            throw new IllegalStateException("Item to show was not found in the gallery - weird!");
        }
        return idx;
    }

    public void deleteGalleryItem(int fullGalleryIdx) {
        int slideshowIdxOfItemToDelete = galleryResourceItems.indexOf(fullGalleryIdx);
        if (slideshowIdxOfItemToDelete >= 0) {
            // remove the item from the list of items in the slideshow.
            galleryResourceItems.remove(slideshowIdxOfItemToDelete);

            // presume that the parent gallery has also been updated and adjust all items down one.
            for(int i = slideshowIdxOfItemToDelete; i < galleryResourceItems.size(); i++) {
                galleryResourceItems.set(i, galleryResourceItems.get(i)-1);
            }
            // now request a rebuild of the slideshow pages
//            notifyDataSetChanged();

            // the object is not used by this.
            super.destroyItem(getContainer(),slideshowIdxOfItemToDelete, getActiveFragment(slideshowIdxOfItemToDelete));

            notifyDataSetChanged();
        }
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

    public S getContainer() {
        return container;
    }

    public void setContainer(S container) {
        this.container = container;
    }
}
