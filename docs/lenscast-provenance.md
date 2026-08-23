# LensCast baseline provenance

DarkCat Camera (LensCast Test) is based on:

- Repository: https://github.com/raulshma/lenscast
- Accepted tag: `v0.0.9`
- Accepted commit: `e014fe9808ceef4b9b610be821324e6aeb46721d`
- Vendor ref: `vendor/lenscast-v0.0.9`
- Adoption ref: `agent/lenscast-adoption-baseline`

The imported history and upstream `LICENSE` file are preserved. The Android
test applicationId is isolated as `com.dedtsss.darkcat.lenscast.test` so it
can coexist with the existing DarkCat and stock LensCast installations.

The updater is restricted to the DarkCat `lenscast-test` manifest and cannot
consume the upstream LensCast GitHub release channel. The embedded web UI
remains the upstream LensCast UI; this baseline does not redesign or replace
its camera, capture, settings, or gallery surfaces.
