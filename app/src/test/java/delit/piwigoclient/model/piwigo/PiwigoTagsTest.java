package delit.piwigoclient.model.piwigo;

import androidx.annotation.NonNull;

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
import delit.piwigoclient.test.PiwigoTagUtil;
import delit.piwigoclient.test.PiwigoTagUtil;
import delit.piwigoclient.test.TagFactory;

import static org.junit.Assert.assertArrayEquals;

public class PiwigoTagsTest {
    private static MockedStatic<Logging> mockLogging;
    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private Logger logger = Logger.getLogger(PiwigoTagsTest.class.getName());
    private TagFactory tagFactory;

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
        tagFactory = new TagFactory();
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
    public void testSortOrderSingleSource() {
        PiwigoTags<Tag> tags = loadTagPages(false, new TagLoadAction(0, false));
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    @Test
    public void testSortOrderReversedSingleSource() {
        PiwigoTags<Tag> tags = loadTagPages(true, new TagLoadAction(0, false));
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    @Test
    public void testSortOrderPreferFirstSource() {
        TagLoadAction page1 = new TagLoadAction(0, false);
        TagLoadAction page2 = new TagLoadAction(1, true);
        page2.injectFrom(page1, 0, 0);
        PiwigoTags<Tag> tags = loadTagPages(false, page1, page2);
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    @Test
    public void testSortOrderReversedPreferFirstSource() {
        TagLoadAction page1 = new TagLoadAction(0, false);
        TagLoadAction page2 = new TagLoadAction(1, true);
        PiwigoTags<Tag> tags = loadTagPages(true, page1, page2);
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    @Test
    public void testSortOrderPreferNextSource() {
        TagLoadAction page1 = new TagLoadAction(0, false);
        TagLoadAction page2 = new TagLoadAction(1, false);
        page2.injectFrom(page1, 0, 0);
        PiwigoTags<Tag> tags = loadTagPages(false, page1, page2);
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    @Test
    public void testSortOrderReversedPreferNextSource() {
        TagLoadAction page1 = new TagLoadAction(0, false);
        TagLoadAction page2 = new TagLoadAction(1, false);
        PiwigoTags<Tag> tags = loadTagPages(true, page1, page2);
        List<Tag> originalOrder = new ArrayList<>(tags.getItems());
        tags.setRetrieveItemsInReverseOrder(!tags.isRetrieveItemsInReverseOrder());
        PiwigoTagUtil.assertHasBeenReversed(originalOrder, tags.getItems());
    }

    private class TagLoadAction {

        private final ArrayList<ItemLoadPage<Tag>> itemLoadPages;
        private int loadSourceId;
        private boolean preferExistingItems;
        private int pages = 5;
        private int itemsPerPage = 3;

        public TagLoadAction(int loadSourceId, boolean preferExistingItems) {
            this.loadSourceId = loadSourceId;
            this.preferExistingItems = preferExistingItems;
            itemLoadPages = PiwigoTagUtil.initialiseTagItemLoadPages(tagFactory,pages, itemsPerPage);
        }

        public TagLoadAction(int loadSourceId, boolean preferExistingItems, int pages, int itemsPerPage) {
            this.loadSourceId = loadSourceId;
            this.preferExistingItems = preferExistingItems;
            this.pages = pages;
            this.itemsPerPage = itemsPerPage;
            itemLoadPages = PiwigoTagUtil.initialiseTagItemLoadPages(tagFactory,pages, itemsPerPage);
        }

        public ArrayList<ItemLoadPage<Tag>> getItemLoadPages() {
            return itemLoadPages;
        }

        private void loadTagsPage(PiwigoTags<Tag> tags) {
            for(ItemLoadPage<Tag> itemLoadPage : itemLoadPages) {
                tags.addItemPage(loadSourceId, preferExistingItems, itemLoadPage.getPageIdx(), itemLoadPage.getItems().size(), itemLoadPage.getItems());
            }
            List<Tag> expectedResult = buildExpectedOutcome(tags, itemLoadPages, preferExistingItems);
            assertArrayEquals("Final list should match expected content", expectedResult.toArray(), tags.getItems().toArray());
        }

        public void injectFrom(TagLoadAction page1, int srcPage, int srcPageIdx) {
            Tag item = page1.getItemLoadPages().get(srcPage).getItems().get(srcPageIdx);
            itemLoadPages.add(new ItemLoadPage<>(item));
        }
    }

    private PiwigoTags<Tag> loadTagPages(boolean reversed, TagLoadAction ... loadActions) {


        PiwigoTags<Tag> tags = new PiwigoTags<>();
        tags.setRetrieveItemsInReverseOrder(reversed);

        for(TagLoadAction action : loadActions) {
            action.loadTagsPage(tags);
        }

        return tags;
    }


    private List<Tag> buildExpectedOutcome(PiwigoTags<Tag> tags, List<ItemLoadPage<Tag>> itemLoadPages, boolean preferExistingContent) {
        return PiwigoTagUtil.buildExpectedResult(tags, itemLoadPages, preferExistingContent);
    }

}