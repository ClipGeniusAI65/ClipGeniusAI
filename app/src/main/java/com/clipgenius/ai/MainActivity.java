package com.clipgenius.ai;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 1001;
    private Uri selectedUri;
    private long durationMs = 0;
    private VideoView videoView;
    private TextView status;
    private Spinner durationSpinner;
    private Button exportButton;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void buildScreen() {
        int bg = 0xFF10111A;
        int panel = 0xFF1D2030;
        int white = 0xFFF7F7FB;
        int muted = 0xFFB7B9C8;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        root.setBackgroundColor(bg);

        TextView brand = text("✂  ClipGenius AI", 25, white);
        brand.setTypeface(null, 1);
        root.addView(brand);

        TextView subtitle = text("Turn long videos into short clips", 14, muted);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        root.addView(subtitle, subParams);

        Button choose = new Button(this);
        choose.setText("＋  Choose video");
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(54));
        chooseParams.topMargin = dp(24);
        root.addView(choose, chooseParams);
        choose.setOnClickListener(v -> pickVideo());

        videoView = new VideoView(this);
        videoView.setBackgroundColor(0xFF000000);
        LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(210));
        videoParams.topMargin = dp(16);
        root.addView(videoView, videoParams);

        TextView durationLabel = text("Clip duration", 16, white);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(22);
        root.addView(durationLabel, labelParams);

        durationSpinner = new Spinner(this);
        String[] durations = {"15 seconds", "30 seconds", "45 seconds", "60 seconds"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, durations);
        durationSpinner.setAdapter(adapter);
        durationSpinner.setSelection(1);
        root.addView(durationSpinner);

        TextView note = text("MVP: exports the opening segment of your selected video. AI moment detection and captions will be added in the next version.", 13, muted);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(16);
        root.addView(note, noteParams);

        exportButton = new Button(this);
        exportButton.setText("Export MP4 clip");
        exportButton.setEnabled(false);
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(-1, dp(54));
        exportParams.topMargin = dp(22);
        root.addView(exportButton, exportParams);
        exportButton.setOnClickListener(v -> exportClip());

        status = text("Choose a video to get started.", 13, muted);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(18);
        root.addView(status, statusParams);

        setContentView(root);
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            selectedUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                    selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, selectedUri);
                String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                durationMs = d == null ? 0 : Long.parseLong(d);
                retriever.release();
                videoView.setVideoURI(selectedUri);
                videoView.setOnPreparedListener(mp -> mp.setOnVideoSizeChangedListener((m,w,h) -> {}));
                videoView.start();
                exportButton.setEnabled(true);
                status.setText("Video ready · " + formatTime(durationMs));
            } catch (Exception e) {
                status.setText("Could not open this video: " + e.getMessage());
                exportButton.setEnabled(false);
            }
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void exportClip() {
        if (selectedUri == null) return;
        int[] seconds = {15, 30, 45, 60};
        final int requested = seconds[durationSpinner.getSelectedItemPosition()];
        final long endUs = Math.min(durationMs, requested * 1000L) * 1000L;
        if (endUs <= 0) {
            status.setText("Video duration could not be read.");
            return;
        }
        exportButton.setEnabled(false);
        status.setText("Exporting… keep the app open.");
        new Thread(() -> {
            String result;
            try {
                exportFirstSegment(endUs);
                result = "Saved to Movies/ClipGeniusAI";
            } catch (Exception e) {
                result = "Export failed: " + e.getMessage();
            }
            final String message = result;
            mainHandler.post(() -> {
                status.setText(message);
                exportButton.setEnabled(true);
            });
        }).start();
    }

    // Basic stream-copy trim. It keeps the original video/audio tracks and does not add captions.
    private void exportFirstSegment(long endUs) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(this, selectedUri, null);

        ArrayList<Integer> sourceTracks = new ArrayList<>();
        ArrayList<Integer> outputTracks = new ArrayList<>();
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                sourceTracks.add(i);
            }
        }
        if (sourceTracks.isEmpty()) {
            extractor.release();
            throw new Exception("No audio or video tracks found.");
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "ClipGenius_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ClipGeniusAI");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        Uri outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) {
            extractor.release();
            throw new Exception("Could not create output file.");
        }

        MediaMuxer muxer = null;
        try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(outputUri, "w")) {
            if (pfd == null) throw new Exception("Could not open output file.");
            FileDescriptor fd = pfd.getFileDescriptor();
            muxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int[] trackMap = new int[extractor.getTrackCount()];
            for (int i = 0; i < trackMap.length; i++) trackMap[i] = -1;
            for (int sourceTrack : sourceTracks) {
                int out = muxer.addTrack(extractor.getTrackFormat(sourceTrack));
                trackMap[sourceTrack] = out;
                extractor.selectTrack(sourceTrack);
            }
            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            while (true) {
                int track = extractor.getSampleTrackIndex();
                if (track < 0) break;
                long time = extractor.getSampleTime();
                if (time < 0 || time >= endUs) break;
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = time;
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(trackMap[track], buffer, info);
                extractor.advance();
            }
            muxer.stop();
            muxer.release();
            muxer = null;
        } finally {
            if (muxer != null) {
                try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            extractor.release();
        }

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(outputUri, done, null, null);
        }
    }
}
