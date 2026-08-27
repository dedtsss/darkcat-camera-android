# DarkCat lenscast-test signing

The publishable `com.dedtsss.darkcat.lenscast.test` artifact is assembled
unsigned by CI. Publication must use the dedicated Bruce-owned keystore stored
outside the repository, then verify the certificate fingerprint before upload.

Expected signer fingerprint: `5A002D0F1D84849DD02A7ECD24B940BC8412E0CD762182EE0958C5B17D63CE5D`

The `.67` artifact was recovered for inspection (version `0.0.9-test.67`,
versionCode `100067`, commit `fa2588627b7db150d665b07d49a1d4efe700c5d9`) and
was signed by `14c4b8d44247a6cef46299ee67daa07b351c658ef1f0b1657ecd666517a61f28`.
The current `.75`/Bruce candidate signer was
`0df0c48305870367857515520e5ac61b780d7f33f63203e8598228dca613ae97`.
The exact `.67` private key was not recovered; Path 2 therefore requires one
clean uninstall/reinstall migration. App-private settings/data may be lost;
photos saved to shared MediaStore are separate from app-private storage.

Bruce storage locations (private files mode 600, directories mode 700):

- `/etc/bruce/codex/darkcat-lenscast-signing`
- `/srv/darkcat-signing-backup/lenscast-test`

The matching keystore and password file are retained in both locations above;
the backup is not used for routine signing. Credentials are read by the
Bruce-only signing wrapper and are never placed in Git, CI artifacts, logs, or
the publication manifest.
