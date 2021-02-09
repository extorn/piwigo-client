package delit.piwigoclient.ui.file;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.util.List;
import java.util.Map;

import delit.libs.ui.view.list.MappedArrayAdapter;
import delit.libs.util.ObjectUtils;

public class DocumentFileArrayAdapter extends MappedArrayAdapter<String, DocumentFile> {
    public DocumentFileArrayAdapter(@NonNull Context context, int resource, @NonNull String[] objects, @NonNull DocumentFile[] objectIds) {
        super(context, resource, objects, objectIds);
    }

    public DocumentFileArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull String[] objects, @NonNull DocumentFile[] objectValues) {
        super(context, resource, textViewResourceId, objects, objectValues);
    }

    public DocumentFileArrayAdapter(@NonNull Context context, int resource, @NonNull List<String> objects, @NonNull List<DocumentFile> objectValues) {
        super(context, resource, objects, objectValues);
    }

    public DocumentFileArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull List<String> objects, @NonNull List<DocumentFile> objectValues) {
        super(context, resource, textViewResourceId, objects, objectValues);
    }

    public DocumentFileArrayAdapter(@NonNull Context context, int resource, @NonNull Map<String, DocumentFile> objects) {
        super(context, resource, objects);
    }

    public DocumentFileArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull Map<String, DocumentFile> objects) {
        super(context, resource, textViewResourceId, objects);
    }

    @Override
    public int getPositionByValue(DocumentFile value) {
        // this is needed because DocumentFile equals is not overridden to do anything useful.
        if(value != null) {
            List<DocumentFile> objectValues = getObjectValues();
            for (int i = 0; i < objectValues.size(); i++) {
                DocumentFile df = objectValues.get(i);
                if (df != null && ObjectUtils.areEqual(df.getUri(), value.getUri())) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public @Nullable String getItemByValue(@Nullable DocumentFile value) {
        // this is needed because DocumentFile equals is not overridden to do anything useful.
        if(value != null) {
            for(DocumentFile df : getObjectValues()) {
                if(df != null && ObjectUtils.areEqual(df.getUri(), value.getUri())) {
                    return super.getItemByValue(df);
                }
            }
        }
        return null;
    }
}
