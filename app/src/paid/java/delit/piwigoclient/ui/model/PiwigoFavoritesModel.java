package delit.piwigoclient.ui.model;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import delit.piwigoclient.model.piwigo.PiwigoFavorites;
import delit.piwigoclient.model.piwigo.ResourceContainer;

public class PiwigoFavoritesModel extends ViewModelContainer {
    private final MutableLiveData<PiwigoFavorites> albumLiveData = new MutableLiveData<>();

    public PiwigoFavoritesModel() {
    }

    public LiveData<PiwigoFavorites> getPiwigoFavorites() {
        return albumLiveData;
    }

    public LiveData<PiwigoFavorites> getPiwigoFavorites(@NonNull PiwigoFavorites.FavoritesSummaryDetails favoritesSummaryDetails) {
        albumLiveData.setValue(new PiwigoFavorites(favoritesSummaryDetails));
        return albumLiveData;
    }

    @Override
    public ResourceContainer getModel() {
        return getPiwigoFavorites().getValue();
    }

}
