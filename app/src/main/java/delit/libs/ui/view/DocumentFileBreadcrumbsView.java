package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

import delit.libs.util.IOUtils;

public class DocumentFileBreadcrumbsView extends AbstractBreadcrumbsView<DocumentFile> {

    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPathNavigator(new DocFilePathNavigator());
    }

    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setPathNavigator(new DocFilePathNavigator());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setPathNavigator(new DocFilePathNavigator());
    }

    private class DocFilePathNavigator implements PathNavigator<DocumentFile> {

        @Override
        public DocumentFile getParent(@NonNull DocumentFile item) {
            return item.getParentFile();
        }

        @Override
        public String getItemName(@NonNull DocumentFile item) {
            return  item.getName();
        }
    }
}
