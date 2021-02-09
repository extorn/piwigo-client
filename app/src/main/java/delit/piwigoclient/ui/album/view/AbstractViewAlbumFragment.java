package delit.piwigoclient.ui.album.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textview.MaterialTextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.ui.view.slidingsheet.SlidingBottomSheet;
import delit.libs.util.ArrayUtils;
import delit.libs.util.CollectionUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoAlbumAdminList;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.ServerConfig;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumSetStatusResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageChangeParentAlbumHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageSetPrivacyLevelResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumDeletedEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.BadRequestUsesRedirectionServerEvent;
import delit.piwigoclient.ui.events.BadRequestUsingHttpToHttpsServerEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.model.PiwigoAlbumModel;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.AdapterView.INVALID_POSITION;

/**
 * A fragment representing a list of Items.
 */
public abstract class AbstractViewAlbumFragment<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> {

    public static final String TAG = "AbsViewAlbumFrag";
    public static final String RESUME_ACTION = "ALBUM";
    protected static final String ARG_ALBUM = "album";
    private static final String STATE_EDITING_ITEM_DETAILS = "editingItemDetails";
    private static final String STATE_INFORMATION_SHOWING = "informationShowing";
    private static final String STATE_CURRENT_USERS = "currentUsers";
    private static final String STATE_CURRENT_GROUPS = "currentGroups";
    private static final String STATE_IS_ALBUM_DATA_DIRTY = "isAlbumDirty";
    private static final String STATE_GALLERY_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_GALLERY_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED = "movedResourceParentUpdateRequired";
    private static final String STATE_UPDATE_ALBUM_DETAILS_PROGRESS = "updateAlbumDetailsProgress";
    private static final String STATE_USERNAME_SELECTION_WANTED_NEXT = "usernameSelectionWantedNext";
    private static final String STATE_DELETE_ACTION_DATA = "bulkResourceActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String STATE_ALBUMS_PER_ROW = "albumsPerRow";
    private static final int UPDATE_IN_PROGRESS = 1;
    private static final int UPDATE_SETTING_ADDING_PERMISSIONS = 2;
    private static final int UPDATE_SETTING_REMOVING_PERMISSIONS = 3;
    private static final int UPDATE_NOT_RUNNING = 0;
    private static final String STATE_SELECTED_ITEMS = "selectedItemIds";
    public static final String SERVER_CALL_ID_SUB_CATEGORIES = "C";
    public static final String SERVER_CALL_ID_ALBUM_PERMISSIONS = "P";
    public static final String SERVER_CALL_ID_ALBUM_INFO_DETAIL = "U";
    public static final String SERVER_CALL_ID_ADMIN_LIST_ALBUMS = "AL";


    private static PiwigoAlbumAdminList adminOnlyServerCategoriesTree;
    private PiwigoAlbum<CategoryItem,GalleryItem> galleryModel;
    private HashSet<Long> userIdsInSelectedGroups;
    private List<CategoryItem> adminOnlyChildCategories;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;
    private boolean isReopening;
    private String currentResourceSortOrder;

    //****** Start fields maintained in saved session state.
    private boolean editingItemDetails;
    private boolean informationShowing;
    private long[] currentUsers;
    private long[] currentGroups;
    private boolean albumIsDirty;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private boolean movedResourceParentUpdateRequired;
    private int updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
    private boolean usernameSelectionWantedNext;
    private BulkResourceActionData bulkResourceActionData;
    private long userGuid;
    private int albumsPerRow; // calculated each time view created.

    //******  cached view references (all need clearing down)
    AlbumItemRecyclerViewAdapter viewAdapter; // cant use generics as the effect cascades too much
    ExtendedFloatingActionButton bulkActionButtonTag;
    private ExtendedFloatingActionButton retryActionButton;
    private TextView galleryNameHeader;
    private TextView galleryDescriptionHeader;
    private EditText galleryNameView;
    private EditText galleryDescriptionView;
    private MaterialButton saveButton;
    private MaterialButton discardButton;
    private MaterialButton editButton;
    private MaterialButton pasteButton;
    private MaterialButton cutButton;
    private MaterialButton deleteAlbumButton;
    private SwitchMaterial galleryUserCommentsPermittedField;
    private SwitchMaterial galleryPrivacyStatusField;
    private MaterialTextView allowedGroupsField;
    private MaterialTextView allowedUsersField;
    private ConstraintLayout bulkActionsContainer;
    private ExtendedFloatingActionButton bulkActionButtonPermissions;
    private ExtendedFloatingActionButton bulkActionButtonDelete;
    private ExtendedFloatingActionButton bulkActionButtonDownload;
    private ExtendedFloatingActionButton bulkActionButtonCopy;
    private ExtendedFloatingActionButton bulkActionButtonCut;
    private ExtendedFloatingActionButton bulkActionButtonPaste;
    private View basketView;
    private TextView emptyGalleryLabel;
    private TextView allowedGroupsFieldLabel;
    private TextView allowedUsersFieldLabel;
    private MaterialButton addNewAlbumButton;
    private AppCompatImageView actionIndicatorImg;
    private RecyclerView galleryListView;
    private SlidingBottomSheet bottomSheet;
    private CategoryItem albumDetails;
    private View albumHeaderBar;
    private View showInformationButton;
    // View listeners
    private CompoundButton.OnCheckedChangeListener privacyStatusFieldListener;
    private AlbumViewAdapterListener viewAdapterListener;
    private EndlessRecyclerViewScrollListener galleryListViewScrollListener;
    private SpanSizeLookup cellSpanLookup;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AbstractViewAlbumFragment() {
    }

    public static <F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> AbstractViewAlbumFragment<F,FUIH> newInstance(CategoryItem album) {
        AbstractViewAlbumFragment<F,FUIH> fragment = new ViewAlbumFragment<>();
        fragment.addArguments(album);
        return fragment;
    }

    private static <F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  void deleteResourcesFromServerForever(FUIH uiHelper, final HashSet<Long> selectedItemIds, final HashSet<ResourceItem> selectedItems) {
        String msg = uiHelper.getAppContext().getString(R.string.alert_confirm_really_delete_items_from_server);
        uiHelper.showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new DeleteResourceForeverAction<>(uiHelper, selectedItemIds, selectedItems));
    }

    public static <UIH extends UIHelper<UIH,?>> boolean canHandleReopenAction(UIH uiHelper) {
        ConnectionPreferences.ResumeActionPreferences resumePrefs = uiHelper.getResumePrefs();
        if (RESUME_ACTION.equals(resumePrefs.getReopenAction(uiHelper.getAppContext()))) {
            // Can handle it. Lets try.
            ArrayList<Long> albumPath = resumePrefs.getAlbumPath(uiHelper.getAppContext());
            return albumPath.size() > 1;
        }
        return false;
    }

    private void addArguments(CategoryItem album) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_ALBUM, album);
        setArguments(args);
    }

    protected void loadModelFromArguments(Bundle args) {
        Log.e(TAG, "Loading model from args");
        if (args != null) {
            albumDetails = args.getParcelable(ARG_ALBUM);
        }
        if (albumDetails == null) {
            throw new IllegalStateException("album details are null for some reason");
        }
        albumDetails.forcePermissionsReload();
        updatePiwigoAlbumModelAndOurCopyOfIt(albumDetails);

        galleryModel.setContainerDetails(albumDetails);
        albumIsDirty = true;
        Log.e(TAG, "Loading model from args: finished");
    }

    protected void onReopenModelRetrieved(CategoryItem rootAlbum, CategoryItem album) {
        try {
            isReopening = false;
            albumIsDirty = true;
            albumDetails = album;
            addArguments(albumDetails); // ensure we don't reopen next time - just handle as usual!

            // add the album path to the ViewModelProvider
            CategoryItem thisAlbum = album;
            Long albumParentId;
            do {
                albumParentId = thisAlbum.getParentId();
                if (albumParentId != null) {
                    CategoryItem catItem = rootAlbum.findChild(albumParentId);
                    catItem.removeChildAlbum(thisAlbum);
                    // cache the album in the view model provider
                    new ViewModelProvider(requireActivity()).get("" + catItem.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(catItem);
                    thisAlbum = catItem;
                } else {
                    Logging.log(Log.WARN, TAG, "Attempt to get parent album for album with id " + album.getId());
                }
            } while (albumParentId != null);
            // now add the album to the ViewModelProvider and then get the current value
            galleryModel = updatePiwigoAlbumModelAndOurCopyOfIt(albumDetails);
            populateViewFromModelEtc(requireView(), null);
            populateViewFromModelEtcOnResume();
            // below needed? called on re-login so prob not
//            loadAlbumPermissionsIfNeeded();
//            displayControlsBasedOnSessionState();
//            setEditItemDetailsControlsStatus();
            updatePageTitle();
        } catch (IllegalStateException e) {
            // do nothing - if not attached - app likely closed again.
        }
    }

    protected PiwigoAlbum<CategoryItem,GalleryItem> updatePiwigoAlbumModelAndOurCopyOfIt(CategoryItem newAlbum) {
        PiwigoAlbum<CategoryItem,GalleryItem> album = new ViewModelProvider(requireActivity()).get("" + newAlbum.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(newAlbum).getValue();
        if (album == null) {
            Logging.log(Log.ERROR, TAG, "Gallery model is unexpectedly null on reopening model with album " + album);
        }
        galleryModel = album;
        return album;
    }

    private void fillGroupsField(TextView allowedGroupsField, Collection<Group> selectedGroups) {
        if (selectedGroups.size() == 0) {
            allowedGroupsField.setText(getString(R.string.click_to_view_pattern, 0));
        } else {
            StringBuilder sb = new StringBuilder();
            Iterator<Group> groupIter = selectedGroups.iterator();
            while (groupIter.hasNext()) {
                sb.append(groupIter.next().getName());
                if (groupIter.hasNext()) {
                    sb.append(", ");
                }
            }
            allowedGroupsField.setText(sb.toString());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UIHelper.recycleImageViewContent(actionIndicatorImg);
        galleryModel = null;
        currentUsers = null;
        currentGroups = null;
        if (galleryListView != null) {
            galleryListView.setAdapter(null);
        }
    }

    private void fillUsernamesField(TextView allowedUsernamesField, Collection<Username> selectedUsernames) {
        if (selectedUsernames.size() == 0) {
            allowedUsernamesField.setText(getString(R.string.click_to_view_pattern, 0));
        } else {
            StringBuilder sb = new StringBuilder();
            Iterator<Username> usernameIter = selectedUsernames.iterator();
            while (usernameIter.hasNext()) {
                sb.append(usernameIter.next().getUsername());
                if (usernameIter.hasNext()) {
                    sb.append(", ");
                }
            }
            allowedUsernamesField.setText(sb.toString());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPrefs != null) {
            viewPrefs.storeToBundle(outState);
        }
        outState.putBoolean(STATE_EDITING_ITEM_DETAILS, editingItemDetails);
        outState.putBoolean(STATE_INFORMATION_SHOWING, informationShowing);
        outState.putLongArray(STATE_CURRENT_GROUPS, currentGroups);
        outState.putLongArray(STATE_CURRENT_USERS, currentUsers);
        outState.putBoolean(STATE_IS_ALBUM_DATA_DIRTY, albumIsDirty);
        BundleUtils.writeMap(outState, STATE_GALLERY_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putStringArrayList(STATE_GALLERY_LOADS_TO_RETRY, itemsToLoad);
        outState.putBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED, movedResourceParentUpdateRequired);
        outState.putInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS, updateAlbumDetailsProgress);
        outState.putBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT, usernameSelectionWantedNext);
        outState.putParcelable(STATE_DELETE_ACTION_DATA, bulkResourceActionData);
        outState.putLong(STATE_USER_GUID, userGuid);
        outState.putInt(STATE_ALBUMS_PER_ROW, albumsPerRow);
        if (viewAdapter != null) {
            BundleUtils.putLongHashSet(outState, STATE_SELECTED_ITEMS, viewAdapter.getSelectedItemIds());
        }

        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("ViewAlbumFragment", outState);
        }
    }

    /**
     * is a reload occuring or about to right now
     *
     * @return
     */
    protected boolean isAlbumDataLoading() {
        return loadingMessageIds.size() > 0;
    }

    protected AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, requireContext());

        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, requireContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, requireContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if (viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        String preferredThumbnailSize = AlbumViewPreferences.getPreferredResourceThumbnailSize(prefs, requireContext());

        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());

        viewPrefs.withPreferredThumbnailSize(preferredThumbnailSize);
        viewPrefs.withPreferredAlbumThumbnailSize(preferredAlbumThumbnailSize);
        viewPrefs.withShowingResourceNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
        viewPrefs.withAlbumWidthInches(DisplayUtils.getScreenWidthInches(requireActivity()) / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
        return viewPrefs;
    }

    public AlbumItemRecyclerViewAdapterPreferences getViewPrefs() {
        return viewPrefs;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_album_view, container, false);
        Logging.log(Log.DEBUG, getTag(), "view from album fragment - " + v);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            loadModelFromArguments(getArguments());
        } else {
            // restore previous viewed album.
            ConnectionPreferences.ResumeActionPreferences resumePrefs = getUiHelper().getResumePrefs();
            if (getResumeAction().equals(resumePrefs.getReopenAction(requireContext()))) {
                ArrayList<Long> albumPath = resumePrefs.getAlbumPath(requireContext());
                isReopening = true;
                if(albumPath != null) {
                    String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
                    AlbumsGetFirstAvailableAlbumResponseHandler handler = new AlbumsGetFirstAvailableAlbumResponseHandler(albumPath, preferredAlbumThumbnailSize);
                    getUiHelper().addActionOnResponse(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, handler), new LoadAlbumTreeAction());
                }
            } else {
                Logging.log(Log.WARN, TAG, "Unable to resume album fragment - no resume details stored. Changing to gallery root");
                addArguments(StaticCategoryItem.ROOT_ALBUM.toInstance());
                loadModelFromArguments(getArguments());
            }
        }

        cacheViewComponentReferences(view);

        String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, requireContext());
        if (!sortOrder.equals(currentResourceSortOrder)) {
            // we need to get all the data again (in future, maybe sort locally.
            albumIsDirty = true;
            currentResourceSortOrder = sortOrder;
        }

        if (!isReopening) {
            populateViewFromModelEtc(view, savedInstanceState);
        } else {
            Basket basket = getBasket();
            initialiseBasketView(view);
            setupBottomSheet(bottomSheet);
            setupBulkActionsControls(basket);
            updateBasketDisplay(basket);
            retryActionButton.hide();
        }
    }

    protected String getResumeAction() {
        return AbstractViewAlbumFragment.RESUME_ACTION;
    }

    protected void populateViewFromModelEtc(@NonNull View view, @Nullable Bundle savedInstanceState) {

        if (!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
            // force a reload of the gallery if the session has been destroyed.
            albumIsDirty = true;
            // reset albums per row to get it recalculated on next use
            albumsPerRow = 0;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences(savedInstanceState);
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            currentUsers = savedInstanceState.getLongArray(STATE_CURRENT_USERS);
            currentGroups = savedInstanceState.getLongArray(STATE_CURRENT_GROUPS);
            // if galleryIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            albumIsDirty = albumIsDirty || PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile()) != userGuid;
            albumIsDirty = albumIsDirty || savedInstanceState.getBoolean(STATE_IS_ALBUM_DATA_DIRTY);

            loadingMessageIds.clear();
            BundleUtils.readMap(savedInstanceState, STATE_GALLERY_ACTIVE_LOAD_THREADS, loadingMessageIds, null);
            Set<Long> messageIdsExpired = PiwigoResponseBufferingHandler.getDefault().getUnknownMessageIds(loadingMessageIds.keySet());
            if (messageIdsExpired.size() > 0) {
                for (Long messageId : messageIdsExpired) {
                    loadingMessageIds.remove(messageId);
                }
            }
            CollectionUtils.addToCollectionNullSafe(itemsToLoad, savedInstanceState.getStringArrayList(STATE_GALLERY_LOADS_TO_RETRY));
            movedResourceParentUpdateRequired = savedInstanceState.getBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED);
            updateAlbumDetailsProgress = savedInstanceState.getInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS);
            usernameSelectionWantedNext = savedInstanceState.getBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT);
            if (bulkResourceActionData != null && bulkResourceActionData.isEmpty()) {
                bulkResourceActionData = null;
            } else {
                bulkResourceActionData = savedInstanceState.getParcelable(STATE_DELETE_ACTION_DATA);
            }
            albumsPerRow = savedInstanceState.getInt(STATE_ALBUMS_PER_ROW);
        } else {
            // fresh view of the root of the gallery - reset the admin list
            if (galleryModel.getContainerDetails().isRoot()) {
                adminOnlyServerCategoriesTree = null;
            }
        }

        updateViewPrefs();

        // notify user once and only once per app session
        getUiHelper().doOnce("currentPreferredResourceThumbnailSize", viewPrefs.getPreferredThumbnailSize(), () -> getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_showing_image_thumbnails_of_size, viewPrefs.getPreferredThumbnailSize())));
        getUiHelper().doOnce("currentPreferredAlbumThumbnailSize", viewPrefs.getPreferredAlbumThumbnailSize(), () -> getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_showing_album_thumbnails_of_size, viewPrefs.getPreferredAlbumThumbnailSize())));
        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());


        PiwigoAlbumUpdatedEvent albumUpdatedEvent = EventBus.getDefault().removeStickyEvent(PiwigoAlbumUpdatedEvent.class);
        if (albumUpdatedEvent != null && albumUpdatedEvent.getUpdatedAlbum() instanceof PiwigoAlbum) {
            // retrieved this from the slideshow.
            PiwigoAlbum eventModel = (PiwigoAlbum) albumUpdatedEvent.getUpdatedAlbum();
            if (eventModel.getId() == galleryModel.getId()) {
                galleryModel = eventModel;
            }
        }

        boolean sortOrderChanged;
        if (galleryModel != null) {
            sortOrderChanged = updateAlbumSortOrder(galleryModel);
        } else {
            Logging.log(Log.WARN, getTag(), "Attempt to set album sort order but album model is still null");
        }

        retryActionButton.hide();
        retryActionButton.setOnClickListener(v -> onReloadAlbum());

        refreshEmptyAlbumText(R.string.gallery_empty_text);


        initialiseBasketView(view);

        if (galleryModel != null) {
            EventBus.getDefault().post(new AlbumSelectedEvent(galleryModel.getContainerDetails()));
        }

        AdView adView = view.findViewById(R.id.gallery_adView);
        if (AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.setVisibility(VISIBLE);
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(GONE);
        }

        setGalleryHeadings();

        showInformationButton.setVisibility(VISIBLE);


        setupBottomSheet(bottomSheet);

//        viewInOrientation = getResources().getConfiguration().orientation;


        // need to wait for the gallery model to be initialised.

        int imagesDisplayedPerRow = AlbumViewPreferences.getImagesToDisplayPerRow(getActivity(), prefs);
        int albumsDisplayedPerRow = AlbumViewPreferences.getAlbumsToDisplayPerRow(getActivity(), prefs);
        int totalSpans = imagesDisplayedPerRow;
        if (totalSpans % albumsDisplayedPerRow > 0) {
            totalSpans *= albumsDisplayedPerRow;
        }
        int colsSpannedByAlbum = totalSpans / albumsDisplayedPerRow;
        int colsSpannedByImage = totalSpans / imagesDisplayedPerRow;
        albumsPerRow = albumsDisplayedPerRow;

        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), totalSpans);
        cellSpanLookup = new SpanSizeLookup(galleryModel, totalSpans, colsSpannedByAlbum, colsSpannedByImage);
        gridLayoutMan.setSpanSizeLookup(cellSpanLookup);
        galleryListView.setLayoutManager(gridLayoutMan);

        replaceListViewAdapter(galleryModel, savedInstanceState);
        // basket depends on then adapter being available
        Basket basket = getBasket();
        setupBulkActionsControls(basket);


        galleryListViewScrollListener = new AlbumScrollListener(gridLayoutMan);
        galleryListViewScrollListener.configure(galleryModel.getPagesLoadedIdxToSizeMap(), galleryModel.getItemCount());
        galleryListView.addOnScrollListener(galleryListViewScrollListener);

        //display bottom sheet if needed
        updateInformationShowingStatus();

        if (isSessionDetailsChanged()) {

            if (!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())) {
                //trigger total screen refresh. Any errors will result in screen being closed.
                forceReloadAlbumContent();
            } else {
                if (galleryModel.getContainerDetails().isRoot()) {
                    // root of gallery can always be refreshed successfully.
                    albumIsDirty = true;
                } else {
                    // immediately leave this screen.
                    Logging.log(Log.INFO, TAG, "Unable to show album page - removing from activity");
                    getParentFragmentManager().popBackStack();
                }
            }
        }
    }

    protected void replaceListViewAdapter(@NonNull PiwigoAlbum<CategoryItem,GalleryItem> newModel, @Nullable Bundle savedInstanceState) {
        cellSpanLookup.replaceGalleryModel(newModel);
        viewAdapterListener = new AlbumViewAdapterListener(this);
        viewAdapter = new AlbumItemRecyclerViewAdapter(requireContext(), PiwigoAlbumModel.class, newModel, viewAdapterListener, viewPrefs);
        if (savedInstanceState != null) {
            viewAdapter.setInitiallySelectedItems(new HashSet<>());
            viewAdapter.setSelectedItems(BundleUtils.getLongHashSet(savedInstanceState, STATE_SELECTED_ITEMS));
        }
        galleryListView.setAdapter(viewAdapter);
    }

    protected void refreshEmptyAlbumText(String emptyAlbumText) {
        emptyGalleryLabel.setText(emptyAlbumText);
        if (!albumIsDirty) {
            emptyGalleryLabel.setVisibility(galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
        }
    }

    protected void refreshEmptyAlbumText(@StringRes int emptyAlbumTextRes) {
        refreshEmptyAlbumText(getString(emptyAlbumTextRes));
    }

    protected boolean updateAlbumSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> galleryModel) {
        boolean sortOrderChanged = false;
        int newSortOrder = AlbumViewPreferences.getAlbumChildAlbumsSortOrder(prefs, requireContext());
        try {
            sortOrderChanged = galleryModel.setAlbumSortOrder(newSortOrder);
        } catch(IllegalStateException e) {
            galleryModel.clear();
            sortOrderChanged = galleryModel.setAlbumSortOrder(newSortOrder);
        }
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        sortOrderChanged &= galleryModel.setRetrieveItemsInReverseOrder(invertResourceSortOrder);
        boolean invertChildAlbumSortOrder = AlbumViewPreferences.getAlbumChildAlbumSortOrderInverted(prefs, requireContext());
        sortOrderChanged &= galleryModel.setRetrieveChildAlbumsInReverseOrder(invertChildAlbumSortOrder);
        return sortOrderChanged;
    }

    protected void cacheViewComponentReferences(@NonNull View view) {

        bottomSheet = view.findViewById(R.id.slidingDetailBottomSheet);

        // store references and initialise anything vital to the page (and used when loading data for example)
        retryActionButton = view.findViewById(R.id.gallery_retryAction_actionButton);

        emptyGalleryLabel = view.findViewById(R.id.album_empty_content);

        bulkActionsContainer = view.findViewById(R.id.gallery_actions_bulk_container);

        albumHeaderBar = view.findViewById(R.id.album_header_bar);
        galleryNameHeader = view.findViewById(R.id.gallery_details_name_header);
        galleryNameHeader.setOnClickListener(v -> {
            if (galleryDescriptionHeader.getText().length() > 0) {
                if (galleryDescriptionHeader.getVisibility() == GONE) {
                    galleryDescriptionHeader.setVisibility(View.VISIBLE);
                } else {
                    galleryDescriptionHeader.setVisibility(GONE);
                }
            }
        });
        showInformationButton = view.findViewById(R.id.show_information_action_button);
        showInformationButton.setOnClickListener(v -> {
            if (bottomSheet.isOpen()) {
                bottomSheet.close();
            } else {
                bottomSheet.open();
            }
        });

        galleryDescriptionHeader = view.findViewById(R.id.gallery_details_description_header);

        // Set the adapter
        galleryListView = view.findViewById(R.id.gallery_list);
    }

    private HashSet<GalleryItem> getSelectedItemsNoException() {
        try {
            return getSelectedItems(GalleryItem.class);
        } catch (IllegalStateException e) {
            return new HashSet<>(0);
        }
    }

    private boolean showBulkDeleteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (getSelectedItemsNoException().size() > 0 && basket.isEmpty());
    }

    private boolean showBulkDownloadAction(Basket basket) {
        return viewAdapter.isItemSelectionAllowed() && (getSelectedItemsNoException().size() > 0 && basket.isEmpty());
    }

    private boolean showBulkPermissionsAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (getSelectedItemsNoException().size() > 0 && basket.isEmpty());
    }

    private boolean showBulkCopyAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && ((getSelectedItemsNoException().size() > 0 && basket.isEmpty()) || (galleryModel.getContainerDetails().getId() == basket.getContentParentId() && basket.getAction() == Basket.ACTION_COPY));
    }

    private boolean showBulkCutAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && ((getSelectedItemsNoException().size() > 0 && basket.isEmpty()) || (galleryModel.getContainerDetails().getId() == basket.getContentParentId() && basket.getAction() == Basket.ACTION_CUT));
    }

    private boolean showBulkPasteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !viewAdapter.isItemSelectionAllowed() && basket.getItemCount() > 0 && galleryModel.getContainerDetails().getId() != StaticCategoryItem.ROOT_ALBUM.getId() && galleryModel.getContainerDetails().getId() != basket.getContentParentId();
    }

    private boolean showBulkActionsContainer(Basket basket) {
        return viewAdapter != null && (viewAdapter.isItemSelectionAllowed() || !getBasket().isEmpty());
    }

    protected void setupBulkActionsControls(Basket basket) {

        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);

        bulkActionButtonPermissions = bulkActionsContainer.findViewById(R.id.gallery_action_permissions_bulk);
        bulkActionButtonTag = bulkActionsContainer.findViewById(R.id.gallery_action_tag_bulk);
        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.gallery_action_delete_bulk);
        bulkActionButtonDownload = bulkActionsContainer.findViewById(R.id.gallery_action_download_bulk);
        bulkActionButtonCopy = bulkActionsContainer.findViewById(R.id.gallery_action_copy_bulk);
        bulkActionButtonCut = bulkActionsContainer.findViewById(R.id.gallery_action_cut_bulk);
        bulkActionButtonPaste = bulkActionsContainer.findViewById(R.id.gallery_action_paste_bulk);

        bulkActionButtonPermissions.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                onBulkActionPermissionsButtonPressed();
            }
            return true; // consume the event
        });
        bulkActionButtonDownload.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                onBulkActionDownloadButtonPressed();
            }
            return true; // consume the event
        });
        bulkActionButtonDelete.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                onBulkActionDeleteButtonPressed();
            }
            return true; // consume the event
        });
        bulkActionButtonCopy.setOnTouchListener((v, event) -> {
            boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                addItemsToBasket(Basket.ACTION_COPY);
            }
            return true; // consume the event
        });
        bulkActionButtonCut.setOnTouchListener((v, event) -> {
            boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                addItemsToBasket(Basket.ACTION_CUT);
            }
            return true; // consume the event
        });

        bulkActionButtonPaste.setOnTouchListener((v, event) -> {
            boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                final Basket basket1 = getBasket();
                int msgPatternId = -1;
                if (basket1.getAction() == Basket.ACTION_COPY) {
                    msgPatternId = R.string.alert_confirm_copy_items_here_pattern;
                } else if (basket1.getAction() == Basket.ACTION_CUT) {
                    msgPatternId = R.string.alert_confirm_move_items_here_pattern;
                }
                String message = getString(msgPatternId, basket1.getItemCount(), galleryModel.getContainerDetails().getName());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new BasketAction<>(getUiHelper()));
            }
            return true; // consume the event
        });

        if (!isReopening && showBulkPermissionsAction(basket)) {
            bulkActionButtonPermissions.show();
        } else {
            bulkActionButtonPermissions.hide();
        }


        bulkActionButtonTag.hide();

        if (!isAlbumDataLoading()) {
            // if gallery is dirty, then the album contents are being reloaded and won't yet be available. This method is recalled once it is

            if (!isReopening && showBulkDeleteAction(basket)) {
                bulkActionButtonDelete.show();
            } else {
                bulkActionButtonDelete.hide();
            }
            if (!isReopening && showBulkDownloadAction(basket)) {
                bulkActionButtonDownload.show();
            } else {
                bulkActionButtonDownload.hide();
            }
            if (!isReopening && showBulkCopyAction(basket)) {
                bulkActionButtonCopy.show();
            } else {
                bulkActionButtonCopy.hide();
            }
            if (!isReopening && showBulkCutAction(basket)) {
                bulkActionButtonCut.show();
            } else {
                bulkActionButtonCut.hide();
            }
            if (!isReopening && showBulkPasteAction(basket)) {
                bulkActionButtonPaste.show();
            } else {
                bulkActionButtonPaste.hide();
            }
        }
    }

    protected void loadAdminListOfAlbums() {
        long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetSubAlbumsAdminResponseHandler());
        loadingMessageIds.put(loadingMessageId, SERVER_CALL_ID_ADMIN_LIST_ALBUMS);
    }

    private void onDownloadAllItemsButtonClick(HashSet<Long> imageIds, HashSet<ResourceItem> selectedItems) {
        BulkResourceActionData bulkActionData = new BulkResourceActionData(imageIds, selectedItems, BulkResourceActionData.ACTION_DOWNLOAD_ALL);
        this.bulkResourceActionData = bulkActionData;
        onDownloadAllItems(bulkActionData);
    }

    private void onUpdateImagePermissionsButtonClick(HashSet<Long> imageIds, HashSet<ResourceItem> selectedItems) {
        BulkResourceActionData bulkActionData = new BulkResourceActionData(imageIds, selectedItems, BulkResourceActionData.ACTION_UPDATE_PERMISSIONS);
        this.bulkResourceActionData = bulkActionData;
        onUpdateImagePermissions(bulkActionData);
    }

    private void onUpdateImagePermissions(BulkResourceActionData bulkActionData) {
        final HashSet<ResourceItem> resourcesReadyForAction = new HashSet<>();
        if (bulkResourceActionData.isResourceInfoAvailable()) {
            for (ResourceItem item : bulkResourceActionData.getSelectedItems()) {
                if (item.getPrivacyLevel() >= 0) {
                    resourcesReadyForAction.add(item);
                }
            }
        } else {
            bulkResourceActionData.getResourcesInfoIfNeeded(this);
            return;
        }
        if (resourcesReadyForAction.size() > 0) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_bulk_image_permissions_title, getString(R.string.alert_bulk_image_permissions_message, resourcesReadyForAction.size()), R.layout.layout_bulk_image_permissions, R.string.button_cancel, View.NO_ID, R.string.button_ok, new BulkImagePermissionsListener<>(bulkResourceActionData.getSelectedItemIds(), getUiHelper()));
        }
    }

    protected <T extends GalleryItem> HashSet<T> getSelectedItems(Class<T> type) {
        try {
            return viewAdapter.getSelectedItemsOfType(type);
        } catch (IllegalStateException e) {
            if (galleryModel.isFullyLoaded()) {
                viewAdapter.clearSelectedItemIds(); // the items aren't here any more, but we have all items
                return viewAdapter.getSelectedItemsOfType(type);
            } else {
                throw e;
            }
        }
    }

    protected void updateResourcePermissions(final HashSet<Long> imageIds, final byte privacyLevel) {

        // copy this to avoid remote possibility of a concurrent modification exception.
        HashSet<Long> itemIds = new HashSet<>(imageIds);
        for (Long imageId : itemIds) {
            ResourceItem item = (ResourceItem) getGalleryModel().getItemById(imageId);
            if (item.getPrivacyLevel() != privacyLevel) {
                // update item on server
                getUiHelper().getParent().getBulkResourceActionData().trackMessageId(addActiveServiceCall(R.string.progress_resource_details_updating, new ImageSetPrivacyLevelResponseHandler<>(item, privacyLevel)));
            }
        }
    }

    private void onDownloadAllItems(final BulkResourceActionData downloadActionData) {
        if (!downloadActionData.isResourceInfoAvailable()) {
            downloadActionData.getResourcesInfoIfNeeded(this);
            return;
        }

        if (!downloadActionData.getSelectedItems().isEmpty()) {
            showDownloadResourcesDialog(downloadActionData.getSelectedItems());
        }
    }

    protected abstract void showDownloadResourcesDialog(HashSet<ResourceItem> selectedItems);


    private void onDeleteResources(final BulkResourceActionData deleteActionData) {
        final HashSet<ResourceItem> sharedResources = new HashSet<>();
        if (deleteActionData.isResourceInfoAvailable()) {
            //TODO currently, this won't work. No piwigo support
            for (ResourceItem item : deleteActionData.getSelectedItems()) {
                if (item.getLinkedAlbums().size() > 1) {
                    sharedResources.add(item);
                }
            }
        } else {
            deleteActionData.getResourcesInfoIfNeeded(this);
            return;
        }
        if (sharedResources.size() > 0) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_album_pattern, sharedResources.size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, View.NO_ID, R.string.button_unlink, R.string.button_cancel, R.string.button_delete, new DeleteSharedResourcesAction<>(getUiHelper(), sharedResources));
        } else {
            deleteResourcesFromServerForever(getUiHelper(), deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());
        }

    }

    private void onBulkActionDownloadButtonPressed() {
        HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
        if (selectedItemIds.size() > 0) {
            onDownloadAllItemsButtonClick(selectedItemIds, viewAdapter.getSelectedItemsOfType(ResourceItem.class));
        }
    }

    private void onBulkActionPermissionsButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (selectedItemIds.size() > 0) {
                onUpdateImagePermissionsButtonClick(selectedItemIds, viewAdapter.getSelectedItemsOfType(ResourceItem.class));
            }
        }
    }

    private void onBulkActionDeleteButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (bulkResourceActionData != null && selectedItemIds.equals(bulkResourceActionData.getSelectedItemIds())) {
                //continue with previous action
                onDeleteResources(bulkResourceActionData);
            } else if (selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItemsOfType(ResourceItem.class);
                BulkResourceActionData deleteActionData = new BulkResourceActionData(selectedItemIds, selectedItems, BulkResourceActionData.ACTION_DELETE);
                if (!deleteActionData.isResourceInfoAvailable()) {
                    this.bulkResourceActionData = deleteActionData;
                }
                onDeleteResources(deleteActionData);
            }
        }
    }

    private void initialiseBasketView(View v) {
        basketView = v.findViewById(R.id.basket);

        basketView.setOnTouchListener((v12, event) -> {
            // sink events without action.
            return true;
        });

        AppCompatImageView basketImage = basketView.findViewById(R.id.basket_image);

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
        clearButton.setOnTouchListener((v1, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                Basket basket = getBasket();
                basket.clear();
                updateBasketDisplay(basket);
            }
            return true;
        });
    }

    protected Basket getBasket() {
        MainActivity activity = (MainActivity) requireActivity();
        return activity.getBasket();
    }

    private void addItemsToBasket(int action) {
        Basket basket = getBasket();

        Set<Long> selectedItems = viewAdapter.getSelectedItemIds();
        for (GalleryItem item : galleryModel.getItems()) {
            if (selectedItems.contains(item.getId())) {
                if (item instanceof ResourceItem) {
                    basket.addItem(action, (ResourceItem) item, galleryModel.getContainerDetails());
                }
            }
        }
        updateBasketDisplay(basket);
    }

    protected boolean isPreventItemSelection() {
        if (isAppInReadOnlyMode() || !PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            return true;
        }
        return getBasket().getItemCount() > 0 && getBasket().getContentParentId() != galleryModel.getContainerDetails().getId();
    }

    protected void updateBasketDisplay(Basket basket) {

        if (viewAdapter != null && viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
            viewPrefs.withAllowMultiSelect(false);
            viewPrefs.setAllowItemSelection(false);
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged()); //TODO check this works (refresh the whole list, redrawing all with/without select box as appropriate)
        }

        int basketItemCount = basket.getItemCount();
        if (basketItemCount == 0) {
            basketView.setVisibility(GONE);
        } else {
            basketView.setVisibility(VISIBLE);
            AppCompatTextView basketItemCountField = basketView.findViewById(R.id.basket_item_count);
            basketItemCountField.setText(String.valueOf(basketItemCount));

            actionIndicatorImg = basketView.findViewById(R.id.basket_action_indicator);
            if (basket.getAction() == Basket.ACTION_COPY) {
                PicassoFactory.getInstance().getPicassoSingleton(requireContext()).load(R.drawable.ic_content_copy_black_24px).into(actionIndicatorImg);
            } else {
                PicassoFactory.getInstance().getPicassoSingleton(requireContext()).load(R.drawable.ic_content_cut_black_24px).into(actionIndicatorImg);
            }
        }

        displayControlsBasedOnSessionState();
        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);

        if (!isAlbumDataLoading() && viewAdapter != null) {

            if (showBulkDeleteAction(basket)) {
                bulkActionButtonDelete.show();
            } else {
                bulkActionButtonDelete.hide();
            }
            if (showBulkDownloadAction(basket)) {
                bulkActionButtonDownload.show();
            } else {
                bulkActionButtonDownload.hide();
            }
            if (showBulkCopyAction(basket)) {
                bulkActionButtonCopy.show();
            } else {
                bulkActionButtonCopy.hide();
            }
            if (showBulkCutAction(basket)) {
                bulkActionButtonCut.show();
            } else {
                bulkActionButtonCut.hide();
            }
            if (showBulkPasteAction(basket)) {
                bulkActionButtonPaste.show();
            } else {
                bulkActionButtonPaste.hide();
            }
            if (showBulkPermissionsAction(basket)) {
                bulkActionButtonPermissions.show();
            } else {
                bulkActionButtonPermissions.hide();
            }

        }

    }

    protected void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            galleryModel.acquirePageLoadLock();
            try {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);
                if (pageToActuallyLoad < 0) {
                    // the sort order is inverted so we know for a fact this page is invalid.
                    return;
                }

                if (galleryModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad)) {
                    return;
                }

                String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, requireContext());



                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetImagesResponseHandler(galleryModel.getContainerDetails(), sortOrder, pageToActuallyLoad, pageSize));
                galleryModel.recordPageBeingLoaded(loadingMessageId, pageToActuallyLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            } finally {
                galleryModel.releasePageLoadLock();
            }
        }
    }

    protected int getPageToActuallyLoad(int pageRequested, int pageSize) {
        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        if (galleryModel.setRetrieveItemsInReverseOrder(invertSortOrder)) {
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
            DisplayUtils.runOnUiThread(()->galleryListView.invalidate());
        }
        int pageToActuallyLoad = pageRequested;
        if (invertSortOrder) {
            int pagesOfPhotos = galleryModel.getContainerDetails().getPagesOfPhotos(pageSize);
            pageToActuallyLoad = pagesOfPhotos - pageRequested;
        }
        return pageToActuallyLoad;
    }

    protected void onAlbumDeleteRequest(final CategoryItem album) {
        if (album.getTotalPhotos() > 0 || album.getSubCategories() > 0) {
            String msg;
            if(album.getSubCategories() > 0) {
                msg = String.format(getContext().getString(R.string.alert_confirm_really_really_delete_album_with_child_albums_from_server_pattern), album.getName(), album.getPhotoCount(), album.getSubCategories(), album.getTotalPhotos() - album.getPhotoCount());
            } else {
                msg = String.format(getContext().getString(R.string.alert_confirm_really_really_delete_album_without_child_albums_from_server_pattern), album.getName(), album.getPhotoCount());
            }
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteAlbumAction.DeleteWithOrphansAlbumAction<>(getUiHelper(), album));
        } else {
            String msg = getString(R.string.alert_confirm_really_delete_album_from_server_pattern, album.getName());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteAlbumAction<>(getUiHelper(), album, false));
        }
    }

    protected void loadAlbumSubCategories(@NonNull CategoryItem album) {
        synchronized (loadingMessageIds) {
            ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connPrefs);
//            if(sessionDetails != null && sessionDetails.isUseCommunityPlugin() && !sessionDetails.isGuest()) {
//                connPrefs = ConnectionPreferences.getActiveProfile().asGuest();
//            }
            loadingMessageIds.put(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetSubAlbumsResponseHandler(album, viewPrefs.getPreferredAlbumThumbnailSize(), false)), SERVER_CALL_ID_SUB_CATEGORIES);
        }
    }

    protected BulkResourceActionData getBulkResourceActionData() {
        return bulkResourceActionData;
    }

    private void setupEditFields(View editFields) {
        galleryNameView = editFields.findViewById(R.id.gallery_details_name);
        galleryDescriptionView = editFields.findViewById(R.id.gallery_details_description);
        allowedGroupsFieldLabel = editFields.findViewById(R.id.gallery_details_allowed_groups_label);
        allowedGroupsField = editFields.findViewById(R.id.gallery_details_allowed_groups);
        allowedGroupsField.setOnClickListener(v -> {
            HashSet<Long> groups = new HashSet<>();
            if (currentGroups != null) {
                for (long groupId : currentGroups) {
                    groups.add(groupId);
                }
            }
            GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, editingItemDetails, groups);
            getUiHelper().setTrackingRequest(groupSelectionNeededEvent.getActionId());
            EventBus.getDefault().post(groupSelectionNeededEvent);
        });

        allowedUsersFieldLabel = editFields.findViewById(R.id.gallery_details_allowed_users_label);
        allowedUsersField = editFields.findViewById(R.id.gallery_details_allowed_users);
        allowedUsersField.setOnClickListener(v -> {
            if (currentGroups == null || currentGroups.length == 0 || userIdsInSelectedGroups != null) {
                if (userIdsInSelectedGroups == null) {
                    userIdsInSelectedGroups = new HashSet<>(0);
                }
                HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);
                UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(usernameSelectionNeededEvent);
            } else {
                ArrayList<Long> selectedGroupIds = new ArrayList<>(currentGroups.length);
                for (long groupId : currentGroups) {
                    selectedGroupIds.add(groupId);
                }
                usernameSelectionWantedNext = true;
                addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100));
            }
        });

        galleryUserCommentsPermittedField = editFields.findViewById(R.id.gallery_details_comments_allowed);

        galleryPrivacyStatusField = editFields.findViewById(R.id.gallery_details_status);
        privacyStatusFieldListener = (buttonView, isChecked) -> {
            if (!isReopening && isChecked && !galleryModel.getContainerDetails().isPrivate()) {
                // when reopening, this will be called once the gallery model has been loaded.
                loadAlbumPermissionsIfNeeded();
            }
            allowedGroupsFieldLabel.setEnabled(isChecked);
            allowedGroupsField.setEnabled(isChecked);
            allowedUsersFieldLabel.setEnabled(isChecked);
            allowedUsersField.setEnabled(isChecked);
            fillGroupsAndUserPrivacyFields();
        };
        galleryPrivacyStatusField.setOnCheckedChangeListener(privacyStatusFieldListener);
    }

    protected void loadAlbumPermissionsIfNeeded() {
        if (galleryModel != null
                && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())
                && !galleryModel.getContainerDetails().isRoot()
                && galleryModel.getContainerDetails().isFromServer()
                && !galleryModel.getContainerDetails().isPermissionsLoaded()) {
            // never want to load permissions for the root album (it's not legal to call this service with category id 0).
            addActiveServiceCall(R.string.progress_loading_album_permissions, new AlbumGetPermissionsResponseHandler(galleryModel.getContainerDetails()));
        }
    }

    @Override
    public void updatePageTitle() {
        ToolbarEvent event = new ToolbarEvent(getActivity());
        event.setTitle(buildPageHeading());
        if (event.getTitle().startsWith("... / ")) {
            SpannableString spannableTitle = new SpannableString(event.getTitle());
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if(getActivity() == null || isReopening) { // block the action if the page is reopening.
                        Logging.log(Log.WARN, TAG, "Unable to action request to navigate using title album link");
                        return; // unable to action.
                    }
                    requireActivity().onBackPressed();
                }
            };
            spannableTitle.setSpan(clickableSpan, 0, 5, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            event.setSpannableTitle(spannableTitle);
        }
        EventBus.getDefault().post(event);
    }

    @Override
    protected String buildPageHeading() {
        if (galleryModel != null) {
            CategoryItem catItem = galleryModel.getContainerDetails();
            if (catItem.isRoot()) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                if (sessionDetails != null) {
                    ServerConfig serverConfig = sessionDetails.getServerConfig();
                    if (serverConfig != null) {
                        return serverConfig.getGalleryTitle();
                    }
                }
                return getString(R.string.album_title_home);
            } else {
                String currentAlbumName = "... / " + catItem.getName();
                return currentAlbumName;
            }
        } else if (isReopening) {
            ConnectionPreferences.ResumeActionPreferences resumePrefs = getUiHelper().getResumePrefs();
            if (getResumeAction().equals(resumePrefs.getReopenAction(requireContext()))) {
                return resumePrefs.getAlbumName(requireContext());
            }
        }
        return "";
    }

    @Override
    public void onResume() {
        super.onResume();

        AdsManager.getInstance(getContext()).showPleadingMessageIfNeeded(getUiHelper());


        if (galleryListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        if (!isReopening) {
            populateViewFromModelEtcOnResume();
        }
    }

    protected void populateViewFromModelEtcOnResume() {
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        String profileId = activeProfile.getProfileId(getPrefs(), getContext());


        if (galleryModel == null) {
            if (getArguments() != null) {
                loadModelFromArguments(getArguments()); // restoring view.
            } else {
                Logging.log(Log.ERROR, TAG, "Gallery model is null, but there are no arguments available from which to load one - unable to populate view");
                return;
            }
        }

        updateAppResumeDetails();
        


        if (albumIsDirty) {
            reloadAlbumContent();
        } else if (itemsToLoad.size() > 0) {
            onReloadAlbum();
        } else {

            int spacerAlbumsNeeded = galleryModel.getChildAlbumCount() % albumsPerRow;
            if (spacerAlbumsNeeded > 0) {
                spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
            }
            galleryModel.setSpacerAlbumCount(spacerAlbumsNeeded);
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
        }

        updateBasketDisplay(getBasket());

        getUiHelper().showUserHint(TAG, 1, R.string.hint_album_view);
    }

    protected void updateAppResumeDetails() {
        List<Long> fullAlbumPath = galleryModel.getContainerDetails().getFullPath();
        ConnectionPreferences.ResumeActionPreferences resumePrefs = getUiHelper().getResumePrefs();
        resumePrefs.setReopenAction(requireContext(), getResumeAction());
        resumePrefs.setAlbumDetails(requireContext(), fullAlbumPath, buildPageHeading());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        if (!isReopening) {
            loadAlbumPermissionsIfNeeded();
            displayControlsBasedOnSessionState();
            setEditItemDetailsControlsStatus();
            updatePageTitle();
            updateBasketDisplay(getBasket());
        }
    }

    protected void forceReloadAlbumContent() {
        albumIsDirty = true;
        //we've altered the album content (now update this album view to reflect the server content)
        reloadAlbumContent();
    }

    protected void reloadAlbumContent() {

        if (albumIsDirty) {
            emptyGalleryLabel.setVisibility(GONE);
            albumIsDirty = false;
            if (loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            galleryModel.clear();
            galleryListViewScrollListener.resetState();
            cellSpanLookup.replaceGalleryModel(galleryModel);
            galleryListView.swapAdapter(viewAdapter, true);
//            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());

            loadAlbumSubCategories(galleryModel.getContainerDetails());
            loadAlbumResourcesPage(0);
            loadAlbumPermissionsIfNeeded();
            if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                boolean loadAdminList = false;
                if (adminOnlyServerCategoriesTree == null) {
                    loadAdminList = true;
                } else {
                    if (galleryModel.getContainerDetails().isRoot()) {
                        adminOnlyChildCategories = adminOnlyServerCategoriesTree.getAlbums();
                    } else {
                        CategoryItem adminCopyOfThisAlbum = null;
                        try {
                            adminCopyOfThisAlbum = adminOnlyServerCategoriesTree.getAlbum(galleryModel.getContainerDetails());
                        } catch (IllegalStateException e) {
                            Logging.recordException(e);
                            Logging.log(Log.ERROR, getTag(), String.format("current container details (%1$s) not in admin list", galleryModel.getContainerDetails()));
                        }
                        if (adminCopyOfThisAlbum != null) {
                            adminOnlyChildCategories = adminCopyOfThisAlbum.getChildAlbums();
                        } else {
                            // this admin list is outdated.
                            adminOnlyServerCategoriesTree = null;
                            loadAdminList = true;
                        }
                    }
                }
                if (loadAdminList) {
                    loadAdminListOfAlbums();
                }
            }
        }
    }

    private void onPiwigoResponseResourceMoved(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<?> response) {
        Logging.log(Log.VERBOSE, TAG, "deleting 1 album items from the UI display after moving it to different album");
        Basket basket = getBasket();
        if (basket == null) {
            Logging.log(Log.VERBOSE, TAG, "Basket is null when expecting to handle onResourceMoved event");
            return;
        }
        CategoryItem movedParent = basket.getContentParent();
        boolean itemRemoved = basket.removeItem(response.getPiwigoResource());
        if (itemRemoved) {
            PiwigoAlbum<CategoryItem,GalleryItem> movedFromPiwigoAlbum = new ViewModelProvider(requireActivity()).get("" + movedParent.getParentId(), PiwigoAlbumModel.class).getModel();
            if (movedFromPiwigoAlbum != null) {
                movedFromPiwigoAlbum.remove(response.getPiwigoResource());
            }
            movedParent.reducePhotoCount();
            if (movedParent.getRepresentativePictureId() != null && response.getPiwigoResource().getId() == movedParent.getRepresentativePictureId()) {
                if (movedParent.getPhotoCount() > 0) {
                    movedResourceParentUpdateRequired = true;
                }
            }
            if (basket.getItemCount() == 0 && movedResourceParentUpdateRequired) {
                movedResourceParentUpdateRequired = false;
                addActiveServiceCall(R.string.progress_move_resources, new AlbumThumbnailUpdatedResponseHandler(movedParent.getId(), movedParent.getParentId(), null));
            }

            updateBasketDisplay(basket);

            // Now ensure any parents are also updated when next shown
            notifyAllParentAlbumsOfContentChange();

            //we've altered the album content (now update this album view to reflect the server content)
            forceReloadAlbumContent();
        } else {
            Logging.log(Log.ERROR, getTag(), "processed onResourceMoved but basket (" + basket.getItemCount() + " items) does not contain resource");
        }
    }

    private void updateAlbumDetails() {
        switch (updateAlbumDetailsProgress) {
            case UPDATE_IN_PROGRESS:
            case UPDATE_NOT_RUNNING:
                updateAlbumDetailsProgress = UPDATE_IN_PROGRESS;
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumUpdateInfoResponseHandler(galleryModel.getContainerDetails()));
                break;
            case UPDATE_SETTING_ADDING_PERMISSIONS:
                addingAlbumPermissions();
            case UPDATE_SETTING_REMOVING_PERMISSIONS:
                removeAlbumPermissions();
                break;
            default:
                // unsupported state
                throw new RuntimeException("Unsupported state");
        }
    }

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            if (currentGroups == null) {
                // haven't yet loaded the existing permissions - do this now.
                privacyStatusFieldListener.onCheckedChanged(galleryPrivacyStatusField, galleryPrivacyStatusField.isChecked());
            }
        }
    }

    private void setupBottomSheet(final SlidingBottomSheet bottomSheet) {
        bottomSheet.setOnInteractListener(new SlidingBottomSheet.OnInteractListener() {

            @Override
            public void onOpened() {
                informationShowing = !informationShowing;
                updateInformationShowingStatus();
            }

            @Override
            public void onClosed() {
                informationShowing = !informationShowing;
                updateInformationShowingStatus();
            }
        });

        boolean visibleBottomSheet = isPermitUserToViewExtraDetailsSheet();
        bottomSheet.setVisibility(visibleBottomSheet ? View.VISIBLE : View.GONE);

        int editFieldVisibility = VISIBLE;
        if (galleryModel == null || galleryModel.getContainerDetails().isRoot()) {
            editFieldVisibility = GONE;
        }

        View editFields = bottomSheet.findViewById(R.id.gallery_details_edit_fields);
        editFields.setVisibility(editFieldVisibility);

        // always setting them up eliminates the chance they might be null.
        setupBottomSheetButtons(bottomSheet, editFieldVisibility);
        setupEditFields(editFields);
        if (galleryModel != null && !galleryModel.getContainerDetails().isRoot()) {
            fillGalleryEditFields();
        } else {
            displayControlsBasedOnSessionState();
            setEditItemDetailsControlsStatus();
        }

    }

    protected boolean isPermitUserToViewExtraDetailsSheet() {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || (galleryModel != null && galleryModel.getContainerDetails() != StaticCategoryItem.ROOT_ALBUM);
    }

    private void setupBottomSheetButtons(View bottomSheet, int editFieldsVisibility) {
        saveButton = bottomSheet.findViewById(R.id.gallery_details_save_button);
        saveButton.setVisibility(editFieldsVisibility);
        saveButton.setOnClickListener(v -> {
            galleryModel.getContainerDetails().setName(galleryNameView.getText().toString());
            galleryModel.getContainerDetails().setDescription(galleryDescriptionView.getText().toString());
            galleryModel.getContainerDetails().setUserCommentsAllowed(galleryUserCommentsPermittedField.isChecked());
            galleryModel.getContainerDetails().setPrivate(galleryPrivacyStatusField.isChecked());
            updateAlbumDetails();
        });

        discardButton = bottomSheet.findViewById(R.id.gallery_details_discard_button);
        discardButton.setVisibility(editFieldsVisibility);
        discardButton.setOnClickListener(v -> {
            editingItemDetails = false;
            currentUsers = galleryModel.getContainerDetails().getUsers();
            currentGroups = galleryModel.getContainerDetails().getGroups();
            fillGalleryEditFields();
        });


        editButton = bottomSheet.findViewById(R.id.gallery_details_edit_button);
        editButton.setVisibility(editFieldsVisibility);
        editButton.setOnClickListener(v -> {
            editingItemDetails = true;
            fillGalleryEditFields();
        });

        addNewAlbumButton = bottomSheet.findViewById(R.id.album_add_new_album_button);
        addNewAlbumButton.setVisibility(VISIBLE);
        addNewAlbumButton.setOnClickListener(v -> {

            AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(galleryModel.getContainerDetails().toStub());
            getUiHelper().setTrackingRequest(event.getActionId());
            EventBus.getDefault().post(event);
        });

        deleteAlbumButton = bottomSheet.findViewById(R.id.gallery_action_delete);
        deleteAlbumButton.setVisibility(editFieldsVisibility);
        deleteAlbumButton.setOnClickListener(v -> onAlbumDeleteRequest(galleryModel.getContainerDetails()));

        pasteButton = bottomSheet.findViewById(R.id.gallery_action_paste);
        pasteButton.setVisibility(editFieldsVisibility);
        pasteButton.setOnClickListener(v -> onMoveItem(galleryModel.getContainerDetails()));

        cutButton = bottomSheet.findViewById(R.id.gallery_action_cut);
        cutButton.setVisibility(editFieldsVisibility);
        cutButton.setOnClickListener(v -> onCopyItem(galleryModel.getContainerDetails()));
    }

    protected boolean addingAlbumPermissions() {
        updateAlbumDetailsProgress = UPDATE_SETTING_ADDING_PERMISSIONS;
        // Get all groups newly added to the list of permissions
        HashSet<Long> wantedAlbumGroups = SetUtils.asSet(currentGroups);
        final HashSet<Long> newlyAddedGroups = SetUtils.difference(wantedAlbumGroups, SetUtils.asSet(galleryModel.getContainerDetails().getGroups()));
        // Get all users newly added to the list of permissions
        HashSet<Long> wantedAlbumUsers = SetUtils.asSet(currentUsers);
        final HashSet<Long> newlyAddedUsers = SetUtils.difference(wantedAlbumUsers, SetUtils.asSet(galleryModel.getContainerDetails().getUsers()));

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        long currentLoggedInUserId = sessionDetails.getUserId();

        Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(sessionDetails.getGroupMemberships(), wantedAlbumGroups);

        if (currentLoggedInUserId >= 0) {
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == sessionDetails.getGroupMemberships().size();
            boolean noUserAccess = !wantedAlbumUsers.contains(currentLoggedInUserId);

            if (noGroupAccess && noUserAccess && !sessionDetails.isAdminUser()) {
                //You're attempting to remove your own permission to access this album. Add it back in.
                currentUsers = Arrays.copyOf(currentUsers, currentUsers.length + 1);
                currentUsers[currentUsers.length - 1] = currentLoggedInUserId;
                wantedAlbumUsers.add(currentLoggedInUserId);
                // update the ui.
                allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
                int msgId = R.string.alert_information_own_user_readded_to_permissions_list;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(msgId), R.string.button_ok, false, new AddingAlbumPermissionsAction<>(getUiHelper()));
                return true;
            }

            if (newlyAddedGroups.size() > 0 || newlyAddedUsers.size() > 0) {

                if (galleryModel.getContainerDetails().getSubCategories() > 0) {
                    final CategoryItem currentCategoryDetails = galleryModel.getContainerDetails();

                    if (newlyAddedUsers.contains(currentLoggedInUserId)) {
                        //we're having to force add this user explicitly therefore for safety we need to apply the change recursively
                        String msg = getString(R.string.alert_information_add_album_permissions_recursively_pattern, galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg, R.string.button_ok, false, new AddingChildPermissionsAction<>(getUiHelper(), newlyAddedGroups, newlyAddedUsers));
                    } else {

                        String msg = getString(R.string.alert_confirm_add_album_permissions_recursively_pattern, newlyAddedGroups.size(), newlyAddedUsers.size(), galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new AddAccessToAlbumAction<>(getUiHelper(), newlyAddedGroups, newlyAddedUsers));
                    }
                } else {
                    // no need to be recursive as this album is a leaf node.
                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, false));
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean removeAlbumPermissions() {
        updateAlbumDetailsProgress = UPDATE_SETTING_REMOVING_PERMISSIONS;
        // Get all groups newly added to the list of permissions
        final HashSet<Long> newlyRemovedGroups = SetUtils.difference(SetUtils.asSet(galleryModel.getContainerDetails().getGroups()), SetUtils.asSet(currentGroups));
        // Get all users newly added to the list of permissions
        HashSet<Long> currentAlbumUsers = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        final HashSet<Long> newlyRemovedUsers = SetUtils.difference(currentAlbumUsers, SetUtils.asSet(currentUsers));

        if (newlyRemovedGroups.size() > 0 || newlyRemovedUsers.size() > 0) {

            if (galleryModel.getContainerDetails().getSubCategories() > 0) {
                String message = getString(R.string.alert_confirm_really_remove_album_permissions_pattern, newlyRemovedGroups.size(), newlyRemovedUsers.size());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new RemoveAccessToAlbumAction<>(getUiHelper(), newlyRemovedGroups, newlyRemovedUsers));
            } else {
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers));
            }

            return true;
        } else {
            return false;
        }
    }

    protected void fillGalleryEditFields() {
        if (galleryModel.getContainerDetails().getName() != null && !galleryModel.getContainerDetails().getName().isEmpty()) {
            galleryNameView.setText(galleryModel.getContainerDetails().getName());
        } else {
            galleryNameView.setText("");
        }
        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty()) {
            galleryDescriptionView.setText(galleryModel.getContainerDetails().getDescription());
        } else {
            galleryDescriptionView.setText("");
        }
        fillGroupsAndUserPrivacyFields();

        galleryPrivacyStatusField.setChecked(galleryModel.getContainerDetails().isPrivate());
        galleryUserCommentsPermittedField.setChecked(galleryModel.getContainerDetails().isUserCommentsAllowed());


        displayControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    private void fillGroupsAndUserPrivacyFields() {
        if (allowedGroupsField.isEnabled()) {
            allowedGroupsField.setText(R.string.click_to_view);
            if (currentGroups != null) {
                allowedGroupsField.setText(getString(R.string.click_to_view_pattern, currentGroups.length));
            }
        } else {
            allowedGroupsField.setText(R.string.all);
        }
        if (allowedUsersField.isEnabled()) {
            allowedUsersField.setText(R.string.click_to_view);
            if (currentUsers != null) {
                allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
            }
        } else {
            allowedUsersField.setText(R.string.all);
        }
    }

    private void setEditItemDetailsControlsStatus() {
        if (galleryModel == null) {
            return; // if reopening page (will be set once data loaded)
        }
        boolean visibleBottomSheet = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || !galleryModel.getContainerDetails().isRoot();
        bottomSheet.setVisibility(visibleBottomSheet ? VISIBLE : GONE);

        addNewAlbumButton.setEnabled(!editingItemDetails && galleryModel.getContainerDetails().isFromServer() || galleryModel.getContainerDetails().isRoot());

        galleryNameView.setEnabled(editingItemDetails);
        galleryDescriptionView.setEnabled(editingItemDetails);
        galleryPrivacyStatusField.setEnabled(editingItemDetails);
        galleryUserCommentsPermittedField.setEnabled(editingItemDetails);
        allowedUsersField.setEnabled(true); // Always enabled (but is read only when not editing)
        allowedGroupsField.setEnabled(true); // Always enabled (but is read only when not editing)
        // this tint mirrors what would occur if the field could be disabled.
        ColorStateList tintA = null;
        ColorStateList tintB = null;
        if (!editingItemDetails) {
            tintA = ColorStateList.valueOf(DisplayUtils.getColor(allowedGroupsField.getContext(), R.attr.scrimLight));
            tintB = ColorStateList.valueOf(DisplayUtils.getColor(allowedUsersField.getContext(), R.attr.scrimLight));
        } else {
            tintA = ColorStateList.valueOf(DisplayUtils.getColor(allowedGroupsField.getContext(), R.attr.scrimHeavy));
            tintB = ColorStateList.valueOf(DisplayUtils.getColor(allowedUsersField.getContext(), R.attr.scrimHeavy));
        }
        ViewCompat.setBackgroundTintList(allowedGroupsField, tintA);
        ViewCompat.setBackgroundTintList(allowedUsersField, tintB);
        ViewCompat.setBackgroundTintMode(allowedGroupsField, PorterDuff.Mode.SRC_IN);
        ViewCompat.setBackgroundTintMode(allowedUsersField, PorterDuff.Mode.SRC_IN);

        editButton.setVisibility(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode() && !editingItemDetails && galleryModel.getContainerDetails().isFromServer() ? VISIBLE : GONE);

        saveButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        saveButton.setEnabled(editingItemDetails);
        discardButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        discardButton.setEnabled(editingItemDetails);
    }

    private void displayControlsBasedOnSessionState() {
        if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode()) {
            boolean showPersistenceControls = galleryModel != null && galleryModel.getContainerDetails().isFromServer();
            saveButton.setVisibility(editingItemDetails && showPersistenceControls ? VISIBLE : GONE);
            discardButton.setVisibility(editingItemDetails && showPersistenceControls ? VISIBLE : GONE);
            editButton.setVisibility((!editingItemDetails) && showPersistenceControls ? VISIBLE : GONE);
            deleteAlbumButton.setVisibility(showPersistenceControls ? VISIBLE : GONE);
            addNewAlbumButton.setVisibility(VISIBLE);
            //TODO make visible once functionality written.
            cutButton.setVisibility(GONE);
            pasteButton.setVisibility(GONE);
        } else {
            addNewAlbumButton.setVisibility(GONE);
            saveButton.setVisibility(GONE);
            discardButton.setVisibility(GONE);
            editButton.setVisibility(GONE);
            deleteAlbumButton.setVisibility(GONE);
            cutButton.setVisibility(GONE);
            pasteButton.setVisibility(GONE);
        }

        int visibility = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) ? VISIBLE : GONE;

        allowedGroupsFieldLabel.setVisibility(visibility);
        allowedGroupsField.setVisibility(visibility);
        allowedUsersFieldLabel.setVisibility(visibility);
        allowedUsersField.setVisibility(visibility);
    }

    private void setGalleryHeadings() {
        if (galleryModel == null) {
            return;
        }

        CategoryItem currentAlbum = galleryModel.getContainerDetails();

        if (currentAlbum != null && currentAlbum.getName() != null && !currentAlbum.getName().isEmpty() && !currentAlbum.isRoot()) {
            galleryNameHeader.setText(galleryModel.getContainerDetails().getName());
            galleryNameHeader.setVisibility(View.VISIBLE);
            albumHeaderBar.setVisibility(VISIBLE);
        } else {
            galleryNameHeader.setVisibility(INVISIBLE);
        }


        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty() && StaticCategoryItem.ROOT_ALBUM != galleryModel.getContainerDetails()) {
            // support for the extended description plugin.
            String description = PiwigoUtils.getResourceDescriptionInsideAlbum(galleryModel.getContainerDetails().getDescription());
            Spanned spannedText = PiwigoUtils.getSpannedHtmlText(description);
            galleryDescriptionHeader.setText(spannedText);
        }

        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty()) {
            albumHeaderBar.setVisibility(VISIBLE);
            galleryDescriptionHeader.setVisibility(VISIBLE);
        } else {
            galleryDescriptionHeader.setVisibility(GONE);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    private void onMoveItem(CategoryItem model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onCopyItem(CategoryItem model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onPiwigoResponseUpdateResourceInfo(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<?> response) {
        if (viewAdapterListener.handleAlbumThumbnailInfoLoaded(response.getMessageId(), response.getPiwigoResource())) {
            int itemIdx = viewAdapter.getItemPosition(response.getPiwigoResource());
            viewAdapter.notifyItemChanged(itemIdx);
            /* FIXME check the code above does the trick!
            VH vh = parentAdapter.findViewHolderForItemId(item.getId());
            if (vh != null) {
                // item currently displaying.
                viewAdapter.redrawItem(vh, item);
            }*/
        } else {
            if (bulkResourceActionData != null && bulkResourceActionData.isTrackingMessageId(response.getMessageId())) {
                switch (bulkResourceActionData.getAction()) {
                    case BulkResourceActionData.ACTION_DELETE:
                        //currently mid delete of resources.
                        onPiwigoResponseResourceDeleteProcessed(response);
                        break;
                    case BulkResourceActionData.ACTION_UPDATE_PERMISSIONS:
                        onPiwigoResponseResourceUpdateProcessed(response);
                        break;
                    default:
                        Logging.log(Log.WARN, TAG, "unsupported bulk resource action type");
                }
            } else {
                onPiwigoResponseResourceMoved(response);
            }
        }
    }

    private void onPiwigoResponseResourceDeleteProcessed(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<?> response) {
        onPiwigoResponseResourceUpdateProcessed(response);
    }

    protected synchronized void onPiwigoResponseAdminListOfAlbumsLoaded(AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
        adminOnlyServerCategoriesTree = response.getAdminList();
        try {
            adminOnlyChildCategories = adminOnlyServerCategoriesTree.getDirectChildrenOfAlbum(galleryModel.getContainerDetails().getParentageChain(), galleryModel.getContainerDetails().getId());
        } catch (IllegalStateException e) {
            Logging.recordException(e);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_album_no_longer_on_server), new AlbumNoLongerExistsAction<>(getUiHelper()));
            return;
        }

        if (adminOnlyChildCategories.size() > 1) {
            if (!galleryModel.containsItem(StaticCategoryItem.ALBUM_HEADING)) {
                galleryModel.addItem(StaticCategoryItem.ALBUM_HEADING);
            }
        }
        updateAlbumWithAdminChildCategories(galleryModel, adminOnlyChildCategories);
    }

    protected void updateAlbumWithAdminChildCategories(PiwigoAlbum<CategoryItem, GalleryItem> galleryModel, List<CategoryItem> adminOnlyChildCategories) {
        boolean changed = galleryModel.addMissingAlbums(adminOnlyChildCategories);
        if (changed) {
            galleryModel.updateSpacerAlbumCount(albumsPerRow);
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
        }
    }

    private void onPiwigoResponseResourceUpdateProcessed(PiwigoResponseBufferingHandler.PiwigoResourceItemResponse<?> response) {
        if (bulkResourceActionData.removeProcessedResource(response.getPiwigoResource())) {
            bulkResourceActionData = null;
            viewAdapter.clearSelectedItemIds();
            viewAdapter.toggleItemSelection();
        }
    }

    protected void onPiwigoResponseResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?> response) {
        if (bulkResourceActionData != null && bulkResourceActionData.isTrackingMessageId(response.getMessageId())) {
            this.bulkResourceActionData.updateLinkedAlbums(response.getResource());
            if (this.bulkResourceActionData.isResourceInfoAvailable()) {
                switch (bulkResourceActionData.getAction()) {
                    case BulkResourceActionData.ACTION_DELETE:
                        onDeleteResources(bulkResourceActionData);
                        break;
                    case BulkResourceActionData.ACTION_UPDATE_PERMISSIONS:
                        onUpdateImagePermissions(bulkResourceActionData);
                        break;
                    case BulkResourceActionData.ACTION_DOWNLOAD_ALL:
                        onDownloadAllItems(bulkResourceActionData);
                }

            } else {
                // this will load the next batch of resource infos...
                bulkResourceActionData.getResourcesInfoIfNeeded(this);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewAdapter  = null;
        bulkActionButtonTag  = null;
        retryActionButton  = null;
        galleryNameHeader  = null;
        galleryDescriptionHeader  = null;
        galleryNameView  = null;
        galleryDescriptionView  = null;
        saveButton  = null;
        discardButton  = null;
        editButton  = null;
        pasteButton  = null;
        cutButton  = null;
        deleteAlbumButton = null;
        galleryUserCommentsPermittedField  = null;
        galleryPrivacyStatusField  = null;
        allowedGroupsField  = null;
        allowedUsersField  = null;
        bulkActionsContainer  = null;
        bulkActionButtonPermissions  = null;
        bulkActionButtonDelete  = null;
        bulkActionButtonDownload  = null;
        bulkActionButtonCopy  = null;
        bulkActionButtonCut  = null;
        bulkActionButtonPaste  = null;
        basketView  = null;
        emptyGalleryLabel  = null;
        allowedGroupsFieldLabel  = null;
        allowedUsersFieldLabel  = null;
        addNewAlbumButton  = null;
        actionIndicatorImg  = null;
        galleryListView  = null;
        bottomSheet  = null;
        albumDetails  = null;
        albumHeaderBar  = null;
        showInformationButton  = null;
    }

    private HashSet<Long> buildPreselectedUserIds(List<Username> selectedUsernames) {
        HashSet<Long> preselectedUsernames = PiwigoUtils.toSetOfIds(selectedUsernames);
        if (selectedUsernames == null) {
            preselectedUsernames = new HashSet<>(0);
        }
        return preselectedUsernames;
    }

    protected void onPiwigoResponseUsernamesRetrievedForSelectedGroups(UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        if (response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernames = response.getUsernames();
            userIdsInSelectedGroups = buildPreselectedUserIds(usernames);

            if (usernameSelectionWantedNext) {
                usernameSelectionWantedNext = false;
                HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);
                UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(usernameSelectionNeededEvent);
            }
        }
    }

    private HashSet<Long> getSetFromArray(long[] itemIds) {
        HashSet<Long> itemIdsSet;
        itemIdsSet = new HashSet<>(itemIds != null ? itemIds.length : 0);
        if (itemIds != null) {
            int i = 0;
            for (long id : itemIds) {
                itemIdsSet.add(id);
            }
        }
        return itemIdsSet;
    }

    protected void onPiwigoResponseThumbnailUpdated(AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        if (response.getAlbumParentIdAltered() != null && response.getAlbumParentIdAltered() == galleryModel.getContainerDetails().getId()) {
            // need to refresh this gallery content.
            forceReloadAlbumContent();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final GroupSelectionCompleteEvent groupSelectionCompleteEvent) {
        if (getUiHelper().isTrackingRequest(groupSelectionCompleteEvent.getActionId())) {
            HashSet<Long> selectedGroupIds = PiwigoUtils.toSetOfIds(groupSelectionCompleteEvent.getSelectedItems());
            currentGroups = ArrayUtils.unwrapLongs(selectedGroupIds);
            userIdsInSelectedGroups = null;
            fillGroupsField(allowedGroupsField, groupSelectionCompleteEvent.getSelectedItems());

            if (selectedGroupIds.isEmpty()) {
                userIdsInSelectedGroups = new HashSet<>(0);
            } else {
                addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100));
            }
        }
    }

    protected void onPiwigoResponseResourcesDeleted(ImageDeleteResponseHandler.PiwigoDeleteImageResponse response) {
        Logging.log(Log.VERBOSE, TAG, String.format(Locale.getDefault(), "deleting %1$d album items from the UI display", response.getDeletedItems().size()));
        // clear the selection
        viewAdapter.clearSelectedItemIds();
        viewAdapter.toggleItemSelection();
        // now update this album view to reflect the server content
        if (bulkResourceActionData != null && bulkResourceActionData.removeProcessedResources(response.getDeletedItems())) {
            bulkResourceActionData = null;
        }
        // Now ensure any parents are also updated when next shown
        notifyAllParentAlbumsOfContentChange();

        forceReloadAlbumContent();

    }

    private void onReloadAlbum() {
        retryActionButton.hide();
        emptyGalleryLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                onRerunServerCall(itemToLoad);
            }
        }
    }

    protected void onRerunServerCall(String itemToLoad) {
        switch (itemToLoad) {
            case SERVER_CALL_ID_SUB_CATEGORIES:
                loadAlbumSubCategories(galleryModel.getContainerDetails());
                break;
            case SERVER_CALL_ID_ALBUM_PERMISSIONS:
                loadAlbumPermissionsIfNeeded();
                break;
            case SERVER_CALL_ID_ALBUM_INFO_DETAIL:
                updateAlbumDetails();
                break;
            case SERVER_CALL_ID_ADMIN_LIST_ALBUMS:
                loadAdminListOfAlbums();
                break;
            default:
                try {
                    int page = Integer.parseInt(itemToLoad);
                    loadAlbumResourcesPage(page);
                } catch(NumberFormatException e) {
                    Logging.log(Log.WARN, TAG, "Unable to reload unrecognised page number : %1$s", itemToLoad);
                }
                break;
        }
    }

    protected synchronized void onPiwigoResponseListOfAlbumsLoaded(final AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {

        if (galleryModel.getContainerDetails().isRoot()) {
            galleryModel.updateMaxExpectedItemCount(response.getAlbums().size());
        }
        if (response.getAlbums().size() > 1) {
            if (!galleryModel.containsItem(StaticCategoryItem.ALBUM_HEADING)) {
                galleryModel.addItem(StaticCategoryItem.ALBUM_HEADING);
            }
        }
        long currentGalleryId = galleryModel.getContainerDetails().getId();
        boolean itemsReplaced = false;
        boolean itemsAdded = false;
        boolean hasAlbumsAlready = galleryModel.getChildAlbumCount() > 0;
        for (CategoryItem item : response.getAlbums()) {
            if (item.getId() != currentGalleryId) {
                if(hasAlbumsAlready) {
                    // the 'child' album is not the current album we're viewing
                    // first try and remove it (will remove any admin copies)
                    try { // can't do an equality search since admin items aren't equal
                        GalleryItem existing = galleryModel.getItemById(item.getId());
                        itemsReplaced = galleryModel.replace(existing, item);
                    } catch (IllegalArgumentException e) {
                        Logging.log(Log.DEBUG, TAG, "Item not found. Adapter size : %1$d", galleryModel.getChildAlbumCount());
                        // just means it isn't present - sink.
                    }
                } else {
                    galleryModel.addItem(item);
                }
                itemsAdded = true;
            } else {
                // copy the extra data across not retrieved by default.
                item.setGroups(galleryModel.getContainerDetails().getGroups());
                item.setUsers(galleryModel.getContainerDetails().getUsers());
                // now update the reference.
                galleryModel.setContainerDetails(item);
                // update the display.
                setGalleryHeadings();
            }
        }
        if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            galleryModel.addMissingAlbums(adminOnlyChildCategories);
        }
        galleryModel.updateSpacerAlbumCount(albumsPerRow);
        if(itemsReplaced) {
            // everything has changed including the admin -> default items.
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
        } else if(itemsAdded) {
            // everything has changed
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
        }
        emptyGalleryLabel.setVisibility(getUiHelper().getActiveServiceCallCount() == 0 && galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
    }

    protected void onPiwigoResponseGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            galleryModel.getContainerDetails().setPhotoCount(response.getTotalResourceCount());
            boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
            int firstPage = invertSortOrder ? galleryModel.getContainerDetails().getPagesOfPhotos(pageSize) : 0;

            if (response.getPage() == firstPage && response.getResources().size() > 0) {
                if (!galleryModel.containsItem(StaticCategoryItem.PICTURE_HEADING)) {
                    galleryModel.addItem(StaticCategoryItem.PICTURE_HEADING);
                }
            }
            ArrayList<GalleryItem> resources = response.getResources();
            galleryModel.addItemPage(response.getPage(), response.getPageSize(), resources);
            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
        }
        emptyGalleryLabel.setVisibility(getUiHelper().getActiveServiceCallCount() == 0 && galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
    }

    private void notifyAllParentAlbumsOfContentChange() {
        List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
        if (!parentageChain.isEmpty()) {
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i - 1), parentageChain.get(i)));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(parentageChain.size() - 1), galleryModel.getContainerDetails().getId()));
        }
    }

    protected void onPiwigoResponseAlbumContentAltered(final ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse<?> response) {
        getBasket().removeItem(response.getPiwigoResource());
        notifyAllParentAlbumsOfContentChange();
        forceReloadAlbumContent();
    }

    protected void onPiwigoResponseAlbumInfoAltered(final AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse response) {
        galleryModel.setContainerDetails(response.getAlbum());
        setGalleryHeadings();
        updateAlbumPermissions();
    }

    protected void onPiwigoResponseAlbumPermissionsAdded(AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.addAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.addAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

        if (!removeAlbumPermissions()) {
            onAlbumUpdateFinished();
        }
    }

    protected void onPiwigoResponseAlbumPermissionsRemoved(AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.removeAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.removeAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

        onAlbumUpdateFinished();
    }

    private void updateAlbumPermissions() {
        if (galleryModel.getContainerDetails().isPrivate()) {
            if (!addingAlbumPermissions() && !removeAlbumPermissions()) {
                onAlbumUpdateFinished();
            }
        } else {
            onAlbumUpdateFinished();
        }
    }

    protected void onAlbumUpdateFinished() {
        updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
        setGalleryHeadings();
        getUiHelper().hideProgressIndicator();
        if (editingItemDetails) {
            editingItemDetails = false;
            currentUsers = galleryModel.getContainerDetails().getUsers();
            currentGroups = galleryModel.getContainerDetails().getGroups();
            fillGalleryEditFields();
        }
    }

    protected void onPiwigoResponseAlbumStatusUpdated(AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse response) {
        updateAlbumPermissions();
    }

    protected void onPiwigoResponseAlbumPermissionsRetrieved(AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse response) {

        galleryModel.setContainerDetails(response.getAlbum());
        currentUsers = this.galleryModel.getContainerDetails().getUsers();
        currentGroups = this.galleryModel.getContainerDetails().getGroups();
        allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
        allowedGroupsField.setText(getString(R.string.click_to_view_pattern, currentGroups.length));
    }

    protected void onPiwigoResponseAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {
        boolean exitFragment = false;
        CategoryItem galleryDetails = galleryModel.getContainerDetails();
        if (galleryDetails.getId() == response.getAlbumId()) {
            // we've deleted the current album.
            AlbumDeletedEvent event = new AlbumDeletedEvent(galleryDetails);
            EventBus.getDefault().post(event);
            exitFragment = true;
        } else {
            if (adminOnlyServerCategoriesTree != null) {
                adminOnlyServerCategoriesTree.removeAlbumById(galleryDetails, response.getAlbumId());
                adminOnlyChildCategories = adminOnlyServerCategoriesTree.getDirectChildrenOfAlbum(galleryDetails);
            }
            galleryDetails.removeChildAlbum(response.getAlbumId()); // will return false if it was the admin copy (unlikely but do after to be sure).
            //we've deleted a child album (now update this album view to reflect the server content)
            forceReloadAlbumContent();
        }
        List<Long> parentageChain = galleryDetails.getParentageChain();
        if (!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryDetails.getId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
        }
        if (exitFragment) {
            Logging.log(Log.INFO, TAG, "removing from activity on album deleted piwigo response rxd");
            getParentFragmentManager().popBackStack();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        if (getUiHelper().isTrackingRequest(usernameSelectionCompleteEvent.getActionId())) {
            HashSet<Long> selectedItemIds = PiwigoUtils.toSetOfIds(usernameSelectionCompleteEvent.getSelectedItems());
            Username currentLoggedInUser = sessionDetails.getUser();
            boolean currentUserExplicitlyPresent = selectedItemIds.contains(currentLoggedInUser.getId());

            HashSet<Long> wantedAlbumGroups = SetUtils.asSet(currentGroups);
            HashSet<Long> currentUsersGroupMemberships = sessionDetails.getGroupMemberships();
            Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(currentUsersGroupMemberships, wantedAlbumGroups);
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == currentUsersGroupMemberships.size();
            if (currentLoggedInUser.getId() >= 0 && noGroupAccess && !currentUserExplicitlyPresent && !sessionDetails.isAdminUser()) {
                //You've attempted to remove your own permission to access this album. Adding it back in.
                selectedItemIds.add(currentLoggedInUser.getId());
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_information_own_user_readded_to_permissions_list));
            }
            currentUsers = ArrayUtils.unwrapLongs(selectedItemIds);
            fillUsernamesField(allowedUsersField, usernameSelectionCompleteEvent.getSelectedItems());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            albumIsDirty = true;
            if (isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    public CategoryItem getParentAlbum() {
        if (albumDetails == null) {
            Logging.log(Log.WARN, TAG, "Attempt to reopen parent but is null");
            return null;
        }
        PiwigoAlbum<CategoryItem,GalleryItem> nextPiwigoAlbum = new ViewModelProvider(requireActivity()).get("" + albumDetails.getParentId(), PiwigoAlbumModel.class).getModel();
        if (nextPiwigoAlbum == null) {
            Logging.log(Log.WARN, TAG, "Attempt to reopen parent but parent is not available. Returning null");
            return null;
        }
        return nextPiwigoAlbum.getContainerDetails();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if (isResumed()) {
            if (editingItemDetails) {
                discardButton.callOnClick();
            } else {
                if (!galleryModel.getContainerDetails().isRoot()) {
                    displayControlsBasedOnSessionState();
                }
            }
            if (viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
                viewPrefs.withAllowMultiSelect(false);
                viewPrefs.setAllowItemSelection(false);
                DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged()); //TODO check this does what it should...
            }

            Basket basket = getBasket();
            basket.clear();
            updateBasketDisplay(basket);
        } else {
            // if not showing, just flush the state and rebuild the page
            albumIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppUnlockedEvent event) {
        if (isResumed()) {
            if (!galleryModel.getContainerDetails().isRoot()) {
                displayControlsBasedOnSessionState();
                setEditItemDetailsControlsStatus();
            }
            if (viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
                viewPrefs.withAllowMultiSelect(false);
                viewPrefs.setAllowItemSelection(false);
                DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged()); //TODO check this does what it should...
            }
        } else {
            // if not showing, just flush the state and rebuild the page
            albumIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestUsingHttpToHttpsServerEvent event) {
        final ConnectionPreferences.ProfilePreferences connectionPreferences = event.getConnectionPreferences();
        String failedUriPath = event.getFailedUri().toString();
        getUiHelper().showOrQueueEnhancedDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_http_to_https), failedUriPath, R.string.button_no, R.string.button_yes, new BadHttpProtocolAction<>(getUiHelper(), connectionPreferences));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestUsesRedirectionServerEvent event) {
        final ConnectionPreferences.ProfilePreferences connectionPreferences = event.getConnectionPreferences();
        String failedUriPath = event.getFailedUri().toString();
        getUiHelper().showOrQueueEnhancedDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_follow_redirects), failedUriPath, R.string.button_no, R.string.button_yes, new BadRequestRedirectionAction<>(getUiHelper(), connectionPreferences));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (galleryModel != null && albumAlteredEvent.isRelevant(galleryModel.getContainerDetails().getId())) {
            albumIsDirty = true;
            if (isResumed()) {
                //TODO Do something more fine grained to avoid refreshing the entire album view!
                reloadAlbumContent();
            }
            if (albumAlteredEvent.isCascadeToParents()) {
                notifyAllParentAlbumsOfContentChange();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumDeletedEvent event) {
        if (event.getItem().getParentId() == galleryModel.getId()) {
            int fullGalleryIdx = galleryModel.getItemIdx(event.getItem());
            // now removed from the backing gallery too.
            galleryModel.remove(fullGalleryIdx);
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent<?> event) {
        if (event.item.getParentId() == galleryModel.getId()) {
            int fullGalleryIdx = galleryModel.getItemIdx(event.item);
            // now removed from the backing gallery too.
            galleryModel.remove(fullGalleryIdx);
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    protected PiwigoAlbum<CategoryItem, GalleryItem> getGalleryModel() {
        return galleryModel;
    }

    protected HashMap<Long, String> getLoadingMessageIds() {
        return loadingMessageIds;
    }

    protected void onServerDataLoadFailed(long messageId) {

        String failedCall = loadingMessageIds.get(messageId);
        if (failedCall == null) {
            if (editingItemDetails) {
                failedCall = SERVER_CALL_ID_ALBUM_INFO_DETAIL;
            } else {
                failedCall = SERVER_CALL_ID_ALBUM_PERMISSIONS;
            }
        }
        synchronized (itemsToLoad) {
            itemsToLoad.add(failedCall);
            switch (failedCall) {
                case SERVER_CALL_ID_ALBUM_INFO_DETAIL:
                    emptyGalleryLabel.setText(R.string.gallery_update_failed_text);
                    break;
                case SERVER_CALL_ID_ALBUM_PERMISSIONS:
                    emptyGalleryLabel.setText(R.string.gallery_permissions_load_failed_text);
                    break;
                case SERVER_CALL_ID_ADMIN_LIST_ALBUMS:
                    emptyGalleryLabel.setText(R.string.gallery_admin_albums_list_load_failed_text);
                    break;
                default:
                    // Could be 'C' or a number of current image page being loaded.
                    emptyGalleryLabel.setText(R.string.gallery_album_content_load_failed_text);
                    galleryModel.recordPageLoadFailed(messageId);
                    break;
            }
            if (itemsToLoad.size() > 0) {
                emptyGalleryLabel.setVisibility(VISIBLE);
                retryActionButton.show();
            }
        }
    }

    private static class LoadAlbumTreeAction<FUIH extends FragmentUIHelper<FUIH,F>, F extends AbstractViewAlbumFragment<F,FUIH>> extends UIHelper.Action<FUIH, F, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse> implements Parcelable {

        public static final Creator<LoadAlbumTreeAction<?,?>> CREATOR = new Creator<LoadAlbumTreeAction<?,?>>() {
            @Override
            public LoadAlbumTreeAction<?,?> createFromParcel(Parcel in) {
                return new LoadAlbumTreeAction<>(in);
            }

            @Override
            public LoadAlbumTreeAction<?,?>[] newArray(int size) {
                return new LoadAlbumTreeAction[size];
            }
        };

        private LoadAlbumTreeAction() {
        }

        protected LoadAlbumTreeAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public boolean onSuccess(FUIH uiHelper, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse response) {
            CategoryItem currentItem = response.getAlbumTreeRoot();

            // cache the retrieved category tree into the model
            if (currentItem != null) {
                FragmentActivity activity = uiHelper.getParent().requireActivity();
                for (Long albumId : response.getAlbumPath()) {
                    if (albumId.equals(StaticCategoryItem.ROOT_ALBUM.getId())) {
                        continue;
                    }
                    new ViewModelProvider(activity).get("" + currentItem.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(currentItem).getValue();
                    try {
                        currentItem = currentItem.getChild(albumId);
                    } catch (IllegalStateException e) {
                        // thrown if no child albums were set. This should never occur really since it was a success but the root could now theoretically be empty.
                    }
                    if (currentItem == null) {
                        break; // were unable to load this item.
                    }
                }
            } else {
                Logging.log(Log.ERROR, TAG, "album tree retrieved, but albumTreeRoot is null");
            }

            // now reopen the model
            uiHelper.getParent().onReopenModelRetrieved(response.getAlbumTreeRoot(), response.getDeepestAlbumOnDesiredPath());
            return true; // to close the progress indicator
        }

        @Override
        public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().onReopenModelRetrieved(StaticCategoryItem.ROOT_ALBUM.toInstance(), StaticCategoryItem.ROOT_ALBUM.toInstance());
            return true; // to close the progress indicator
        }
    }

    private static final class BulkImagePermissionsListener<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<BulkImagePermissionsListener<?,?>> CREATOR = new Creator<BulkImagePermissionsListener<?,?>>() {
            @Override
            public BulkImagePermissionsListener<?,?> createFromParcel(Parcel in) {
                return new BulkImagePermissionsListener<>(in);
            }

            @Override
            public BulkImagePermissionsListener<?,?>[] newArray(int size) {
                return new BulkImagePermissionsListener[size];
            }
        };
        private final HashSet<Long> imageIds;
        private Spinner privacyLevelSpinner;

        BulkImagePermissionsListener(HashSet<Long> imageIds, FUIH uiHelper) {
            super(uiHelper);
            this.imageIds = imageIds;
        }

        protected BulkImagePermissionsListener(Parcel in) {
            super(in);
            imageIds = ParcelUtils.readLongSet(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeLongSet(dest, imageIds);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onPopulateDialogView(ViewGroup dialogView, int layoutId) {

            privacyLevelSpinner = dialogView.findViewById(R.id.privacy_level);
            // Create an ArrayAdapter using the string array and a default spinner layout
            ArrayAdapter<CharSequence> privacyLevelOptionsAdapter = ArrayAdapter.createFromResource(dialogView.getContext(),
                    R.array.privacy_levels_groups_array, android.R.layout.simple_spinner_item);
            // Specify the layout to use when the list of choices appears
            privacyLevelOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);

            HashSet<ResourceItem> itemsToAction = getUiHelper().getParent().getSelectedItems(ResourceItem.class);
            
            byte privacyLevel = -1;
            for (ResourceItem item : itemsToAction) {
                if (privacyLevel < 0) {
                    privacyLevel = item.getPrivacyLevel();
                }
                if (privacyLevel != item.getPrivacyLevel()) {
                    // privacy levels differ
                    privacyLevel = -1;
                    break;
                }
            }
            setPrivacyLevelSpinnerOption(privacyLevelSpinner, privacyLevel);

        }

        private void setPrivacyLevelSpinnerOption(Spinner privacyLevelSpinner, int value) {

            int[] values = privacyLevelSpinner.getResources().getIntArray(R.array.privacy_levels_values_array);
            int selectionIdx = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == value) {
                    selectionIdx = i;
                    break;
                }
            }
            privacyLevelSpinner.setSelection(selectionIdx);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE.equals(positiveAnswer)) {
                byte privacyLevel = getPrivacyLevelValue();
                if (privacyLevel >= 0) {
                    getUiHelper().getParent().updateResourcePermissions(imageIds, privacyLevel);
                }
            }
        }

        private byte getPrivacyLevelValue() {
            int selectedIdx = privacyLevelSpinner.getSelectedItemPosition();
            if (selectedIdx == INVALID_POSITION) {
                return INVALID_POSITION;
            }
            return (byte) privacyLevelSpinner.getResources().getIntArray(R.array.privacy_levels_values_array)[selectedIdx];
        }
    }

    private static class AlbumNoLongerExistsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public static final Creator<AlbumNoLongerExistsAction<?,?>> CREATOR = new Creator<AlbumNoLongerExistsAction<?,?>>() {
            @Override
            public AlbumNoLongerExistsAction<?,?> createFromParcel(Parcel in) {
                return new AlbumNoLongerExistsAction<>(in);
            }

            @Override
            public AlbumNoLongerExistsAction<?,?>[] newArray(int size) {
                return new AlbumNoLongerExistsAction[size];
            }
        };

        public AlbumNoLongerExistsAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected AlbumNoLongerExistsAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            super.onDismiss(dialog);
            try {
                Logging.log(Log.INFO, TAG, "removing from activity on dialog dismiss");
                getUiHelper().getParent().getParentFragmentManager().popBackStack();
            } catch(IllegalStateException e) {
                Logging.log(Log.ERROR, TAG, "Unable to pop fragment as it is no longer visible!");
            }
        }
    }

    private static class BasketAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<BasketAction<?,?>> CREATOR = new Creator<BasketAction<?,?>>() {
            @Override
            public BasketAction<?,?> createFromParcel(Parcel in) {
                return new BasketAction<>(in);
            }

            @Override
            public BasketAction<?,?>[] newArray(int size) {
                return new BasketAction[size];
            }
        };

        BasketAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected BasketAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            F fragment = getUiHelper().getParent();
            Basket basket = fragment.getBasket();
            final int basketAction = basket.getAction();
            final HashSet<ResourceItem> basketContent = basket.getContents();
            final CategoryItem containerDetails = fragment.getGalleryModel().getContainerDetails();

            if (Boolean.TRUE == positiveAnswer) {
                if (basketAction == Basket.ACTION_COPY) {
                    HashSet<ResourceItem> itemsToCopy = basketContent;
                    CategoryItem copyToAlbum = containerDetails;
                    for (ResourceItem itemToCopy : itemsToCopy) {
                        getUiHelper().addActiveServiceCall(R.string.progress_copy_resources, new ImageCopyToAlbumResponseHandler<>(itemToCopy, copyToAlbum));
                    }
                } else if (basketAction == Basket.ACTION_CUT) {
                    HashSet<ResourceItem> itemsToMove = basketContent;
                    CategoryItem moveToAlbum = containerDetails;
                    for (ResourceItem itemToMove : itemsToMove) {
                        getUiHelper().addActiveServiceCall(R.string.progress_move_resources, new ImageChangeParentAlbumHandler<>(itemToMove, moveToAlbum));
                    }
                }
            }
        }
    }

    private static class DeleteAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<DeleteAlbumAction<?,?>> CREATOR = new Creator<DeleteAlbumAction<?,?>>() {
            @Override
            public DeleteAlbumAction<?,?> createFromParcel(Parcel in) {
                return new DeleteAlbumAction<>(in);
            }

            @Override
            public DeleteAlbumAction<?,?>[] newArray(int size) {
                return new DeleteAlbumAction[size];
            }
        };
        private final CategoryItem album;
        private final boolean deleteOrphans;

        public DeleteAlbumAction(FUIH uiHelper, CategoryItem album, boolean deleteOrphans) {
            super(uiHelper);
            this.album = album;
            this.deleteOrphans = deleteOrphans;
        }

        protected DeleteAlbumAction(Parcel in) {
            super(in);
            album = in.readParcelable(CategoryItem.class.getClassLoader());
            deleteOrphans = ParcelUtils.readBool(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(album, flags);
            ParcelUtils.writeBool(dest, deleteOrphans);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId(), deleteOrphans));
            }
        }

        private static class DeleteWithOrphansAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {
            public static final Creator<DeleteWithOrphansAlbumAction<?,?>> CREATOR = new Creator<DeleteWithOrphansAlbumAction<?,?>>() {
                @Override
                public DeleteWithOrphansAlbumAction<?,?> createFromParcel(Parcel in) {
                    return new DeleteWithOrphansAlbumAction<>(in);
                }

                @Override
                public DeleteWithOrphansAlbumAction<?,?>[] newArray(int size) {
                    return new DeleteWithOrphansAlbumAction[size];
                }
            };
            private final CategoryItem album;

            public DeleteWithOrphansAlbumAction(FUIH uiHelper, CategoryItem album) {
                super(uiHelper);
                this.album = album;
            }

            protected DeleteWithOrphansAlbumAction(Parcel in) {
                super(in);
                album = in.readParcelable(CategoryItem.class.getClassLoader());
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                dest.writeParcelable(album, flags);
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                String msg = getContext().getString(R.string.alert_confirm_really_delete_album_from_server_pattern, album.getName());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteAlbumAction<>(getUiHelper(), album, positiveAnswer));
            }
        }
    }

    private static class DeleteSharedResourcesAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public static final Creator<DeleteSharedResourcesAction<?,?>> CREATOR = new Creator<DeleteSharedResourcesAction<?,?>>() {
            @Override
            public DeleteSharedResourcesAction<?,?> createFromParcel(Parcel in) {
                return new DeleteSharedResourcesAction<>(in);
            }

            @Override
            public DeleteSharedResourcesAction<?,?>[] newArray(int size) {
                return new DeleteSharedResourcesAction[size];
            }
        };
        private final HashSet<ResourceItem> sharedResources;

        public DeleteSharedResourcesAction(FUIH uiHelper, HashSet<ResourceItem> sharedResources) {
            super(uiHelper);
            this.sharedResources = sharedResources;
        }

        protected DeleteSharedResourcesAction(Parcel in) {
            super(in);
            sharedResources = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeSet(dest, sharedResources);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            F fragment = getUiHelper().getParent();
            BulkResourceActionData deleteActionData = fragment.getBulkResourceActionData();

            if (Boolean.TRUE == positiveAnswer) {
                deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<>(deleteActionData.getSelectedItemIds(), deleteActionData.selectedItems)));
            } else if (Boolean.FALSE == positiveAnswer) {
                final long currentAlbumId = fragment.getGalleryModel().getContainerDetails().getId();
                HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItemIds());
                HashSet<ResourceItem> itemsForPermananentDelete = new HashSet<>(deleteActionData.getSelectedItems());
                for (ResourceItem item : sharedResources) {
                    itemIdsForPermanentDelete.remove(item.getId());
                    itemsForPermananentDelete.remove(item);
                    item.getLinkedAlbums().remove(currentAlbumId);
                    if(item.getLinkedAlbums().isEmpty()) {
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.alert_error_item_must_belong_to_at_least_one_album));
                    } else {
                        deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_unlink_resources, new ImageUpdateInfoResponseHandler<>(item, true)));
                    }
                }
                //now we need to delete the rest.
                deleteResourcesFromServerForever(getUiHelper(), itemIdsForPermanentDelete, itemsForPermananentDelete);
            }
        }
    }

    private static class DeleteResourceForeverAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<DeleteResourceForeverAction<?,?,?>> CREATOR = new Creator<DeleteResourceForeverAction<?,?,?>>() {
            @Override
            public DeleteResourceForeverAction<?,?,?> createFromParcel(Parcel in) {
                return new DeleteResourceForeverAction<>(in);
            }

            @Override
            public DeleteResourceForeverAction<?,?,?>[] newArray(int size) {
                return new DeleteResourceForeverAction[size];
            }
        };
        private final HashSet<Long> selectedItemIds;
        private final HashSet<T> selectedItems;

        public DeleteResourceForeverAction(FUIH uiHelper, HashSet<Long> selectedItemIds, HashSet<T> selectedItems) {
            super(uiHelper);
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
        }

        protected DeleteResourceForeverAction(Parcel in) {
            super(in);
            selectedItemIds = ParcelUtils.readLongSet(in);
            selectedItems = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeSet(dest, selectedItems);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                BulkResourceActionData currentBulkResourceActionData = getUiHelper().getParent().getBulkResourceActionData();
                if (currentBulkResourceActionData != null) {
                    currentBulkResourceActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<>(selectedItemIds, selectedItems)));
                }
            }
        }
    }

    private static class AddAccessToAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<AddAccessToAlbumAction<?,?>> CREATOR = new Creator<AddAccessToAlbumAction<?,?>>() {
            @Override
            public AddAccessToAlbumAction<?,?> createFromParcel(Parcel in) {
                return new AddAccessToAlbumAction<>(in);
            }

            @Override
            public AddAccessToAlbumAction<?,?>[] newArray(int size) {
                return new AddAccessToAlbumAction[size];
            }
        };
        private final HashSet<Long> newlyAddedGroups;
        private final HashSet<Long> newlyAddedUsers;

        public AddAccessToAlbumAction(FUIH uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
            super(uiHelper);
            this.newlyAddedGroups = newlyAddedGroups;
            this.newlyAddedUsers = newlyAddedUsers;
        }

        protected AddAccessToAlbumAction(Parcel in) {
            super(in);
            newlyAddedGroups = ParcelUtils.readLongSet(in);
            newlyAddedUsers = ParcelUtils.readLongSet(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeLongSet(dest, newlyAddedGroups);
            ParcelUtils.writeLongSet(dest, newlyAddedUsers);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            F fragment = getUiHelper().getParent();
            final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
            } else {
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, false));
            }
        }
    }

    private static class AddingChildPermissionsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {
        private HashSet<Long> newlyAddedGroups;
        private HashSet<Long> newlyAddedUsers;

        public AddingChildPermissionsAction(FUIH uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
            super(uiHelper);
            this.newlyAddedGroups = newlyAddedGroups;
            this.newlyAddedUsers = newlyAddedUsers;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F fragment = getUiHelper().getParent();
                final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
            }
        }
    }

    private static class RemoveAccessToAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {
        public static final Creator<RemoveAccessToAlbumAction<?,?>> CREATOR = new Creator<RemoveAccessToAlbumAction<?,?>>() {
            @Override
            public RemoveAccessToAlbumAction<?,?> createFromParcel(Parcel in) {
                return new RemoveAccessToAlbumAction<>(in);
            }

            @Override
            public RemoveAccessToAlbumAction<?,?>[] newArray(int size) {
                return new RemoveAccessToAlbumAction[size];
            }
        };
        private final HashSet<Long> newlyRemovedGroups;
        private final HashSet<Long> newlyRemovedUsers;

        public RemoveAccessToAlbumAction(FUIH uiHelper, HashSet<Long> newlyRemovedGroups, HashSet<Long> newlyRemovedUsers) {
            super(uiHelper);
            this.newlyRemovedGroups = newlyRemovedGroups;
            this.newlyRemovedUsers = newlyRemovedUsers;
        }

        protected RemoveAccessToAlbumAction(Parcel in) {
            super(in);
            newlyRemovedGroups = ParcelUtils.readLongSet(in);
            newlyRemovedUsers = ParcelUtils.readLongSet(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            ParcelUtils.writeLongSet(dest, newlyRemovedGroups);
            ParcelUtils.writeLongSet(dest, newlyRemovedUsers);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F fragment = getUiHelper().getParent();
                PiwigoAlbum<CategoryItem,GalleryItem> galleryModel = fragment.getGalleryModel();
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers));
            } else {
                getUiHelper().getParent().onAlbumUpdateFinished();
            }
        }
    }

    private static class BadHttpProtocolAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public static final Creator<BadHttpProtocolAction<?,?>> CREATOR = new Creator<BadHttpProtocolAction<?,?>>() {
            @Override
            public BadHttpProtocolAction<?,?> createFromParcel(Parcel in) {
                return new BadHttpProtocolAction<>(in);
            }

            @Override
            public BadHttpProtocolAction<?,?>[] newArray(int size) {
                return new BadHttpProtocolAction[size];
            }
        };
        private final ConnectionPreferences.ProfilePreferences connectionPreferences;

        public BadHttpProtocolAction(FUIH uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
            super(uiHelper);
            this.connectionPreferences = connectionPreferences;
        }

        protected BadHttpProtocolAction(Parcel in) {
            super(in);
            connectionPreferences = in.readParcelable(ConnectionPreferences.ProfilePreferences.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(connectionPreferences, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (positiveAnswer != null && positiveAnswer) {
                F fragment = getUiHelper().getParent();
                SharedPreferences prefs = fragment.getPrefs();
                connectionPreferences.setForceHttps(prefs, getContext(), true);
            }
        }
    }


    private static class BadRequestRedirectionAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public static final Creator<BadRequestRedirectionAction<?,?>> CREATOR = new Creator<BadRequestRedirectionAction<?,?>>() {
            @Override
            public BadRequestRedirectionAction<?,?> createFromParcel(Parcel in) {
                return new BadRequestRedirectionAction<>(in);
            }

            @Override
            public BadRequestRedirectionAction<?,?>[] newArray(int size) {
                return new BadRequestRedirectionAction[size];
            }
        };
        private final ConnectionPreferences.ProfilePreferences connectionPreferences;

        public BadRequestRedirectionAction(FUIH uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
            super(uiHelper);
            this.connectionPreferences = connectionPreferences;
        }

        protected BadRequestRedirectionAction(Parcel in) {
            super(in);
            connectionPreferences = in.readParcelable(ConnectionPreferences.ProfilePreferences.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(connectionPreferences, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (positiveAnswer != null && positiveAnswer) {
                F fragment = getUiHelper().getParent();
                SharedPreferences prefs = fragment.getPrefs();
                connectionPreferences.setFollowHttpRedirects(prefs, getContext(), true);
                //hard reset all http clients! No other solution sadly.
                getUiHelper().addActiveServiceCall(getContext().getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPreferences, getContext()).start(), "httpCleanup");
            }
        }
    }

    protected static class BulkResourceActionData implements Parcelable {
        public static final Creator<BulkResourceActionData> CREATOR = new Creator<BulkResourceActionData>() {
            @Override
            public BulkResourceActionData createFromParcel(Parcel in) {
                return new BulkResourceActionData(in);
            }

            @Override
            public BulkResourceActionData[] newArray(int size) {
                return new BulkResourceActionData[size];
            }
        };
        final static int ACTION_DELETE = 1;
        final static int ACTION_UPDATE_PERMISSIONS = 2;
        final static int ACTION_DOWNLOAD_ALL = 3;
        private final static int maxHttpRequestsQueued = 20;
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashMap<Long, Long> itemsUpdating;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ArrayList<Long> trackedMessageIds = new ArrayList<>();
        private final int action;

        BulkResourceActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems, int action) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
            itemsUpdating = new HashMap<>();
            this.action = action;
        }

        BulkResourceActionData(Parcel in) {
            selectedItemIds = ParcelUtils.readLongSet(in);
            itemsUpdated = ParcelUtils.readLongSet(in);
            itemsUpdating = ParcelUtils.readMap(in);
            selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            resourceInfoAvailable = ParcelUtils.readBool(in);
            trackedMessageIds = ParcelUtils.readLongArrayList(in);
            action = in.readInt();
        }

        public int getAction() {
            return action;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeLongSet(dest, itemsUpdated);
            ParcelUtils.writeSet(dest, selectedItems);
            ParcelUtils.writeBool(dest, resourceInfoAvailable);
            ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
            dest.writeInt(action);
        }

        void updateLinkedAlbums(ResourceItem item) {
            itemsUpdated.add(item.getId());
            if (itemsUpdated.size() == selectedItemIds.size()) {
                resourceInfoAvailable = true;
            }
            selectedItems.add(item); // will replace the previous with this one.
        }

        boolean isResourceInfoAvailable() {
            return resourceInfoAvailable;
        }

        public HashSet<Long> getSelectedItemIds() {
            return selectedItemIds;
        }

        public HashSet<ResourceItem> getSelectedItems() {
            return selectedItems;
        }

        public void clear() {
            selectedItemIds.clear();
            selectedItems.clear();
            itemsUpdated.clear();
        }

        Set<ResourceItem> getItemsWithoutLinkedAlbumData() {
            if (itemsUpdated.size() == 0) {
                return selectedItems;
            }
            Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
            Iterator<Map.Entry<Long, Long>> entriesIter = itemsUpdating.entrySet().iterator();
            while (entriesIter.hasNext()) { // remove any supposedly updating but that whose service calls are not running.
                Map.Entry<Long, Long> itemUpdating = entriesIter.next();
                if (!trackedMessageIds.contains(itemUpdating.getValue())) {
                    entriesIter.remove();
                }
            }
            for (ResourceItem r : selectedItems) {
                if (!itemsUpdated.contains(r.getId()) && itemsUpdating.get(r.getId()) == null) {
                    itemsWithoutLinkedAlbumData.add(r);
                }
            }
            return itemsWithoutLinkedAlbumData;
        }

        boolean removeProcessedResource(ResourceItem resource) {
            selectedItemIds.remove(resource.getId());
            selectedItems.remove(resource);
            itemsUpdated.remove(resource.getId());
            return selectedItemIds.size() == 0;
        }

        boolean removeProcessedResources(HashSet<? extends ResourceItem> deletedItems) {
            HashSet<Long> deletedItemIds = PiwigoUtils.toSetOfIds(deletedItems);
            selectedItemIds.removeAll(deletedItemIds);
            itemsUpdated.removeAll(deletedItemIds);
            PiwigoUtils.removeAll(selectedItems, deletedItemIds);
            return selectedItemIds.size() == 0;
        }

        public boolean isEmpty() {
            return selectedItemIds.isEmpty();
        }

        long trackMessageId(long messageId) {
            trackedMessageIds.add(messageId);
            return messageId;
        }

        boolean isTrackingMessageId(long messageId) {
            return trackedMessageIds.remove(messageId);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public int getTrackedMessageIdsCount() {
            return trackedMessageIds.size();
        }

        void getResourcesInfoIfNeeded(AbstractViewAlbumFragment<?,?> fragment) {
            int simultaneousCalls = trackedMessageIds.size();
            if (maxHttpRequestsQueued > simultaneousCalls) {
                for (ResourceItem item : getItemsWithoutLinkedAlbumData()) {
                    simultaneousCalls++;
                    itemsUpdating.put(item.getId(), trackMessageId(fragment.addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler<>(item))));
                    if (simultaneousCalls >= maxHttpRequestsQueued) {
                        break;
                    }
                }
            }
        }
    }

    private static class AddingAlbumPermissionsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

        public static final Creator<AddingAlbumPermissionsAction<?,?>> CREATOR = new Creator<AddingAlbumPermissionsAction<?,?>>() {
            @Override
            public AddingAlbumPermissionsAction<?,?> createFromParcel(Parcel in) {
                return new AddingAlbumPermissionsAction<>(in);
            }

            @Override
            public AddingAlbumPermissionsAction<?,?>[] newArray(int size) {
                return new AddingAlbumPermissionsAction[size];
            }
        };

        AddingAlbumPermissionsAction(FUIH uiHelper) {
            super(uiHelper);
        }

        protected AddingAlbumPermissionsAction(Parcel in) {
            super(in);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                F fragment = getUiHelper().getParent();
                fragment.addingAlbumPermissions();
            }
        }
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int totalSpans;
        private final int spansPerAlbum;
        private final int spansPerImage;
        private ResourceContainer<CategoryItem, GalleryItem> galleryModel;

        SpanSizeLookup(ResourceContainer<CategoryItem, GalleryItem> galleryModel, int totalSpans, int spansPerAlbum, int spansPerImage) {
            this.totalSpans = totalSpans;
            this.spansPerAlbum = spansPerAlbum;
            this.spansPerImage = spansPerImage;
            this.galleryModel = galleryModel;
        }

        public void replaceGalleryModel(PiwigoAlbum<CategoryItem, GalleryItem> model) {
            this.galleryModel = model;
        }

        @Override
        public int getSpanSize(int position) {
            int itemType = -1;
            try {
                itemType = galleryModel.getItemByIdx(position).getType();
            } catch(IndexOutOfBoundsException e) {
                Logging.log(Log.WARN, TAG, "SpanSizeLookup gallery out of sync with the gallery being shown");
                Logging.recordException(e);
            }
            switch (itemType) {
                case GalleryItem.ALBUM_HEADING_TYPE:
                    return totalSpans;
                case GalleryItem.CATEGORY_TYPE:
                    return spansPerAlbum;
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    return spansPerImage;
                case GalleryItem.PICTURE_HEADING_TYPE:
                    return totalSpans;
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                    return spansPerImage;
                default:
                    return totalSpans;
            }
        }
    }

    protected static class CustomPiwigoResponseListener<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (getParent().isVisible()) {
                getParent().updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public final void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            synchronized (getParent().getLoadingMessageIds()) {
                processAlbumPiwigoResponse(response);
                getParent().getLoadingMessageIds().remove(response.getMessageId());
            }
        }

        protected void processAlbumPiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                getParent().onPiwigoResponseResourcesDeleted((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response);
            } else if (response instanceof AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                getParent().onPiwigoResponseListOfAlbumsLoaded((AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response);
            } else if (response instanceof AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) {
                getParent().onPiwigoResponseGetResources((AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) response);
            } else if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                getParent().onPiwigoResponseAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
            } else if (response instanceof AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) {
                getParent().onPiwigoResponseAlbumPermissionsRetrieved((AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) response);
            } else if (response instanceof AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) {
                getParent().onPiwigoResponseAlbumInfoAltered((AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) response);
            } else if (response instanceof AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                getParent().onPiwigoResponseAdminListOfAlbumsLoaded((AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response);
            } else if (response instanceof ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse) {
                getParent().onPiwigoResponseAlbumContentAltered((ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse<?>) response);
            } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                getParent().onPiwigoResponseUpdateResourceInfo((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse<?>) response);
            } else if (response instanceof AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                getParent().onPiwigoResponseThumbnailUpdated((AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
            } else if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                getParent().onPiwigoResponseUsernamesRetrievedForSelectedGroups((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
            } else if (response instanceof AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) {
                getParent().onPiwigoResponseAlbumStatusUpdated((AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) response);
            } else if (response instanceof AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) {
                getParent().onPiwigoResponseAlbumPermissionsAdded((AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) response);
            } else if (response instanceof AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse) {
                getParent().onPiwigoResponseAlbumPermissionsRemoved((AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse) response);
            } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                getParent().onPiwigoResponseResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse<?>) response);
            } else if (response instanceof AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse) {
                // do nothing. This is handled. just dont want it to be registered as an error.
            } else {
                getParent().onServerDataLoadFailed(response.getMessageId());
            }
        }
    }

    private final class AlbumScrollListener extends EndlessRecyclerViewScrollListener {

        AlbumScrollListener(GridLayoutManager gridLayoutMan) {
            super(gridLayoutMan);
        }

        @Override
        public void onLoadMore(int requestedPage, int totalItemsCount, RecyclerView view) {
            int pageToLoad = requestedPage;

            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
            int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);

            if (galleryModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad) || galleryModel.isFullyLoaded()) {
                Integer missingPage = galleryModel.getAMissingPage();
                if (missingPage != null) {
                    pageToLoad = missingPage;
                } else {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
            }
            loadAlbumResourcesPage(pageToLoad);
        }
    }

    private static class AlbumViewAdapterListener<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>, MSL extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T>,LVA extends AlbumItemRecyclerViewAdapter<LVA, T, MSL, VH, RC> , VH extends AlbumItemViewHolder<VH, LVA, T, MSL, RC>, RC extends ResourceContainer<?, T>, T extends GalleryItem> extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter<MSL,LVA,VH,RC,T> {

        private F parentFragment;
        private Map<Long, CategoryItem> albumThumbnailLoadActions = new HashMap<>();

        public AlbumViewAdapterListener(F parentFragment) {
            this.parentFragment = parentFragment;
        }

        public Map<Long, CategoryItem> getAlbumThumbnailLoadActions() {
            return albumThumbnailLoadActions;
        }

        public void setAlbumThumbnailLoadActions(Map<Long, CategoryItem> albumThumbnailLoadActions) {
            this.albumThumbnailLoadActions = albumThumbnailLoadActions;
        }

        @Override
        public void onMultiSelectStatusChanged(LVA adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
        }

        @Override
        public void onItemSelectionCountChanged(LVA adapter, int selectionCount) {
            getParentFragment().onListSelectionChanged(selectionCount);
        }

        @Override
        public void onCategoryLongClick(CategoryItem album) {
            getParentFragment().onAlbumDeleteRequest(album);
        }

        public F getParentFragment() {
            return parentFragment;
        }

        @Override
        protected void onCategoryClick(CategoryItem item) {
            getParentFragment().onListItemCategoryClick(item);
        }

        @Override
        public void onAlbumHeadingClick(RC itemStore) {
            PiwigoAlbum album = (PiwigoAlbum)itemStore;
            int firstResourceIdx = itemStore.getItems().size() - itemStore.getResourcesCount();
            // need to do this because otherwise the next page of data won't be triggered to load.
            getParentFragment().scrollToListItem(firstResourceIdx);
        }

        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
            PictureResourceItem resourceItem = new PictureResourceItem(mItem.getRepresentativePictureId(), null, null, null, null, null);
            albumThumbnailLoadActions.put(getParentFragment().requestThumbnailLoad(resourceItem), mItem);
        }

        public boolean handleAlbumThumbnailInfoLoaded(long messageId, ResourceItem thumbnailResource) {
            CategoryItem item = albumThumbnailLoadActions.remove(messageId);
            if (item == null) {
                return false;
            }
            item.setThumbnailUrl(thumbnailResource.getThumbnailUrl());


            return true;
        }
    }

    protected void onListItemCategoryClick(CategoryItem item) {
        if (viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }
    }

    protected void scrollToListItem(int scrollToItemIdx) {
        galleryListView.getLayoutManager().scrollToPosition(scrollToItemIdx);
    }

    protected void onListSelectionChanged(int selectionCount) {
        bulkActionsContainer.setVisibility(selectionCount > 0 || getBasket().getItemCount() > 0 ? VISIBLE : GONE);
        updateBasketDisplay(getBasket());
    }

    protected long requestThumbnailLoad(PictureResourceItem resourceItem) {

        ImageGetInfoResponseHandler<?> handler = new ImageGetInfoResponseHandler<>(resourceItem);
        long messageId = handler.invokeAsync(getContext());
        getUiHelper().addBackgroundServiceCall(messageId);
        return messageId;
    }
}
