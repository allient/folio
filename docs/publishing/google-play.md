---
title: Publish on Google Play
description: Step-by-step guide to publishing Folio on Google Play, from the first upload to routine releases.
---

This guide takes Folio from the repository to Google Play. Copy-ready listing text and every form answer are in [Play Console answers](play-console-answers.md). Part 1 is done once. Part 2 is the routine for every new version.

Every step runs on your own computer. GitHub Actions only deploys the website.

## What's already in the repository

| Item | Location |
|---|---|
| Application ID | `com.allient.folio` |
| Store title (25 of 30 characters) | `Folio: Notes & PDF Reader` |
| Short and full description, English and Spanish (Latin America) | `apps/mobile/fastlane/metadata/android/en-US/` and `es-419/` |
| App icon, 512×512 | `…/en-US/images/icon.png` |
| Feature graphic, 1024×500 | `…/en-US/images/featureGraphic.png` |
| Phone screenshots (4, 9:16) | `…/en-US/images/phoneScreenshots/` |
| 7-inch and 10-inch tablet screenshots (7 each, 9:16) | `…/en-US/images/sevenInchScreenshots/`, `tenInchScreenshots/` |
| Release notes | `…/<locale>/changelogs/default.txt` |
| Privacy policy | `https://<your-docs-domain>/privacy/` (source: `apps/docs/src/pages/privacy.astro`) |
| Release signing | Reads `apps/mobile/keystore.properties` |
| Release lanes | `apps/mobile/fastlane/Fastfile`, run through `just` |
| In-app updates | `AppUpdates.kt` |

## Part 1: First release (once)

### Step 1: Install the tools

```bash
brew install fastlane          # or: cd apps/mobile && bundle install, then prefix commands with `bundle exec`
java -version                  # JDK 21
```

You also need the Android SDK (Android Studio installs it) and Bun (`bun install` at the repository root).

### Step 2: Create the upload key

Google Play signs the app that users download with its own key (**Play App Signing**). Your key only signs what you upload, so it's called the **upload key**.

```bash
cd apps/mobile
keytool -genkeypair -v -keystore folio-upload.jks -alias folio-upload -keyalg RSA -keysize 4096 -validity 10000
```

Create `apps/mobile/keystore.properties`:

```properties
storeFile=folio-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=folio-upload
keyPassword=YOUR_KEY_PASSWORD
```

Both files are git-ignored. Back them up in a password manager. If you lose the upload key, Google support can reset it, but that takes a few days.

Check the setup:

```bash
just aab
```

The command must finish and print the path of a signed `androidApp-release.aab`. If it says *"The AAB is not signed"*, check `keystore.properties`.

### Step 2b: Connect Google Analytics (Firebase)

Folio sends anonymous usage statistics through Google Analytics for Firebase (`AppAnalytics.kt`). Until the config file exists, analytics stays inactive and builds still work.

1. Open the [Firebase console](https://console.firebase.google.com) → **Add project** → name it `Folio`. When asked, **enable Google Analytics** and pick or create a Google Analytics account.
2. In the project, choose **Add app → Android**. Package name: `com.allient.folio`. App nickname: `Folio`. You can skip the SHA-1.
3. Download `google-services.json` and save it as `apps/mobile/androidApp/google-services.json`. It's git-ignored: it isn't a secret, but forks of this open-source repository must not send data to your project. Keep a copy in your password manager.
4. In Google Analytics (Admin → Data collection and modification → **Data retention**), set event data retention to **14 months**, as the privacy policy says. Leave **Google signals off**.
5. In Firebase → **Crashlytics**, click **Enable**. Release builds upload their R8 mapping file automatically, so stack traces are readable.
6. Check events in Firebase → Analytics → **DebugView**: install a release build (`just install-release <serial>`), then run `adb shell setprop debug.firebase.analytics.app com.allient.folio`. Dev builds (`com.allient.folio.dev`) never use Firebase.

### Step 3: Publish the website

The Play listing needs a public privacy policy URL.

1. Add the repository secrets in GitHub → Settings → Secrets and variables → Actions:
   - `CLOUDFLARE_API_TOKEN`: create it in Cloudflare → My Profile → API Tokens, from the **Edit Cloudflare Workers** template.
   - `CLOUDFLARE_ACCOUNT_ID`: shown in Cloudflare → Workers & Pages.
   - `SLACK_WEBHOOK_URL`: optional; for deploy messages in `#eventos`.
2. Push to `main`, or run `just deploy-docs` locally after `bunx wrangler login`.
3. Open `https://folio-docs.<your-subdomain>.workers.dev/privacy/`. You can attach a custom domain in Cloudflare → Workers & Pages → folio-docs → Settings → Domains.

### Step 4: Create the app in Play Console

In [Play Console](https://play.google.com/console), choose **Create app**:

- App name: `Folio: Notes & PDF Reader`
- Default language: English (United States), `en-US`
- App or game: **App**. Free or paid: **Free**
- Accept the declarations.

### Step 5: Complete the setup forms

Under **Dashboard → Set up your app**, Play Console lists the required forms. Folio's answers:

| Form | Answer |
|---|---|
| Privacy policy | Your `/privacy/` URL |
| App access | All functionality is available without special access |
| Ads | No, the app doesn't contain ads |
| Content rating | Fill in the IARC questionnaire. Category: *Utility, Productivity, Communication, or Other*. Answer **No** to violence, sexual content, profanity, drugs, gambling, user-to-user interaction and location sharing. Expected rating: Everyone / PEGI 3 |
| Target audience | 13 and over. Choosing under 13 brings Families policy requirements Folio doesn't need |
| News app | No |
| Data safety | See [Data safety answers](#data-safety-answers) below |
| Government app | No |
| Financial features | None |
| Health | None |
| App category | **Productivity**. Tags: Note taking, Productivity, Education |
| Contact details | A support email (required and shown publicly); website: your docs URL |

### Data safety answers

Data safety → **Data collection and security**:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (Firebase uses HTTPS) |
| Which way can users request that their data is deleted? | **Users can request data deletion**. Point to the privacy policy (contact section); statistics can also be turned off in the app |

Data types. Declare **only** these, all *Collected*, **not Shared**, *Processed ephemerally: No*, *Required or optional: Optional* (users can turn it off), purpose **Analytics** (and **App functionality** for crash logs):

| Category | Data type |
|---|---|
| App activity | **App interactions** (screens and features used) |
| App info and performance | **Crash logs** (Crashlytics) and **Diagnostics** (app version, device model, OS version) |
| Device or other IDs | **Device or other IDs** (the Firebase app instance ID) |

Don't declare personal info, location, files and docs, audio, photos or messages. Folio's notes, books, recordings and files stay on the device and aren't collected.

Also answer the **Advertising ID** question (App content → Advertising ID): **No**, Folio doesn't use the advertising ID (the permission is removed in the manifest).

### Step 6: Store listing

Go to **Grow users → Store presence → Main store listing**. Either:

- **Paste it by hand** from `apps/mobile/fastlane/metadata/android/en-US/`: `title.txt`, `short_description.txt`, `full_description.txt`, then upload `icon.png`, `featureGraphic.png` and the three screenshot folders. Add the Spanish translation (`es-419`) under **Manage translations**.
- **Upload it with fastlane** after Step 7: `just play-metadata`.

### Step 7: Give fastlane access to Play (optional, recommended)

1. In [Google Cloud Console](https://console.cloud.google.com), create a project (or reuse one) and enable the **Google Play Android Developer API**.
2. Go to **IAM & Admin → Service accounts → Create service account**, then **Keys → Add key → JSON**. Save the file as `apps/mobile/play-service-account.json` (git-ignored), or keep it elsewhere and set `PLAY_JSON_KEY=/path/to/key.json`.
3. In Play Console → **Users and permissions → Invite new users**, enter the service account's email. For the Folio app, grant: *View app information*, *Release to testing tracks*, *Release to production*, *Manage store presence*.
4. Test it:

   ```bash
   cd apps/mobile
   fastlane run validate_play_store_json_key json_key:play-service-account.json
   ```

### Step 8: First upload (by hand)

Google's API can't upload to an app that has never had a build, so the first one goes through the website:

1. `just aab`
2. Play Console → **Test and release → Testing → Internal testing → Create new release**.
3. When asked, accept **Play App Signing** (let Google generate and keep the app signing key).
4. Upload `apps/mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab`.
5. Release name: `1.0.0`. Release notes: copy from `changelogs/default.txt`.
6. **Testers** tab: create an email list with your own account and share the opt-in link.
7. **Save → Review release → Start rollout to Internal testing**.
8. Install Folio from the opt-in link on a phone and a tablet and check the main flows: library, writing, PDF (search, highlight, OCR), EPUB, export, backup.

### Step 9: Closed testing (new personal accounts only)

Personal developer accounts created after 13 November 2023 must run a **closed test with at least 12 testers who stay opted in for 14 continuous days** before they can apply for production. Organization accounts skip this step.

```bash
RELEASE_STATUS=completed just play-beta     # uploads to the Closed testing track ("alpha")
```

In Play Console → Closed testing, add a tester list, share the link, and wait out the 14 days. Then apply for production access from the Dashboard.

### Step 10: Production

1. Check the **Pre-launch report** (Test and release → Testing → Pre-launch report) for crashes on Google's test devices.
2. Choose the countries: Test and release → Production → **Countries/regions**.
3. Release, either way:
   - Promote the build you tested: `just play-promote`.
   - Or build and upload a new one as a 20% staged rollout: `just play-production`.
4. Google reviews the first production release. This usually takes a few days.
5. Once it's live, increase the rollout in Play Console (20% → 50% → 100%), or upload again with `ROLLOUT=1.0`.

## Part 2: Releasing an update

1. Update the release notes in `apps/mobile/fastlane/metadata/android/en-US/changelogs/default.txt` and `es-419/changelogs/default.txt` (500 characters at most).
2. Build and send it to testers:

   ```bash
   FOLIO_VERSION_NAME=1.1.0 just play-internal
   ```

   fastlane sets the version code to one above the highest code on any track. You never edit it by hand.
3. Test the internal build on a phone and a tablet.
4. Ship it: `just play-promote`, or `FOLIO_VERSION_NAME=1.1.0 just play-production` for a staged rollout.
5. If the store text or screenshots changed: `just play-metadata`.
6. Tag the release in git: `git tag v1.1.0 && git push --tags`.

### Command reference

| Command | What it does |
|---|---|
| `just aab` | Builds the signed release bundle only |
| `just play-internal` | Builds and uploads to Internal testing |
| `just play-beta` | Builds and uploads to Closed testing (`alpha` track) |
| `just play-production` | Builds and uploads to Production as a staged rollout (20% by default) |
| `just play-promote` | Moves the latest Internal build to Production without rebuilding |
| `just play-metadata` | Uploads title, descriptions, icon, feature graphic and screenshots |

| Variable | Default | Meaning |
|---|---|---|
| `FOLIO_VERSION_NAME` | `1.0.<code>` | Version name shown to users |
| `RELEASE_STATUS` | `completed` (`inProgress` for production) | Use `draft` while the app has never been published |
| `ROLLOUT` | `0.2` | Production rollout fraction |
| `UPDATE_PRIORITY` | `0` | In-app update priority, 0–5 (see below) |
| `PLAY_JSON_KEY` | `play-service-account.json` | Path to the service account key |
| `FOLIO_UPLOAD_KEYSTORE`, `…_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD` | – | Alternative to `keystore.properties` |

## In-app updates

When Folio starts, it asks Google Play whether a newer version exists:

- **Priority 4–5, or a version more than 30 days old:** a full-screen update the user must complete.
- **Anything else:** the update downloads in the background, then a bar offers **Restart**. If the user taps *Later*, Folio asks again after 3 days.

Set the priority when uploading, for example for an important fix: `UPDATE_PRIORITY=5 just play-production`. It can't be changed after upload.

To test it, upload two versions through **Internal app sharing** and install the older one from its link. Builds installed with Android Studio or adb never see updates.

## Troubleshooting

| Problem | Fix |
|---|---|
| `The AAB is not signed` | `keystore.properties` is missing or its paths and passwords are wrong |
| `Only releases with status draft may be created on draft app` | The app isn't published yet: `RELEASE_STATUS=draft just play-internal`, then roll out in Play Console |
| `Package not found: com.allient.folio` | Do the first upload by hand (Step 8) |
| `The caller does not have permission` | The service account lacks Play Console permissions for Folio (Step 7.3); new invites can take up to a day to apply |
| `Version code N has already been used` | Upload again; fastlane picks a higher code. If an upload was rejected, set `FOLIO_VERSION_CODE` explicitly |
| A feature works in debug but not in release | R8 removed something. Add a keep rule in `apps/mobile/androidApp/proguard-rules.pro` and test with `just install-release <serial>` |
| A dependency added a permission | Check the merged release manifest; remove unneeded ones with `tools:node="remove"` and update the privacy policy and Data safety form if data handling changes |
