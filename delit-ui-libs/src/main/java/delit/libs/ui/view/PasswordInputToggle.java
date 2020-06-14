package delit.libs.ui.view;

import android.text.InputType;
import android.widget.CompoundButton;
import android.widget.EditText;

public class PasswordInputToggle implements CompoundButton.OnCheckedChangeListener {
    private final EditText editText;
    int defaultInputType;

    public PasswordInputToggle(EditText editText) {
        this.defaultInputType = editText.getInputType();
        this.editText = editText;
    }

    public static boolean isTextPassword(EditText editText) {
        return InputType.TYPE_TEXT_VARIATION_PASSWORD == (editText.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    public static boolean isNumberPassword(EditText editText) {
        return InputType.TYPE_NUMBER_VARIATION_PASSWORD == (editText.getInputType() & InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    public static boolean isWebPassword(EditText editText) {
        return InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD == (editText.getInputType() & InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    }

    public int getVisibleInputType(int currentInputType) {
        return defaultInputType | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    }

    public int getDefaultInputType() {
        return defaultInputType;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(isChecked) {
            editText.setInputType(getVisibleInputType(editText.getInputType()));
        } else {
            editText.setInputType(getDefaultInputType());
        }
    }
}
