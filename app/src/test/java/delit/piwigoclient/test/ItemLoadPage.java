package delit.piwigoclient.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import delit.piwigoclient.model.piwigo.GalleryItem;

public class ItemLoadPage<T> {
    private static int nextLoadId = 0;
    private static int nextPageId = 0;
    private long loadId;
    private int pageIdx;
    private List<T> items;

    public ItemLoadPage(T ... items) {
        this(Arrays.asList(items));
    }

    public static void resetLoadId() {
        nextLoadId = 0;
        nextPageId = 0;
    }

    public ItemLoadPage(List<T> items) {
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

    public List<T> getItems() {
        return items;
    }

    public void add(T item) {
        getItems().add(item);
    }

    public int getPageIdx() {
        return pageIdx;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public void reverseContents() {
        Collections.reverse(this.items);
    }
}
