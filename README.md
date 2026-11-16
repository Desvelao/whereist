# Locator

Native Android (Kotlin, Jetpack Compose, Room) app to save where a thing is and find it again with one search.
Fully offline: the manifest does not declare `INTERNET`. Everything builds inside Docker; nothing but Docker is
needed on the host.

## Commands

```bash
make image      # build the Docker image (once, and after Dockerfile changes)
make check      # unit tests and lint
make debug      # debug APK
make install    # build and install on the connected phone (see the plan: wireless adb)
make release    # signed APK and AAB, needs signing (below)
make help       # all targets
```

`make` wraps `docker compose run --rm dev ./gradlew ...`. CI runs the same targets with `RUN=` (no Docker wrapper) on GitHub's runner, which installs JDK 17 and the Android SDK
with its own actions instead of using this image, so keep the JDK and SDK versions in the Dockerfile and
`.github/workflows/release.yml` in step. `make check-release` runs release lint and R8 shrinking without signing.

## App parameters

The name, id and URL are build parameters; the defaults are neutral. Precedence: `-P` Gradle property, then
environment variable, then the tracked `app.properties`, then the default.

| Parameter | `app.properties` | Default |
|---|---|---|
| `APP_NAME` | `WhereIst` | `Locator` |
| `APP_ID` | `io.github.desvelao.whereisit` | `app.example.locator` |
| `APP_URL` | `https://github.com/Desvelao/whereist` | `https://example.com` |
| `APP_VERSION_NAME` / `APP_VERSION_CODE` | not set (release workflow) | `0.1.0` / `1` |

```bash
APP_NAME="My App" APP_ID=com.example.myapp make debug
```

## Release signing

Create a keystore inside the container (kept out of git) and `keystore.properties`:

```bash
docker compose run --rm dev bash -c '
  PW=$(head -c 24 /dev/urandom | base64 | tr -dc A-Za-z0-9 | head -c 24)
  keytool -genkeypair -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=Release" -storepass "$PW" -keypass "$PW"
  printf "storeFile=release.jks\nstorePassword=%s\nkeyAlias=release\nkeyPassword=%s\n" "$PW" "$PW" > keystore.properties'
```

Or pass `SIGNING_KEYSTORE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` as environment
variables. A release build without signing details fails on purpose. **Back up `release.jks` and
`keystore.properties` outside the repo**: losing the keystore means installed copies can never be updated.

Outputs: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`
and the R8 mapping in `app/build/outputs/mapping/release/mapping.txt`.

## Releases

`.github/workflows/release.yml` tests, signs and builds the app on GitHub and publishes a GitHub Release.

1. Add repository secrets: `SIGNING_KEYSTORE_BASE64` (`base64 -w0 release.jks`), `SIGNING_STORE_PASSWORD`,
   `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
2. Move the `Unreleased` entries in `CHANGELOG.md` under a new `## [x.y.z] - date` heading.
3. Tag and push: `git tag v1.2.3 && git push origin v1.2.3`. Tags `v1.2.3-rc.1`, `-beta.N`, `-alpha.N` create a
   GitHub pre-release.

Release tags use `versionCode = major*10000 + minor*100 + patch` and `versionName` `1.2.3+<sha>`. Manual
(`workflow_dispatch`) runs use `github.run_number` as code and `<version>-dev+<sha>` as name, and only upload
a workflow artifact.

## Download and verify

Each release on the GitHub Releases page has the APK, the AAB, the R8 mapping file and a `SHA256SUMS` file. The
release notes also list the SHA-256 digest of the signing certificate.

1. Download the APK you want and `SHA256SUMS` into the same folder.
2. Check the file against the checksums. Only the file you downloaded is checked:

   ```bash
   sha256sum --check --ignore-missing SHA256SUMS      # Linux
   shasum -a 256 --check --ignore-missing SHA256SUMS  # macOS
   ```

   Each line must say `OK`. On Windows run `certutil -hashfile <file>.apk SHA256` and compare the result with
   the line for that file in `SHA256SUMS`. If it does not match, delete the file and download it again.
3. Optional, stronger: check who signed it. The checksum only proves the file was not damaged or changed after
   it was published; the signature proves it comes from the project's key. With the Android SDK build-tools:

   ```bash
   apksigner verify --print-certs <file>.apk | grep 'certificate SHA-256 digest'
   ```

   The digest must equal the one in the release notes. Keep the first digest you trust: a later release signed
   by a different key is not an update from the same author.

Uninstall a debug build before installing a release build, because their signatures differ.

## License

MIT, see `LICENSE`.
