# Diagnostics and update boundary

The capture service records a bounded local event log without image bytes,
credentials, or tokens. A diagnostic ZIP contains that log plus bounded
`diagnostics-metadata.txt`: versionName/versionCode, Git SHA, build timestamp,
device and Android, permission/configuration status, runtime status, and
available Android process-exit reasons. Endpoint values are never included.
Both transport URLs are HTTPS-only build configuration from GitHub repository
variables; an empty URL fails closed. The client contains no deployment
credential.

The update check accepts only a numeric `versionCode` newer than the installed
BuildConfig value. It resolves the manifest `apk` relative to `latest.json`
and exposes Download APK only when the final URL is HTTPS. Tagged cloud builds
create `latest.json` and `SHA256SUMS`; this is not evidence of physical-device
acceptance. Tagged GitHub Actions builds publish the APK, `latest.json`, and
`SHA256SUMS` to Bruce over SFTP using CI-held GitHub secrets.

The branch-scoped test channel uses `versionName` `0.1.0-test.<run_number>` and
monotonic `versionCode` `100000 + run_number`. Its manifest includes
`channel: "test"`, build/publication timestamps, and the permanent HTTPS APK
URL. The successful Actions artifact is consumed by the Bruce TEST publisher,
which validates the SHA-256, preserves a versioned archive, and atomically
switches the permanent `/test/` pointer only after the complete set is ready.
