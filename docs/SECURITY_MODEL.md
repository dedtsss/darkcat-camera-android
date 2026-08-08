# Security model

- Vault media is encrypted with AES-256-GCM, with a fresh random 12-byte IV per file and an authentication tag verified on decrypt.
- The master key is generated and retained by Android Keystore; it is never serialized into preferences or logs.
- Encrypted media and thumbnails use random UUID filenames under app-private internal storage.
- Credential values use a separate Keystore-backed AES-GCM key. Passwords/tokens are not logged or stored in ordinary SharedPreferences.
- `allowBackup="false"` is retained and Vault/Viewer/Editor activities set `FLAG_SECURE` to reduce screenshots and recents exposure.
- The only plaintext recovery material is in the app-private `recovery-pending` directory. It is not uploaded or TTL-cleaned automatically. It is removed only after a successful vault commit, or by an explicit user action added later.
- Failed encryption, database commit, rename, disk-full and interrupted-process paths leave recovery material. A failed upload never deletes the encrypted local vault by default.
- Generic WebDAV uses PUT and HEAD verification. HTTP 2xx alone is not treated as permission to delete local vault material.
- Default retention is KEEP LOCAL. Verified auto-delete is opt-in.

Known boundary: Android 5.0/5.1 devices remain supported by the imported camera core, but Android Keystore AES-GCM secure storage requires API 23; Secure Mode therefore defaults off on those old devices and must not be represented as a tested secure-vault path there.
