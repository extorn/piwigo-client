package delit.piwigoclient.ui.common.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.IBinder;
import android.text.method.TransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

/**
 * Created by gareth on 26/01/18.
 */

public class EditTextPreference extends android.preference.EditTextPreference {

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextPreference(Context context) {
        super(context);
    }

    /**
     * Returns the summary of this ListPreference. If the summary
     * has a {@linkplain java.lang.String#format String formatting}
     * marker in it (i.e. "%s" or "%1$s"), then the current entry
     * value will be substituted in its place.
     *
     * @return the summary with appropriate string substitution
     */
    @Override
    public CharSequence getSummary() {
        CharSequence entry = getText();
        if (entry != null) {
            TransformationMethod transformationMethod = getEditText().getTransformationMethod();
            if (transformationMethod != null) {
                entry = transformationMethod.getTransformation(entry, null);
            }
        }
        CharSequence summary = super.getSummary();
        if (summary != null) {
            return String.format(summary.toString(), entry == null ? "" : entry);
        }
        return null;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        hideKeyboardFrom(getContext(), ((AlertDialog)dialog).getCurrentFocus().getWindowToken());
        super.onClick(dialog, which);
    }

    public void hideKeyboardFrom(Context context, IBinder windowToken) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(windowToken, 0);
    }
}
