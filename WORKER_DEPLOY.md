# Deploy the ClipGenius AI Worker update

1. Open Cloudflare Dashboard.
2. Go to Workers & Pages → `clipgenius-ai` → Edit code.
3. Select all existing Worker source and replace it with the complete contents of `worker.js`.
4. Save and Deploy.
5. Confirm the `AI` binding still exists and is named exactly `AI`.
6. Open the Worker root URL. The response should include `"version":"0.2.0"`.

This update adds timestamp-aware transcription output, sectioned long-transcript analysis, up to 12 ranked suggestions, and diagnostic information when a model call returns an empty or invalid response.
