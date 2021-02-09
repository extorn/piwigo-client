package delit.libs.util.progress;

import androidx.annotation.FloatRange;

public interface ProgressListener {
    void onProgress(@FloatRange(from = 0, to = 1) double percent);
    @FloatRange(from = 0, to = 1) double getUpdateStep();
}