package delit.piwigoclient.model.piwigo;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import delit.libs.core.util.Logging;
import delit.piwigoclient.test.IdentifiableItemFactory;
import delit.piwigoclient.test.ItemLoadPage;
import delit.piwigoclient.test.PiwigoResourceUtil;
import delit.piwigoclient.test.ResourceItemFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PiwigoFavoritesTest {
    private static MockedStatic<Logging> mockLogging;
    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private Logger logger = Logger.getLogger(PiwigoFavoritesTest.class.getName());
    private ResourceItemFactory resourceItemFactory;

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
    public void testSortOrder() {
        PiwigoFavorites favs = loadResourcePages(false);
        List<GalleryItem> originalOrder = new ArrayList<>(favs.getItems());
        assertTrue("Sort order should be switched as fully loaded", favs.setRetrieveResourcesInReverseOrder(!favs.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, favs.getItems(), true, true);
    }

    @Test
    public void testSortOrderPreReversed() {
        // load the pages in
        PiwigoFavorites favs = loadResourcePages(true);
        List<GalleryItem> originalOrder = new ArrayList<>(favs.getItems());
        // reverse again after load
        assertTrue("list order should have changed as is fully loaded", favs.setRetrieveResourcesInReverseOrder(!favs.isRetrieveResourcesInReverseOrder()));
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, favs.getItems(), true, true);
    }

    private PiwigoFavorites loadResourcePages(boolean reversed) {
        int photosLoaded = 15;
        int pageSize = 3;
        List<ItemLoadPage<GalleryItem>> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, photosLoaded, pageSize, reversed);
        PiwigoFavorites.FavoritesSummaryDetails favoritesSummaryDetails = new PiwigoFavorites.FavoritesSummaryDetails(photosLoaded);
        PiwigoFavorites favs = new PiwigoFavorites(favoritesSummaryDetails);
        assertFalse("list order should not have changed as not fully loaded", favs.setRetrieveResourcesInReverseOrder(reversed)); // this is not expected to do anything unless the list if fully loaded
        for(ItemLoadPage<GalleryItem> resourceItemLoadPage : resourceItemLoadPages) {
            favs.addItemPage(resourceItemLoadPage.getPageIdx(), resourceItemLoadPage.getItems().size(), resourceItemLoadPage.getItems());
        }

        List<GalleryItem> expectedResult = buildExpectedOutcome(resourceItemLoadPages, favs.isRetrieveResourcesInReverseOrder());
        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), favs.getItems().toArray());
        return favs;
    }

    private List<GalleryItem> buildExpectedOutcome(List<ItemLoadPage<GalleryItem>> resourceItemLoadPages, boolean reverseOrder) {
        return PiwigoResourceUtil.buildExpectedResult(false, reverseOrder, resourceItemLoadPages);
    }

}