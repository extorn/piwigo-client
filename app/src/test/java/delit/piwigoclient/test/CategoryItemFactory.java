package delit.piwigoclient.test;

import java.util.Calendar;

import delit.piwigoclient.model.piwigo.CategoryItem;

public class CategoryItemFactory extends IdentifiableItemFactory {
    private Calendar lastAlteredCalendar = buildCalendar(2000,01,01);

    public CategoryItemFactory() {
        super("Album");
    }

    public CategoryItem getNextByName(int albumCount, int photoCount) {
        return new CategoryItem(nextItemId++, incrementAndGetName(), "", false, null, photoCount, photoCount, albumCount, null);
    }
    public CategoryItem getNextByName(int photoCount) {
        return new CategoryItem(nextItemId++, incrementAndGetName(), "", false, null, photoCount, photoCount, 0, null);
    }
    public CategoryItem getNextByLastAltered(int photoCount) {
        return new CategoryItem(nextItemId++, incrementAndGetName(), "", false, getAndIncrementAndDate(lastAlteredCalendar), photoCount, photoCount, 0, null);
    }
}
