package delit.libs.ui.view;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import delit.piwigoclient.R;

public class ProgressIndicator extends FrameLayout {
    private Button actionButton;
    private TextView descriptionField;
    private ProgressBar progressBar;

    public ProgressIndicator(Context context) {
        super(context);
        init(context);
    }

    public ProgressIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ProgressIndicator(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ProgressIndicator(Context context, AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        addView(inflate(context, R.layout.layout_progress_indicator, null));
        actionButton = findViewById(R.id.progressAction);
        descriptionField = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
    }
    
    public void showProgressIndicator(@StringRes int textResId, @IntRange(from=-1,to=100) int progress) {
        showProgressIndicator(getContext().getString(textResId), progress);
    }

    public void showProgressIndicator(@IntRange(from=-1,to=100) int progress) {
        showProgressIndicator(null, progress);
    }

    public void hideProgressIndicator() {
        setVisibility(GONE);
    }

    public void showProgressIndicator(@Nullable String progressText, @IntRange(from=-1,to=100) int progress) {

        actionButton.setVisibility(View.GONE);

        if(progressText != null) {
            descriptionField.setText(progressText);
            descriptionField.setVisibility(VISIBLE);
        } else {
            descriptionField.setVisibility(GONE);
        }

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);

        setVisibility(VISIBLE);
    }

    public void showProgressIndicator(@StringRes int textResId, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {
        showProgressIndicator(getContext().getString(textResId), progress, actionListener);
    }

    public void showProgressIndicator(@Nullable String progressText, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {

        setVisibility(VISIBLE);

        actionButton.setVisibility(View.VISIBLE);
        actionButton.setOnClickListener(actionListener);

        if(progressText != null) {
            descriptionField.setText(progressText);
            descriptionField.setVisibility(VISIBLE);
        } else {
            descriptionField.setVisibility(GONE);
        }

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);
    }

    public void updateProgressIndicator(@IntRange(from=0,to=100) int progress) {

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);
    }

    public void updateProgressIndicator(@StringRes int textResId, @IntRange(from=0,to=100) int progress) {
        showProgressIndicator(getContext().getString(textResId), progress);
    }

    public void updateProgressIndicator(String progressText, @IntRange(from=0,to=100) int progress) {

        descriptionField.setText(progressText);

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);
    }
}
