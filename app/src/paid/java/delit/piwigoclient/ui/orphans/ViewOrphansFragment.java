package delit.piwigoclient.ui.orphans;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoGalleryDetails;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumCreateResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAddToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesListOrphansResponseHandler;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

/**
 * A fragment representing a list of Items.
 */
public class ViewOrphansFragment<F extends ViewOrphansFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends ViewAlbumFragment<F,FUIH> {

    private static final String TAG = "ViewOrphansFrag";
    public static final String RESUME_ACTION = "ORPHANS";
    public static final String CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS = "PiwigoClient.Orphans";
    public static final String STATE_ORPHAN_RESCUE_CALLS = "orphansView.orphanRescueCalls";
    public static final String ORPHANS_PAGE_LOAD_PREFIX = "orphans_";
    private Set<Long> orphanRescueCalls = new HashSet<>();
    private long orphanAlbumCreateActionId;
    private ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse orphansToBeRescuedResponse;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewOrphansFragment() {
    }

    protected void refreshEmptyAlbumText() {
        refreshEmptyAlbumText(R.string.orphans_no_orphans_found_on_server);
    }

    protected void refreshEmptyAlbumText(@StringRes int emptyAlbumTextRes) {
        Logging.log(Log.DEBUG, TAG, "Replacing empty album text");
        super.refreshEmptyAlbumText(getString(R.string.orphans_no_orphans_found_on_server));
    }

    /**
     * This is needed to stop the album reopen being used.
     * @param uiHelper
     * @param <UIH>
     * @return
     */
    public static <UIH extends UIHelper<UIH,?>> boolean canHandleReopenAction(UIH uiHelper) {
        ConnectionPreferences.ResumeActionPreferences resumePrefs = uiHelper.getResumePrefs();
        if (RESUME_ACTION.equals(resumePrefs.getReopenAction(uiHelper.getAppContext()))) {
            // Can handle it. Lets try.
            ArrayList<Long> albumPath = resumePrefs.getAlbumPath(uiHelper.getAppContext());
            return albumPath.size() > 1;
        }
        return false;
    }

    /**
     * @return
     */
    @Override
    protected String getResumeAction() {
        return RESUME_ACTION; // this essentially stops it auto loading on resume.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BundleUtils.putLongHashSet(outState, STATE_ORPHAN_RESCUE_CALLS, orphanRescueCalls);
    }

    @Override
    protected void populateViewFromModelEtc(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            orphanRescueCalls = BundleUtils.getLongHashSet(savedInstanceState, STATE_ORPHAN_RESCUE_CALLS);
        }
        super.populateViewFromModelEtc(view, savedInstanceState);
    }

    public static <F extends ViewOrphansFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> ViewOrphansFragment<F,FUIH> newInstance() {
        ViewOrphansFragment<F,FUIH> fragment = new ViewOrphansFragment<>();
        Bundle args = new Bundle();
        args.putParcelable(ARG_ALBUM, StaticCategoryItem.ORPHANS_ROOT_ALBUM.toInstance());
        fragment.setArguments(args);
        return fragment;
    }

    /*@Override
    protected void loadAdminListOfAlbums() {
        // use this list to check if we've got an existing orphans album.
        super.loadAdminListOfAlbums();
    }

    @Override
    protected synchronized void onPiwigoResponseAdminListOfAlbumsLoaded(AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
        // don't do the normal (inject into the list)
        List<CategoryItem> rootLevelAlbums = response.getAdminList().getDirectChildrenOfAlbum(getGalleryModel().getContainerDetails());
        for(CategoryItem cat : rootLevelAlbums) {
            if(cat.getName().equals(CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS)) {
                // there is now a point trying to get the child images (we know the album id)
                updatePiwigoAlbumModel(cat);
                loadAlbumPermissionsIfNeeded();
            }
        }

        // load any true orphans.
        loadOrphanedResourceIdsPage(0);
    }*/

    protected void createOrphansAlbum() {
        String galleryDescription = getString(R.string.orphans_album_description);
        PiwigoGalleryDetails orphanAlbumDetail = new PiwigoGalleryDetails(CategoryItemStub.ROOT_GALLERY, null, CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS, galleryDescription, false, true);
        orphanAlbumCreateActionId = addActiveServiceCall(R.string.progress_creating_album, new AlbumCreateResponseHandler(orphanAlbumDetail, false));
    }
    protected void onPiwigoResponseOrphanAlbumCreated(AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse response) {
        CategoryItem orphansAlbum = response.getAlbumDetails().asCategoryItem(new Date(), 0, 0, 0, null);
        // Add the newly created album to the view model and switch to it.
        switchViewToShowContentsAndDetailsForAlbum(orphansAlbum);
    }

    private void switchViewToShowContentsAndDetailsForAlbum(CategoryItem orphansAlbum) {
        // there is now a point trying to get the child images (we know the album id)
        PiwigoAlbum<CategoryItem, GalleryItem> model = updatePiwigoAlbumModelAndOurCopyOfIt(orphansAlbum);
        model.setContainerDetails(orphansAlbum);// force set the category (might be the admin copy).
        processPageOfOrphansResponse(orphansToBeRescuedResponse);
        orphansToBeRescuedResponse = null;
        replaceListViewAdapter(model, null);
        fillGalleryEditFields();
        loadAlbumPermissionsIfNeeded();
    }

    protected void onPiwigoResponseGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        // this is so we can capture the event as being for the orphans page.
        super.onPiwigoResponseGetResources(response);
        //FIXME why do I need this hack?
    }

    @Override
    protected void onPiwigoResponseAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {
        CategoryItem galleryDetails = getGalleryModel().getContainerDetails();
        if (galleryDetails.getId() == response.getAlbumId()) {
            switchViewToShowContentsAndDetailsForAlbum(StaticCategoryItem.ORPHANS_ROOT_ALBUM.toInstance());
        } else {
            super.onPiwigoResponseAlbumDeleted(response);
        }
    }

    @Override
    protected void loadAlbumSubCategories(@NonNull CategoryItem album) {
        // we need to use the root if we're at the orphan root (it doesn't actually exist)
        if(album.equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
            super.loadAlbumSubCategories(StaticCategoryItem.ROOT_ALBUM.toInstance());
        } else {
            super.loadAlbumSubCategories(album);
        }
    }

    protected synchronized void onPiwigoResponseAdminListOfAlbumsLoaded(AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
        // don't do the normal (inject into the list)
        List<CategoryItem> rootLevelAlbums = response.getAdminList().getDirectChildrenOfAlbum(getGalleryModel().getContainerDetails());
        for(CategoryItem cat : rootLevelAlbums) {
            if(CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS.equals(cat.getName())
                    && !CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS.equals(getGalleryModel().getContainerDetails().getName())) {
                // there is now a point trying to get the child images (we know the album id)
                updatePiwigoAlbumModelAndOurCopyOfIt(cat);
                loadAlbumPermissionsIfNeeded();
            }
        }

        // load any true orphans.
        loadOrphanedResourceIdsPage(0);
    }

    @Override
    protected synchronized void onPiwigoResponseListOfAlbumsLoaded(AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {
        // don't do the normal (inject into the list)
        if(response.getParentAlbum().equals(StaticCategoryItem.ROOT_ALBUM)) {
            for (CategoryItem cat : response.getAlbums()) {
                if (cat.getName().equals(CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS)) {
                    switchViewToShowContentsAndDetailsForAlbum(cat);
                }
            }
            // wipe the model  so it is reloaded
            getGalleryModel().clear();
            // load any true orphans.
            loadOrphanedResourceIdsPage(0);
        } else {
            super.onPiwigoResponseListOfAlbumsLoaded(response);
        }
    }

    protected void onPiwigoResponseGetOrphanIds(ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse response) {

        if(response.getTotalCount() == 0) {
            if(!getGalleryModel().getContainerDetails().equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
                // there are no true orphans on this server
                loadAlbumResourcesPage(0);
            } else {
                refreshEmptyAlbumText();
            }
        } else {
            if(!getGalleryModel().getContainerDetails().equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
                // first move any orphans into our orphan folder
                processPageOfOrphansResponse(response);
            } else {
                requestPermissionToCreateOrphansAlbum(response.getTotalCount());
                orphansToBeRescuedResponse = response;
            }
        }
    }

    private void requestPermissionToCreateOrphansAlbum(int orphanCount) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_confirm_okay_to_create_orphans_album), View.NO_ID, R.string.button_cancel, R.string.button_ok, new CreateOrphansAlbumQuestionListener<>(getUiHelper(), orphanCount));
    }

    private static class CreateOrphansAlbumQuestionListener<F extends ViewOrphansFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public static final Creator<CreateOrphansAlbumQuestionListener<?,?>> CREATOR = new Creator<CreateOrphansAlbumQuestionListener<?,?>>() {
            @Override
            public CreateOrphansAlbumQuestionListener<?,?> createFromParcel(Parcel in) {
                return new CreateOrphansAlbumQuestionListener<>(in);
            }

            @Override
            public CreateOrphansAlbumQuestionListener<?,?>[] newArray(int size) {
                return new CreateOrphansAlbumQuestionListener[size];
            }
        };
        private final int orphanCount;

        protected CreateOrphansAlbumQuestionListener(FUIH uiHelper, int orphanCount) {
            super(uiHelper);
            this.orphanCount = orphanCount;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(orphanCount);
        }


        protected CreateOrphansAlbumQuestionListener(Parcel in) {
            super(in);
            orphanCount = in.readInt();
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if(positiveAnswer) {
                F fragment = getParent();
                fragment.createOrphansAlbum();
            } else {
                // immediately leave this screen.
                Logging.log(Log.INFO, TAG, "Permission not granted to manage orphans.");
                getParent().refreshEmptyAlbumText(getContext().getString(R.string.orphans_found_on_server_pattern, orphanCount));
            }
        }
    }
    
    private void processPageOfOrphansResponse(ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse response) {
        if(response != null) {
            for (Long resourceId : response.getResources()) {
                addOrphanToOrphansFolder(resourceId);
            }
            if (response.getPageSize() == response.getTotalCount()) {
                // load the next page (under the orphans album - possibly newly created)
                loadOrphanedResourceIdsPage(response.getPage() + 1);
            }
        }
    }

    private void addOrphanToOrphansFolder(Long resourceId) {
        orphanRescueCalls.add(addActiveServiceCall(R.string.progress_resource_details_updating, new ImageAddToAlbumResponseHandler<>(resourceId, getGalleryModel().getContainerDetails())));
    }

//    @Override
//    protected void loadAlbumSubCategories() {
//        if(getGalleryModel().getContainerDetails().isFromServer()) {
//            super.loadAlbumSubCategories();
//        }
//    }

    @Override
    protected boolean updateAlbumSortOrder(PiwigoAlbum<CategoryItem, GalleryItem> galleryModel) {
        // support inverting the resources order only.
        boolean invertResourceSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, requireContext());
        boolean reversed;
        try {
            reversed = galleryModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
            //FIXME reversed - I think we need to refresh this page as the sort order was just flipped.
        } catch(IllegalStateException e) {
            galleryModel.removeAllResources();
            reversed = galleryModel.setRetrieveResourcesInReverseOrder(invertResourceSortOrder);
        }
        return reversed;
    }
    @Override
    protected String buildPageHeading() {
        return getString(R.string.orphans_heading);
    }
    @Override
    protected void updateAppResumeDetails() {
        // Don't support this for now
    }
    @Override
    protected boolean isPermitUserToViewExtraDetailsSheet() {
        return false; // nothing relevant for orphaned resources list
    }
    @Override
    protected void loadAlbumResourcesPage(int pageToLoad) {
        if(getGalleryModel().getContainerDetails() == null || getGalleryModel().getContainerDetails().equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
            // no idea what to load yet
            return;
        }
        super.loadAlbumResourcesPage(pageToLoad);
    }

    @Override
    protected void onRerunServerCall(String itemToLoad) {
        if(itemToLoad.startsWith(ORPHANS_PAGE_LOAD_PREFIX)) {
            String orphanPageId = itemToLoad.replace(ORPHANS_PAGE_LOAD_PREFIX, "");
            try {
                int pageId = Integer.parseInt(orphanPageId);
                loadOrphanedResourceIdsPage(pageId);
            } catch(NumberFormatException e) {
                Logging.log(Log.WARN, TAG, "Unable to reload unrecognised page number : %1$s", itemToLoad);
            }
        } else {
            super.onRerunServerCall(itemToLoad);
        }
    }

    protected void loadOrphanedResourceIdsPage(int pageToLoad) {
        synchronized (getLoadingMessageIds()) {
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
            int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);
            if (pageToActuallyLoad < 0) {
                // the sort order is inverted so we know for a fact this page is invalid.
                return;
            }

            long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_orphan_ids, new ImagesListOrphansResponseHandler(pageToActuallyLoad, pageSize));
            getLoadingMessageIds().put(loadingMessageId, ORPHANS_PAGE_LOAD_PREFIX +pageToActuallyLoad);
        }
    }
    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new ViewOrphansPiwigoResponseListener<>();
    }

    private static class ViewOrphansPiwigoResponseListener<F extends ViewOrphansFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends ViewAlbumPiwigoResponseListener<F,FUIH> {

        @Override
        protected void processAlbumPiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(response instanceof ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse) {
                getParent().onPiwigoResponseGetOrphanIds((ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse) response);
            } else if (response instanceof AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) {
                getParent().onPiwigoResponseOrphanAlbumCreated((AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse) response);
            } else if(response instanceof ImageAddToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse) {
                getParent().onPiwigoResponseOrphanRescued((ImageAddToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse<?>) response);
            } else {
                super.processAlbumPiwigoResponse(response);
            }
        }
    }

    protected void onPiwigoResponseOrphanRescued(ImageAddToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse<?> response) {
        orphanRescueCalls.remove(response.getMessageId());
        getGalleryModel().getContainerDetails().setPhotoCount(getGalleryModel().getContainerDetails().getPhotoCount()+1);
        if(orphanRescueCalls.isEmpty() && getLoadingMessageIds().isEmpty()) {
            // now load the images
            loadAlbumResourcesPage(0);
        }
    }
}
