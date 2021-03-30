package delit.piwigoclient.ui.subscription.list;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.billingclient.api.SkuDetails;

import delit.libs.core.util.Logging;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.piwigoclient.R;

public class AvailableProductItemViewHolder<IVH extends AvailableProductItemViewHolder<IVH,LVA,MSA>,LVA extends AvailableProductsRecyclerViewAdapter<LVA,MSA,IVH>, MSA extends AvailableProductsMultiSelectStatusAdapter<MSA,LVA,IVH>> extends CustomViewHolder<IVH, LVA, AvailableProductsRecyclerViewAdapter.AvailableProductAdapterPrefs, SkuDetails, MSA> {
    private static final String TAG = "AvailableProductIVH";
    private TextView titleField;
    private TextView descriptionField;
    private TextView priceField;

    public AvailableProductItemViewHolder(View view) {
        super(view);
    }

    @Override
    public void fillValues(SkuDetails item, boolean allowItemDeletion) {
        setItem(item);
        titleField.setText(item.getTitle());
        descriptionField.setText(item.getDescription());
        priceField.setText(item.getPrice());
    }

    @Override
    public void cacheViewFieldsAndConfigure(AvailableProductsRecyclerViewAdapter.AvailableProductAdapterPrefs adapterPrefs) {
        titleField = itemView.findViewById(R.id.product_title_field);
        descriptionField = itemView.findViewById(R.id.product_description_field);
        Button subscriptionButton = itemView.findViewById(R.id.product_manage_subscription_button);
        subscriptionButton.setOnClickListener((v)->onManagePayment());
        priceField = itemView.findViewById(R.id.product_price_field);
    }

    private void onManagePayment() {
        Logging.log(Log.INFO, TAG, "Managing payment for sku " + getItem().getSku());
        ((AvailableProductsRecyclerViewAdapter<?,?,?>.ProductItemCustomClickListener)getItemActionListener()).onManagePayment(getItem());
    }

    @Override
    public void setChecked(boolean checked) {

    }
}
