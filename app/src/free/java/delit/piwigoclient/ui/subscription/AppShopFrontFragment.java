package delit.piwigoclient.ui.subscription;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.SkuDetails;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.subscription.api.AvailableProducts;
import delit.piwigoclient.subscription.api.SubscriptionManager;
import delit.piwigoclient.subscription.piwigo.PiwigoClientSubscriptionManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.subscription.list.AvailableProductsRecyclerViewAdapter;

public class AppShopFrontFragment<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends MyFragment<F, FUIH> implements MyFragmentRecyclerPagerAdapter.PagerItemView {
    private SubscriptionManager subscriptionManager;
    private AvailableProductsRecyclerViewAdapter subscriptionOptionsAdapter;
    private RecyclerView productsList;

    private void loadProductList() {
        ExecutorManager executorManager = new ExecutorManager(1,1,30000,1);
        executorManager.submit(() -> {
            try {
                getUiHelper().showProgressIndicator(getUiHelper().getString(R.string.loading_product_details_please_wait), -1);

                subscriptionManager.obtainConnection(requireContext());
                AvailableProducts products = subscriptionManager.getAvailableProducts();
                long timeoutAt = 8000 + System.currentTimeMillis();
                while(!products.isLoaded()) {
                    products.waitForLoad(1000);
                    if(System.currentTimeMillis() > timeoutAt) {
                        DisplayUtils.runOnUiThread(()->getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getUiHelper().getString(R.string.error_retrieving_product_information)));
                        break;
                    }
                }
                subscriptionManager.closeConnection();
                DisplayUtils.runOnUiThread(()-> addProductsToList(products));
                return products;
            } catch(RuntimeException e) {
                Logging.recordException(e);
                return null;
            } finally {
                getUiHelper().hideProgressIndicator();
            }
        });
    }

    private void addProductsToList(@Nullable AvailableProducts products) {
        if(products != null) {
            subscriptionOptionsAdapter.setItems(products.getAvailableProducts());
            subscriptionOptionsAdapter.notifyDataSetChanged();
        } else {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.error_retrieving_product_information));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_app_shop_front, container, false);
        productsList = v.findViewById(R.id.list);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        this.subscriptionManager = new PiwigoClientSubscriptionManager(requireContext());
        AvailableProducts availableProducts = subscriptionManager.getAvailableProducts();

        subscriptionOptionsAdapter = new AvailableProductsRecyclerViewAdapter<>(availableProducts, this::manageProductPurchase);
        productsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        productsList.setAdapter(subscriptionOptionsAdapter);
        loadProductList();
    }

    private void manageProductPurchase(SkuDetails item) {
        subscriptionManager.launchBillingFlow(requireActivity(), item);
    }

    @Override
    public void onPageSelected() {
        if(getView() != null) {
            loadProductList();
        }
    }

    @Override
    public void onPageDeselected() {

    }

    @Override
    public int getPagerIndex() {
        return 0;
    }

    @Override
    public void onPagerIndexChangedTo(int newPagerIndex) {

    }
}
