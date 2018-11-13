package delit.piwigoclient.ui.common;

import android.content.Context;
import androidx.annotation.IntRange;
import androidx.annotation.StringRes;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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

    private void init(Context context) {
        addView(inflate(context, R.layout.layout_progress_indicator, null));
        actionButton = findViewById(R.id.progressAction);
        descriptionField = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
    }

    public ProgressIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    public void showProgressIndicator(@StringRes int textResId, @IntRange(from=-1,to=100) int progress) {
        showProgressIndicator(getContext().getString(textResId), progress);
    }

    public void showProgressIndicator(String progressText, @IntRange(from=-1,to=100) int progress) {

        actionButton.setVisibility(View.GONE);

        descriptionField.setText(progressText);

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);

        setVisibility(VISIBLE);
    }

    public void showProgressIndicator(@StringRes int textResId, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {
        showProgressIndicator(getContext().getString(textResId), progress, actionListener);
    }

    public void showProgressIndicator(String progressText, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {

        setVisibility(VISIBLE);

        actionButton.setVisibility(View.VISIBLE);
        actionButton.setOnClickListener(actionListener);

        descriptionField.setText(progressText);

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
