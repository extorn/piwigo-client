package delit.piwigoclient.ui.preferences;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.util.DisplayUtils;

public class ServerAlbumListPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    // state persistant values
    private long activeServiceCall = -1;
    private CustomPiwigoResponseListener serviceCallHandler;
    private ListView itemListView;
    private String STATE_ACTIVE_SERVICE_CALL = "ServerAlbumListPreference.ActiveCallId";
    private CustomUIHelper uiHelper;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            savedInstanceState.getLong(STATE_ACTIVE_SERVICE_CALL);
        }
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_ACTIVE_SERVICE_CALL, activeServiceCall);
    }

    @Override
    protected View onCreateDialogView(Context context) {
        return buildDialogView();
    }

    private View buildDialogView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_fullsize_list, null, false);

        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(View.GONE);
        }

        view.findViewById(R.id.list_action_cancel_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_toggle_all_button).setVisibility(View.GONE);
        view.findViewById(R.id.list_action_add_item_button).setVisibility(View.GONE);

        TextView heading = view.findViewById(R.id.heading);
//        heading.setText(R.string.preference_data_upload_automatic_job_server_album_title);
        heading.setVisibility(View.GONE);

        itemListView = view.findViewById(R.id.list);

        Button saveChangesButton = view.findViewById(R.id.list_action_save_button);
        saveChangesButton.setVisibility(View.GONE);

        return view;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        serviceCallHandler = new CustomPiwigoResponseListener();
        triggerAlbumsListLoad();
    }

    @Override
    public Preference findPreference(CharSequence key) {
        return getPreference();
    }

    @Override
    public ServerAlbumListPreference getPreference() {
        return (ServerAlbumListPreference) super.getPreference();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (activeServiceCall >= 0) {
            PiwigoResponseBufferingHandler.getDefault().registerResponseHandler(activeServiceCall, serviceCallHandler);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (activeServiceCall >= 0) {
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(activeServiceCall);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (activeServiceCall >= 0) {
            PiwigoResponseBufferingHandler.getDefault().deRegisterResponseHandler(activeServiceCall);
        }
    }

    public static DialogFragment newInstance(String key) {
        final ServerAlbumListPreferenceDialogFragmentCompat fragment =
                new ServerAlbumListPreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = getConnectionProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
            } else if (response instanceof CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) {
                addAlbumsToUI(false, ((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                PiwigoSessionDetails.getInstance(connectionPrefs);
                // need to try again as this call will have been pointless.
                if (sessionDetails != null && (sessionDetails.isUseCommunityPlugin() || sessionDetails.isAdminUser())) {
                    retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
                } else {
                    addAlbumsToUI(false, ((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
                }
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) {
                addAlbumsToUI(true, ((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) response).getAdminList().flattenTree());
            }
            super.onAfterHandlePiwigoResponse(response);
        }
    }

    private void triggerAlbumsListLoad() {

        ServerAlbumListPreference pref = getPreference();

        uiHelper = new CustomUIHelper(this, getSharedPreferences(), getContext());
        uiHelper.setPiwigoResponseListener(serviceCallHandler);
        serviceCallHandler.withUiHelper(this, uiHelper);

        activeServiceCall = invokeRetrieveSubCategoryNamesCall(getConnectionProfile());
    }

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
    }

    private ConnectionPreferences.ProfilePreferences getConnectionProfile() {
        SharedPreferences prefs = getSharedPreferences();
        ServerAlbumListPreference pref = getPreference();
        String connectionProfile = prefs.getString(pref.getConnectionProfileNamePreferenceKey(), null);
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getPreferences(connectionProfile);

        return connectionPrefs;
    }

    private long invokeRetrieveSubCategoryNamesCall(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isFullyLoggedIn()) {
            return retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
        } else {
            return addActiveServiceCall(R.string.progress_loading_user_details, new LoginResponseHandler().invokeAsync(getContext(), connectionPrefs));
        }
    }

    private long retrieveAppropriateAlbumList(ConnectionPreferences.ProfilePreferences connectionPrefs, PiwigoSessionDetails sessionDetails) {
        if (PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
            return addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumsAdminResponseHandler().invokeAsync(getContext(), connectionPrefs));
        } else if (sessionDetails != null && sessionDetails.isUseCommunityPlugin()) {
            final boolean recursive = true;
            return addActiveServiceCall(R.string.progress_loading_albums, new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive).invokeAsync(getContext(), connectionPrefs));
        } else {
            final boolean recursive = true;
            return addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive).invokeAsync(getContext(), connectionPrefs));
        }
    }

    private long addActiveServiceCall(int progress_loading_albums, long messageId) {
        uiHelper.addActiveServiceCall(messageId);
        return messageId;
    }

    protected boolean isAppInReadOnlyMode() {
        return getSharedPreferences().getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    private void addAlbumsToUI(boolean isAdminList, ArrayList<CategoryItemStub> albumNames) {

        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences viewPrefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.withShowHierachy();
        AvailableAlbumsListAdapter adapter = new AvailableAlbumsListAdapter(viewPrefs, CategoryItem.ROOT_ALBUM, getContext());
        adapter.addAll(albumNames);
        if (!viewPrefs.isAllowRootAlbumSelection()) {
            CategoryItemStub catItem = adapter.getItemById(CategoryItemStub.ROOT_GALLERY.getId());
            if (catItem != null) {
                int idx = adapter.getPosition(catItem.getId());
                adapter.remove(catItem);
                adapter.insert(catItem.markNonUserSelectable(), idx);
            }
        }
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

        ServerAlbumListPreference pref = getPreference();
        long selectedAlbumId = pref.getSelectedAlbumId();
        if (selectedAlbumId >= 0) {
            int itemPos = adapter.getPosition(selectedAlbumId);
            if (itemPos >= 0) {
                itemListView.setItemChecked(itemPos, true);
            }
        }
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        int selectedItemPos = itemListView.getCheckedItemPosition();
        if (positiveResult && selectedItemPos >= 0) {
            CategoryItemStub selectedAlbum = (CategoryItemStub) itemListView.getAdapter().getItem(selectedItemPos);
            String selectedAlbumAsStr = ServerAlbumListPreference.ServerAlbumPreference.toValue(selectedAlbum);

            ServerAlbumListPreference pref = getPreference();
            long selectedAlbumId = selectedAlbumAsStr == null ? null : selectedAlbum.getId();
            if (pref.callChangeListener(selectedAlbumId)) {
                pref.persistStringValue(selectedAlbumAsStr);
            }
        }
    }

    private class CustomUIHelper extends UIHelper {
        public CustomUIHelper(Object parent, SharedPreferences prefs, Context context) {
            super(parent, prefs, context);
        }

        @Override
        protected boolean canShowDialog() {
            return true;
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
