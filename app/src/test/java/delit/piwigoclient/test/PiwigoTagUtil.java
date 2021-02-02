package delit.piwigoclient.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoAlbumTest;
import delit.piwigoclient.model.piwigo.PiwigoTags;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

import static org.junit.Assert.assertArrayEquals;

public class PiwigoTagUtil {

    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private static Logger logger = Logger.getLogger(PiwigoAlbumTest.class.getName());

    public static ArrayList<ItemLoadPage<Tag>> initialiseTagItemLoadPages(TagFactory tagFactory, int pagesWanted, int pageSizeWanted) {
        ArrayList<ItemLoadPage<Tag>> resourceItemLoadPages = new ArrayList<>();
        for(int pageIdx = 0; pageIdx < pagesWanted; pageIdx++) {
            ItemLoadPage<Tag> page = new ItemLoadPage<>();
            for(int pageSize = 0; pageSize < pageSizeWanted; pageSize++) {
                page.add(tagFactory.getNextByName());
            }
            resourceItemLoadPages.add(page);
        }
        return resourceItemLoadPages;
    }

    public static void assertHasBeenReversed(List<Tag> originalOrder, ArrayList<Tag> reversedOrder) {
        assertArrayEquals(originalOrder.toArray(), reversedOrder.toArray());
    }

    public static List<Tag> buildExpectedResult(PiwigoTags<Tag> tags, List<ItemLoadPage<Tag>> itemLoadPages, boolean preferExistingContent) {
        boolean reverseOrder = tags.isRetrieveItemsInReverseOrder();
        ArrayList<Tag> expected = new ArrayList<>();
        expected.addAll(tags.getItems());

        ListIterator<ItemLoadPage<Tag>> iterator = itemLoadPages.listIterator(reverseOrder?itemLoadPages.size():0);
        while (reverseOrder != iterator.hasNext() || reverseOrder == iterator.hasPrevious()) {
            ItemLoadPage<Tag> page = reverseOrder?iterator.previous():iterator.next();
            ArrayList<Tag> pageOfItems = new ArrayList<>(page.getItems());
            if (reverseOrder) {
                Collections.reverse(pageOfItems);
            }
            for(Tag item : pageOfItems) {
                if(tags.getItems().contains(item)) {
                    if(preferExistingContent) {
                        continue; // skip this item.
                    } else {
                        int idx = expected.indexOf(item);
                        if(idx < 0) {
                            // not present
                            expected.add(item);
                        } else {
                            expected.remove(idx);
                            expected.add(idx, item);
                        }
                    }
                }
            }
        }
        return expected;
    }
}
