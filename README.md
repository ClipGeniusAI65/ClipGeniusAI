# ClipGenius AI — Android MVP 0.1.0

This starter project is a native Android app written in Java. It lets you choose a video, preview it, select 15/30/45/60 seconds, and export the opening segment as an MP4 in `Movies/ClipGeniusAI`.

## Important MVP limitations
- This is a video import + basic trim starter, not the AI viral clip detector yet.
- It exports the opening segment, not automatically detected highlights.
- Stream-copy trimming may start/end on codec keyframe boundaries and may not work for every unusual or damaged media file.
- It does not yet crop to 9:16, generate captions, or process YouTube URLs.
- Keep the app open during export. Very large or high-bitrate videos may exceed device memory limits.

## Build from a phone using GitHub Actions
1. Create a GitHub repository named `ClipGeniusAI`.
2. Upload all files and folders from this project to the repository root.
3. Open the repository's **Actions** tab.
4. If prompted, enable Actions.
5. Select **Build ClipGenius AI APK** and tap **Run workflow**.
6. Open the completed workflow run, scroll to **Artifacts**, and download `ClipGeniusAI-debug-apk`.
7. Extract the ZIP and install `app-debug.apk`. Allow your browser/file manager to install unknown apps if Android asks.

## Build locally
Requires JDK 17 and Gradle 8.9:
```bash
gradle assembleDebug
```
APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Package
Application ID: `com.clipgenius.ai`
Version: `0.1.0`
Minimum Android: 8.0 (API 26)
Target SDK: 35


## Version 0.2.1 update

- Uses Whisper segment timestamps when available and offsets them by each 30-second audio chunk.
- Stops transcription and reports an error if any audio chunk cannot be extracted instead of silently skipping it.
- Sends timestamped transcript text to the Worker analysis endpoint.
- Displays timestamped AI suggestions with individual export actions.
- Exports the suggested time range instead of always exporting from the beginning.

Important limitations: timestamp precision depends on Whisper segment data and source media keyframes. This version does not yet implement 9:16 re-encoding/cropping, burned-in subtitles, batch export, or arbitrary YouTube video downloading.
