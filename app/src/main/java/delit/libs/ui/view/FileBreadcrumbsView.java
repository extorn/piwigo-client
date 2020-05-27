package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

public class FileBreadcrumbsView extends AbstractBreadcrumbsView<File> {

    public FileBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPathNavigator(new FilePathNavigator());
    }

    public FileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPathNavigator(new FilePathNavigator());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setPathNavigator(new FilePathNavigator());
    }

    private class FilePathNavigator implements PathNavigator<File> {
        @Override
        public String getItemName(File item) {
            return item.getName();
        }

        @Override
        public File getParent(File item) {
            return item.getParentFile();
        }
    }
}
