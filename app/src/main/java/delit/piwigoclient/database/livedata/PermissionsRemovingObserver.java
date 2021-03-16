package delit.piwigoclient.database.livedata;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import delit.piwigoclient.database.AppSettingsRepository;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.ui.util.LiveDataTransientObserver;

public class PermissionsRemovingObserver extends LiveDataTransientObserver<List<UriPermissionUse>> {
    private final Collection<Uri> uris;
    private final String consumerId;
    private final AppSettingsRepository repository;


    public PermissionsRemovingObserver(Context context, AppSettingsRepository repository, LiveData<List<UriPermissionUse>> liveData, Collection<Uri> uris, String consumerId) {
            super(context, liveData);
            this.uris = uris;
            this.consumerId = consumerId;
            this.repository = repository;
        }

        public PermissionsRemovingObserver(Context context, AppSettingsRepository repository, LiveData<List<UriPermissionUse>> liveData, Uri uri, String consumerId) {
            this(context, repository, liveData, Collections.singletonList(uri), consumerId);
        }

        @Override
        public void onChangeObserved(List<UriPermissionUse> permissionsHeld) {
            repository.removePermissionsAndUpdateRealAppPermissions(getContext(), consumerId, uris, permissionsHeld);
        }
    }