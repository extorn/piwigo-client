package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.os.Environment;
import android.util.AttributeSet;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;

public class LocalFoldersListPreference extends EventDrivenPreference<FileSelectionNeededEvent> {


    public LocalFoldersListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LocalFoldersListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LocalFoldersListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LocalFoldersListPreference(Context context) {
        super(context);
    }

    @Override
    public CharSequence getSummary() {
        CharSequence summaryPattern = super.getSummary();
        if (summaryPattern == null) {
            return null;
        }
        String albumName = getValue();
        if (albumName != null) {
            return String.format(super.getSummary().toString(), albumName);
        } else {
            return getContext().getString(R.string.local_folder_preference_summary_default);
        }
    }

    @Override
    protected FileSelectionNeededEvent buildOpenSelectionEvent() {

        String initialFolder = getValue();
        ArrayList<String> selection = new ArrayList<>();
        if (initialFolder == null) {
            initialFolder = Environment.getExternalStorageDirectory().getAbsolutePath();
        } else {
            File initialSelection = new File(initialFolder);
            if (initialSelection.exists()) {
                initialFolder = initialSelection.getParentFile().getAbsolutePath();
                selection.add(initialSelection.getAbsolutePath());
            } else {
                while (!initialSelection.exists()) {
                    initialSelection = initialSelection.getParentFile();
                }
                initialFolder = initialSelection.getAbsolutePath();
            }
        }
        FileSelectionNeededEvent fileSelectNeededEvent = new FileSelectionNeededEvent(false, true, false);
        fileSelectNeededEvent.withInitialFolder(initialFolder);
        fileSelectNeededEvent.withVisibleContent(FileSelectionNeededEvent.ALPHABETICAL);
        fileSelectNeededEvent.withInitialSelection(selection);
        return fileSelectNeededEvent;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED, sticky = true)
    public void onEvent(FileSelectionCompleteEvent event) {
        if(isTrackingEvent(event)) {
            EventBus.getDefault().removeStickyEvent(event);
            File selectedFile = event.getSelectedFiles().get(0);
            if (selectedFile.isDirectory()) {
                persistStringValue(selectedFile.getAbsolutePath());
            }
        }
    }

}
