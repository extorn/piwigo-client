package delit.piwigoclient.ui.common.preference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

import com.google.android.gms.ads.AdView;

import java.util.ArrayList;

import delit.libs.ui.util.DisplayUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.common.DialogFragmentUIHelper;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;
import delit.piwigoclient.ui.preferences.MyDialogPreferenceFragment;

public class ServerAlbumListPreferenceDialogFragmentCompat<F extends ServerAlbumListPreferenceDialogFragmentCompat<F,FUIH>, FUIH extends DialogFragmentUIHelper<FUIH,F>> extends MyDialogPreferenceFragment<F,FUIH> implements DialogPreference.TargetFragment {
    // state persistent values
    private long activeServiceCall = -1;
    private CustomPiwigoResponseListener<F,FUIH> serviceCallHandler;
    private ListView itemListView;
    private final String STATE_ACTIVE_SERVICE_CALL = "ServerAlbumListPreference.ActiveCallId";

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
        triggerAlbumsListLoad();
    }

    @Override
    public <T extends Preference> T findPreference(@NonNull CharSequence key) {
        return (T) getPreference();
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
        final ServerAlbumListPreferenceDialogFragmentCompat<?,?> fragment =
                new ServerAlbumListPreferenceDialogFragmentCompat<>();
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
            return addActiveServiceCall(R.string.progress_loading_albums, new CommunityGetSubAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive), connectionPrefs);
        } else {
            final boolean recursive = true;
            return addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId()/*currentGallery.id*/, recursive), connectionPrefs);
        }
    }

    @Override
    protected void setupUiHelper(@NonNull View view) {
        super.setupUiHelper(view);
        // need to recreate this because we're binding it to this view.
        serviceCallHandler = new CustomPiwigoResponseListener<>();
        getUIHelper().setPiwigoResponseListener(serviceCallHandler);
        serviceCallHandler.withUiHelper((F) this, getUIHelper());
    }

    private void triggerAlbumsListLoad() {
        activeServiceCall = invokeRetrieveSubCategoryNamesCall(getConnectionProfile());
    }

    ConnectionPreferences.ProfilePreferences getConnectionProfile() {
        SharedPreferences prefs = getSharedPreferences();
        ServerAlbumListPreference pref = getPreference();
        String connectionProfile = prefs.getString(pref.getConnectionProfileNamePreferenceKey(), null);
        return ConnectionPreferences.getPreferences(connectionProfile, prefs, getContext());
    }

    private long invokeRetrieveSubCategoryNamesCall(ConnectionPreferences.ProfilePreferences connectionPrefs) {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if (sessionDetails != null && sessionDetails.isFullyLoggedIn()) {
            return retrieveAppropriateAlbumList(connectionPrefs, sessionDetails);
        } else {
            return addActiveServiceCall(R.string.progress_loading_user_details, new LoginResponseHandler(), connectionPrefs);
        }
    }

    private static class CustomPiwigoResponseListener<F extends ServerAlbumListPreferenceDialogFragmentCompat<F,FUIH>, FUIH extends DialogFragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {


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
        getUIHelper().addActiveServiceCall(getString(loadingMsgId), messageId, handler.getTag());
        return messageId;
    }

    protected boolean isAppInReadOnlyMode() {
        return getSharedPreferences().getBoolean(getContext().getString(R.string.preference_app_read_only_mode_key), false);
    }

    void addAlbumsToUI(boolean isAdminList, ArrayList<CategoryItemStub> albumNames) {

        AlbumSelectionListAdapterPreferences viewPrefs = new AlbumSelectionListAdapterPreferences(true, false,false, false);
        AvailableAlbumsListAdapter adapter = new AvailableAlbumsListAdapter(viewPrefs, StaticCategoryItem.ROOT_ALBUM, requireContext());
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
        itemListView.setOnItemClickListener((parent, view, position, id) -> {
            itemListView.setItemChecked(itemListView.getSelectedItemPosition(), false);
            itemListView.setItemChecked(position, true);
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

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DisplayUtils.hideKeyboardFrom(getContext(), dialog);
        super.onClick(dialog, which);
    }
}
