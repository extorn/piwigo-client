package delit.piwigoclient.model.piwigo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import delit.libs.core.util.Logging;
import delit.piwigoclient.test.CategoryItemFactory;
import delit.piwigoclient.test.IdentifiableItemFactory;
import delit.piwigoclient.test.ItemLoadPage;
import delit.piwigoclient.test.PiwigoResourceUtil;
import delit.piwigoclient.test.ResourceItemFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class PiwigoAlbumTest {
    private static MockedStatic<Logging> mockLogging;
    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private Logger logger = Logger.getLogger(PiwigoAlbumTest.class.getName());
    private ResourceItemFactory resourceItemFactory;
    private CategoryItemFactory categoryItemFactory;

    @BeforeClass
    public static void beforeClass() {
        mockLogging = Mockito.mockStatic(Logging.class);
    }

    @AfterClass
    public static void afterClass() {
        mockLogging.close();
    }

    @Before
    public void setUp() throws Exception {
        logger.log(Level.ALL, "Logger initialised");
        resourceItemFactory = new ResourceItemFactory();
        categoryItemFactory = new CategoryItemFactory();
        IdentifiableItemFactory.resetId();
        ItemLoadPage.resetLoadId();
    }

    @After
    public void afterTest() {
        mockLogging.reset();
    }

    @After
    public void tearDown() throws Exception {
    }


    @Test
    public void testItemReplacementJustResources() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean reversed = false;
        int photosLoaded = 15;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, 3, reversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, photosLoaded));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveResourcesInReverseOrder(reversed);
        loadResourceItemPages(resourceItemLoadPages, album, new ResourceItemReplacementAction());
    }

    @Test
    public void testRemoveAllCategoriesJustCategories() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, 0);
        album.removeAllAlbums();
        assertEquals(0, album.getItemCount());

        album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, 3);
        album.removeAllAlbums();
        assertEquals(0, album.getItemCount());
    }

    @Test
    public void testRemoveAllCategoriesMixedPartial() {
        int loadedAlbumCount = 20;
        int sortOrder= PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, loadedAlbumCount);
        int photosLoaded = 25;
        List<Integer> skipPages = Arrays.asList(2,3);
        int spacerAlbums = 3;
        int pageSize = 5;
        boolean isReversed = false;

        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false,spacerAlbums);
        loadResourceItemPages(resourceItemLoadPages, album);
        int resourcesLoaded = PiwigoResourceUtil.countItemsInPagesLessThan(Integer.MAX_VALUE, resourceItemLoadPages);
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        album.removeAllAlbums();
        assertEquals(resourcesLoaded + album.getBannerCount(), album.getItemCount());
    }

    @Test
    public void testRemoveAllResourcesMixedPartial() {
        int loadedAlbumCount = 20;
        int sortOrder= PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, loadedAlbumCount);
        int photosLoaded = 25;
        List<Integer> skipPages = Arrays.asList(2,3);
        int spacerAlbums = 3;
        int pageSize = 5;
        boolean isReversed = false;

        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false,spacerAlbums);
        loadResourceItemPages(resourceItemLoadPages, album);
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        album.removeAllResources();
        assertEquals(categoryItemLoad.size() + spacerAlbums + album.getBannerCount(), album.getItemCount());
    }

    @Test
    public void testRemoveAllResourcesMixedPartialReversed() {
        int loadedAlbumCount = 20;
        int sortOrder= PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, loadedAlbumCount);
        int photosLoaded = 25;
        List<Integer> skipPages = Arrays.asList(2,3);
        int spacerAlbums = 3;
        int pageSize = 5;
        boolean isReversed = true;

        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false,spacerAlbums);
        loadResourceItemPages(resourceItemLoadPages, album);
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        album.removeAllResources();
        assertEquals(categoryItemLoad.size() + spacerAlbums + album.getBannerCount(), album.getItemCount());
    }

    @Test
    public void testRemoveAllCategoriesJustCategoriesReversed() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, 0);
        album.removeAllAlbums();
        assertEquals(0, album.getItemCount());

        album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, 3);
        album.removeAllAlbums();
        assertEquals(0, album.getItemCount());
    }

    @Test
    public void testRemoveAllCategoriesMixedPartialReversed() {
        int loadedAlbumCount = 20;
        int sortOrder= PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, loadedAlbumCount);
        int photosLoaded = 25;
        List<Integer> skipPages = Arrays.asList(2,3);
        int spacerAlbums = 3;
        int pageSize = 5;
        boolean isReversed = true;

        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false,spacerAlbums);
        loadResourceItemPages(resourceItemLoadPages, album);
        int resourcesLoaded = PiwigoResourceUtil.countItemsInPagesLessThan(Integer.MAX_VALUE, resourceItemLoadPages);
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        album.removeAllAlbums();
        assertEquals(resourcesLoaded + album.getBannerCount(), album.getItemCount());
    }


    @Test
    public void testItemReplacementJustCategories() {
        loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, 0);
    }

    private PiwigoAlbum<CategoryItem, GalleryItem> loadCategories(int sortOrder, boolean reversed, int spacerAlbums) {
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem, GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveResourcesInReverseOrder(reversed);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false, spacerAlbums, new CategoryItemReplacementAction());
        return album;
    }

    @Test
    public void testItemReplacementMixedContentSortingByName() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 2, new MultiItemReplacementAction());
    }

    @Test
    public void testItemReplacementMixedContentSortingByNameReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true, 2, new MultiItemReplacementAction());
    }

    @Test
    public void testAllCategoriesUpdateSpacerCountWhileHideAlbumsOrderByName() {
        testAllCategoriesUpdatingSpacerCount(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false);
    }

    @Test
    public void testAllCategoriesUpdateSpacerCountWhileHideAlbumsOrderByNameReversed() {
        testAllCategoriesUpdatingSpacerCount(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true);
    }

    @Test
    public void testAllCategoriesUpdateSpacerCountWhileHideAlbumsOrderByDefault() {
        testAllCategoriesUpdatingSpacerCount(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false);
    }

    @Test
    public void testAllCategoriesUpdateSpacerCountWhileHideAlbumsOrderByDefaultReversed() {
        testAllCategoriesUpdatingSpacerCount(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true);
    }

    private void testAllCategoriesUpdatingSpacerCount(int sortOrder, boolean reversed) {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategories(sortOrder, reversed, 0);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        int itemCount = album.getItemCount();
        album.setHideAlbums(true);
        album.setSpacerAlbumCount(1);
        album.setSpacerAlbumCount(3);
        album.setSpacerAlbumCount(1);
        album.setSpacerAlbumCount(0);
        assertEquals(1, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(StaticCategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
        for(int i = album.getBannerCount(); i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testAllCategoriesHideAlbums() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, 0);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        int itemCount = album.getItemCount();
        album.setHideAlbums(true);
        assertEquals(1, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(StaticCategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
        for(int i = album.getBannerCount(); i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testAllCategoriesHideAlbumsReversed() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, 2);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        int itemCount = album.getItemCount();
        album.setHideAlbums(true);
        assertEquals(1, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(StaticCategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
        if(resourceCount > 0) {
            assertEquals(ResourceItem.PICTURE_HEADING, album.getItemByIdx(1));
        } else {
            assertEquals(1, album.getItemCount()); // just the album header
        }
        for(int i = album.getBannerCount(); i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testPagesMissingAllResources() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean isReversed = false;
        int photosLoaded = 15;
        int pageSize = 3;
        int headers = 1;
        int loadedAlbumCount = 0;
        List<Integer> skipPages = Arrays.asList(1,2,5);

        runPagesMissingAllResourcesTest(sortOrder, isReversed, photosLoaded, pageSize, headers, loadedAlbumCount, skipPages);
    }

    @Test
    public void testPagesMissingAllResourcesReversed() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean isReversed = true;
        int photosLoaded = 15;
        int pageSize = 3;
        int headers = 1;
        int loadedAlbumCount = 0;
        List<Integer> skipPages = Arrays.asList(1,2,5);

        runPagesMissingAllResourcesTest(sortOrder, isReversed, photosLoaded, pageSize, headers, loadedAlbumCount, skipPages);
    }

    private void runPagesMissingAllResourcesTest(int sortOrder, boolean isReversed, int photosLoaded, int pageSize, int headers, int loadedAlbumCount, List<Integer> skipPages) {
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadResourceItemPages(resourceItemLoadPages, album);
        // reverse whatever order the albums and resources are currently in.
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        assertThrows("Setting the resource sort order is not valid for a partial load", IllegalStateException.class, ()->{album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder());});

        GalleryItem itemPresent = PiwigoResourceUtil.getItemFromPage(3,1, resourceItemLoadPages);
        GalleryItem itemNotPresent = PiwigoResourceUtil.getItemFromPage(1,1, resourceItemLoadPagesToSkip);
        int expectedAlbumIdx = PiwigoResourceUtil.getExpectedAlbumIdx(album, itemPresent, resourceItemLoadPages);
        int missingItemsBelowItemPresentInPage = PiwigoResourceUtil.countItemsInPagesLessThan(3, resourceItemLoadPagesToSkip);
        int loadedItemCount = getItemCountForPages(resourceItemLoadPages);
        int skippedItemCount = getItemCountForPages(resourceItemLoadPagesToSkip);
        assertEquals(expectedAlbumIdx, album.getItemIdx(itemPresent));
        assertEquals(-1, album.getItemIdx(itemNotPresent));
        assertEquals(itemPresent, album.getItemByIdx(missingItemsBelowItemPresentInPage + expectedAlbumIdx));
        assertEquals(loadedAlbumCount, album.getChildAlbumCount());
        assertEquals(loadedItemCount, album.getResourcesCount());
        assertEquals(loadedItemCount + headers, album.getItemCount());
        int expectedResourceHeaderIdx = album.getItemCount() - album.getResourcesCount() - 1;
        assertEquals(album.getItemByIdx(expectedResourceHeaderIdx), StaticCategoryItem.PICTURE_HEADING);

        // this needs to loop over all the i in the loaded items pages. Then check the remainder (in skip) are all returning exception
        runResourceIdxTestsOnOrganicLoadedList(pageSize, resourceItemLoadPages, album, resourceItemLoadPagesToSkip);

        album.setHideAlbums(true);

        assertEquals(loadedAlbumCount, album.getChildAlbumCount());
        assertEquals(loadedItemCount, album.getResourcesCount());
        assertEquals(loadedItemCount + headers, album.getItemCount());

        expectedResourceHeaderIdx = album.getItemCount() - album.getResourcesCount() - 1;
        assertEquals(album.getItemByIdx(expectedResourceHeaderIdx), StaticCategoryItem.PICTURE_HEADING);

        runResourceIdxTestsOnOrganicLoadedList(pageSize, resourceItemLoadPages, album, resourceItemLoadPagesToSkip);
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testPagesMissingMixedContent() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        List<Integer> skipPages = Arrays.asList(1,2,5);
        runPagesMissingMixedContentTest(sortOrder, false, 2, 15, 3,  5, skipPages);
    }

    @Test
    public void testPagesMissingMixedContentReversed() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        List<Integer> skipPages = Arrays.asList(1,2,5);
        runPagesMissingMixedContentTest(sortOrder, true, 0, 15, 3, 0, skipPages);
    }

    private void runPagesMissingMixedContentTest(int sortOrder, boolean isReversed, int spacerAlbums, int photosLoaded, int pageSize, int loadedAlbumCount, List<Integer> skipPages) {
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, loadedAlbumCount);
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, isReversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(0, photosLoaded));
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip = skipResourcePageLoads(resourceItemLoadPages, skipPages);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false,spacerAlbums);
        loadResourceItemPages(resourceItemLoadPages, album);
        album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder());
        assertThrows("Setting the resource sort order is not valid for a partial load", IllegalStateException.class, ()->album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        GalleryItem itemPresent = PiwigoResourceUtil.getItemFromPage(3,1, resourceItemLoadPages);
        GalleryItem itemNotPresent = PiwigoResourceUtil.getItemFromPage(1,1, resourceItemLoadPagesToSkip);
        int expectedAlbumIdx = PiwigoResourceUtil.getExpectedAlbumIdx(album, itemPresent, resourceItemLoadPages);
        int missingItemsBelowItemPresentInPage = PiwigoResourceUtil.countItemsInPagesLessThan(3, resourceItemLoadPagesToSkip);
        int loadedItemCount = getItemCountForPages(resourceItemLoadPages);
        int skippedItemCount = getItemCountForPages(resourceItemLoadPagesToSkip);
        assertEquals(expectedAlbumIdx, album.getItemIdx(itemPresent));
        assertEquals(-1, album.getItemIdx(itemNotPresent));
        assertEquals(itemPresent, album.getItemByIdx(missingItemsBelowItemPresentInPage + expectedAlbumIdx));
        assertEquals(loadedAlbumCount, album.getChildAlbumCount());
        assertEquals(loadedItemCount, album.getResourcesCount());
        assertFalse(album.isHideAlbums());
        int expectHeaderCount = (loadedAlbumCount > 0 ? 1 : 0) + (loadedItemCount > 0 ? 1 : 0);
        assertEquals(loadedAlbumCount + loadedItemCount + expectHeaderCount + spacerAlbums, album.getItemCount());
        int expectedResourceHeaderIdx = album.getItemCount() - album.getResourcesCount() - 1;
        assertEquals(album.getItemByIdx(expectedResourceHeaderIdx), StaticCategoryItem.PICTURE_HEADING);

        // this needs to loop over all the i in the loaded items pages. Then check the remainder (in skip) are all returning exception
        runResourceIdxTestsOnOrganicLoadedList(pageSize, resourceItemLoadPages, album, resourceItemLoadPagesToSkip);

        album.setHideAlbums(true);

        assertEquals(loadedAlbumCount, album.getChildAlbumCount());
        assertEquals(loadedItemCount, album.getResourcesCount());
        assertEquals(loadedItemCount + expectHeaderCount, album.getItemCount());

        expectedResourceHeaderIdx = album.getItemCount() - album.getResourcesCount() - 1;
        assertEquals(album.getItemByIdx(expectedResourceHeaderIdx), StaticCategoryItem.PICTURE_HEADING);

        runResourceIdxTestsOnOrganicLoadedList(pageSize, resourceItemLoadPages, album, resourceItemLoadPagesToSkip);
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    private void runResourceIdxTestsOnOrganicLoadedList(int pageSize, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, PiwigoAlbum<CategoryItem, GalleryItem> album, List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesToSkip) {
        List<ItemLoadPage<GalleryItem>> allResourceItemLoadPages = new ArrayList<>(resourceItemLoadPages);
        allResourceItemLoadPages.addAll(resourceItemLoadPagesToSkip);
        int firstResourceIdx = album.getFirstResourceIdx();
        int lastResourceIdx = album.getServerResourcesCount();
        for(int i = firstResourceIdx; i < lastResourceIdx; i++) {
            int zeroIdxdI = i - firstResourceIdx;
            int pageIdx = zeroIdxdI / pageSize;
            int resourceIdxInPage = zeroIdxdI - (pageIdx * pageSize);
            GalleryItem item = PiwigoResourceUtil.getItemFromPage(pageIdx,resourceIdxInPage, allResourceItemLoadPages);
            int expectedItemIdx = PiwigoResourceUtil.getExpectedAlbumIdx(album, item, resourceItemLoadPages);
            String failMsg = String.format("Error retrieving resource item with server idx %1$d (resource Idx %7$d)" +
                                            " in server resource local list idx range %2$d-%3$d \ncontained in page %4$d (pageIdx %5$d)." +
                                            " We expect it to be at list idx %6$d.",
                                            i, firstResourceIdx,  lastResourceIdx, pageIdx,
                                            resourceIdxInPage, expectedItemIdx, zeroIdxdI);
            if(expectedItemIdx < 0) {
                int finalListIdx = i;
                assertThrows(failMsg, IndexOutOfBoundsException.class, () -> album.getItemByIdx(finalListIdx));
            } else {
                try {
                    GalleryItem retrievedItem = album.getItemByIdx(i);
                    assertEquals(failMsg, item, retrievedItem);
                } catch(RuntimeException e) {
                    logger.log(Level.SEVERE, "Unexpected error", e);
                    fail(failMsg);
                }
            }
        }
    }

    private int getItemCountForPages(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages) {
        int count = 0;
        for (ItemLoadPage<GalleryItem> resourceItemLoadPage : resourceItemLoadPages) {
            count += resourceItemLoadPage.getItems().size();
        }
        return count;
    }

    /**
     * Removes the pages from the list to load and returns them as a new list.
     * @param resourceItemLoadPages
     * @param pagesToSkip
     * @return
     */
    private List<ItemLoadPage<GalleryItem>> skipResourcePageLoads(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<Integer> pagesToSkip) {
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPagesSkipped = new ArrayList<>();
        for (Iterator<ItemLoadPage<GalleryItem>> iterator = resourceItemLoadPages.iterator(); iterator.hasNext(); ) {
            ItemLoadPage<GalleryItem> resourceItemLoadPage = iterator.next();
            if (pagesToSkip.contains(resourceItemLoadPage.getPageIdx())) {
                resourceItemLoadPagesSkipped.add(resourceItemLoadPage);
                iterator.remove();
            }
        }
        return resourceItemLoadPagesSkipped;
    }

    @Test
    public void testAllResourcesHideAlbums() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean reversed = false;
        int photosLoaded = 15;
        int pageSize = 3;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, reversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, photosLoaded));
        loadResourceItemPages(resourceItemLoadPages, album);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveResourcesInReverseOrder(reversed);

        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        assertEquals(0, albumsCount);
        assertEquals(15, resourceCount);
        assertEquals(16, album.getItemCount());
        album.setHideAlbums(true);
        assertEquals(15, album.getResourcesCount());
        assertEquals(16, album.getItemCount());
        assertEquals(0, album.getChildAlbumCount());
        assertEquals(album.getItemByIdx(0), StaticCategoryItem.PICTURE_HEADING);
        for(int i = 2; i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testAllResourcesHideAlbumsReversed() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean reversed = true;
        int photosLoaded = 15;
        int pageSize = 3;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, reversed);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, photosLoaded));
        loadResourceItemPages(resourceItemLoadPages, album);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveResourcesInReverseOrder(reversed);

        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        assertEquals(0, albumsCount);
        assertEquals(15, resourceCount);
        assertEquals(16, album.getItemCount());
        album.setHideAlbums(true);
        assertEquals(15, album.getResourcesCount());
        assertEquals(16, album.getItemCount());
        assertEquals(0, album.getChildAlbumCount());
        assertEquals(album.getItemByIdx(0), StaticCategoryItem.PICTURE_HEADING);
        for(int i = 2; i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    @Test
    public void testMixedContentReverseResourceOrderAfterLoad() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 0);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        assertEquals(albumsCount + resourceCount + (albumsCount > 0 ? 1 : 0) + (resourceCount > 0 ? 1 : 0), album.getItemCount());
        album.setHideAlbums(true);
        ArrayList<GalleryItem> albumResourcesPreReverse = PiwigoResourceUtil.getListOfAllResourcesByIdx(album);
        boolean reversedExistingList = album.setRetrieveResourcesInReverseOrder(true);
        assertTrue("List resources should have been reversed", reversedExistingList);
        ArrayList<GalleryItem> resourcesAfterReverse = PiwigoResourceUtil.getListOfAllResourcesByIdx(album);
        Collections.reverse(albumResourcesPreReverse);
        assertEquals(albumResourcesPreReverse, resourcesAfterReverse);

        assertEquals(resourceCount + 2, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(album.getItemByIdx(0), StaticCategoryItem.ALBUM_HEADING);
        for(int i = 2; i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
    }

    @Test
    public void testMixedContentHideAlbums() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 0);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        int itemCount = album.getItemCount();
        album.setHideAlbums(true);
        assertEquals(resourceCount + 2, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(album.getItemByIdx(0), StaticCategoryItem.ALBUM_HEADING);
        for(int i = 2; i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
    }

    @Test
    public void testMixedContentHideAlbumsReversed() {
        PiwigoAlbum<CategoryItem, GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true, 2);
        int albumsCount = album.getChildAlbumCount();
        int resourceCount = album.getResourcesCount();
        int itemCount = album.getItemCount();
        album.setHideAlbums(true);
        assertEquals(resourceCount + 2, album.getItemCount());
        assertEquals(resourceCount, album.getResourcesCount());
        assertEquals(albumsCount, album.getChildAlbumCount());
        assertEquals(StaticCategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
        assertEquals(ResourceItem.PICTURE_HEADING, album.getItemByIdx(1));
        for(int i = 2; i <= resourceCount; i++) {
            assertTrue("Item at idx"+ i+" was not a resource item : " + album.getItemByIdx(i) ,album.getItemByIdx(i) instanceof ResourceItem);
        }
    }

    @Test
    public void testAddSpacerAlbumsMixedContent() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 0);
        int startCount = album.getItemCount();
        int startAlbumCount = album.getChildAlbumCount();
        int startResourceCount = album.getResourcesCount();
        album.setSpacerAlbumCount(3);
        assertEquals(startCount + 3, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(3, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        // try changing the spacer album count
        album.setSpacerAlbumCount(5);
        assertEquals(startCount + 5, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(5, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
    }

    @Test
    public void testAddSpacerAlbumsMixedContentReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true, 0);
        int startCount = album.getItemCount();
        int startAlbumCount = album.getChildAlbumCount();
        int startResourceCount = album.getResourcesCount();
        album.setSpacerAlbumCount(3);
        assertEquals(startCount + 3, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(3, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
        // try changing the spacer album count
        album.setSpacerAlbumCount(5);
        assertEquals(startCount + 5, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(5, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
    }

    @Test
    public void testAddSpacerAlbumsCategoriesOnly() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, 0);
        int startCount = album.getItemCount();
        int startAlbumCount = album.getChildAlbumCount();
        int startResourceCount = album.getResourcesCount();
        album.setSpacerAlbumCount(3);
        assertEquals(startCount + 3, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(3, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
        // try changing the spacer album count
        album.setSpacerAlbumCount(5);
        assertEquals(startCount + 5, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(5, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
    }

    @Test
    public void testAddSpacerAlbumsCategoriesOnlyReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategories(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, 0);
        int startCount = album.getItemCount();
        int startAlbumCount = album.getChildAlbumCount();
        int startResourceCount = album.getResourcesCount();
        album.setSpacerAlbumCount(3);
        assertEquals(startCount + 3, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(3, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
        // try changing the spacer album count
        album.setSpacerAlbumCount(5);
        assertEquals(startCount + 5, album.getItemCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startAlbumCount, album.getChildAlbumCount());
        assertEquals(startResourceCount, album.getResourcesCount());
        assertEquals(5, album.getItemCount() - (album.getResourcesCount() + album.getChildAlbumCount() + album.getBannerCount()));
        assertEquals(StaticCategoryItem.BLANK,  album.getItemByIdx(album.getChildAlbumCount() + 1));
    }

    @Test
    public void testAllCategoriesFirstSortingByName() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 2);
    }

    @Test
    public void testAllCategoriesFirstSortingByNameReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true, 2);
    }

    @Test
    public void testAllCategoriesFirstSortingByDefault() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false, false, 2);
    }

    @Test
    public void testAllCategoriesFirstSortingByDefaultReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true, true, 2);
    }

    @Test
    public void testAllCategoriesFirstSortingByDate() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false, false, 2);
    }

    @Test
    public void testAllCategoriesFirstSortingByDateReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true, true, 2);
    }

    @Test
    public void testReverseOriginalSortingByName() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false, 2);
        //Now do the actual test
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveChildAlbumsInReverseOrder(), album.isRetrieveResourcesInReverseOrder());
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByNameReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true, 2);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDefault() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false, false, 2);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveChildAlbumsInReverseOrder(), album.isRetrieveResourcesInReverseOrder());
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    private int getResourcesCount(List<? extends GalleryItem> items) {
        int count = 0;
        for(GalleryItem gi : items) {
            if(gi instanceof ResourceItem && !ResourceItem.PICTURE_HEADING.equals(gi)) {
                count++;
            }
        }
        return count;
    }

    private int getAlbumCount(List<? extends GalleryItem> items) {
        int count = 0;
        for(GalleryItem gi : items) {
            if(gi instanceof CategoryItem && !StaticCategoryItem.ALBUM_HEADING.equals(gi) && !StaticCategoryItem.BLANK.equals(gi)) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void testReverseOriginalSortingByDefaultReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true, true, 2);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDate() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false, false, 2);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveChildAlbumsInReverseOrder(), album.isRetrieveResourcesInReverseOrder());
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDateReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true, true, 2);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue("album sort order was not changed", album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveChildAlbumsInReverseOrder()));
        assertTrue("resource sort order was not changed", album.setRetrieveResourcesInReverseOrder(!album.isRetrieveResourcesInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testMixedOrderLoadSortingByName() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, false);
    }

    @Test
    public void testMixedOrderLoadSortingByNameReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, true);
    }

    @Test
    public void testMixedOrderLoadSortingByDefault() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false, false);
    }

    @Test
    public void testMixedOrderLoadSortingByDefaultReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true, true);
    }

    @Test
    public void testMixedOrderLoadSortingByDate() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false, false);
    }

    @Test
    public void testMixedOrderLoadSortingByDateReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true, true);
    }

    private ArrayList<CategoryItem> initialiseCategoryItemLoadData(int sortOrder, int catsWanted) {
        ArrayList<CategoryItem> categoryItemLoad = new ArrayList<>();
        for(int i = 0; i < catsWanted; i++) {
            switch(sortOrder) {
                case PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT:
                    categoryItemLoad.add(categoryItemFactory.getNextByName(0));
                    break;
                case PiwigoAlbum.ALBUM_SORT_ORDER_DATE:
                    categoryItemLoad.add(categoryItemFactory.getNextByLastAltered(0));
                    break;
                case PiwigoAlbum.ALBUM_SORT_ORDER_NAME:
                    categoryItemLoad.add(categoryItemFactory.getNextByName(0));
                    break;
            }
        }

        return categoryItemLoad;
    }

    public void loadMixedOrder(int sortOrder, boolean reverseCats, boolean reverseResources,  AlbumAction... actions) {
        int photosLoaded = 15;
        int pageSize = 3;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, reverseResources);
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reverseCats);
        album.setRetrieveResourcesInReverseOrder(reverseResources);

        int loadCategoriesAfterPages = 3;
        boolean headingAdded = false;
        int pagesLoaded = 0;
        int spacerAlbumCount = 2;

        for(ItemLoadPage<GalleryItem> resourceItemLoadPage : resourceItemLoadPages) {
            if(loadCategoriesAfterPages == pagesLoaded) {
                loadCategoriesCheckingSortOrder(album, categoryItemLoad,  true, spacerAlbumCount);
            }

            if(!headingAdded && resourceItemLoadPage.getItems().size() > 0) {
                album.addItem(ResourceItem.PICTURE_HEADING);
                headingAdded= true;
            }
            album.addItemPage(resourceItemLoadPage.getPageIdx(), resourceItemLoadPage.getItems().size(), resourceItemLoadPage.getItems());
            testSortOrder(album, "Loading page " + resourceItemLoadPage.getPageIdx() + ". " + album);
            pagesLoaded++;
        }

        List<GalleryItem> expectedResult = buildMixedExpectedOutcome(categoryItemLoad, resourceItemLoadPages, spacerAlbumCount, album.isRetrieveChildAlbumsInReverseOrder(), album.isRetrieveResourcesInReverseOrder());

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, categoryItemLoad, resourceItemLoadPages, expectedResult);
        }


        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(expectedResult), album.getChildAlbumCount());
        assertEquals(getResourcesCount(expectedResult), album.getResourcesCount());

        album.removeAllResources();
        assertEquals(0, album.getResourcesCount());
    }

    private static interface AlbumAction {
        void doWithAlbumPostLoad(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, @Nullable ArrayList<CategoryItem> categoryItemLoad, @Nullable List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, @Nullable List<GalleryItem> expectedResult);
    }

    public PiwigoAlbum<CategoryItem,GalleryItem> loadCategoriesFirst(int sortOrder, boolean reversedCats, boolean reversedResources, int spacerAlbumCount, AlbumAction... actions) {
        int photosLoaded = 15;
        int pageSize = 3;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, photosLoaded, pageSize, reversedResources);
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversedCats);
        album.setRetrieveResourcesInReverseOrder(reversedResources);

        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false, spacerAlbumCount);
        album.setSpacerAlbumCount(spacerAlbumCount);
        loadResourceItemPages(resourceItemLoadPages, album);

        List<GalleryItem> expectedResult = buildMixedExpectedOutcome(categoryItemLoad, resourceItemLoadPages, spacerAlbumCount, album.isRetrieveChildAlbumsInReverseOrder(), album.isRetrieveResourcesInReverseOrder());

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, categoryItemLoad, resourceItemLoadPages, expectedResult);
        }

        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(expectedResult), album.getChildAlbumCount());
        assertEquals(getResourcesCount(expectedResult), album.getResourcesCount());
        return album;
    }

    private void loadResourceItemPages(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, PiwigoAlbum<CategoryItem, GalleryItem> album, AlbumAction ... actions) {
        boolean headingAdded = false;
        for(ItemLoadPage<GalleryItem> resourceItemLoadPage : resourceItemLoadPages) {
            if(!headingAdded && resourceItemLoadPage.getItems().size() > 0) {
                album.addItem(ResourceItem.PICTURE_HEADING);
                headingAdded= true;
            }
            album.addItemPage(resourceItemLoadPage.getPageIdx(), resourceItemLoadPage.getItems().size(), resourceItemLoadPage.getItems());
            testSortOrder(album, "Loading page " + resourceItemLoadPage.getPageIdx() + ". " + album);
        }

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, null, resourceItemLoadPages, null);
        }
        logger.info("Loaded resource pages");
    }

    private List<GalleryItem> buildMixedExpectedOutcome(ArrayList<CategoryItem> categoryItemLoad, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, int spacerAlbumCount, boolean reverseCats, boolean reverseResources) {
        ArrayList<GalleryItem> expected = new ArrayList<>();
        if(categoryItemLoad.size() > 0) {
            expected.add(StaticCategoryItem.ALBUM_HEADING);
        }
        ArrayList<CategoryItem> cats = new ArrayList<>(categoryItemLoad);
        int insertAt = reverseCats ? 0 : cats.size();
        for(int i = 0; i < spacerAlbumCount; i++) {
            cats.add(insertAt, StaticCategoryItem.BLANK.toInstance());
        }
        if(reverseCats) {
            Collections.reverse(cats);
        }
        expected.addAll(cats);
        expected.addAll(PiwigoResourceUtil.buildExpectedResult(true, reverseResources, resourceItemLoadPages));

        return expected;
    }

    private void testSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> album, String assertionMsg) {
        if(album.getChildAlbumCount() > 0) {
            assertEquals(assertionMsg, StaticCategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
            assertEquals(assertionMsg, album.getResourcesCount() == 0 ? 0 : album.getFirstResourceIdx() - 1, album.getItemIdx(ResourceItem.PICTURE_HEADING));
        } else {
            assertEquals(assertionMsg, album.getResourcesCount() == 0 ? -1 : 0, album.getItemIdx(ResourceItem.PICTURE_HEADING));
        }
    }


    /**
     *
     * @param album destination to load into
     * @param categoryItemLoad list of categories to load
     * @param trimToMatchExpectedLength trim any resource load data from the end before comparing actual and expected
     * @param spacerAlbumCount spacer albums to add
     * @param actions and post load actions
     */
    protected void loadCategoriesCheckingSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, boolean trimToMatchExpectedLength, int spacerAlbumCount, AlbumAction ... actions) {
        if(categoryItemLoad.size() > 0) {
            album.addItem(StaticCategoryItem.ALBUM_HEADING);
        }
        for(CategoryItem item : categoryItemLoad) {
            album.addItem(item);
        }
        album.setSpacerAlbumCount(spacerAlbumCount);

        List<CategoryItem> expected = new ArrayList<>(categoryItemLoad);
        int insertAt = album.isRetrieveChildAlbumsInReverseOrder() ? 0 : expected.size();
        for(int i = 0; i < spacerAlbumCount; i++) {
            CategoryItem spacer = StaticCategoryItem.BLANK.toInstance();
            expected.add(insertAt, spacer);
        }

        if(album.isRetrieveChildAlbumsInReverseOrder()) {
            Collections.reverse(expected);
        }
        if(expected.size() > 0) {
            expected.add(0, StaticCategoryItem.ALBUM_HEADING);
        }
        List<GalleryItem> actual = new ArrayList<>(album.getItems());
        if(trimToMatchExpectedLength && actual.size() > expected.size()) {
            ListIterator<GalleryItem> iterator = actual.listIterator(actual.size());
            while(actual.size() > expected.size() && iterator.hasPrevious()) {
                iterator.previous();
                iterator.remove();
            }
        }

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, categoryItemLoad, null, null);
        }

        assertArrayEquals(expected.toArray(), actual.toArray());
        assertEquals(getAlbumCount(expected), album.getChildAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    private static class MultiItemReplacementAction extends AlbumItemReplacementAction {

        @Override
        public void doWithAlbumPostLoad(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<GalleryItem> expectedResult) {
            replaceXthCategory(album, categoryItemLoad, expectedResult, 0);
            replaceXthCategory(album, categoryItemLoad, expectedResult, 3);
            replaceXthResource(album, resourceItemLoadPages, expectedResult, 0);
            replaceXthResource(album, resourceItemLoadPages, expectedResult, 4);
        }

    }

    private static class CategoryItemReplacementAction extends AlbumItemReplacementAction {
        @Override
        public void doWithAlbumPostLoad(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<GalleryItem> expectedResult) {
            replaceXthCategory(album, categoryItemLoad, expectedResult, 0);
        }
    }

    private static abstract class AlbumItemReplacementAction implements AlbumAction {

        public void removeAndReplaceItem(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, @NonNull GalleryItem itemToRemove, int idxItemToRemove, @Nullable List<GalleryItem> expectedResult) {
            GalleryItem item = album.getItemByIdx(idxItemToRemove);
            // check the correct item was retrieved
            assertEquals(itemToRemove, item);
            // check the getItemIdx also works as expected
            assertEquals(idxItemToRemove, album.getItemIdx(item));
//            // check remove by idx works
//            assertEquals(itemToRemove, album.remove(idxItemToRemove));
            // now re-insert the same item (expectations then still work without tweak to the original load)
            album.replace(itemToRemove, itemToRemove);

            if(item instanceof ResourceItem && expectedResult != null) {
                // need to tweak the expected result
                int expectedNewIdx = idxItemToRemove;//expectedResult.indexOf(GalleryItem.PICTURE_HEADING) + 1;
                expectedResult.remove(item);
                expectedResult.add(expectedNewIdx, item);

            }
        }

        protected void replaceXthCategory(PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, List<GalleryItem> expectedResult, int removeItemAtOffset) {
            boolean hideAlbums = false;
            if(album.isHideAlbums()) {
                album.setHideAlbums(false);
                hideAlbums = true;
            }
            int lastAlbumIdx;
            if(album.getResourcesCount() > 0) {
                lastAlbumIdx = album.getFirstResourceIdx() - 2;
            } else {
                lastAlbumIdx = album.getItemCount() - 1;
            }
            int spacerCount = album.getSpacerAlbumCount();
            int idxItemToRemove = (album.isRetrieveChildAlbumsInReverseOrder() ? lastAlbumIdx - removeItemAtOffset - spacerCount : 1 + removeItemAtOffset);
            removeAndReplaceItem(album, categoryItemLoad.get(removeItemAtOffset), idxItemToRemove, expectedResult);
            album.setHideAlbums(hideAlbums);
        }

        protected void replaceXthResource(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<GalleryItem> expectedResult, int removeItemAtOffset) {
            int idxItemToRemove = removeItemAtOffset + album.getFirstResourceIdx();
            GalleryItem itemToRemove = getResourceByLoadIdx(resourceItemLoadPages, removeItemAtOffset);
            removeAndReplaceItem(album, itemToRemove, idxItemToRemove, expectedResult);
        }

        private GalleryItem getResourceByLoadIdx(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, int removeItemAtOffset) {
            boolean loadPagesReversed = false;
            if(resourceItemLoadPages.size() > 1 && resourceItemLoadPages.get(0).getPageIdx() > resourceItemLoadPages.get(1).getPageIdx()) {
                // page loads were reversed rather than just the album post load
                loadPagesReversed = true;
            }
            List<GalleryItem> loadItems = PiwigoResourceUtil.buildExpectedResult(false, loadPagesReversed, resourceItemLoadPages);
            return loadItems.get(removeItemAtOffset);
        }
    }

    private static class ResourceItemReplacementAction extends AlbumItemReplacementAction {
        @Override
        public void doWithAlbumPostLoad(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<GalleryItem> expectedResult) {
            // remove the first resource item
            replaceXthResource(album, resourceItemLoadPages, expectedResult, 0);
            // remove the third resource item
            replaceXthResource(album, resourceItemLoadPages, expectedResult, 2);
        }
    }
}