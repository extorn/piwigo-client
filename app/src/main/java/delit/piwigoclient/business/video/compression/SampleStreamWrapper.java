package delit.piwigoclient.business.video.compression;

import android.os.Build;

import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;

import java.io.IOException;

import androidx.annotation.RequiresApi;

import static com.google.android.exoplayer2.C.RESULT_END_OF_INPUT;
import static com.google.android.exoplayer2.C.RESULT_NOTHING_READ;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class SampleStreamWrapper implements SampleStream {
    private final MediaMuxerControl mediaMuxerControl;
    private SampleStream wrapped;
    private DecoderInputBuffer lastUsedBuffer;

    public SampleStreamWrapper(SampleStream wrapped, MediaMuxerControl mediaMuxerControl) {
        this.wrapped = wrapped;
        this.mediaMuxerControl = mediaMuxerControl;
    }

    @Override
    public boolean isReady() {
        return wrapped.isReady();
    }

    @Override
    public void maybeThrowError() throws IOException {
        wrapped.maybeThrowError();
    }

    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        lastUsedBuffer = buffer;
        if (isMediaMuxerBeingConfigured(mediaMuxerControl)) {
            return RESULT_NOTHING_READ;
        }
        int readResult = wrapped.readData(formatHolder, buffer, formatRequired);
        if (buffer.isEndOfStream()) {
            // force buffer read else the bytes just get lost and the buffer isn't cleared
            return RESULT_END_OF_INPUT;
        }
        return readResult;
    }

    public DecoderInputBuffer getLastUsedBuffer() {
        return lastUsedBuffer;
    }

    public boolean haveReadEndOfStream() {
        return lastUsedBuffer.isEndOfStream();
    }

    abstract boolean isMediaMuxerBeingConfigured(MediaMuxerControl mediaMuxerControl);

    @Override
    public int skipData(long positionUs) {
        return wrapped.skipData(positionUs);
    }
}