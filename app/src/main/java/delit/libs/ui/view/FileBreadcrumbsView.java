package delit.libs.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import java.io.File;

public class FileBreadcrumbsView extends AbstractBreadcrumbsView<File> {

    public FileBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected String getItemName(File item) {
        return item.getName();
    }

    @Override
    protected File getParent(File item) {
        return item.getParentFile();
    }
}
