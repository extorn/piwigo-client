package delit.piwigoclient.ui.common;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

/**
 * Created by gareth on 10/26/17.
 */

public class MyProgressDialog extends DialogFragment {
    private static final java.lang.String STATE_TITLE = "title";
    private String title;

    public static MyProgressDialog createInstance(String title) {
        MyProgressDialog dialog = new MyProgressDialog();
        Bundle args = new Bundle();
        args.putString(STATE_TITLE, title);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        Bundle args = getArguments();
        if(args != null) {
            title = args.getString(STATE_TITLE);
        }
        if(savedInstanceState != null) {
            title = savedInstanceState.getString(STATE_TITLE);
        }
        progressDialog.setTitle(title);
        return progressDialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_TITLE, title);
    }
}
