package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.greenrobot.eventbus.Subscribe;

import java.util.HashSet;
import java.util.Locale;

import androidx.annotation.NonNull;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
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
    protected void initPreference(Context context, AttributeSet attrs) {
        super.initPreference(context, attrs);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ServerAlbumSelectPreference, 0, 0);
        connectionProfileNamePreferenceKey = a.getString(R.styleable.ServerAlbumSelectPreference_connectionProfileNameKey);
        a.recycle();
        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if(key.equals(connectionProfileNamePreferenceKey)) {
                    setEnabled(null != sharedPreferences.getString(connectionProfileNamePreferenceKey, null));
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
        String currentValue = getPersistedString(getCurrentValue());
        if (super.getSummary() == null) {
            return ServerAlbumPreference.getSelectedAlbumName(currentValue);
        } else {
            String albumName = ServerAlbumPreference.getSelectedAlbumName(currentValue);
            if (albumName != null) {
                return String.format(super.getSummary().toString(), albumName);
            } else {
                return getContext().getString(R.string.server_album_preference_summary_default);
            }
        }
    }
    
    @Override
    protected ExpandingAlbumSelectionNeededEvent buildOpenSelectionEvent() {
        HashSet<Long> currentSelection = new HashSet<>(1);
        long selectedAlbumId = getSelectedAlbumId();
        long defaultRootAlbumId = 0;
        if(selectedAlbumId >= 0) {
            currentSelection.add(selectedAlbumId);
            defaultRootAlbumId = selectedAlbumId;
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
                persistStringValue(ServerAlbumPreference.toValue(selectedVal));
                notifyChanged();
            }
        }
    }

    public long getSelectedAlbumId() {
        return ServerAlbumPreference.getSelectedAlbumId(getCurrentValue());
    }

    public static class ServerAlbumPreference {

        public static String toValue(@NonNull CategoryItem album) {
            return String.format(Locale.UK,"%1$d;%2$s", album.getId(), album.getName());
        }

        public static long getSelectedAlbumId(String preferenceValue) {
            if (preferenceValue != null) {
                return Long.valueOf(preferenceValue.split(";", 2)[0]);
            }
            return -1;
        }

        public static String getSelectedAlbumName(String preferenceValue) {
            if (preferenceValue != null) {
                return preferenceValue.split(";", 2)[1];
            }
            return null;
        }
    }
}
