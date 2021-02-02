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
import java.util.Collections;
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
import static org.junit.Assert.assertTrue;


public class PiwigoAlbumTest {
    private static MockedStatic<Logging> mockLogging;
    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private Logger  logger = Logger.getLogger(PiwigoAlbumTest.class.getName());
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
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, 5, 3);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveItemsInReverseOrder(reversed);
        loadResourceItemPages(resourceItemLoadPages, album, new ResourceItemReplacementAction());
    }

    @Test
    public void testItemReplacementJustCategories() {
        int sortOrder = PiwigoAlbum.ALBUM_SORT_ORDER_NAME;
        boolean reversed = false;
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveItemsInReverseOrder(reversed);
        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false, new CategoryItemReplacementAction());
    }

    @Test
    public void testItemReplacementMixedContent() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false, new MultiItemReplacementAction());
    }

    @Test
    public void testItemReplacementMixedContentReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true, new MultiItemReplacementAction());
    }

    @Test
    public void testAllCategoriesFirstSortingByName() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false);
    }

    @Test
    public void testAllCategoriesFirstSortingByNameReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true);
    }

    @Test
    public void testAllCategoriesFirstSortingByDefault() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false);
    }

    @Test
    public void testAllCategoriesFirstSortingByDefaultReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true);
    }

    @Test
    public void testAllCategoriesFirstSortingByDate() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false);
    }

    @Test
    public void testAllCategoriesFirstSortingByDateReversed() {
        loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true);
    }

    @Test
    public void testReverseOriginalSortingByName() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false);
        //Now do the actual test
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveAlbumsInReverseOrder(), album.isRetrieveItemsInReverseOrder());
        //Now put it back again.
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByNameReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDefault() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveAlbumsInReverseOrder(), album.isRetrieveItemsInReverseOrder());
        //Now put it back again.
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
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
            if(gi instanceof CategoryItem && !CategoryItem.ALBUM_HEADING.equals(gi)) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void testReverseOriginalSortingByDefaultReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDate() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), album.isRetrieveAlbumsInReverseOrder(), album.isRetrieveItemsInReverseOrder());
        //Now put it back again.
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testReverseOriginalSortingByDateReversed() {
        PiwigoAlbum<CategoryItem,GalleryItem> album = loadCategoriesFirst(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true);
        //First reverse the list
        List<GalleryItem> originalOrder = new ArrayList<>(album.getItems());
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, album.getItems(), true, true);
        //Now put it back again.
        assertTrue(album.setRetrieveItemsInReverseOrder(!album.isRetrieveItemsInReverseOrder()));
        assertTrue(album.setRetrieveChildAlbumsInReverseOrder(!album.isRetrieveAlbumsInReverseOrder()));
        assertArrayEquals("Order has been reversed again", originalOrder.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(album.getItems()), album.getSubAlbumCount());
        assertEquals(getResourcesCount(album.getItems()), album.getResourcesCount());
    }

    @Test
    public void testMixedOrderLoadSortingByName() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, false);
    }

    @Test
    public void testMixedOrderLoadSortingByNameReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_NAME, true);
    }

    @Test
    public void testMixedOrderLoadSortingByDefault() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, false);
    }

    @Test
    public void testMixedOrderLoadSortingByDefaultReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, true);
    }

    @Test
    public void testMixedOrderLoadSortingByDate() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, false);
    }

    @Test
    public void testMixedOrderLoadSortingByDateReversed() {
        loadMixedOrder(PiwigoAlbum.ALBUM_SORT_ORDER_DATE, true);
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

    public void loadMixedOrder(int sortOrder, boolean reversed,  AlbumAction... actions) {
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, 5, 3);
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveItemsInReverseOrder(reversed);

        int loadCategoriesAfterPages = 3;
        boolean headingAdded = false;
        int pagesLoaded = 0;
        for(ItemLoadPage<GalleryItem> resourceItemLoadPage : resourceItemLoadPages) {
            if(loadCategoriesAfterPages == pagesLoaded) {
                loadCategoriesCheckingSortOrder(album, categoryItemLoad,true);
            }

            if(!headingAdded && resourceItemLoadPage.getItems().size() > 0) {
                album.addItem(ResourceItem.PICTURE_HEADING);
                headingAdded= true;
            }
            album.addItemPage(resourceItemLoadPage.getPageIdx(), resourceItemLoadPage.getItems().size(), resourceItemLoadPage.getItems());
            testSortOrder(album, "Loading page " + resourceItemLoadPage.getPageIdx() + ". " + album);
            pagesLoaded++;
        }

        List<GalleryItem> expectedResult = buildExpectedOutcome(categoryItemLoad, resourceItemLoadPages, album.isRetrieveItemsInReverseOrder());

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, categoryItemLoad, resourceItemLoadPages, expectedResult);
        }


        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(expectedResult), album.getSubAlbumCount());
        assertEquals(getResourcesCount(expectedResult), album.getResourcesCount());
    }

    private static interface AlbumAction {
        void doWithAlbumPostLoad(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, @Nullable ArrayList<CategoryItem> categoryItemLoad, @Nullable List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, @Nullable List<GalleryItem> expectedResult);
    }

    public PiwigoAlbum<CategoryItem,GalleryItem> loadCategoriesFirst(int sortOrder, boolean reversed, AlbumAction... actions) {

        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, sortOrder, 5, 3);
        ArrayList<CategoryItem> categoryItemLoad = initialiseCategoryItemLoadData(sortOrder, 5);
        PiwigoAlbum<CategoryItem,GalleryItem> album = new PiwigoAlbum<>(categoryItemFactory.getNextByName(5, 15));
        album.setAlbumSortOrder(sortOrder);
        album.setRetrieveChildAlbumsInReverseOrder(reversed);
        album.setRetrieveItemsInReverseOrder(reversed);

        loadCategoriesCheckingSortOrder(album, categoryItemLoad, false);
        loadResourceItemPages(resourceItemLoadPages, album);

        List<GalleryItem> expectedResult = buildExpectedOutcome(categoryItemLoad, resourceItemLoadPages, album.isRetrieveItemsInReverseOrder());

        for(AlbumAction action : actions) {
            action.doWithAlbumPostLoad(album, categoryItemLoad, resourceItemLoadPages, expectedResult);
        }

        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), album.getItems().toArray());
        assertEquals(getAlbumCount(expectedResult), album.getSubAlbumCount());
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
    }

    private List<GalleryItem> buildExpectedOutcome(ArrayList<CategoryItem> categoryItemLoad, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, boolean reverseOrder) {
        ArrayList<GalleryItem> expected = new ArrayList<>();
        if(categoryItemLoad.size() > 0) {
            expected.add(CategoryItem.ALBUM_HEADING);
        }
        ArrayList<CategoryItem> cats = new ArrayList<>(categoryItemLoad);
        if(reverseOrder) {
            Collections.reverse(cats);
        }
        expected.addAll(cats);
        expected.addAll(PiwigoResourceUtil.buildExpectedResult(true, reverseOrder, resourceItemLoadPages));

        return expected;
    }

    private void testSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> album, String assertionMsg) {
        if(album.getSubAlbumCount() > 0) {
            assertEquals(assertionMsg, CategoryItem.ALBUM_HEADING, album.getItemByIdx(0));
            assertEquals(assertionMsg, album.getImgResourceCount() == 0 ? 0 : album.getSubAlbumCount() + 1, album.getItemIdx(ResourceItem.PICTURE_HEADING));
        } else {
            assertEquals(assertionMsg, album.getImgResourceCount() == 0 ? -1 : 0, album.getItemIdx(ResourceItem.PICTURE_HEADING));
        }
    }


    protected void loadCategoriesCheckingSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> album, ArrayList<CategoryItem> categoryItemLoad, boolean trimToMatchExpectedLength, AlbumAction ... actions) {
        if(categoryItemLoad.size() > 0) {
            album.addItem(CategoryItem.ALBUM_HEADING);
        }
        for(CategoryItem item : categoryItemLoad) {
            album.addItem(item);
        }
        List<CategoryItem> expected = new ArrayList<>(categoryItemLoad);
        if(album.isRetrieveItemsInReverseOrder()) {
            Collections.reverse(expected);
        }
        if(expected.size() > 0) {
            expected.add(0, CategoryItem.ALBUM_HEADING);
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
        assertEquals(getAlbumCount(expected), album.getSubAlbumCount());
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
            int idxItemToRemove = (album.isRetrieveAlbumsInReverseOrder() ? album.getSubAlbumCount() - removeItemAtOffset : 1 + removeItemAtOffset);
            removeAndReplaceItem(album, categoryItemLoad.get(removeItemAtOffset), idxItemToRemove, expectedResult);
        }

        protected void replaceXthResource(@NonNull PiwigoAlbum<CategoryItem, GalleryItem> album, List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, List<GalleryItem> expectedResult, int removeItemAtOffset) {
            int idxItemToRemove;
            if(album.isRetrieveItemsInReverseOrder()) {
                idxItemToRemove = album.getItemCount() -1 - removeItemAtOffset;
            } else {
                idxItemToRemove = removeItemAtOffset + (album.getSubAlbumCount() == 0 ? 1 : album.getSubAlbumCount() + 2);
            }
            GalleryItem itemToRemove = getResourceByLoadIdx(resourceItemLoadPages, removeItemAtOffset);
            removeAndReplaceItem(album, itemToRemove, idxItemToRemove, expectedResult);
        }

        private GalleryItem getResourceByLoadIdx(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, int removeItemAtOffset) {
            int offset = 0;
            for(ItemLoadPage<GalleryItem> loadPage : resourceItemLoadPages) {
                if(removeItemAtOffset < offset + loadPage.getItems().size()) {
                    return loadPage.getItems().get(removeItemAtOffset - offset);
                }
                offset += loadPage.getItems().size();
            }
            throw new ArrayIndexOutOfBoundsException("Item idx requested to be removed is not in the range of those loaded");
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