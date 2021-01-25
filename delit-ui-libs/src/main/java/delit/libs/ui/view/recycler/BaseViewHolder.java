package delit.libs.ui.view.recycler;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import delit.libs.R;
import delit.libs.ui.view.button.MaterialCheckboxTriState;

public abstract class BaseViewHolder<VH extends BaseViewHolder<VH, P, T, LVA,MSL>, P extends BaseRecyclerViewAdapterPreferences<P>, T, LVA extends BaseRecyclerViewAdapter<LVA,P, T, VH,MSL>, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusListener<MSL,LVA,P,T,VH>> extends CustomViewHolder<VH, LVA, P, T, MSL> {
    private TextView txtTitle;
    private TextView detailsTitle;
    private View deleteButton;
    private MaterialCheckboxTriState checkBox;

    public BaseViewHolder(View view) {
        super(view);
    }

    public TextView getTxtTitle() {
        return txtTitle;
    }

    public TextView getDetailsTitle() {
        return detailsTitle;
    }

    public MaterialCheckboxTriState getCheckBox() {
        return checkBox;
    }

    public View getDeleteButton() {
        return deleteButton;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + " '" + txtTitle.getText() + "'";
    }

    public abstract void fillValues(T newItem, boolean allowItemDeletion);

    @Override
    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }

    @Override
    public void cacheViewFieldsAndConfigure(P adapterPrefs) {

        checkBox = itemView.findViewById(R.id.list_item_checked);
        checkBox.setClickable(getItemActionListener().getParentAdapter().isItemSelectionAllowed());
        checkBox.setOnCheckedChangeListener(new BaseRecyclerViewAdapter.ItemSelectionListener<>((LVA) getItemActionListener().getParentAdapter(), (VH) this));
        if (adapterPrefs.isMultiSelectionEnabled()) {
            checkBox.setButtonDrawable(R.drawable.checkbox);
        } else {
            checkBox.setButtonDrawable(R.drawable.radio_button);
        }

        txtTitle = itemView.findViewById(R.id.list_item_name);

        detailsTitle = itemView.findViewById(R.id.list_item_details);

        deleteButton = itemView.findViewById(R.id.list_item_delete_button);
        deleteButton.setOnClickListener(this::onDeleteItemButtonClick);
    }

    private void onDeleteItemButtonClick(View v) {
        getItemActionListener().getParentAdapter().onDeleteItem((VH)this, v);
    }
}