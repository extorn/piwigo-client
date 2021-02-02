package delit.piwigoclient.test;

import java.util.Date;

import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Tag;

public class TagFactory extends IdentifiableItemFactory {


    public TagFactory() {
        super("Tag");
    }

    public Tag getNextByName() {
        return getNewTag(incrementAndGetName());
    }

    private Tag getNewTag(String name) {
        return new Tag(nextItemId++, name);
    }
}
