package delit.piwigoclient.ui.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.AvailableAlbumsListAdapter;
import delit.piwigoclient.util.ObjectUtils;

/**
 * Created by gareth on 15/07/17.
 */

public class ServerAlbumListPreference extends DialogPreference {

    private CustomPiwigoResponseListener serviceCallHandler;
    private ListView itemListView;
    private String value;
    private long activeServiceCall = -1;
    private String connectionProfileNamePreferenceKey;
    private boolean mValueSet;

    public ServerAlbumListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ServerAlbumListPreference, defStyleAttr, 0);
            connectionProfileNamePreferenceKey = a.getString(R.styleable.ServerAlbumListPreference_connectionProfileNameKey);
            a.recycle();
        serviceCallHandler = new CustomPiwigoResponseListener();
    }

    public ServerAlbumListPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public ServerAlbumListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();
        if(activeServiceCall >= 0) {
            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(activeServiceCall, serviceCallHandler);
        }
    }


    @Override
    protected void onPrepareForRemoval() {
        if(activeServiceCall >= 0) {
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(activeServiceCall);
        }
        super.onPrepareForRemoval();
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        triggerAlbumsListLoad();
    }

    /**
     * Sets the value of the key.
     *
     * @param value The value to set for the key.
     */
    private void setValue(String value) {
        // Always persist/notify the first time.
        boolean changed = !ObjectUtils.areEqual(this.value, value);
        if (!mValueSet || changed) {
            this.value = value;
            mValueSet = true;
            persistString(this.value);
            if (changed) {
                notifyChanged();
            }
        }
    }

    @Override
    public CharSequence getSummary() {
        if(super.getSummary() == null) {
            return ServerAlbumPreference.getSelectedAlbumName(value);
        } else {
            return String.format(super.getSummary().toString(), ServerAlbumPreference.getSelectedAlbumName(value));
        }
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        value = super.getPersistedString(null);
        View view = buildListView();
        builder.setView(view);
    }

    private View buildListView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if(AdsManager.getInstance().shouldShowAdverts()) {
            adView.loadAd(new AdRequest.Builder().build());
            adView.setVisibility(View.VISIBLE);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
        heading.setText(R.string.preference_data_upload_automatic_server_album_title);
        heading.setVisibility(View.VISIBLE);

        itemListView = view.findViewById(R.id.list);

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    private SharedPreferences getAppSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    private void triggerAlbumsListLoad() {

        SharedPreferences prefs = getSharedPreferences();

        String connectionProfile = prefs.getString(connectionProfileNamePreferenceKey, null);
        if(connectionProfile == null) {
            getDialog().dismiss();
        }

        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getPreferences(connectionProfile);

        AlbumGetSubAlbumNamesResponseHandler handler = new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true);
        handler.withConnectionPreferences(connectionPrefs);
        activeServiceCall = handler.invokeAsync(getContext());

        CustomUIHelper uiHelper = new CustomUIHelper(this, getAppSharedPreferences(), getContext());
        serviceCallHandler.withUiHelper(this, uiHelper);

        PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(activeServiceCall, serviceCallHandler);
    }

    private class CustomUIHelper extends UIHelper {
        public CustomUIHelper(Object parent, SharedPreferences prefs, Context context) {
            super(parent, prefs, context);
        }

        @Override
        protected boolean canShowDialog() {
            return getDialog() != null;
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                addAlbumsToUI(((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
            }
            super.onAfterHandlePiwigoResponse(response);
        }
    }

    protected boolean isAppInReadOnlyMode() {
        return getSharedPreferences().getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    private void addAlbumsToUI(ArrayList<CategoryItemStub> albumNames) {

        int listItemLayout = android.R.layout.simple_list_item_single_choice;
        AvailableAlbumsListAdapter adapter = new AvailableAlbumsListAdapter(CategoryItem.ROOT_ALBUM, getContext(), listItemLayout);
        adapter.addAll(albumNames);
        itemListView.setAdapter(adapter);
        // clear checked items
        itemListView.clearChoices();
        itemListView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        itemListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                itemListView.setItemChecked(itemListView.getSelectedItemPosition(), false);
                itemListView.setItemChecked(position, true);
            }
        });

        long selectedAlbumId = ServerAlbumPreference.getSelectedAlbumId(value);
        if(selectedAlbumId >= 0) {
            int itemPos = adapter.getPosition(selectedAlbumId);
            if (itemPos >= 0) {
                itemListView.setItemChecked(itemPos, true);
            }
        }
    }

    public static class ServerAlbumPreference {

        public static String toValue(CategoryItemStub album) {
            return String.format("%1$d;%2$s",album.getId(), album.getName());
        }

        public static long getSelectedAlbumId(String preferenceValue) {
            if(preferenceValue != null) {
                return Long.valueOf(preferenceValue.split(";", 2)[0]);
            }
            return -1;
        }

        public static String getSelectedAlbumName(String preferenceValue) {
            if(preferenceValue != null) {
                return preferenceValue.split(";", 2)[1];
            }
            return null;
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            CategoryItemStub selectedAlbum = (CategoryItemStub)itemListView.getAdapter().getItem(itemListView.getCheckedItemPosition());
            String selectedValue = ServerAlbumPreference.toValue(selectedAlbum);
            mValueSet = false; // force the value to be saved.

            if (callChangeListener(selectedAlbum==null?null:selectedAlbum.getId())) {
                setValue(selectedAlbum==null?null:selectedValue);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ? getPersistedString(null) : (String) defaultValue);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = this.value;
        myState.connectionProfileNamePreferenceKey = connectionProfileNamePreferenceKey;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
        this.connectionProfileNamePreferenceKey = myState.connectionProfileNamePreferenceKey;
    }

    public static class SavedState extends BaseSavedState {

        public static final Creator<SavedState> CREATOR =
                new Creator<ServerAlbumListPreference.SavedState>() {
                    public ServerAlbumListPreference.SavedState createFromParcel(Parcel in) {
                        return new ServerAlbumListPreference.SavedState(in);
                    }

                    public ServerAlbumListPreference.SavedState[] newArray(int size) {
                        return new ServerAlbumListPreference.SavedState[size];
                    }
                };

        private String value;
        public String connectionProfileNamePreferenceKey;

        public SavedState(Parcel source) {
            super(source);
            value = source.readString();
            connectionProfileNamePreferenceKey = source.readString();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(value);
            dest.writeString(connectionProfileNamePreferenceKey);
        }
    }

}