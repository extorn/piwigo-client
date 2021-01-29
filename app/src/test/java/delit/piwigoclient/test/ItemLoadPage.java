package delit.piwigoclient.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.model.piwigo.GalleryItem;

public class ItemLoadPage {
    private static int nextLoadId = 0;
    private static int nextPageId = 0;
    private long loadId;
    private int pageIdx;
    private List<GalleryItem> items;

    public ItemLoadPage(GalleryItem ... items) {
        this(Arrays.asList(items));
    }

    public ItemLoadPage(List<GalleryItem> items) {
        this.loadId = nextLoadId++;
        this.pageIdx = nextPageId++;
        this.setItems(items);
    }

    public ItemLoadPage() {
        this(new ArrayList<>());
    }

    public static int getNextLoadId() {
        return nextLoadId;
    }

    public static int getNextPageId() {
        return nextPageId;
    }

    public List<GalleryItem> getItems() {
        return items;
    }

    public void add(GalleryItem item) {
        getItems().add(item);
    }

    public int getPageIdx() {
        return pageIdx;
    }

    public void setItems(List<GalleryItem> items) {
        this.items = items;
    }
}
