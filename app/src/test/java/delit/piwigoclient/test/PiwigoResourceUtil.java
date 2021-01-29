package delit.piwigoclient.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoAlbumTest;
import delit.piwigoclient.model.piwigo.ResourceItem;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class PiwigoResourceUtil {

    //NOTE: this is being ignored at the moment!
    //Adding this doesn't seem to help.  -Djava.util.logging.config.file=src/test/resources/logging.properties
    private static Logger logger = Logger.getLogger(PiwigoAlbumTest.class.getName());

    public static ArrayList<ItemLoadPage> initialiseResourceItemLoadPages(ResourceItemFactory resourceItemFactory, int sortOrder, int pagesWanted, int pageSizeWanted) {
        ArrayList<ItemLoadPage> resourceItemLoadPages = new ArrayList<>();
        for(int pageIdx = 0; pageIdx < pagesWanted; pageIdx++) {
            ItemLoadPage page = new ItemLoadPage();
            for(int pageSize = 0; pageSize < pageSizeWanted; pageSize++) {
                switch(sortOrder) {
                    case PiwigoAlbum.ALBUM_SORT_ORDER_DEFAULT:
                        page.add(resourceItemFactory.getNextByName());
                        break;
                    case PiwigoAlbum.ALBUM_SORT_ORDER_DATE:
                        page.add(resourceItemFactory.getNextByCreateDate());
                        break;
                    case PiwigoAlbum.ALBUM_SORT_ORDER_NAME:
                        page.add(resourceItemFactory.getNextByName());
                        break;
                }
            }
            resourceItemLoadPages.add(page);
        }
        return resourceItemLoadPages;
    }

    public static void assertHasBeenReversed(List<GalleryItem> originalOrder, ArrayList<GalleryItem> reversedOrder) {
        List<Integer> bannersAtIdx = new ArrayList<>();
        int i = 0;
        for(GalleryItem item : originalOrder) {
            if(item == ResourceItem.PICTURE_HEADING || item == CategoryItem.ALBUM_HEADING || item == CategoryItem.ADVERT || item == CategoryItem.BLANK) {
                bannersAtIdx.add(i);
            }
            i++;
        }
        int fromIdx = 0;
        int toIdx = 0;
        int checksDone = 0;
        for(int nonItemIdx : bannersAtIdx) {
            if(fromIdx >= 0) {
                toIdx = nonItemIdx;
            }
            if(toIdx != fromIdx) {
                if(toIdx - fromIdx > 1) {
                    // there is an item to check is reversed
                    List<GalleryItem> originalArr = originalOrder.subList(fromIdx, toIdx);
                    List<GalleryItem> reversedArr = reversedOrder.subList(fromIdx, toIdx);
                    logger.log(Level.FINE, String.format("Checking for equality from album idx %1$d to %2$d", fromIdx, toIdx));
                    assertArrayEquals(originalArr.toArray(), reversedArr.toArray());
                    checksDone++;
                } else {
                    fromIdx = toIdx;
                }

            }
        }
        if(checksDone == 0) {
            // compare the entire list
            assertArrayEquals(originalOrder.toArray(), reversedOrder.toArray());
        }
    }

    public static List<GalleryItem> buildExpectedResult(boolean addHeader, boolean reverseOrder, List<ItemLoadPage> resourceItemLoadPages) {
        boolean addedHeading = false;
        ArrayList<GalleryItem> expected = new ArrayList<>();
        ListIterator<ItemLoadPage> iterator = resourceItemLoadPages.listIterator(reverseOrder?resourceItemLoadPages.size():0);
        while (reverseOrder != iterator.hasNext() || reverseOrder == iterator.hasPrevious()) {
            ItemLoadPage page = reverseOrder?iterator.previous():iterator.next();
            ArrayList<GalleryItem> pageOfItems = new ArrayList<>(page.getItems());
            if (addHeader && pageOfItems.size() > 0 && !addedHeading) {
                addedHeading = true;
                expected.add(ResourceItem.PICTURE_HEADING);
            }
            if (reverseOrder) {
                Collections.reverse(pageOfItems);
            }
            expected.addAll(pageOfItems);
        }
        return expected;
    }
}
