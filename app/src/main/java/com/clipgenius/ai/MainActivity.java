
package com.clipgenius.ai;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
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
import android.util.Base64;
import android.view.Gravity;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 1001;
    private static final long CHUNK_DURATION_US = 30_000_000L;

    private static final String WORKER_URL =
            "https://clipgenius-ai.nikhilm-82686.workers.dev";

    private Uri selectedUri;
    private long durationMs = 0;

    private VideoView videoView;
    private TextView status;
    private Spinner durationSpinner;

    private Button exportButton;
    private Button transcribeButton;
    private Button analyzeButton;

    private EditText transcriptInput;
    private EditText youtubeUrlInput;

    private TextView aiResults;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
    }

    private int dp(float value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
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
        int white = 0xFFF7F7FB;
        int muted = 0xFFB7B9C8;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        root.setBackgroundColor(bg);

        TextView brand = text("✂  ClipGenius AI", 25, white);
        brand.setTypeface(null, 1);
        root.addView(brand);

        TextView subtitle =
                text("Turn long videos into short clips", 14, muted);

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        root.addView(subtitle, subParams);

        Button choose = new Button(this);
        choose.setText("＋  Choose video");

        LinearLayout.LayoutParams chooseParams =
                new LinearLayout.LayoutParams(-1, dp(54));
        chooseParams.topMargin = dp(24);

        root.addView(choose, chooseParams);
        choose.setOnClickListener(v -> pickVideo());

        TextView youtubeHeading =
                text("YouTube Video Link", 18, white);

        LinearLayout.LayoutParams youtubeHeadingParams =
                new LinearLayout.LayoutParams(-1, -2);
        youtubeHeadingParams.topMargin = dp(22);

        root.addView(youtubeHeading, youtubeHeadingParams);

        youtubeUrlInput = new EditText(this);
        youtubeUrlInput.setHint("Paste YouTube video URL here...");
        youtubeUrlInput.setSingleLine(true);
        youtubeUrlInput.setTextColor(white);
        youtubeUrlInput.setHintTextColor(muted);

        root.addView(
                youtubeUrlInput,
                new LinearLayout.LayoutParams(-1, -2)
        );

        Button youtubeCheckButton = new Button(this);
        youtubeCheckButton.setText("Check YouTube Link");

        root.addView(
                youtubeCheckButton,
                new LinearLayout.LayoutParams(-1, dp(50))
        );

        youtubeCheckButton.setOnClickListener(v -> checkYoutubeLink());

        videoView = new VideoView(this);
        videoView.setBackgroundColor(0xFF000000);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(-1, dp(210));
        videoParams.topMargin = dp(16);

        root.addView(videoView, videoParams);

        TextView aiHeading =
                text("✨ AI Clip Detection", 18, white);

        LinearLayout.LayoutParams aiHeadingParams =
                new LinearLayout.LayoutParams(-1, -2);
        aiHeadingParams.topMargin = dp(20);

        root.addView(aiHeading, aiHeadingParams);

        transcriptInput = new EditText(this);
        transcriptInput.setHint(
                "Your Hindi or English transcript will appear here..."
        );
        transcriptInput.setTextColor(white);
        transcriptInput.setHintTextColor(muted);
        transcriptInput.setMinLines(4);
        transcriptInput.setGravity(Gravity.TOP);
        transcriptInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );

        LinearLayout.LayoutParams transcriptParams =
                new LinearLayout.LayoutParams(-1, -2);
        transcriptParams.topMargin = dp(10);

        root.addView(transcriptInput, transcriptParams);

        transcribeButton = new Button(this);
        transcribeButton.setText("Transcribe Video");
        transcribeButton.setEnabled(false);

        LinearLayout.LayoutParams transcribeParams =
                new LinearLayout.LayoutParams(-1, dp(50));
        transcribeParams.topMargin = dp(10);

        root.addView(transcribeButton, transcribeParams);

        transcribeButton.setOnClickListener(v -> transcribeVideo());

        analyzeButton = new Button(this);
        analyzeButton.setText("Analyze with AI");

        LinearLayout.LayoutParams analyzeParams =
                new LinearLayout.LayoutParams(-1, dp(50));
        analyzeParams.topMargin = dp(10);

        root.addView(analyzeButton, analyzeParams);

        analyzeButton.setOnClickListener(v -> analyzeTranscript());

        aiResults = text(
                "AI clip suggestions will appear here.",
                14,
                muted
        );

        LinearLayout.LayoutParams resultsParams =
                new LinearLayout.LayoutParams(-1, -2);
        resultsParams.topMargin = dp(12);

        root.addView(aiResults, resultsParams);

        TextView durationLabel = text("Clip duration", 16, white);

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(22);

        root.addView(durationLabel, labelParams);

        durationSpinner = new Spinner(this);

        String[] durations = {
                "15 seconds",
                "30 seconds",
                "45 seconds",
                "60 seconds"
        };

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        durations
                );

        durationSpinner.setAdapter(adapter);
        durationSpinner.setSelection(1);

        root.addView(durationSpinner);

        TextView note = text(
                "Current export creates an opening segment. "
                        + "AI timestamps and captions are not yet applied "
                        + "to exported clips.",
                13,
                muted
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(16);

        root.addView(note, noteParams);

        exportButton = new Button(this);
        exportButton.setText("Export MP4 clip");
        exportButton.setEnabled(false);

        LinearLayout.LayoutParams exportParams =
                new LinearLayout.LayoutParams(-1, dp(54));
        exportParams.topMargin = dp(22);

        root.addView(exportButton, exportParams);

        exportButton.setOnClickListener(v -> exportClip());

        status = text("Choose a video to get started.", 13, muted);

        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(18);

        root.addView(status, statusParams);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    // =====================================
    // YOUTUBE LINK CHECK
    // =====================================

    private void checkYoutubeLink() {

        String link = youtubeUrlInput.getText().toString().trim();

        if (link.isEmpty()) {
            youtubeUrlInput.setError("Please paste a YouTube link.");
            return;
        }

        try {
            Uri uri = Uri.parse(link);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            boolean valid = host != null &&
                    (
                            host.equalsIgnoreCase("youtu.be") ||
                            host.equalsIgnoreCase("youtube.com") ||
                            host.endsWith(".youtube.com")
                    );

            if (valid &&
                    scheme != null &&
                    (
                            scheme.equalsIgnoreCase("https") ||
                            scheme.equalsIgnoreCase("http")
                    )) {

                status.setText(
                        "YouTube link format is valid. "
                                + "Import your authorized video file "
                                + "using Choose video."
                );

            } else {
                youtubeUrlInput.setError(
                        "Please enter a valid YouTube URL."
                );
            }

        } catch (Exception e) {
            youtubeUrlInput.setError("Invalid YouTube URL.");
        }
    }

    // =====================================
    // VIDEO SELECTION
    // =====================================

    private void pickVideo() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");

        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO &&
                resultCode == RESULT_OK &&
                data != null) {

            selectedUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        selectedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }

            try {
                MediaMetadataRetriever retriever =
                        new MediaMetadataRetriever();

                retriever.setDataSource(this, selectedUri);

                String d = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                );

                durationMs = d == null
                        ? 0
                        : Long.parseLong(d);

                retriever.release();

                videoView.setVideoURI(selectedUri);
                videoView.start();

                exportButton.setEnabled(true);
                transcribeButton.setEnabled(true);

                status.setText(
                        "Video ready · " + formatTime(durationMs)
                );

            } catch (Exception e) {

                status.setText(
                        "Could not open this video: " + e.getMessage()
                );

                exportButton.setEnabled(false);
                transcribeButton.setEnabled(false);
            }
        }
    }

    private String formatTime(long ms) {

        long seconds = ms / 1000;

        return String.format(
                Locale.US,
                "%02d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    // =====================================
    // AUTOMATIC VIDEO TRANSCRIPTION
    // =====================================

    private void transcribeVideo() {

        if (selectedUri == null) {
            status.setText("Please choose a video first.");
            return;
        }

        if (durationMs <= 0) {
            status.setText("Video duration could not be read.");
            return;
        }

        transcribeButton.setEnabled(false);
        analyzeButton.setEnabled(false);

        transcriptInput.setText("");
        aiResults.setText("Waiting for transcription...");

        status.setText("Preparing video audio...");

        new Thread(() -> {

            StringBuilder fullTranscript = new StringBuilder();

            try {

                long totalUs = durationMs * 1000L;

                int totalChunks = (int) (
                        (totalUs + CHUNK_DURATION_US - 1)
                                / CHUNK_DURATION_US
                );

                for (int i = 0; i < totalChunks; i++) {

                    long startUs = i * CHUNK_DURATION_US;

                    long endUs = Math.min(
                            totalUs,
                            startUs + CHUNK_DURATION_US
                    );

                    final int part = i + 1;
                    final int total = totalChunks;

                    mainHandler.post(() ->
                            status.setText(
                                    "Processing audio part "
                                            + part + " of " + total
                            )
                    );

                    File audioFile = extractAudioChunk(
                            startUs,
                            endUs
                    );

                    try {

                        String transcript =
                                sendAudioToWorker(audioFile);

                        if (!transcript.trim().isEmpty()) {

                            if (fullTranscript.length() > 0) {
                                fullTranscript.append("\n");
                            }

                            fullTranscript.append(transcript.trim());
                        }

                    } finally {
                        if (audioFile.exists()) {
                            audioFile.delete();
                        }
                    }
                }

                String finalText = fullTranscript.toString().trim();

                mainHandler.post(() -> {

                    transcriptInput.setText(finalText);

                    if (finalText.isEmpty()) {

                        status.setText(
                                "No speech detected in this video."
                        );

                    } else {

                        status.setText(
                                "Transcription complete. "
                                        + "Now press Analyze with AI."
                        );
                    }

                    transcribeButton.setEnabled(true);
                    analyzeButton.setEnabled(true);
                });

            } catch (Exception e) {

                String error = e.getMessage() == null
                        ? "Unknown error"
                        : e.getMessage();

                mainHandler.post(() -> {

                    status.setText(
                            "Transcription failed: " + error
                    );

                    transcribeButton.setEnabled(true);
                    analyzeButton.setEnabled(true);
                });
            }

        }).start();
    }

    // =====================================
    // EXTRACT AUDIO CHUNK FROM VIDEO
    // =====================================

    private File extractAudioChunk(
            long startUs,
            long endUs
    ) throws Exception {

        MediaExtractor extractor = new MediaExtractor();

        MediaMuxer muxer = null;
        boolean muxerStarted = false;

        File outputFile = File.createTempFile(
                "clipgen_audio_",
                ".m4a",
                getCacheDir()
        );

        try {

            extractor.setDataSource(
                    this,
                    selectedUri,
                    null
            );

            int audioTrack = -1;
            MediaFormat audioFormat = null;

            for (int i = 0; i < extractor.getTrackCount(); i++) {

                MediaFormat format = extractor.getTrackFormat(i);

                String mime = format.getString(
                        MediaFormat.KEY_MIME
                );

                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    audioFormat = format;
                    break;
                }
            }

            if (audioTrack < 0 || audioFormat == null) {
                throw new Exception(
                        "No audio track found in this video."
                );
            }

            muxer = new MediaMuxer(
                    outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            );

            int outputTrack = muxer.addTrack(audioFormat);

            muxer.start();
            muxerStarted = true;

            extractor.selectTrack(audioTrack);

            extractor.seekTo(
                    startUs,
                    MediaExtractor.SEEK_TO_PREVIOUS_SYNC
            );

            ByteBuffer buffer = ByteBuffer.allocate(
                    4 * 1024 * 1024
            );

            android.media.MediaCodec.BufferInfo info =
                    new android.media.MediaCodec.BufferInfo();

            int sampleCount = 0;

            while (true) {

                int currentTrack = extractor.getSampleTrackIndex();

                if (currentTrack < 0) {
                    break;
                }

                long sampleTime = extractor.getSampleTime();

                if (sampleTime < 0 || sampleTime >= endUs) {
                    break;
                }

                if (sampleTime < startUs) {
                    if (!extractor.advance()) {
                        break;
                    }
                    continue;
                }

                buffer.clear();

                int size = extractor.readSampleData(buffer, 0);

                if (size < 0) {
                    break;
                }

                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = sampleTime - startUs;
                info.flags = extractor.getSampleFlags();

                muxer.writeSampleData(
                        outputTrack,
                        buffer,
                        info
                );

                sampleCount++;

                if (!extractor.advance()) {
                    break;
                }
            }

            if (sampleCount == 0) {
                throw new Exception(
                        "No audio samples found in this part."
                );
            }

            muxer.stop();
            muxerStarted = false;

            muxer.release();
            muxer = null;

            return outputFile;

        } catch (Exception e) {

            if (outputFile.exists()) {
                outputFile.delete();
            }

            throw e;

        } finally {

            if (muxer != null) {

                if (muxerStarted) {
                    try {
                        muxer.stop();
                    } catch (Exception ignored) {
                    }
                }

                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }

            extractor.release();
        }
    }

    // =====================================
    // SEND AUDIO TO CLOUDFLARE WORKER
    // =====================================

    private String sendAudioToWorker(File audioFile)
            throws Exception {

        ByteArrayOutputStream byteStream =
                new ByteArrayOutputStream();

        try (FileInputStream fileInput =
                     new FileInputStream(audioFile)) {

            byte[] buffer = new byte[8192];

            int count;

            while ((count = fileInput.read(buffer)) != -1) {
                byteStream.write(buffer, 0, count);
            }
        }

        byte[] audioBytes = byteStream.toByteArray();

        String base64Audio = Base64.encodeToString(
                audioBytes,
                Base64.NO_WRAP
        );

        HttpURLConnection conn = null;

        try {

            URL url = new URL(
                    WORKER_URL + "/transcribe"
            );

            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setDoOutput(true);

            conn.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            JSONObject payload = new JSONObject();
            payload.put("audio", base64Audio);

            byte[] requestBytes = payload.toString().getBytes(
                    StandardCharsets.UTF_8
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
            }

            int code = conn.getResponseCode();

            InputStream stream = code >= 200 && code < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (stream == null) {
                throw new Exception(
                        "Server returned HTTP " + code
                );
            }

            StringBuilder responseText = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            stream,
                            StandardCharsets.UTF_8
                    )
            )) {

                String line;

                while ((line = reader.readLine()) != null) {
                    responseText.append(line);
                }
            }

            JSONObject response = new JSONObject(
                    responseText.toString()
            );

            if (code < 200 || code >= 300) {
                throw new Exception(
                        response.optString(
                                "details",
                                response.optString(
                                        "error",
                                        "Server error " + code
                                )
                        )
                );
            }

            if (!response.optBoolean("success", false)) {
                throw new Exception(
                        response.optString(
                                "error",
                                "Transcription failed"
                        )
                );
            }

            return response.optString("text", "");

        } finally {

            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // =====================================
    // AI CLIP ANALYSIS
    // =====================================

    private void analyzeTranscript() {

        String transcript = transcriptInput
                .getText()
                .toString()
                .trim();

        if (transcript.length() < 20) {

            transcriptInput.setError(
                    "Please enter at least 20 characters."
            );

            return;
        }

        analyzeButton.setEnabled(false);
        aiResults.setText("AI is analyzing your transcript...");

        new Thread(() -> {

            HttpURLConnection conn = null;

            try {

                URL url = new URL(
                        WORKER_URL + "/analyze"
                );

                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);

                conn.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                JSONObject payload = new JSONObject();
                payload.put("transcript", transcript);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(
                            payload.toString().getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
                }

                int code = conn.getResponseCode();

                InputStream stream = code >= 200 && code < 300
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                if (stream == null) {
                    throw new Exception(
                            "Server returned HTTP " + code
                    );
                }

                StringBuilder responseText = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                        )
                )) {

                    String line;

                    while ((line = reader.readLine()) != null) {
                        responseText.append(line);
                    }
                }

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "Server error: " + responseText
                    );
                }

                JSONObject response = new JSONObject(
                        responseText.toString()
                );

                if (!response.optBoolean("success", false)) {
                    throw new Exception(
                            response.optString(
                                    "error",
                                    "AI analysis failed"
                            )
                    );
                }

                JSONArray clips = response.getJSONArray("clips");

                StringBuilder result = new StringBuilder();

                for (int i = 0; i < clips.length(); i++) {

                    JSONObject clip = clips.getJSONObject(i);

                    result.append("\nClip ")
                            .append(i + 1)
                            .append("\n");

                    result.append("Title: ")
                            .append(clip.optString("title"))
                            .append("\n");

                    result.append("Start: ")
                            .append(clip.optString("start_hint"))
                            .append("\n");

                    result.append("End: ")
                            .append(clip.optString("end_hint"))
                            .append("\n");

                    result.append("Reason: ")
                            .append(clip.optString("reason"))
                            .append("\n");

                    result.append("Score: ")
                            .append(clip.optInt("score"))
                            .append("/100\n");
                }

                String finalResult = result.length() == 0
                        ? "No clip suggestions found."
                        : result.toString();

                mainHandler.post(() -> {

                    aiResults.setText(finalResult);
                    analyzeButton.setEnabled(true);
                    status.setText("AI analysis complete.");
                });

            } catch (Exception e) {

                String error = "AI error: " + e.getMessage();

                mainHandler.post(() -> {

                    aiResults.setText(error);
                    analyzeButton.setEnabled(true);
                });

            } finally {

                if (conn != null) {
                    conn.disconnect();
                }
            }

        }).start();
    }

    // =====================================
    // VIDEO EXPORT
    // =====================================

    private void exportClip() {

        if (selectedUri == null) {
            return;
        }

        int[] seconds = {15, 30, 45, 60};

        final int requested =
                seconds[durationSpinner.getSelectedItemPosition()];

        final long endUs = Math.min(
                durationMs,
                requested * 1000L
        ) * 1000L;

        if (endUs <= 0) {

            status.setText(
                    "Video duration could not be read."
            );

            return;
        }

        exportButton.setEnabled(false);
        status.setText("Exporting... keep the app open.");

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

    // =====================================
    // EXPORT FIRST VIDEO SEGMENT
    // =====================================

    private void exportFirstSegment(long endUs)
            throws Exception {

        MediaExtractor extractor = new MediaExtractor();

        extractor.setDataSource(
                this,
                selectedUri,
                null
        );

        ArrayList<Integer> sourceTracks = new ArrayList<>();

        for (int i = 0; i < extractor.getTrackCount(); i++) {

            MediaFormat format = extractor.getTrackFormat(i);

            String mime = format.getString(
                    MediaFormat.KEY_MIME
            );

            if (mime != null &&
                    (
                            mime.startsWith("video/") ||
                            mime.startsWith("audio/")
                    )) {

                sourceTracks.add(i);
            }
        }

        if (sourceTracks.isEmpty()) {

            extractor.release();

            throw new Exception(
                    "No audio or video tracks found."
            );
        }

        ContentValues values = new ContentValues();

        values.put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "ClipGenius_" + System.currentTimeMillis() + ".mp4"
        );

        values.put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
        );

        if (Build.VERSION.SDK_INT >= 29) {

            values.put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/ClipGeniusAI"
            );

            values.put(
                    MediaStore.Video.Media.IS_PENDING,
                    1
            );
        }

        Uri outputUri = getContentResolver().insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (outputUri == null) {

            extractor.release();

            throw new Exception(
                    "Could not create output file."
            );
        }

        MediaMuxer muxer = null;

        try (android.os.ParcelFileDescriptor pfd =
                     getContentResolver().openFileDescriptor(
                             outputUri,
                             "w"
                     )) {

            if (pfd == null) {
                throw new Exception(
                        "Could not open output file."
                );
            }

            FileDescriptor fd = pfd.getFileDescriptor();

            muxer = new MediaMuxer(
                    fd,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            );

            int[] trackMap = new int[extractor.getTrackCount()];

            for (int i = 0; i < trackMap.length; i++) {
                trackMap[i] = -1;
            }

            for (int sourceTrack : sourceTracks) {

                int out = muxer.addTrack(
                        extractor.getTrackFormat(sourceTrack)
                );

                trackMap[sourceTrack] = out;

                extractor.selectTrack(sourceTrack);
            }

            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(
                    4 * 1024 * 1024
            );

            android.media.MediaCodec.BufferInfo info =
                    new android.media.MediaCodec.BufferInfo();

            while (true) {

                int track = extractor.getSampleTrackIndex();

                if (track < 0) {
                    break;
                }

                long time = extractor.getSampleTime();

                if (time < 0 || time >= endUs) {
                    break;
                }

                buffer.clear();

                int size = extractor.readSampleData(buffer, 0);

                if (size < 0) {
                    break;
                }

                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = time;
                info.flags = extractor.getSampleFlags();

                muxer.writeSampleData(
                        trackMap[track],
                        buffer,
                        info
                );

                if (!extractor.advance()) {
                    break;
                }
            }

            muxer.stop();
            muxer.release();
            muxer = null;

        } finally {

            if (muxer != null) {

                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }

                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }

            extractor.release();
        }

        if (Build.VERSION.SDK_INT >= 29) {

            ContentValues done = new ContentValues();

            done.put(
                    MediaStore.Video.Media.IS_PENDING,
                    0
            );

            getContentResolver().update(
                    outputUri,
                    done,
                    null,
                    null
            );
        }
    }
}

