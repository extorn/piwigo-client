package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.NonNull;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;

public class ServerAlbumSelectPreference extends EventDrivenPreference<ExpandingAlbumSelectionNeededEvent> {

    public static final String TAG = "SrvAlbSelPref";
    private String connectionProfileNamePreferenceKey;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    
    public ServerAlbumSelectPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ServerAlbumSelectPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ServerAlbumSelectPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ServerAlbumSelectPreference(Context context) {
        super(context);
    }

    @Override
    protected void initPreference(final Context context, AttributeSet attrs) {
        super.initPreference(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ServerAlbumSelectPreference, 0, 0);
        connectionProfileNamePreferenceKey = a.getString(R.styleable.ServerAlbumSelectPreference_connectionProfileNameKey);
        a.recycle();
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals(connectionProfileNamePreferenceKey)) {
                    String connectionProfileName = sharedPreferences.getString(connectionProfileNamePreferenceKey, null);
                    setEnabled(connectionProfileName != null && ConnectionPreferences.getPreferences(connectionProfileName, sharedPreferences, context).isValid(getContext()));
                }
            }
        };
    }

    @Override
    public void onAttached() {
        getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        listener.onSharedPreferenceChanged(getSharedPreferences(), connectionProfileNamePreferenceKey);
        super.onAttached();
    }

    @Override
    public void onDetached() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
        super.onDetached();
    }

    @Override
    public CharSequence getSummary() {
        String currentValue = getPersistedString(getValue());
        ServerAlbumDetails pref = ServerAlbumDetails.fromEncodedPersistenceString(currentValue);
        String albumPath =pref.getAlbumPath();
        if (albumPath != null) {
            if (super.getSummary() == null) {
                return albumPath;
            } else {
                return String.format(super.getSummary().toString(), albumPath);
            }
        } else {
            return getContext().getString(R.string.server_album_preference_summary_default);
        }
    }
    
    @Override
    protected ExpandingAlbumSelectionNeededEvent buildOpenSelectionEvent() {
        HashSet<Long> currentSelection = new HashSet<>(1);
        long selectedAlbumId = getSelectedServerAlbumDetails().getAlbumId();
        long defaultRootAlbumId = 0;
        if(selectedAlbumId >= 0) {
            currentSelection.add(selectedAlbumId);
            defaultRootAlbumId = selectedAlbumId;
        }
        if(getSelectedServerAlbumDetails().getParentage() != null && getSelectedServerAlbumDetails().getParentage().size() > 0) {
            defaultRootAlbumId = getSelectedServerAlbumDetails().getParentage().get(getSelectedServerAlbumDetails().getParentage().size() -1);
        }
        ExpandingAlbumSelectionNeededEvent event = new ExpandingAlbumSelectionNeededEvent(false, true, currentSelection, defaultRootAlbumId);
        event.setConnectionProfileName(connectionProfileNamePreferenceKey);
        return event;
    }

    @Subscribe
    public void onEvent(ExpandingAlbumSelectionCompleteEvent event) {
        if(isTrackingEvent(event)) {
            HashSet<CategoryItem> selectedItems = event.getSelectedItems();
            if(selectedItems.size() > 1) {
                Crashlytics.log(Log.ERROR, TAG, "more than one album selected (using first)");
            }
            if(selectedItems.size() > 0) {
                CategoryItem selectedVal = selectedItems.iterator().next();
                setValue(new ServerAlbumDetails(selectedVal, event.getAlbumPath(selectedVal)).escapeSemiColons());
            }
        }
    }

    public ServerAlbumDetails getSelectedServerAlbumDetails() {
        return ServerAlbumDetails.fromEncodedPersistenceString(getValue());
    }

    public static class ServerAlbumDetails {

        private String albumPath;
        private List<Long> parentage;
        private String albumName;
        private long albumId = -1;

        public ServerAlbumDetails(long albumId, @NonNull String albumName, List<Long> parentage, String albumPath) {
            this.albumId = albumId;
            this.albumName = albumName;
            this.parentage = parentage;
            if (!CategoryItem.isRoot(albumId)) {
                if (albumPath == null) {
                    this.albumPath = "??? / " + albumName;
                } else {
                    this.albumPath = albumPath;
                }
            } else {
                this.albumPath = albumName;
            }
        }

        public static ServerAlbumDetails fromEncodedPersistenceString(String value) {
            if(value != null) {
                String[] pieces = value.split(";(?<!\\\\)");
                long albumId = Long.valueOf(pieces[0]);
                String albumName = unescapeSemiColons(pieces[1]);
                String albumPath;
                List<Long> parentage = null;
                if (CategoryItem.isRoot(albumId)) {
                    albumPath = albumName;
                    parentage = new ArrayList<>(0);
                } else {
                    albumPath = "??? / " + albumName;
                    if (pieces.length == 4) {
                        if (!"null".equals(pieces[2])) { // ignore this - this is only needed for preferences already corrupted.
                            try {
                                parentage = CollectionUtils.longsFromCsvList(pieces[2]);
                                albumPath = unescapeSemiColons(pieces[3]);
                            } catch (NumberFormatException e) {
                                // ignore this - this is only needed for preferences already corrupted.
                            }
                        }

                    }
                }
                return new ServerAlbumDetails(albumId, albumName, parentage, albumPath);
            }
            return new ServerAlbumDetails(-1, null, null, null);
        }

        public ServerAlbumDetails(@NonNull CategoryItem album, String albumPath) {
            this(album.getId(), album.getName(), album.getParentageChain(), albumPath);
        }

        private static String escapeSemiColons(String val) {
            return val.replaceAll(";", "\\;");
        }

        @NonNull
        @Override
        public String toString() {
            return getAlbumPath();
        }

        private static String unescapeSemiColons(String val) {
            return val.replaceAll("\\;", ";");
        }

        public String escapeSemiColons() {
            return String.format(Locale.UK, "%1$d;%2$s;%3$s;%4$s", albumId, escapeSemiColons(albumName), parentage == null ? "" : CollectionUtils.toCsvList(parentage), albumPath == null ? "" : escapeSemiColons(albumPath));
        }

        public List<Long> getParentage() {
            return parentage;
        }

        public String getAlbumName() {
            return albumName;
        }

        public String getAlbumPath() {
            return albumPath;
        }

        public long getAlbumId() {
            return albumId;
        }

        public CategoryItemStub toCategoryItemStub() {
            return new CategoryItemStub(albumName, albumId);
        }
    }
}
