# Create the release keystore and add GitHub Actions secrets from an Android phone

A keystore is the permanent signing identity for this app. Keep at least two secure backups. Never commit the `.jks` file, passwords, or Base64 text into the repository. Losing the key can prevent updates to an app distributed outside Play App Signing.

## 1. Generate a keystore in Termux

Make sure Java and `keytool` are installed, then run:

```sh
mkdir -p ~/storage/downloads
keytool -genkeypair -v \
  -keystore ~/storage/downloads/sdstudio-release.jks \
  -alias sdstudio \
  -keyalg RSA -keysize 4096 -validity 10000
```

Choose strong keystore and key passwords. Record both securely. The alias in this example is `sdstudio`; use the exact same alias in GitHub. Enter the developer identity details accurately when prompted.

## 2. Create the Base64 value

```sh
base64 ~/storage/downloads/sdstudio-release.jks | tr -d '\n' > ~/storage/downloads/sdstudio-keystore-base64.txt
```

Open `sdstudio-keystore-base64.txt` in a text editor, select all, and copy the complete one-line value. Do not paste it into a chat or repository file.

## 3. Add four GitHub repository secrets

1. Open your SD Studio GitHub repository in your phone browser.
2. Open **Settings → Secrets and variables → Actions**.
3. Tap **New repository secret** for each of these, then save it.

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Entire Base64 line from step 2 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `sdstudio` (or the exact alias you selected) |
| `ANDROID_KEY_PASSWORD` | Key password |

GitHub does not show secret values after saving. Double-check the names and values before starting the workflow.

## 4. Build the signed AAB

1. Open the repository's **Actions** tab.
2. Select **Signed Android App Bundle**.
3. Tap **Run workflow** and select `main`.
4. Wait for the workflow to complete successfully.
5. Open the run and download the `sd-studio-release-aab` artifact.

The workflow also runs when you push a version tag such as `v1.1.0`. The AAB is an Actions artifact; it is not automatically published to Google Play.

## 5. Keep the keystore safe

Keep the original JKS in a secure offline backup and a second encrypted backup. Do not rotate the key casually. CI decodes the keystore into the runner's temporary directory and does not add it to the repository.
