# TubeSave Pro V3.1 — Android source (development build)

This project extends the previous Phase 1 starter with resumable HTTPS MP4/WebM downloads, Room progress, WorkManager foreground notifications, MediaStore publishing and Media3 playback. 4K files are downloaded unchanged if the source provides a real 3840×2160 file; **no resolution selection, DASH/HLS assembly, or resolution verification has been implemented**.

## Build from a phone
Upload the *contents* of this folder, including `.github/workflows/build.yml`, to a GitHub repository. Open Actions > Build TubeSave Pro APK > Run workflow. Download the debug APK artifact if CI succeeds. This source has not been compiled in this environment; CI is the build verification step.

## Important limitations
- Direct public HTTPS MP4/WebM/audio responses only, no YouTube extraction, no adaptive playlists.
- Pause/resume uses a private `.part` file and Range/If-Range. Servers without Range support restart the download.
- No separate 4K quality selector; a direct link represents one file.
- No secure DNS pinning/connection-level SSRF mitigation, only hostname/address preflight checks; do not treat it as hardened against DNS rebinding.
- Room schema is upgraded with destructive fallback; existing dev download history may be reset.
- Android background service restrictions and runtime notification permissions require device testing.
- ZIP contains no Gradle wrapper binary. GitHub workflow installs Gradle 8.11.1.
- For >4GB downloads use a destination/filesystem supporting large files.

## Tests needed
GitHub CI build, direct MP4 4K source, HTTP 206 resume, HTTP 200 restart, redirects, storage exhaustion, background execution on Android 14–16, Media3 codec support.
