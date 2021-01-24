package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

public class ServerAlbumListPreferenceDialogFragmentCompat<F extends ServerAlbumListPreferenceDialogFragmentCompat<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends PreferenceDialogFragmentCompat implements DialogPreference.TargetFragment {
    // state persistent values
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
        View view = getLayoutInflater().inflate(R.layout.layout_fullsize_list, null, false);


        AdView adView = view.findViewById(R.id.list_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
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
    public Preference findPreference(@NonNull CharSequence key) {
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
    public void onDismiss(@NonNull DialogInterface dialog) {
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

    long retrieveAppropriateAlbumList(ConnectionPreferences.ProfilePreferences connectionPrefs, PiwigoSessionDetails sessionDetails) {
        if (PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
            return addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumsAdminResponseHandler(), connectionPrefs);
        } else if (sessionDetails != null && sessionDetails.isCommunityApiAvailable()) {
            final boolean recursive = true;
            return addActiveServiceCall(R.string.progress_loading_albums, new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive), connectionPrefs);
        } else {
            final boolean recursive = true;
            return addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive), connectionPrefs);
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
        return PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
    }

    ConnectionPreferences.ProfilePreferences getConnectionProfile() {
        SharedPreferences prefs = getSharedPreferences();
        ServerAlbumListPreference pref = getPreference();
        String connectionProfile = prefs.getString(pref.getConnectionProfileNamePreferenceKey(), null);
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getPreferences(connectionProfile, prefs, getContext());

        return connectionPrefs;
    }

    private long invokeRetrieveSubCategoryNamesCall(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isFullyLoggedIn()) {
            return retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
        } else {
            return addActiveServiceCall(R.string.progress_loading_user_details, new LoginResponseHandler(), connectionPrefs);
        }
    }

    private static class CustomPiwigoResponseListener<F extends ServerAlbumListPreferenceDialogFragmentCompat<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {


        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            ConnectionPreferences.ProfilePreferences connectionPrefs = getParent().getConnectionProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                getParent().retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
            } else if (response instanceof CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) {
                getParent().addAlbumsToUI(false, ((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
            } else if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                PiwigoSessionDetails.getInstance(connectionPrefs);
                // need to try again as this call will have been pointless.
                if (sessionDetails != null && (sessionDetails.isCommunityApiAvailable() || sessionDetails.isAdminUser())) {
                    getParent().retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
                } else {
                    getParent().addAlbumsToUI(false, ((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response).getAlbumNames());
                }
            } else if (response instanceof AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                getParent().addAlbumsToUI(true, ((AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response).getAdminList().flattenTree());
            }
            super.onAfterHandlePiwigoResponse(response);
        }
    }

    private long addActiveServiceCall(int loadingMsgId, AbstractPiwigoWsResponseHandler handler, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        long messageId = handler.invokeAsync(getContext(), connectionPrefs);
        uiHelper.addActiveServiceCall(getString(loadingMsgId), messageId, handler.getTag());
        return messageId;
    }

    protected boolean isAppInReadOnlyMode() {
        return getSharedPreferences().getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    void addAlbumsToUI(boolean isAdminList, ArrayList<CategoryItemStub> albumNames) {

        AlbumSelectionListAdapterPreferences viewPrefs = new AlbumSelectionListAdapterPreferences();
        viewPrefs.selectable(false, false);
        viewPrefs.setFlattenAlbumHierarchy(true);
        AvailableAlbumsListAdapter adapter = new AvailableAlbumsListAdapter(viewPrefs, CategoryItem.ROOT_ALBUM, requireContext());
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
            Long selectedAlbumId = selectedAlbum.getId();
            if (pref.callChangeListener(selectedAlbumId)) {
                pref.persistStringValue(selectedAlbumAsStr);
            }
        }
    }

    private static class CustomUIHelper extends UIHelper<CustomUIHelper,DialogFragment> {
        public CustomUIHelper(DialogFragment parent, SharedPreferences prefs, Context context) {
            super(parent, prefs, context);
        }

        @Override
        protected View getParentView() {
            DialogFragment parent = getParent();
            if(parent == null) {
                return null;
            }
            return parent.getView();
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
