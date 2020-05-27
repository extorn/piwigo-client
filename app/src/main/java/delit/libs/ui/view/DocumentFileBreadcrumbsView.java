package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.AttributeSet;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;

import delit.libs.util.IOUtils;

public class DocumentFileBreadcrumbsView extends AbstractBreadcrumbsView<DocumentFile> {

    public DocumentFileBreadcrumbsView(Context context) {
        super(context);
    }

    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public DocumentFileBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected String getItemName(DocumentFile item) {
        return item.getName();
    }

    @Override
    protected DocumentFile getParent(DocumentFile item) {
        return item.getParentFile();
    }
}