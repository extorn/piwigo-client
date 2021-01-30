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
import delit.piwigoclient.test.GalleryItemFactory;
import delit.piwigoclient.test.ItemLoadPage;
import delit.piwigoclient.test.PiwigoResourceUtil;
import delit.piwigoclient.test.ResourceItemFactory;

import static org.junit.Assert.assertArrayEquals;

public class PiwigoTagsTest {
    private static MockedStatic<Logging> mockLogging;
    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private Logger logger = Logger.getLogger(PiwigoTagsTest.class.getName());
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
        GalleryItemFactory.resetId();
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
        PiwigoTags tags = loadResourcePages(false);
        List<GalleryItem> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, tags.getItems(), true, true);
    }

    @Test
    public void testSortOrderReversed() {
        PiwigoTags tags = loadResourcePages(true);
        List<GalleryItem> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoResourceUtil.assertHasBeenReversed(originalOrder, tags.getItems(),true, true);
    }

    private PiwigoTags loadResourcePages(boolean reversed) {
        List<ItemLoadPage> resourceItemLoadPages = PiwigoResourceUtil.initialiseResourceItemLoadPages(resourceItemFactory, PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT, 5, 3);

        int photoCount = 20;
        PiwigoTags tags = new PiwigoTags();
        tags.setRetrieveItemsInReverseOrder(reversed);

        for(ItemLoadPage resourceItemLoadPage : resourceItemLoadPages) {
            tags.addItemPage(resourceItemLoadPage.getPageIdx(), resourceItemLoadPage.getItems().size(), resourceItemLoadPage.getItems());
        }

        List<GalleryItem> expectedResult = buildExpectedOutcome(resourceItemLoadPages, tags.isRetrieveItemsInReverseOrder());
        assertArrayEquals("Final gallery should match expected content", expectedResult.toArray(), tags.getItems().toArray());
        return tags;
    }

    private List<GalleryItem> buildExpectedOutcome(List<ItemLoadPage> resourceItemLoadPages, boolean reverseOrder) {
        return PiwigoResourceUtil.buildExpectedResult(false, reverseOrder, resourceItemLoadPages);
    }

}