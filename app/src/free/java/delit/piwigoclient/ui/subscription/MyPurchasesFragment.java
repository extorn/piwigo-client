package delit.piwigoclient.ui.subscription;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.subscription.api.AvailableProducts;
import delit.piwigoclient.subscription.api.ExistingPurchases;
import delit.piwigoclient.subscription.api.PurchaseDetail;
import delit.piwigoclient.subscription.api.SubscriptionManager;
import delit.piwigoclient.subscription.piwigo.PiwigoClientSubscriptionManager;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.subscription.list.PurchasedProductsRecyclerViewAdapter;

public class MyPurchasesFragment<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends MyFragment<F, FUIH> implements MyFragmentRecyclerPagerAdapter.PagerItemView {
    private SubscriptionManager subscriptionManager;
    private PurchasedProductsRecyclerViewAdapter purchasedProductsListAdapter;
    private RecyclerView productsList;
    private View emptyListText;

    private void loadProductList() {
        ExecutorManager executorManager = new ExecutorManager(1,1,30000,1);
        executorManager.submit(() -> {
            try {
                getUiHelper().showProgressIndicator(getString(R.string.loading_purchase_details_please_wait), -1);

                subscriptionManager.obtainConnection(requireContext());
                ExistingPurchases purchases = subscriptionManager.getExistingPurchases();
                long timeoutAt = 3000 + System.currentTimeMillis();
                while(!purchases.isLoaded()) {
                    purchases.waitForLoad(1000);
                    if(System.currentTimeMillis() > timeoutAt) {
                        DisplayUtils.runOnUiThread(()->getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getUiHelper().getString(R.string.error_retrieving_product_information)));
                        break;
                    }
                }
                AvailableProducts availableProducts = subscriptionManager.getAvailableProducts();
                while(!availableProducts.isLoaded()) {
                    availableProducts.waitForLoad(1000);
                    if(System.currentTimeMillis() > timeoutAt) {
                        DisplayUtils.runOnUiThread(()->getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getUiHelper().getString(R.string.error_retrieving_product_information)));
                        break;
                    }
                }
                PurchaseDetail purchaseDetail = subscriptionManager.getPurchasedProductDetail();
                subscriptionManager.checkForPriceChanges(requireActivity(), purchaseDetail);
                DisplayUtils.runOnUiThread(()-> addProductsToList(purchaseDetail));
                return purchases;
            } catch(RuntimeException e) {
                Logging.recordException(e);
                return null;
            } finally {
                getUiHelper().hideProgressIndicator();
            }
        });
    }

    private void onPriceChange() {
        //TODO do something.
    }

    private void addProductsToList(@Nullable PurchaseDetail purchases) {
        if(purchases != null) {
            purchasedProductsListAdapter.setItems(purchases.getList());
            purchasedProductsListAdapter.notifyDataSetChanged();
            emptyListText.setVisibility(purchases.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.error_retrieving_product_information));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_my_purchases, container, false);
        productsList = v.findViewById(R.id.list);
        emptyListText = v.findViewById(R.id.empty_list_text);
        MaterialButton manageAllSubscriptionsOnGoogleButton = v.findViewById(R.id.product_view_all_google_subscriptions_button);
        manageAllSubscriptionsOnGoogleButton.setOnClickListener(this::onClickManageAllGoogleSubscriptions);
        return v;
    }

    private void onClickManageAllGoogleSubscriptions(View view) {
        Context context = view.getContext();
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https");
        uriBuilder.authority("play.google.com");
        uriBuilder.path("/store/account/subscriptions");
//        uriBuilder.appendQueryParameter("sku", getItem().getSku());
        uriBuilder.appendQueryParameter("package", context.getPackageName());
        Intent intent = new Intent(Intent.ACTION_VIEW, uriBuilder.build());
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.manage_app_purchases)));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        this.subscriptionManager = new PiwigoClientSubscriptionManager(requireContext());
        purchasedProductsListAdapter = new PurchasedProductsRecyclerViewAdapter();
        productsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        productsList.setAdapter(purchasedProductsListAdapter);
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
