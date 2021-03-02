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
        if (albumLiveData.getValue() == null) {
            albumLiveData.setValue(new PiwigoFavorites(favoritesSummaryDetails));
        } else {
            albumLiveData.getValue().setContainerDetails(favoritesSummaryDetails);
        }
        return albumLiveData;
    }

    @Override
    public <T extends ResourceContainer<?, ?>> T getModel() {
        return (T)getPiwigoFavorites().getValue();
    }

}
