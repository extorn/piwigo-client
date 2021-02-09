package delit.piwigoclient.ui.model;

import androidx.lifecycle.ViewModel;

import delit.piwigoclient.model.piwigo.ResourceContainer;

public abstract class ViewModelContainer extends ViewModel {
    public abstract <T extends ResourceContainer<?,?>> T getModel();
}
