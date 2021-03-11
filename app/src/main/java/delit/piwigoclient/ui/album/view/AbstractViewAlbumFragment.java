package delit.piwigoclient.ui.album.view;

import android.content.Context;
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
import java.util.Objects;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.CustomClickTouchListener;
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
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.ServerConfig;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumSetStatusResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageSetPrivacyLevelResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.album.view.action.AddAccessToAlbumAction;
import delit.piwigoclient.ui.album.view.action.AddingChildPermissionsAction;
import delit.piwigoclient.ui.album.view.action.AlbumNoLongerExistsAction;
import delit.piwigoclient.ui.album.view.action.BadHttpProtocolAction;
import delit.piwigoclient.ui.album.view.action.BadRequestRedirectionAction;
import delit.piwigoclient.ui.album.view.action.BasketAction;
import delit.piwigoclient.ui.album.view.action.BulkResourceActionData;
import delit.piwigoclient.ui.album.view.action.DeleteAlbumAction;
import delit.piwigoclient.ui.album.view.action.DeleteResourcesForeverAction;
import delit.piwigoclient.ui.album.view.action.DeleteSharedResourcesAction;
import delit.piwigoclient.ui.album.view.action.DeleteWithOrphansAlbumAction;
import delit.piwigoclient.ui.album.view.action.LoadAlbumTreeAction;
import delit.piwigoclient.ui.album.view.action.RemoveAccessToAlbumAction;
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
public abstract class AbstractViewAlbumFragment<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> implements BulkActionResourceProvider {

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
    private AlbumViewItemSpanSizeLookup cellSpanLookup;

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

    public static <F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  void deleteResourcesFromServerForever(FUIH uiHelper, final HashSet<Long> selectedItemIds, final HashSet<ResourceItem> selectedItems) {
        String msg = uiHelper.getAppContext().getString(R.string.alert_confirm_really_delete_items_from_server);
        uiHelper.showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new DeleteResourcesForeverAction<>(uiHelper, selectedItemIds, selectedItems));
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
        albumIsDirty = true; // presume dirty (if reloading from saved state, this will be overridden)
        Log.e(TAG, "Loading model from args: finished");
    }

    public void onReopenModelRetrieved(CategoryItem rootAlbum, CategoryItem album) {
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
                    PiwigoAlbumModel albumModel = obtainActivityViewModel(requireActivity(), "" + catItem.getId(), PiwigoAlbumModel.class);
                    albumModel.getPiwigoAlbum(catItem);
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
        PiwigoAlbumModel albumViewModel = obtainActivityViewModel(requireActivity(), "" + newAlbum.getId(), PiwigoAlbumModel.class);
        PiwigoAlbum<CategoryItem,GalleryItem> album = albumViewModel.getPiwigoAlbum(newAlbum).getValue();
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
        return inflater.inflate(R.layout.fragment_album_view, container, false);
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
        galleryListView.setHasFixedSize(true); // size does not vary depending on content.

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

        updateAlbumSortOrder(galleryModel);

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
            // we are overriding the albumIsDirty here that has been set when loading args because that's when first opening page.
            albumIsDirty = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile()) != userGuid;
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

        fillGalleryHeadings();

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
        cellSpanLookup = new AlbumViewItemSpanSizeLookup(galleryModel, totalSpans, colsSpannedByAlbum, colsSpannedByImage);
        gridLayoutMan.setSpanSizeLookup(cellSpanLookup);
        galleryListView.setLayoutManager(gridLayoutMan);
        galleryListView.addItemDecoration(new AlbumItemSpacingDecoration(DisplayUtils.dpToPx(requireContext(), 1), DisplayUtils.dpToPx(requireContext(), 16)));

        galleryListViewScrollListener = new AlbumScrollListener(this, galleryListView.getLayoutManager());
        galleryListView.addOnScrollListener(galleryListViewScrollListener);

        replaceListViewAdapter(galleryModel, savedInstanceState);
        // basket depends on then adapter being available
        Basket basket = getBasket();
        setupBulkActionsControls(basket);

        //display bottom sheet if needed
        updateInformationShowingStatus();

        if (isSessionDetailsChanged()) {

            if (!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())) {
                //trigger total screen refresh. Any errors will result in screen being closed.
                doServerActionForceReloadAlbumContent();
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
        //FIXME not sure that the current page being presumed to be the largest is going to work properly
        galleryListViewScrollListener.configure(newModel.getPagesLoadedIdxToSizeMap(), galleryModel.getItemCount());
        viewAdapter = new AlbumItemRecyclerViewAdapter(requireContext(), PiwigoAlbumModel.class, newModel, viewAdapterListener, viewPrefs);
        if (savedInstanceState != null) {
            viewAdapter.setInitiallySelectedItems(new HashSet<>());
            viewAdapter.setSelectedItems(BundleUtils.getLongHashSet(savedInstanceState, STATE_SELECTED_ITEMS));
        }
        galleryListView.setAdapter(viewAdapter);
        galleryModel = newModel;
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
        // switch to a different sort order if wanted (will require reload from server so remove all albums)
        boolean sortOrderChanged;
        int newSortOrder = AlbumViewPreferences.getAlbumChildAlbumsSortOrder(prefs, requireContext());
        try {
            sortOrderChanged = galleryModel.setAlbumSortOrder(newSortOrder);
        } catch(IllegalStateException e) {
            galleryModel.removeAllAlbums();
            sortOrderChanged = galleryModel.setAlbumSortOrder(newSortOrder);
        }
        // flip the album order if wanted
        boolean invertChildAlbumSortOrder = AlbumViewPreferences.getAlbumChildAlbumSortOrderInverted(prefs, requireContext());
        sortOrderChanged |= galleryModel.setRetrieveChildAlbumsInReverseOrder(invertChildAlbumSortOrder);

        // flip the resource order if wanted
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        try {
            sortOrderChanged |= galleryModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        } catch(IllegalStateException e) {
            galleryModel.removeAllResources();
            sortOrderChanged = galleryModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        }
        if(galleryModel.setResourceSortOrder(AlbumViewPreferences.getResourceSortOrder(prefs, requireContext()))) {
            galleryModel.removeAllResources();
            sortOrderChanged = true;
        }
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
        //NOTE this touch listener is needed because we use a touch listener in the images below and they'll pick up the clicks if we don't
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonPermissions, (v)->onClickBulkActionPermissionsButton());
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonDownload, (v)->onClickBulkActionDownloadButton());
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonDelete, (v)->onClickBulkActionDeleteButton());
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonCopy, (v)->onUserActionAddSelectedItemsToBasket(Basket.ACTION_COPY));
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonCut, (v)->onUserActionAddSelectedItemsToBasket(Basket.ACTION_CUT));
        CustomClickTouchListener.callClickOnTouch(bulkActionButtonPaste, (v)->onUserActionPasteItemsFromBasket());

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

    private void onUserActionPasteItemsFromBasket() {
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

    protected void loadAdminListOfAlbums() {
        long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetChildAlbumsAdminResponseHandler());
        loadingMessageIds.put(loadingMessageId, SERVER_CALL_ID_ADMIN_LIST_ALBUMS);
    }

    private void onDownloadAllItemsButtonClick(HashSet<Long> imageIds, HashSet<ResourceItem> selectedItems) {
        BulkResourceActionData bulkActionData = new BulkResourceActionData(imageIds, selectedItems, BulkResourceActionData.ACTION_DOWNLOAD_ALL);
        this.bulkResourceActionData = bulkActionData;
        onDownloadAllItems(bulkActionData);
    }

    private void onClickUpdateImagePermissionsButton(HashSet<Long> imageIds, HashSet<ResourceItem> selectedItems) {
        BulkResourceActionData bulkActionData = new BulkResourceActionData(imageIds, selectedItems, BulkResourceActionData.ACTION_UPDATE_PERMISSIONS);
        this.bulkResourceActionData = bulkActionData;
        onUserActionUpdateImagePermissions(bulkActionData);
    }

    private void onUserActionUpdateImagePermissions(BulkResourceActionData bulkActionData) {
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
            getUiHelper().showOrQueueTriButtonDialogQuestion(R.string.alert_bulk_image_permissions_title, getString(R.string.alert_bulk_image_permissions_message, resourcesReadyForAction.size()), R.layout.layout_bulk_image_permissions, R.string.button_cancel, View.NO_ID, R.string.button_ok, new BulkImagePermissionsListener<>(bulkResourceActionData.getSelectedItemIds(), getUiHelper()));
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
            getUiHelper().showOrQueueTriButtonDialogQuestion(R.string.alert_confirm_title, msg, View.NO_ID, R.string.button_unlink, R.string.button_cancel, R.string.button_delete, new DeleteSharedResourcesAction<>(getUiHelper(), sharedResources));
        } else {
            deleteResourcesFromServerForever(getUiHelper(), deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());
        }

    }

    private void onClickBulkActionDownloadButton() {
        HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
        if (selectedItemIds.size() > 0) {
            onDownloadAllItemsButtonClick(selectedItemIds, viewAdapter.getSelectedItemsOfType(ResourceItem.class));
        }
    }

    private void onClickBulkActionPermissionsButton() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (selectedItemIds.size() > 0) {
                onClickUpdateImagePermissionsButton(selectedItemIds, viewAdapter.getSelectedItemsOfType(ResourceItem.class));
            }
        }
    }

    private void onClickBulkActionDeleteButton() {
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

//        AppCompatImageView basketImage = basketView.findViewById(R.id.basket_image);

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
        //NOTE this touch listener is needed because we use a touch listener in the images below and they'll pick up the clicks if we don't
        CustomClickTouchListener.callClickOnTouch(clearButton, (cb)->onClickClearBasketButton());
    }

    private void onClickClearBasketButton() {
        Basket basket = getBasket();
        basket.clear();
        updateBasketDisplay(basket);
    }

    public Basket getBasket() {
        MainActivity<?> activity = (MainActivity<?>) requireActivity();
        return activity.getBasket();
    }

    private void onUserActionAddSelectedItemsToBasket(int action) {
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
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToActuallyLoad));
            } finally {
                galleryModel.releasePageLoadLock();
            }
        }
    }

    protected int getPageToActuallyLoad(int pageRequested, int pageSize) {
        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        try {
            boolean reversed = galleryModel.setRetrieveResourcesInReverseOrder(invertSortOrder);
        } catch(IllegalStateException e) {
            Logging.logAnalyticEvent(requireContext(), "IllegalResourceReverseRequest");
            Logging.log(Log.ERROR, TAG, "ignoring request to switch load order of resources");
//            DisplayUtils.runOnUiThread(()->viewAdapter.notifyDataSetChanged());
//            DisplayUtils.runOnUiThread(()->galleryListView.invalidate());
            //FIXME currently, reversing the sort order after some pages are loaded does nothing.
        }
        int pageToActuallyLoad = pageRequested;
        if (invertSortOrder) {
            int lastPageId = galleryModel.getContainerDetails().getPagesOfPhotos(pageSize) -1;
            pageToActuallyLoad = lastPageId - pageRequested;
        }
        return pageToActuallyLoad;
    }

    protected void onUserActionAlbumDeleteRequest(final CategoryItem album) {
        if (album.getTotalPhotos() > 0 || album.getSubCategories() > 0) {
            String msg;
            if(album.getSubCategories() > 0) {
                msg = String.format(getContext().getString(R.string.alert_confirm_really_really_delete_album_with_child_albums_from_server_pattern), album.getName(), album.getPhotoCount(), album.getSubCategories(), album.getTotalPhotos() - album.getPhotoCount());
            } else {
                msg = String.format(getContext().getString(R.string.alert_confirm_really_really_delete_album_without_child_albums_from_server_pattern), album.getName(), album.getPhotoCount());
            }
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteWithOrphansAlbumAction<>(getUiHelper(), album));
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
            loadingMessageIds.put(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetChildAlbumsResponseHandler(album, viewPrefs.getPreferredAlbumThumbnailSize(), false)), SERVER_CALL_ID_SUB_CATEGORIES);
        }
    }

    public BulkResourceActionData getBulkResourceActionData() {
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
                HashSet<Long> preselectedUsernames = CollectionUtils.getSetFromArray(currentUsers);
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
    public void onReturnToFragment() {
        super.onReturnToFragment();
        /*if(albumIsDirty) {
            doServerActionReloadAlbumContent();
        } else {
            DisplayUtils.postOnUiThread(()->viewAdapter.notifyDataSetChanged());
        }*/
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

        boolean resorted = updateAlbumSortOrder(galleryModel);


        if (albumIsDirty) {
            doServerActionReloadAlbumContent();
        } else if (itemsToLoad.size() > 0) {
            onReloadAlbum();
        } else {
            if(resorted) {
                if(galleryModel.getResourcesCount() == 0) {
                    // need to reload the resources.
                    loadAlbumResourcesPage(0);
                }
            }

            int spacerAlbumsNeeded = galleryModel.getChildAlbumCount() % albumsPerRow;
            if (spacerAlbumsNeeded > 0) {
                spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
            }
            try {
                galleryModel.setSpacerAlbumCount(spacerAlbumsNeeded);
            } catch(IndexOutOfBoundsException e) {
                Logging.log(Log.ERROR, TAG, "Unexpected exception trapped");
                Logging.recordException(e);
            }
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

    protected void doServerActionForceReloadAlbumContent() {
        albumIsDirty = true;
        //we've altered the album content (now update this album view to reflect the server content)
        doServerActionReloadAlbumContent();
    }

    protected void doServerActionReloadAlbumContent() {

        if (albumIsDirty) {
            emptyGalleryLabel.setVisibility(GONE);
            albumIsDirty = false;
            if (loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            Logging.log(Log.DEBUG,TAG,"Clearing album content to force reload of all");
            galleryModel.clear();
            galleryListViewScrollListener.resetState();
            cellSpanLookup.replaceGalleryModel(galleryModel);
            galleryListView.swapAdapter(viewAdapter, true);

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
            PiwigoAlbum<CategoryItem,GalleryItem> movedFromPiwigoAlbum = ((PiwigoAlbumModel)obtainActivityViewModel(requireActivity(), "" + movedParent.getParentId(), PiwigoAlbumModel.class)).getModel();
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
            doServerActionForceReloadAlbumContent();
        } else {
            Logging.log(Log.ERROR, getTag(), "processed onResourceMoved but basket (" + basket.getItemCount() + " items) does not contain resource");
        }
    }

    private void doServerActionUpdateAlbumDetails() {
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
            doServerActionUpdateAlbumDetails();
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
        deleteAlbumButton.setOnClickListener(v -> onUserActionAlbumDeleteRequest(galleryModel.getContainerDetails()));

        pasteButton = bottomSheet.findViewById(R.id.gallery_action_paste);
        pasteButton.setVisibility(editFieldsVisibility);
        pasteButton.setOnClickListener(v -> onUserActionMoveItemRequest(galleryModel.getContainerDetails()));

        cutButton = bottomSheet.findViewById(R.id.gallery_action_cut);
        cutButton.setVisibility(editFieldsVisibility);
        cutButton.setOnClickListener(v -> onUserActionCopyItemRequest(galleryModel.getContainerDetails()));
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

    private void fillGalleryHeadings() {
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

    private void onUserActionMoveItemRequest(CategoryItem model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onUserActionCopyItemRequest(CategoryItem model) {
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

    protected synchronized void onPiwigoResponseAdminListOfAlbumsLoaded(AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
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
                        onUserActionUpdateImagePermissions(bulkResourceActionData);
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
                HashSet<Long> preselectedUsernames = CollectionUtils.getSetFromArray(currentUsers);
                UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(usernameSelectionNeededEvent);
            }
        }
    }

    protected void onPiwigoResponseThumbnailUpdated(AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        if (response.getAlbumParentIdAltered() != null && response.getAlbumParentIdAltered() == galleryModel.getContainerDetails().getId()) {
            // need to refresh this gallery content.
            doServerActionForceReloadAlbumContent();
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

        doServerActionForceReloadAlbumContent();

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
                doServerActionUpdateAlbumDetails();
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

    protected synchronized void onPiwigoResponseListOfAlbumsLoaded(final AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {

        if (galleryModel.getContainerDetails().isRoot()) {
            galleryModel.updateMaxExpectedItemCount(response.getAlbums().size());
        }

        long currentGalleryId = galleryModel.getContainerDetails().getId();
        int actualChildAlbums = response.getAlbums().size();
        if(actualChildAlbums > 0 && response.getAlbums().get(0).getId() == currentGalleryId) {
            actualChildAlbums--;
        }
        if (actualChildAlbums > 0) {

            if (!galleryModel.containsItem(StaticCategoryItem.ALBUM_HEADING)) {
                galleryModel.addItem(StaticCategoryItem.ALBUM_HEADING);
            }
        }
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
                fillGalleryHeadings();
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
            int firstPage = invertSortOrder ? galleryModel.getContainerDetails().getPagesOfPhotos(pageSize) -1 : 0;

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
        doServerActionForceReloadAlbumContent();
    }

    protected void onPiwigoResponseAlbumInfoAltered(final AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse response) {
        galleryModel.setContainerDetails(response.getAlbum());
        fillGalleryHeadings();
        onModelUpdatedAlbumPermissions();
    }

    protected void onPiwigoResponseAlbumPermissionsAdded(AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.addAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.addAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

        if (!removeAlbumPermissions()) {
            updateViewFromModelAlbumPermissions();
        }
    }

    protected void onPiwigoResponseAlbumPermissionsRemoved(AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.removeAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.removeAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

        updateViewFromModelAlbumPermissions();
    }

    private void onModelUpdatedAlbumPermissions() {
        if (galleryModel.getContainerDetails().isPrivate()) {
            if (!addingAlbumPermissions() && !removeAlbumPermissions()) {
                updateViewFromModelAlbumPermissions();
            }
        } else {
            updateViewFromModelAlbumPermissions();
        }
    }

    public void updateViewFromModelAlbumPermissions() {
        updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
        fillGalleryHeadings();
        getUiHelper().hideProgressIndicator();
        if (editingItemDetails) {
            editingItemDetails = false;
            currentUsers = galleryModel.getContainerDetails().getUsers();
            currentGroups = galleryModel.getContainerDetails().getGroups();
            fillGalleryEditFields();
        }
    }

    protected void onPiwigoResponseAlbumStatusUpdated(AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse response) {
        onModelUpdatedAlbumPermissions();
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
            doServerActionForceReloadAlbumContent();
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
                doServerActionReloadAlbumContent();
            }
        }
    }

    public CategoryItem getParentAlbum() {
        if (albumDetails == null) {
            Logging.log(Log.WARN, TAG, "Attempt to reopen parent but is null");
            return null;
        }
        PiwigoAlbumModel albumViewModel = obtainActivityViewModel(requireActivity(), "" + albumDetails.getParentId(), PiwigoAlbumModel.class);
        PiwigoAlbum<CategoryItem,GalleryItem> nextPiwigoAlbum = albumViewModel.getModel();
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
            //TODO Do something more fine grained to avoid refreshing the entire album view!
            if (isVisible()) {
                doServerActionReloadAlbumContent();
            }
            if (albumAlteredEvent.isCascadeToParents()) {
                notifyAllParentAlbumsOfContentChange();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumDeletedEvent event) {
        if (event.getItem().getParentId() == galleryModel.getId()) {
            // now removed from the backing gallery too.
            galleryModel.remove(event.getItem());
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent<?> event) {
        if (event.item.getParentId() == galleryModel.getId()) {
            // now removed from the backing gallery too.
            galleryModel.remove(event.item);
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    public PiwigoAlbum<CategoryItem, GalleryItem> getGalleryModel() {
        return galleryModel;
    }

    protected HashMap<Long, String> getLoadingMessageIds() {
        return loadingMessageIds;
    }

    protected void onPiwigoResponseLoadFailed(long messageId) {

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
            } else if (response instanceof AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                getParent().onPiwigoResponseListOfAlbumsLoaded((AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response);
            } else if (response instanceof AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) {
                getParent().onPiwigoResponseGetResources((AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse) response);
            } else if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                getParent().onPiwigoResponseAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
            } else if (response instanceof AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) {
                getParent().onPiwigoResponseAlbumPermissionsRetrieved((AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) response);
            } else if (response instanceof AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) {
                getParent().onPiwigoResponseAlbumInfoAltered((AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) response);
            } else if (response instanceof AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                getParent().onPiwigoResponseAdminListOfAlbumsLoaded((AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response);
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
                // do nothing. This is handled. just don't want it to be registered as an error.
            } else {
                getParent().onPiwigoResponseLoadFailed(response.getMessageId());
            }
        }
    }

    protected void onClickListItemCategory(CategoryItem item) {
        if (viewAdapter.isItemSelectionAllowed()) {
            viewAdapter.toggleItemSelection();
        }
    }

    protected void scrollToListItem(int scrollToItemIdx) {
        Objects.requireNonNull(galleryListView.getLayoutManager()).scrollToPosition(scrollToItemIdx);
    }

    protected void onUserActionListSelectionChanged(int selectionCount) {
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
