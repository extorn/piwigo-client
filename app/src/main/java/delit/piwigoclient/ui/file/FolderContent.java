package delit.piwigoclient.ui.file;

import java.io.File;

import delit.piwigoclient.model.piwigo.PagedList;

public class FolderContent extends PagedList<File> {
    public FolderContent(String itemType) {
        super(itemType);
    }

    public FolderContent(String itemType, int maxExpectedItemCount) {
        super(itemType, maxExpectedItemCount);
    }

    @Override
    public Long getItemId(File item) {
        return Long.valueOf(item.getAbsolutePath().hashCode());
    }
}
