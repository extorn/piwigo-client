package delit.piwigoclient.ui;

import android.app.Dialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import delit.libs.ui.view.CustomToolbar;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.appbar.AppBarLayout;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.MediaScanner;
import delit.libs.ui.view.ProgressIndicator;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.IOUtils;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.VersionCompatability;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetToFileHandler;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.album.listSelect.AlbumSelectFragment;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.EulaAgreedEvent;
import delit.piwigoclient.ui.events.EulaNotAgreedEvent;
import delit.piwigoclient.ui.events.GenericLowMemoryEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedRunningAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.SlideshowEmptyEvent;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.ThemeAlteredEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.file.FolderItemRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.groups.GroupFragment;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.groups.GroupsListFragment;
import delit.piwigoclient.ui.permissions.users.UserFragment;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsersListFragment;
import delit.piwigoclient.ui.preferences.PreferencesFragment;
import delit.piwigoclient.ui.slideshow.AlbumVideoItemFragment;
import delit.piwigoclient.ui.slideshow.SlideshowFragment;
import hotchemi.android.rate.MyAppRate;

import static android.view.View.VISIBLE;

public abstract class AbstractMainActivity<T extends AbstractMainActivity<T>> extends MyActivity<T> implements ComponentCallbacks2 {

    private static final String STATE_CURRENT_ALBUM = "currentAlbum";
    private static final String STATE_QUEUED_DOWNLOADS = "queuedDownloads";
    private static final String STATE_ACTIVE_DOWNLOADS = "activeDownloads";
    private static final String STATE_BASKET = "basket";
    private static final String TAG = "mainActivity";
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    private static final String MEDIA_SCANNER_TASK_ID_DOWNLOADED_FILE = "id_downloadedFile";
    // these fields are persisted.
    private CategoryItem currentAlbum = CategoryItem.ROOT_ALBUM;
    private String onLoginActionMethodName = null;
    private ArrayList<Serializable> onLoginActionParams = new ArrayList<>();
    private Basket basket = new Basket();
    private CustomToolbar toolbar;
    private AppBarLayout appBar;
    //TODO move the download mechanism into a background service so it isn't cancelled if the user leaves the app.
    private final ArrayList<DownloadFileRequestEvent> queuedDownloads = new ArrayList<>();
    private final ArrayList<DownloadFileRequestEvent> activeDownloads = new ArrayList<>(1);

    public static void performNoBackStackTransaction(final FragmentManager fragmentManager, String tag, Fragment fragment) {
        final int newBackStackLength = fragmentManager.getBackStackEntryCount() + 1;

        fragmentManager.beginTransaction()
                .replace(R.id.main_view, fragment, tag)
                .addToBackStack(tag)
                .commit();

        fragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int nowCount = fragmentManager.getBackStackEntryCount();
                if (newBackStackLength != nowCount) {
                    // we don't really care if going back or forward. we already performed the logic here.
                    fragmentManager.removeOnBackStackChangedListener(this);

                    if (newBackStackLength > nowCount) { // user pressed back
                        fragmentManager.popBackStackImmediate();
                    }
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        LoaderManager.getInstance(this).getLoader(0);
        outState.putParcelable(STATE_CURRENT_ALBUM, currentAlbum);
        outState.putParcelable(STATE_BASKET, basket);
        outState.putParcelableArrayList(STATE_ACTIVE_DOWNLOADS, activeDownloads);
        outState.putParcelableArrayList(STATE_QUEUED_DOWNLOADS, queuedDownloads);
//        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(true);
//        }
        super.onSaveInstanceState(outState);
        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(false);
            BundleUtils.logSizeVerbose("Current Main Activity", outState);
        }
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentAlbum = savedInstanceState.getParcelable(STATE_CURRENT_ALBUM);
            basket = savedInstanceState.getParcelable(STATE_BASKET);
            ArrayList<DownloadFileRequestEvent> readVal;
            readVal = savedInstanceState.getParcelableArrayList(STATE_QUEUED_DOWNLOADS);
            if (readVal != null) {
                queuedDownloads.addAll(readVal);
            }
            readVal = savedInstanceState.getParcelableArrayList(STATE_ACTIVE_DOWNLOADS);
            if (readVal != null) {
                activeDownloads.addAll(readVal);
            }
        }

        setContentView(R.layout.activity_main);


        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        appBar = findViewById(R.id.appbar);
        /*
        Floating action button (all screens!) - if wanted

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(pkg View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.makeSnackbar(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        final DrawerLayout drawer = findViewById(R.id.drawer_layout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewCompat.setOnApplyWindowInsetsListener(drawer, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    if (!AppPreferences.isAlwaysShowStatusBar(prefs, v.getContext())) {
                        insets.replaceSystemWindowInsets(
                                insets.getStableInsetLeft(),
                                0,
                                insets.getStableInsetRight(),
                                0);
                        insets.consumeStableInsets();
                        //TODO forcing the top margin like this is really not a great idea. Find a better way.
                        ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin = 0;
                    } else {
                        if (!AppPreferences.isAlwaysShowNavButtons(prefs, v.getContext())) {
                            int topMargin = ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin;
                            if (topMargin == 0) {
                                ((FrameLayout.LayoutParams) v.getLayoutParams()).topMargin = insets.getSystemWindowInsetTop();
                            }
                        }
                    }
                    return insets;
                }
            });
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                ((CustomNavigationView) drawerView).onDrawerOpened();
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (!hasAgreedToEula()) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        if (savedInstanceState == null) {
            if (!hasAgreedToEula()) {
                showEula();
            } else if (ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
                showPreferences();
            } else {
                showGallery(currentAlbum);
            }
        }

        configureAndShowRateAppReminderIfNeeded();
    }

    private void configureAndShowRateAppReminderIfNeeded() {
        MyAppRate.instance()
                .setInstallDays(10) // default 10, 0 means install day.
                .setLaunchTimes(10) // default 10
                .setRemindInterval(30) // default 1
                .setShowLaterButton(true) // default true
                .setDebug(false) // default false
                .monitor(getApplicationContext());

        // Show a dialog if meets conditions
        MyAppRate.showRateDialogIfMeetsConditions(this);
    }

    public Basket getBasket() {
        return basket;
    }

    @Override
    public void onResume() {
        super.onResume();
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        if (result != ConnectionResult.SUCCESS) {
            if (googleApi.isUserResolvableError(result)) {
                Dialog d = googleApi.getErrorDialog(this, result, OPEN_GOOGLE_PLAY_INTENT_REQUEST);
                d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (!BuildConfig.DEBUG) {
                            finish();
                        }
                    }
                });
                d.show();
            } else {
                getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.unsupported_device), new UIHelper.QuestionResultAdapter<ActivityUIHelper<T>>(getUiHelper()) {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                        if (!BuildConfig.DEBUG) {
                            finish();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onBackPressed() {

        int backstackCount = getSupportFragmentManager().getBackStackEntryCount();

        boolean preferencesShowing;
        Fragment myFragment = getSupportFragmentManager().findFragmentByTag(PreferencesFragment.class.getName());
        preferencesShowing = myFragment != null && myFragment.isVisible();

        if (!hasAgreedToEula() && !preferencesShowing) {
            // exit immediately.
            finish();
            return;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // pop the current fragment off, close app if it is the last one
            boolean blockDefaultBackOperation = false;
            if (backstackCount == 1) {
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
                if (currentFragment instanceof ViewAlbumFragment && currentAlbum != null && !currentAlbum.isRoot()) {
                    // get the next album to show
                    CategoryItem nextAlbumToShow = ((ViewAlbumFragment) currentFragment).getParentAlbum();
                    if (nextAlbumToShow != null) {
                        // remove this fragment (so we don't have an illogical fragment back-stack - child album first)
                        removeFragmentsFromHistory(ViewAlbumFragment.class, true);
                        // open this fragment again, but with new album
                        showGallery(nextAlbumToShow);
                        blockDefaultBackOperation = true;
                    }
                }
            }
            if (!blockDefaultBackOperation) {
                doDefaultBackOperation();
            }
        }
    }

    private void doDefaultBackOperation() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // pop the current fragment off
            getSupportFragmentManager().popBackStack();
            // get the next fragment
            int i = getSupportFragmentManager().getBackStackEntryCount() - 2;
            // if there are no fragments left, do default back operation (i.e. close app)
            if (i < 0) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.piwigo_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!hasAgreedToEula()) {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.please_read_and_agree_with_eula_first);
            return true;
        }
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            showPreferences();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showPreferences() {
//        Intent i = new Intent(this, PreferencesActivity.class);
//        startActivity(i);
        PreferencesFragment fragment = new PreferencesFragment();
        showFragmentNow(fragment);
    }

    private void showEula() {
        showFragmentNow(EulaFragment.newInstance());
    }

    protected abstract void showFavorites();

    private void showGallery(final CategoryItem gallery) {
        boolean restore = false;
        if (gallery != null && gallery.isRoot()) {
            // check if we've shown any albums before. If so, pop everything off the stack.
            if (!removeFragmentsFromHistory(ViewAlbumFragment.class, true)) {
                // we're opening the activity freshly.

                // check for reopen details and use them instead if possible.
                if (AbstractViewAlbumFragment.canHandleReopenAction(getUiHelper())) {
                    restore = true;
                }

            }
        }
        if (restore) {
            showFragmentNow(new ViewAlbumFragment(), true);
        } else {
            showFragmentNow(ViewAlbumFragment.newInstance(gallery), true);
        }
        AdsManager.getInstance().showAlbumBrowsingAdvertIfAppropriate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(DownloadFileRequestEvent event) {
        synchronized (activeDownloads) {
            queuedDownloads.add(event);
            if (activeDownloads.size() == 0) {
                processNextQueuedDownloadEvent();
            } else {
                getUiHelper().showDetailedShortMsg(R.string.alert_information, getString(R.string.resource_queued_for_download, queuedDownloads.size()));
            }
        }
    }

    protected void processNextQueuedDownloadEvent() {
        synchronized (activeDownloads) {
            DownloadFileRequestEvent nextEvent = queuedDownloads.remove(0);

            activeDownloads.add(nextEvent);
            processDownloadEvent(nextEvent);
        }
    }

    private File getDestinationFile(String outputFilename) {
        File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsFolder, outputFilename);
    }

    private void processDownloadEvent(DownloadFileRequestEvent event) {
        DownloadFileRequestEvent.FileDetails fileDetail = event.getNextFileDetailToDownload();
        if(fileDetail != null) {
            if (fileDetail.getLocalFileToCopy() != null) {
                // copy this local download cache to the destination.
                try {
                    File outputFile = getDestinationFile(fileDetail.getOutputFilename());
                    IOUtils.copy(fileDetail.getLocalFileToCopy(), outputFile);
                    fileDetail.setDownloadedFile(outputFile);
                    processDownloadEvent(event);
                } catch (IOException e) {
                    Crashlytics.logException(e);
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unable_to_copy_file_from_cache_pattern, e.getMessage()));
                }
            } else {
                // invoke a download of this file
                File destinationFile = getDestinationFile(fileDetail.getOutputFilename());
                event.setRequestId(getUiHelper().invokeActiveServiceCall(getString(R.string.progress_downloading), new ImageGetToFileHandler(fileDetail.getRemoteUri(), destinationFile), new DownloadAction(event)));
            }
        } else {
            // all items downloaded - process them as needed.
            onFileDownloadEventProcessed(event);
        }
    }

//TODO add some sort of cancel operation  if the activity is backgrounded perhaps.
//    @Override
//    public void onDetach() {
//        if (activeDownloadAction != null) {
//            EventBus.getDefault().post(new CancelDownloadEvent(activeDownloadAction.getActiveDownloadActionId()));
//        }
//        super.onDetach();
//    }
//
//    private static class DownloadAction implements Parcelable {
//        private long activeDownloadActionId;
//        private boolean shareDownloadedResource;
//
//        public DownloadAction(long activeDownloadActionId, boolean shareDownloadedResource) {
//            this.activeDownloadActionId = activeDownloadActionId;
//            this.shareDownloadedResource = shareDownloadedResource;
//        }
//
//        public DownloadAction(Parcel in) {
//            activeDownloadActionId = in.readLong();
//            shareDownloadedResource = ParcelUtils.readBool(in);
//        }
//
//        public static final Creator<DownloadAction> CREATOR = new Creator<DownloadAction>() {
//            public DownloadAction createFromParcel(Parcel in) {
//                return new DownloadAction(in);
//            }
//
//            public DownloadAction[] newArray(int size) {
//                return new DownloadAction[size];
//            }
//        };
//
//        @Override
//        public int describeContents() {
//            return 0;
//        }
//
//        public long getActiveDownloadActionId() {
//            return activeDownloadActionId;
//        }
//
//        public boolean isShareDownloadedResource() {
//            return shareDownloadedResource;
//        }
//
//        @Override
//        public void writeToParcel(Parcel dest, int flags) {
//            dest.writeLong(activeDownloadActionId);
//            ParcelUtils.writeBool(dest, shareDownloadedResource);
//        }
//    }


    public void onFileDownloadEventProcessed(DownloadFileRequestEvent event) {
        //DownloadFileRequestEvent event =
        removeActionDownloadEvent(); // we've got the event, so ignore the return
        for(DownloadFileRequestEvent.FileDetails fileDetail : event.getFileDetails()) {
            // add the file details to the media store :-)
            MediaScanner.instance(this).invokeScan(new MediaScanner.MediaScannerImportTask(MEDIA_SCANNER_TASK_ID_DOWNLOADED_FILE, fileDetail.getDownloadedFile()));
        }
        if (event.isShareDownloadedWithAppSelector()) {
            Set<File> destinationFiles = new HashSet<>(event.getFileDetails().size());
            for(DownloadFileRequestEvent.FileDetails fileDetail : event.getFileDetails()) {
                destinationFiles.add(fileDetail.getDownloadedFile());
            }
            shareFilesWithOtherApps(this, destinationFiles);
        }
    }

    private void notifyUserFileDownloadComplete(final UIHelper uiHelper, final File downloadedFile) {
        uiHelper.showDetailedMsg(R.string.alert_image_download_title, uiHelper.getContext().getString(R.string.alert_image_download_complete_message));
        PicassoFactory.getInstance().getPicassoSingleton(uiHelper.getContext()).load(R.drawable.ic_notifications_black_24dp).into(new DownloadTarget(uiHelper, downloadedFile));
    }

    private void shareFilesWithOtherApps(Context context, final Set<File> filesToShare) {
//        File sharedFolder = new File(getContext().getExternalCacheDir(), "shared");
//        sharedFolder.mkdir();
//        File tmpFile = File.createTempFile(resourceFilename, resourceFileExt, sharedFolder);
//        tmpFile.deleteOnExit();

        //Send multiple seems essential to allow to work with the other apps. Not clear why.
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        ContentResolver contentResolver = context.getContentResolver();
        MimeTypeMap map = MimeTypeMap.getSingleton();

        ArrayList<Uri> urisToShare = new ArrayList<>(filesToShare.size());
        ArrayList<String> mimesOfSharedUris = new ArrayList<>(filesToShare.size());

        for(File fileToShare : filesToShare) {
            Uri uri = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider", fileToShare);
            String ext = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(fileToShare).toString());
            String mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
            urisToShare.add(uri);
            mimesOfSharedUris.add(mimeType);
        }
        if(mimesOfSharedUris.size() == 1) {
            intent.setType(mimesOfSharedUris.get(0));
        } else {
            intent.setType("application/octet-stream");
        }

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisToShare);
        intent.putStringArrayListExtra(Intent.EXTRA_MIME_TYPES, mimesOfSharedUris);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.setFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }
        context.startActivity(Intent.createChooser(intent, getString(R.string.open_files)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(CancelDownloadEvent event) {
        synchronized (activeDownloads) {
            Iterator<DownloadFileRequestEvent> iter = activeDownloads.iterator();
            while (iter.hasNext()) {
                DownloadFileRequestEvent evt = iter.next();
                if (evt.getRequestId() == event.messageId) {
                    iter.remove();
                    break;
                }
            }

        }
    }

    private void scheduleNextDownloadIfPresent() {
        synchronized (activeDownloads) {
            if (!queuedDownloads.isEmpty() && activeDownloads.isEmpty()) {
                processNextQueuedDownloadEvent();
            }
        }
    }

    private @Nullable
    DownloadFileRequestEvent removeActionDownloadEvent() {
        synchronized (activeDownloads) {
            if (activeDownloads.isEmpty()) {
                return null;
            }
            return activeDownloads.remove(0);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemSelectedEvent event) {

        Fragment newFragment = null;

        ResourceContainer<?, GalleryItem> albumOpen = event.getResourceContainer();
        GalleryItem selectedItem = event.getSelectedItem();

        if (selectedItem instanceof CategoryItem) {
            currentAlbum = (CategoryItem) selectedItem;
            showGallery(currentAlbum);
        } else {
            boolean showVideosInSlideshow = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, this);
            boolean allowVideoPlayback = AlbumViewPreferences.isVideoPlaybackEnabled(prefs, this);
            if (selectedItem instanceof VideoResourceItem) {
                if (showVideosInSlideshow) {
                    newFragment = new SlideshowFragment();
                    newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
                } else if (allowVideoPlayback) {
                    newFragment = new AlbumVideoItemFragment();
                    newFragment.setArguments(AlbumVideoItemFragment.buildStandaloneArgs(event.getModelType(), albumOpen.getId(), selectedItem.getId(), 1, 1, 1, true));
                    ((AlbumVideoItemFragment) newFragment).onPageSelected();
                }
            } else if (selectedItem instanceof PictureResourceItem) {
                newFragment = new SlideshowFragment();
                newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
            }
        }

        if (newFragment != null) {
            showFragmentNow(newFragment);
        }
    }

    private static class DownloadTarget implements Target {


        private final UIHelper uiHelper;
        private final File downloadedFile;

        public DownloadTarget(UIHelper uiHelper, File downloadedFile) {
            this.uiHelper = uiHelper;
            this.downloadedFile = downloadedFile;
        }

        private Context getContext() {
            return uiHelper.getContext();
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            Intent notificationIntent;
            Context context;
            try {
                context = getContext();
            } catch (IllegalStateException e) {
                Crashlytics.log(Log.ERROR, TAG, "No context to create notification in");
                return;
            }

//        if(openImageNotFolder) {
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            // Action on click on notification
            Uri selectedUri = Uri.fromFile(downloadedFile);
            MimeTypeMap map = MimeTypeMap.getSingleton();
            String ext = MimeTypeMap.getFileExtensionFromUrl(selectedUri.toString());
            String mimeType = map.getMimeTypeFromExtension(ext.toLowerCase());
            //notificationIntent.setDataAndType(selectedUri, mimeType);

            Uri apkURI = FileProvider.getUriForFile(
                    context,
                    BuildConfig.APPLICATION_ID + ".provider", downloadedFile);
            notificationIntent.setDataAndType(apkURI, mimeType);
            notificationIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            notificationIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                notificationIntent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            }

//        } else {
            // N.B.this only works with a very select few android apps - folder browsing seemingly isn't a standard thing in android.
//            notificationIntent = pkg Intent(Intent.ACTION_VIEW);
//            Uri selectedUri = Uri.fromFile(downloadedFile.getParentFile());
//            notificationIntent.setDataAndType(selectedUri, "resource/folder");
//        }

            PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0,
                    notificationIntent, 0);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getContext(), uiHelper.getDefaultNotificationChannelId())
                    .setLargeIcon(bitmap)
                    .setContentTitle(getContext().getString(R.string.notification_download_event))
                    .setContentText(downloadedFile.getAbsolutePath())
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // this is not a vector graphic
                mBuilder.setSmallIcon(R.drawable.ic_notifications_black);
                mBuilder.setCategory("event");
            } else {
                mBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp);
                mBuilder.setCategory(Notification.CATEGORY_EVENT);
            }

            uiHelper.clearNotification(TAG, 1);
            uiHelper.showNotification(TAG, 1, mBuilder.build());
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            //Do nothing... Should never ever occur
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            // Don't need to do anything before loading image
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        switch (id) {
            case R.id.nav_upload:
                showUpload();
                break;
            case R.id.nav_groups:
                showGroups();
                break;
            case R.id.nav_tags:
                showTags();
                break;
            case R.id.nav_users:
                showUsers();
                break;
            case R.id.nav_top_tips:
                showTopTips();
                break;
            case R.id.nav_gallery:
                showGallery(CategoryItem.ROOT_ALBUM);
                break;
            case R.id.nav_favorites:
                showFavorites();
                break;
            case R.id.nav_about:
                showAboutFragment();
                break;
            case R.id.nav_oss_licences:
                showLicencesFragment();
                break;
            case R.id.nav_settings:
                showPreferences();
                break;
            case R.id.nav_eula:
                showEula();
                break;
            default:
                onNavigationItemSelected(event, id);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    private void showTopTips() {
        TopTipsFragment fragment = TopTipsFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showAboutFragment() {
        AboutFragment fragment = AboutFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showLicencesFragment() {
        LicencesFragment fragment = LicencesFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showUpload() {
        try {
            startActivity(UploadActivity.buildIntent(this, currentAlbum.toStub()));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showGroups() {
        GroupsListFragment fragment = GroupsListFragment.newInstance();
        showFragmentNow(fragment);
    }

    public void setOnLoginActionMethodName(String onLoginActionMethodName) {
        this.onLoginActionMethodName = onLoginActionMethodName;
    }

    protected abstract void showTags();

    private void showAlbumPermissions(final ArrayList<CategoryItemStub> availableAlbums, final HashSet<Long> directAlbumPermissions, final HashSet<Long> indirectAlbumPermissions, boolean allowEdit, int actionId) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(true, false);
        if (!allowEdit) {
            prefs.readonly();
        }
        delit.piwigoclient.ui.permissions.AlbumSelectFragment fragment = delit.piwigoclient.ui.permissions.AlbumSelectFragment.newInstance(availableAlbums, prefs, actionId, indirectAlbumPermissions, directAlbumPermissions);
        showFragmentNow(fragment);
    }

    private void showUsers() {
        showFragmentNow(UsersListFragment.newInstance());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectedEvent event) {
        currentAlbum = event.getAlbum();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return; // don't mess with the status bar
        }

        View v = getWindow().getDecorView();
        v.setFitsSystemWindows(!hasFocus);

        if (hasFocus) {
            DisplayUtils.setUiFlags(this, AppPreferences.isAlwaysShowNavButtons(prefs, this), AppPreferences.isAlwaysShowStatusBar(prefs, this));
            Crashlytics.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Crashlytics.log(Log.ERROR, TAG, "showing status bar!");
        }

//        v.requestApplyInsets(); // is this needed
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final AlbumCreateNeededEvent event) {

        CreateAlbumFragment fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(Intent.ACTION_VIEW, null, this, FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    private static class DownloadAction extends UIHelper.Action<ActivityUIHelper<AbstractMainActivity>, AbstractMainActivity, PiwigoResponseBufferingHandler.Response> {
        private final DownloadFileRequestEvent downloadEvent;

        public DownloadAction(DownloadFileRequestEvent event) {
            super();
            downloadEvent = event;
        }

        @Override
        public boolean onSuccess(ActivityUIHelper<AbstractMainActivity> uiHelper, PiwigoResponseBufferingHandler.Response response) {
            //UrlProgressResponse, UrlToFileSuccessResponse,
            if (response instanceof PiwigoResponseBufferingHandler.UrlProgressResponse) {
                onProgressUpdate(uiHelper, (PiwigoResponseBufferingHandler.UrlProgressResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) {
                onGetResource(uiHelper, (PiwigoResponseBufferingHandler.UrlToFileSuccessResponse) response);
            }
            return super.onSuccess(uiHelper, response);
        }

        @Override
        public boolean onFailure(ActivityUIHelper<AbstractMainActivity> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            if (response instanceof PiwigoResponseBufferingHandler.UrlCancelledResponse) {
                onGetResourceCancelled(uiHelper, (PiwigoResponseBufferingHandler.UrlCancelledResponse) response);
            }
            if (response.isEndResponse()) {
                //TODO handle the failure and retry here so we can keep the activeDownloads field in sync properly. Presently two downloads may occur simulataneously.
                uiHelper.getParent().removeActionDownloadEvent();
                uiHelper.getParent().scheduleNextDownloadIfPresent();
            }
            return super.onFailure(uiHelper, response);
        }

        private void onProgressUpdate(UIHelper<AbstractMainActivity> uiHelper, final PiwigoResponseBufferingHandler.UrlProgressResponse response) {
            ProgressIndicator progressIndicator = uiHelper.getProgressIndicator();
            if (response.getProgress() < 0) {
                progressIndicator.showProgressIndicator(R.string.progress_downloading, -1);
            } else {
                if (response.getProgress() == 0) {
                    progressIndicator.showProgressIndicator(R.string.progress_downloading, response.getProgress(), new CancelDownloadListener(response.getMessageId()));
                } else if (progressIndicator.getVisibility() == VISIBLE) {
                    progressIndicator.updateProgressIndicator(response.getProgress());
                }
            }
        }

        public void onGetResource(UIHelper<AbstractMainActivity> uiHelper, final PiwigoResponseBufferingHandler.UrlToFileSuccessResponse response) {
            downloadEvent.markDownloaded(response.getUrl(), response.getFile());
            uiHelper.getParent().notifyUserFileDownloadComplete(uiHelper, response.getFile());
            uiHelper.getParent().processDownloadEvent(downloadEvent);
        }


        private void onGetResourceCancelled(UIHelper uiHelper, PiwigoResponseBufferingHandler.UrlCancelledResponse response) {
            uiHelper.showDetailedMsg(R.string.alert_information, uiHelper.getContext().getString(R.string.alert_image_download_cancelled_message));
        }

        private static class CancelDownloadListener implements View.OnClickListener {
            private final long downloadMessageId;

            public CancelDownloadListener(long messageId) {
                downloadMessageId = messageId;
            }

            @Override
            public void onClick(View v) {
                EventBus.getDefault().post(new CancelDownloadEvent(downloadMessageId));
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK && data.getExtras() != null) {
//                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                long actionTimeMillis = data.getExtras().getLong(FileSelectActivity.ACTION_TIME_MILLIS);
                ArrayList<FolderItemRecyclerViewAdapter.FolderItem> filesForUpload = data.getParcelableArrayListExtra(FileSelectActivity.INTENT_SELECTED_FILES);
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, actionTimeMillis).withFolderItems(filesForUpload);
                EventBus.getDefault().postSticky(event);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    @Override
    public void onTrimMemory(int level) {

        boolean cacheCleared;
        // Determine which lifecycle or system event was raised.
        switch (level) {

            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:

                //TODO do something useful?

                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedRunningAppEvent(level, cacheCleared));

                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedEvent(level, cacheCleared));
                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new GenericLowMemoryEvent(level, cacheCleared));

                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GenericLowMemoryEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedRunningAppEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    private void showLowMemoryWarningMessage(int messageId, int memoryLevel) {
        getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(messageId, memoryLevel));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final UsernameSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewUserEvent event) {
        UserFragment fragment = UserFragment.newInstance(event.getUser());
        showFragmentNow(fragment);
    }

    private void showAlbumSelectionFragment(int actionId, AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs, HashSet<Long> currentSelection) {
        AlbumSelectFragment fragment = AlbumSelectFragment.newInstance(prefs, actionId, currentSelection);
        showFragmentNow(fragment);
    }

    protected void showFragmentNow(Fragment f) {
        showFragmentNow(f, false);
    }

    private void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        Crashlytics.log(Log.DEBUG, TAG, String.format("showing fragment: %1$s (%2$s)", f.getTag(), f.getClass().getName()));
        checkLicenceIfNeeded();

        DisplayUtils.hideKeyboardFrom(getApplicationContext(), getWindow());

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        String lastFragmentName = "";
        if (lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }
        if (!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        //TODO I've added code that clears stack when showing root album... is this "good enough"?
        //TODO - trying to prevent adding duplicates here. not sure it works right.
//        TODO maybe should be using current fragment classname when adding to backstack rather than one being replaced... hmmmm
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.replace(R.id.main_view, f, f.getClass().getName()).commit();
        Crashlytics.log(Log.DEBUG, TAG, "replaced existing fragment with new: " + f.getClass().getName());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewGroupEvent event) {
        GroupFragment fragment = GroupFragment.newInstance(event.getGroup());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final GroupSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        showFragmentNow(fragment);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaNotAgreedEvent event) {
        // exit the app now.
        finishAffinity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaAgreedEvent event) {
        // unlock the drawer.
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        if (ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
            showPreferences();
        } else {
            showGallery(CategoryItem.ROOT_ALBUM);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumPermissionsSelectionNeededEvent event) {
        showAlbumPermissions(event.getAvailableAlbums(), event.getDirectAlbumPermissions(), event.getIndirectAlbumPermissions(), event.isAllowEdit(), event.getActionId());
    }

    private boolean invokeStoredActionIfAvailable() {

        boolean invoked = invokeStoredActionIfAvailableOnClass(AbstractMainActivity.class);
        if (!invoked) {
            invoked = invokeStoredActionIfAvailableOnClass(MainActivity.class);
        }
        return invoked;
    }

    private boolean invokeStoredActionIfAvailableOnClass(Class c) {

        boolean invoked = false;
        boolean actionAvailable = onLoginActionMethodName != null;
        if (actionAvailable) {
            Method[] methods = c.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals(onLoginActionMethodName)) {
                    onLoginActionMethodName = null;
                    try {
                        invoked = true;
                        Object[] params = onLoginActionParams.toArray();
                        onLoginActionParams.clear();
                        m.invoke(this, params);
                        break;
                    } catch (IllegalAccessException e) {
                        Crashlytics.logException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    } catch (InvocationTargetException e) {
                        Crashlytics.logException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    }
                }
            }
        }
        return invoked;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        if(event.getSpannableTitle() != null) {
            toolbar.setSpannableTitle(event.getSpannableTitle());
        } else {
            toolbar.setTitle(event.getTitle());
        }
        if(event.isExpandToolbarView()) {
            appBar.setExpanded(true, event.getTitle()!= null);
        } else if(event.isContractToolbarView()) {
            appBar.setExpanded(false, event.getTitle()!= null);
        }
        appBar.setEnabled(event.getTitle()!= null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ThemeAlteredEvent event) {
        CustomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.updateTheme();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        Crashlytics.setString("ServerVersion", sessionDetails.getPiwigoVersion() /* string value */);


        CustomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setMenuVisibilityToMatchSessionState();
        if (event.isChangePage() && !invokeStoredActionIfAvailable()) {
            // If nothing specified, show the root gallery.
            showGallery(CategoryItem.ROOT_ALBUM);
        } else {
            //TODO notify all pages that need it that they need to be reloaded - i.e. flush them out of the fragment manager or send an event forcing reload.
        }
        AdsManager.getInstance().updateShowAdvertsSetting(getApplicationContext());
        VersionCompatability.INSTANCE.runTests();

        boolean showUserWarning = OtherPreferences.getAndUpdateLastWarnedAboutVersionOrFeatures(prefs, this);

        if (!VersionCompatability.INSTANCE.isSupportedVersion()) {
            String serverVersion = VersionCompatability.INSTANCE.getServerVersionString();
            String minimumVersion = VersionCompatability.INSTANCE.getMinimumTestedVersionString();
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_piwigo_version_pattern, serverVersion, minimumVersion));
        }

        if(sessionDetails.isPiwigoClientPluginInstalled() && !VersionUtils.versionExceeds(VersionUtils.parseVersionString(BuildConfig.PIWIGO_CLIENT_WS_VERSION), VersionUtils.parseVersionString(sessionDetails.getPiwigoClientPluginVersion()))) {
            if(sessionDetails.getServerUrl().equalsIgnoreCase("https://piwigo.com"))
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_piwigo_client_ws_ext_version_please_update_it));
        }

        if(showUserWarning && !VersionCompatability.INSTANCE.isFavoritesEnabled()) {
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_features_pattern));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(SlideshowEmptyEvent event) {
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumSelectionNeededEvent event) {
        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withShowHierachy();
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        showAlbumSelectionFragment(event.getActionId(), prefs, event.getInitialSelection());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences();
        if(event.isAllowEditing()) {
            prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        }
        if(event.getInitialRoot() != null) {
            prefs.withInitialRoot(new CategoryItemStub("???", event.getInitialRoot()));
        } else {
            prefs.withInitialRoot(CategoryItemStub.ROOT_GALLERY);
        }
        prefs.setAllowItemAddition(true);
        prefs.withInitialSelection(event.getInitialSelection());
        RecyclerViewCategoryItemSelectFragment f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }


}
