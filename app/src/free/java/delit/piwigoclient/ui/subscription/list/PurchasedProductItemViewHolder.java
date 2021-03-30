package delit.piwigoclient.ui.subscription.list;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.piwigoclient.R;
import delit.piwigoclient.subscription.api.ExistingPurchase;

public class PurchasedProductItemViewHolder<IVH extends PurchasedProductItemViewHolder<IVH,LVA,MSA>,LVA extends PurchasedProductsRecyclerViewAdapter<LVA,MSA,IVH>, MSA extends PurchasedProductsMultiSelectStatusAdapter<MSA,LVA,IVH>> extends CustomViewHolder<IVH, LVA, PurchasedProductsRecyclerViewAdapter.PurchasedProductAdapterPrefs, ExistingPurchase, MSA> {
    private static final String TAG = "PurchasedProductIVH";
    private TextView titleField;
    private TextView descriptionField;
    private TextView priceField;
    private TextView purchaseOrderIdField;
    private TextView purchasedOnField;
    private SwitchMaterial purchaseRecurringField;

    public PurchasedProductItemViewHolder(View view) {
        super(view);
    }

    @Override
    public void fillValues(ExistingPurchase item, boolean allowItemDeletion) {
        setItem(item);
        titleField.setText(item.getTitle());
        descriptionField.setText(item.getDescription());
        priceField.setText(item.getPricePaid());
        purchaseRecurringField.setVisibility(item.isSubscription() ? View.VISIBLE : View.GONE);
        purchaseRecurringField.setChecked(item.isAutoRenewing());
        purchaseOrderIdField.setText(item.getOrderId());
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_TIME;
        purchasedOnField.setText(DateUtils.formatDateTime(itemView.getContext(), item.getPurchaseTime(), flags));
    }

    @Override
    public void cacheViewFieldsAndConfigure(PurchasedProductsRecyclerViewAdapter.PurchasedProductAdapterPrefs adapterPrefs) {
        titleField = itemView.findViewById(R.id.product_title_field);
        descriptionField = itemView.findViewById(R.id.product_description_field);
        purchasedOnField = itemView.findViewById(R.id.product_purchased_field);
        purchaseRecurringField = itemView.findViewById(R.id.product_purchase_recurring_field);
        purchaseOrderIdField = itemView.findViewById(R.id.product_order_id_field);
        Button manageButton = itemView.findViewById(R.id.product_manage_subscription_button);
        manageButton.setOnClickListener((v)->onManagePayment());
        priceField = itemView.findViewById(R.id.product_price_field);
    }

    private void onManagePayment() {
        Context context = itemView.getContext();
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.scheme("https");
        uriBuilder.authority("play.google.com");
        uriBuilder.path("/store/account/subscriptions");
        uriBuilder.appendQueryParameter("sku", getItem().getSku());
        uriBuilder.appendQueryParameter("package", context.getPackageName());
        Intent intent = new Intent(Intent.ACTION_VIEW, uriBuilder.build());
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.manage_app_purchases)));
    }

    @Override
    public void setChecked(boolean checked) {

    }
}
