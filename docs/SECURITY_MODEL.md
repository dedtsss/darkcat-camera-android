# Security model

## Protected material

- Primary photo/video ciphertext lives in `<app filesDir>/vault/` under random UUID-derived `.dcv` filenames.
- Thumbnails are separately AES-GCM encrypted in `<app filesDir>/vault-thumbnails/`.
- Metadata lives in the app-private SQLite database. It is not in EXIF by default and is not visible to ordinary gallery applications.
- The master key is a non-exportable Android Keystore AES-256 key alias `darkcat.camera.vault.v1`. The key is created on first use and is not stored as bytes in SharedPreferences or the database.
- Every file has a fresh random 12-byte GCM IV and a 128-bit authentication tag. The header is `DCV1 || IV || ciphertext+tag`; a tampered/truncated file fails authenticated decryption.

## Temporary plaintext

CameraX and the video recorder require a file output. Those files are created only below the app-private cache directory `cache/capture-temp/`. FAST photo/video import encrypts them and then deletes them. EDIT keeps the capture file only while the editor is open; save encrypts the final JPEG and deletes the source, while cancel deletes it. Viewing an encrypted photo for editing creates a short-lived private decrypted file, which the editor deletes after successful save/cancel.

`TempFiles.cleanupStale` runs on application/repository startup and removes capture-temp files older than 24 hours. This mitigates crash/process-death leftovers but cannot guarantee immediate deletion after a device loses power. A future hardened build should add a startup journal and test device-specific recovery paths.

## Upload integrity

The checksum recorded in SQLite is SHA-256 of the complete encrypted file. The uploader gets the checksum and a stable media UUID as its idempotency key. A local file is never deleted because an HTTP call merely returned without throwing: the future server adapter must return a checksum-confirmed `UploadReceipt`. The worker transitions through `UPLOADED` and `VERIFIED`; local deletion is not enabled by default.

## Threat assumptions

The model protects against ordinary gallery/file-browser exposure, accidental cloud upload of plaintext and undetected ciphertext corruption. It assumes the Android OS and Keystore are not compromised and that the app process is not being actively instrumented on an unlocked/rooted device. It does not provide deniable storage, secure deletion from flash wear-levelled media, protection from screenshots/screen recording, or server-side access control.

## Currently not protected / not complete

- The SQLite metadata DB is app-private but not SQLCipher-encrypted in the MVP; GPS and CRM identifiers therefore depend on Android app-sandbox protection.
- The Keystore key is not user-authentication-bound, so WorkManager can upload after reboot without an interactive unlock. A product decision is needed before enabling biometric/device-credential gating.
- Video playback through an authenticated streaming decryptor is not implemented; the gallery shows encrypted-video metadata in this slice.
- The fake uploader does not provide network transport, server authentication, resumable multipart upload or server retention policy.
- Android debug builds, logs and crash reporting must be audited before production because captured paths/metadata must not be logged.
