package delit.piwigoclient.ui.common.recyclerview;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.AppCompatCheckboxTriState;

public abstract class BaseViewHolder<A> extends CustomViewHolder<A> {
    private TextView txtTitle;
    private TextView detailsTitle;
    private View deleteButton;
    private AppCompatCheckboxTriState checkBox;

    public BaseViewHolder(View view) {
        super(view);
    }

    public TextView getTxtTitle() {
        return txtTitle;
    }

    public TextView getDetailsTitle() {
        return detailsTitle;
    }

    public AppCompatCheckboxTriState getCheckBox() {
        return checkBox;
    }

    public View getDeleteButton() {
        return deleteButton;
    }

    @Override
    public String toString() {
        return super.toString() + " '" + txtTitle.getText() + "'";
    }

    public abstract void fillValues(Context context, A newItem, boolean allowItemDeletion);

    @Override
    public void setChecked(boolean checked) {
        checkBox.setChecked(checked);
    }

    @Override
    public void cacheViewFieldsAndConfigure() {

        checkBox = itemView.findViewById(R.id.checked);
        checkBox.setClickable(getItemActionListener().getParentAdapter().isItemSelectionAllowed());
        checkBox.setOnCheckedChangeListener(getItemActionListener().getParentAdapter().new ItemSelectionListener(this));

        txtTitle = itemView.findViewById(R.id.name);

        detailsTitle = itemView.findViewById(R.id.details);

        deleteButton = itemView.findViewById(R.id.list_item_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteItemButtonClick(v);
            }
        });
    }

    private void onDeleteItemButtonClick(View v) {
        getItemActionListener().getParentAdapter().onDeleteItem(this, v);
    }
}