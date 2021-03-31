package delit.piwigoclient.ui.orphans;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.progress.BasicProgressTracker;
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
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetImagesBasicResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageAddToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesListOrphansResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.orphans.action.CreateOrphansAlbumQuestionAction;
import delit.piwigoclient.ui.util.UiUpdatingProgressListener;

/**
 * A fragment representing a list of Items.
 */
public class ViewOrphansFragment<F extends ViewOrphansFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends ViewAlbumFragment<F,FUIH> {

    private static final String TAG = "ViewOrphansFrag";
    public static final String RESUME_ACTION = "ORPHANS";
    public static final String CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS = "PiwigoClient.Orphans";
    public static final String ORPHANS_PAGE_LOAD_PREFIX = "orphans_";
    private final Set<Long> orphanRescueCalls = new HashSet<>();
    private int nextOrphansListPageToLoad = -1;
    private BasicProgressTracker orphanRescueTracker;
    private final Set<Long> orphanResourceIds = new HashSet<>();

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewOrphansFragment() {
    }

    protected void refreshEmptyAlbumText(@StringRes int emptyAlbumTextRes) {
        Logging.log(Log.DEBUG, TAG, "Replacing empty album text");
        showEmptyAlbumText(0);
    }

    public void showEmptyAlbumText(int orphanCount) {
        if(orphanCount > 0) {
            super.refreshEmptyAlbumText(getContext().getString(R.string.orphans_found_on_server_pattern, orphanCount));
        } else {
            super.refreshEmptyAlbumText(R.string.orphans_no_orphans_found_on_server);
        }
    }

    /**
     * This is needed to stop the album reopen being used.
     * @param uiHelper uiHelper
     * @return true if can reopen an album using the resume action data
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
     * @return #RESUME_ACTION
     */
    @Override
    protected String getResumeAction() {
        return RESUME_ACTION; // this essentially stops it auto loading on resume.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void populateViewFromModelEtc(@NonNull View view, @Nullable Bundle savedInstanceState) {
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

    public void userActionCreateRescuedOrphansAlbum() {
        String galleryDescription = getString(R.string.orphans_album_description);
        PiwigoGalleryDetails orphanAlbumDetail = new PiwigoGalleryDetails(CategoryItemStub.ROOT_GALLERY, null, CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS, galleryDescription, false, true);
        addActiveServiceCall(R.string.progress_creating_album, new AlbumCreateResponseHandler(orphanAlbumDetail, false));
    }
    protected void onPiwigoResponseOrphanAlbumCreated(AlbumCreateResponseHandler.PiwigoAlbumCreatedResponse response) {
        CategoryItem orphansAlbum = response.getAlbumDetails().asCategoryItem(new Date(), 0, 0, 0, null);
        //TODO set permissions on this folder BEFORE we add anything to it.

        // Add the newly created album to the view model and switch to it.
        switchViewToShowContentsAndDetailsForAlbum(orphansAlbum);
        //now the album is open, we should have another page to open (this might be empty, but it won't matter)
        if(nextOrphansListPageToLoad > 0) {
            actionLoadOrphanedResourceIdsPage(nextOrphansListPageToLoad);
            nextOrphansListPageToLoad = -1;
        }
    }

    private void switchViewToShowContentsAndDetailsForAlbum(CategoryItem orphansAlbum) {
        // open this rescued orphans album in the view
        PiwigoAlbum<CategoryItem, GalleryItem> model = updatePiwigoAlbumModelAndOurCopyOfIt(orphansAlbum);
        model.setContainerDetails(orphansAlbum);// force set the category (might be the admin copy).
        replaceListViewAdapter(model, null);
        fillGalleryEditFields();
        loadAlbumPermissionsIfNeeded();
    }

    @Override
    protected void onPiwigoResponseGetResources(final AlbumGetImagesBasicResponseHandler.PiwigoGetResourcesResponse response) {
        // this is so we can capture the event as being for the orphans page during debug with a breakpoint, no other purpose.
        super.onPiwigoResponseGetResources(response);
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
        // once we've found or created a rescued orphans folder, we'll switch to that
        if(album.equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
            // load the categories available at the root of our server (we'll check in them for a rescued orphans folder we maybe already created)
            super.loadAlbumSubCategories(StaticCategoryItem.ROOT_ALBUM.toInstance());
        } else {
            // we load sub categories just in case the user has created some child categories in this rescued orphans folder
            super.loadAlbumSubCategories(album);
        }
    }

    @Override
    protected synchronized void onPiwigoResponseAdminListOfAlbumsLoaded(AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
        // don't do the normal (inject into the list)
        List<CategoryItem> rootLevelAlbums = response.getAdminList().getDirectChildrenOfAlbum(getGalleryModel().getContainerDetails());
        for(CategoryItem cat : rootLevelAlbums) {
            // If we already have a rescued orphans folder, switch to it. (otherwise we'll create it if we need to - i.e. there are orphan resources)
            if(CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS.equals(cat.getName())
                    && !CATEGORY_NAME_PIWIGO_CLIENT_ORPHANS.equals(getGalleryModel().getContainerDetails().getName())) {
                //switch to the rescued orphans folder.
                updatePiwigoAlbumModelAndOurCopyOfIt(cat);
                loadAlbumPermissionsIfNeeded();
            }
        }
        // start loading any true orphans.
        actionLoadOrphanedResourceIdsPage(0);
    }

    /**
     * this is called for the rescued orphans album. When it is we should update the album permissions
     * so only this user can view it.
     * @param response
     */
    @Override
    protected void onPiwigoResponseAlbumPermissionsRetrieved(AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse response) {
        super.onPiwigoResponseAlbumPermissionsRetrieved(response);
        //FIXME set the album permissions so as to restrict to just this user.
    }

    /**
     * We have a list of albums from the server. If we're currently in the rescued orphans folder, we show them as normal
     * otherwise we look for the rescued orphans folder and either switch to it, or
     * @param response
     */
    @Override
    protected synchronized void onPiwigoResponseListOfAlbumsLoaded(AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {
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
            actionLoadOrphanedResourceIdsPage(0);
        } else {
            super.onPiwigoResponseListOfAlbumsLoaded(response);
        }
    }

    private boolean isShowingRescuedOrphansFolderContents() {
        return !getGalleryModel().getContainerDetails().equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM);
    }

    /**
     * This is where we find out if there are any orphan resources that need rescuing.
     * If there are, we can trigger creation of a folder to place them in if needed, or start rescuing them if it exists already
     * If there are none, then, if the folder exists, we can delete it, else we can load the contents of it.
     * @param response PiwigoGetOrphansResponse true orphan resource ids
     */
    protected void onPiwigoResponseGetOrphanIds(ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse response) {
        // totalCount is the total on the server.
        // pageSize is max number on a page
        if(response.getTotalCount() == 0) {
            // there are no orphans to find.
            if(isShowingRescuedOrphansFolderContents()) {
                // invoke the rescue of the orphans we found (should be none since none available)
                if(!actionRescueOrphans()) {
                    // not waiting for orphans to be loaded
                    // we're in the rescued orphans folder (and none are being rescued), load the contents.
                    // there might be some left from a previous operation, load those if available
                    loadAlbumResourcesPage(0);
                }
            }
        } else {
            // there are orphans to rescue
            // always record them.
            actionRecordOrphanIds(response);
            // if we're showing the rescued orphans album
            if(isShowingRescuedOrphansFolderContents()) {
                // load the next page of orphans (if the current loaded page had some)
                if(response.getResources().size() > 0 && !isAllOrphansIdentified(response.getTotalCount())) {
                    actionLoadOrphanedResourceIdsPage(response.getPage() + 1);
                } else {
                    // we've now loaded all the available orphan ids.
                    actionRescueOrphans();
                }
            } else {
                // create the album
                requestPermissionToCreateOrphansAlbum(response.getTotalCount());
                nextOrphansListPageToLoad = response.getPage() + 1;
            }
        }
    }

    private boolean isAllOrphansIdentified(int totalCount) {
        synchronized (orphanResourceIds) {
            return orphanResourceIds.size() == totalCount;
        }
    }

    private void requestPermissionToCreateOrphansAlbum(int orphanCount) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_warning, getString(R.string.alert_confirm_okay_to_create_orphans_album), View.NO_ID, R.string.button_cancel, R.string.button_ok, new CreateOrphansAlbumQuestionAction<>(getUiHelper(), orphanCount));
    }

    private boolean actionRescueOrphans() {
        boolean rescuingOrphans = false;
        synchronized (orphanResourceIds) {
            if (orphanResourceIds.size() > 0) {
                orphanRescueTracker = new BasicProgressTracker("Rescue Orphans Task", orphanResourceIds.size(), new UiUpdatingProgressListener(getUiHelper().getProgressIndicator(), R.string.progress_rescuing_orphans));
                for (long orphanId : orphanResourceIds) {
                    synchronized (orphanRescueCalls) {
                        getUiHelper().showProgressIndicator(getString(R.string.progress_rescuing_orphans), 0);
                        orphanRescueCalls.add(addActiveServiceCall(R.string.progress_rescuing_orphans, new ImageAddToAlbumResponseHandler<>(orphanId, getGalleryModel().getContainerDetails())));
                    }
                    rescuingOrphans = true;
                }
            }
        }
        return rescuingOrphans;
    }

    private void actionRecordOrphanIds(@NonNull ImagesListOrphansResponseHandler.PiwigoGetOrphansResponse response) {
        synchronized (orphanResourceIds) {
            orphanResourceIds.addAll(response.getResources());
        }
    }

//    @Override
//    protected void loadAlbumSubCategories() {
//        if(getGalleryModel().getContainerDetails().isFromServer()) {
//            super.loadAlbumSubCategories();
//        }
//    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.orphans_heading);
    }
    @Override
    protected void updateAppResumeDetails() {
        // Don't support returning to this fragment instantly after app restart for now
    }
    @Override
    protected boolean isPermitUserToViewExtraDetailsSheet() {
        //TODO might be useful for seeing and setting the permissions....
        return false; // nothing relevant for orphaned resources list
    }
    @Override
    protected void loadAlbumResourcesPage(int pageToLoad) {
        if(getGalleryModel().getContainerDetails() == null || getGalleryModel().getContainerDetails().equals(StaticCategoryItem.ORPHANS_ROOT_ALBUM)) {
            //Don't load resources until we're ready (inside the rescued orphans album)
            return;
        }
        // we have the rescued album open and no true orphans exist any more (they're all children of this album)
        super.loadAlbumResourcesPage(pageToLoad);
    }

    @Override
    protected void onRerunServerCall(String itemToLoad) {
        if(itemToLoad.startsWith(ORPHANS_PAGE_LOAD_PREFIX)) {
            String orphanPageId = itemToLoad.replace(ORPHANS_PAGE_LOAD_PREFIX, "");
            try {
                int pageId = Integer.parseInt(orphanPageId);
                actionLoadOrphanedResourceIdsPage(pageId);
            } catch(NumberFormatException e) {
                Logging.log(Log.WARN, TAG, "Unable to reload unrecognised page number : %1$s", itemToLoad);
            }
        } else {
            super.onRerunServerCall(itemToLoad);
        }
    }

    /**
     * Retrieve a list of ids for resources that are presently orphans (not in ANY album)
     * @param pageToLoad will iterate from 0 - pagesOfOrphans
     */
    protected void actionLoadOrphanedResourceIdsPage(int pageToLoad) {
        synchronized (getLoadingMessageIds()) {
            if(!getLoadingMessageIds().containsValue(ORPHANS_PAGE_LOAD_PREFIX +pageToLoad)) {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_orphan_ids, new ImagesListOrphansResponseHandler(pageToLoad, pageSize));
                getLoadingMessageIds().put(loadingMessageId, ORPHANS_PAGE_LOAD_PREFIX + pageToLoad);
            }
        }
    }
    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new ViewOrphansPiwigoResponseListener<>();
    }

    private static class ViewOrphansPiwigoResponseListener<F extends ViewOrphansFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends AbstractViewAlbumFragment.CustomPiwigoResponseListener<F,FUIH> {

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
        synchronized (orphanRescueCalls) {
            orphanRescueCalls.remove(response.getMessageId());
            synchronized (orphanResourceIds) {
                orphanResourceIds.remove(response.getPiwigoResource().getId());
                orphanRescueTracker.incrementWorkDone(1);
            }
            getGalleryModel().getContainerDetails().setPhotoCount(getGalleryModel().getContainerDetails().getPhotoCount() + 1);
            if (orphanRescueCalls.isEmpty() && !isOrphanListRetrievalRunning()) {
                // now load the images
                loadAlbumResourcesPage(0);
                getUiHelper().hideProgressIndicator();
            }
        }
    }

    private boolean isOrphanListRetrievalRunning() {
        synchronized (getLoadingMessageIds()) {
            for(String loadName : getLoadingMessageIds().values()) {
                if(loadName.startsWith(ORPHANS_PAGE_LOAD_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }
}
