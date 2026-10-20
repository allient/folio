---
title: Publish on Google Play
description: First-time Play Console setup, signing, store listing and fastlane releases for Folio.
---

Folio ships on Android only. App builds run on your own machine with fastlane; GitHub Actions only deploys this docs site.

## What is ready in the repo

| Item | Where |
|---|---|
| Application ID | `com.allient.folio` |
| Store title (25/30) | `Folio: Notes & PDF Reader` |
| Short and full description, English and Spanish (Latin America) | `apps/mobile/fastlane/metadata/android/{en-US,es-419}/` |
| App icon 512×512 | `…/en-US/images/icon.png` (from `packages/brand/png/folio-store-primary-512.png`) |
| Feature graphic 1024×500 | `…/en-US/images/featureGraphic.png` |
| Phone screenshots (9:16) | `…/en-US/images/phoneScreenshots/` |
| 7-inch and 10-inch tablet screenshots (9:16) | `…/en-US/images/sevenInchScreenshots/`, `tenInchScreenshots/` |
| Release notes | `…/changelogs/default.txt` (or `<versionCode>.txt` for one release) |
| Privacy policy | `/privacy/` on this site |
| Upload signing | `apps/mobile/keystore.properties` (git-ignored) |
| In-app updates | `AppUpdates.kt` (Google Play in-app updates API) |
| fastlane lanes | `apps/mobile/fastlane/Fastfile` |

## 1. Create the upload key (once)

Play App Signing keeps the real app signing key. You only sign uploads with your **upload key**. Keep the `.jks` file and its passwords in your password manager; if you lose it, Play support can reset it, but it takes days.

```bash
cd apps/mobile
keytool -genkeypair -v -keystore folio-upload.jks -alias folio-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Create `apps/mobile/keystore.properties` (never commit it; `*.jks` and this file are git-ignored):

```properties
storeFile=folio-upload.jks
storePassword=…
keyAlias=folio-upload
keyPassword=…
```

Check it: `just aab` must print a path to a signed `.aab`.

## 2. Create the app in Play Console

1. **Create app** → name `Folio: Notes & PDF Reader`, default language English (United States), **App**, **Free**. Accept the declarations.
2. **Set up your app** checklist (Dashboard):
   - **Privacy policy:** `https://<your docs domain>/privacy/`
   - **App access:** all functionality is available without special access.
   - **Ads:** No ads.
   - **Content rating:** fill the IARC questionnaire. Category *Utility, Productivity, Communication or Other*; answer No to violence, sexual content, gambling, user interaction and sharing location. Expected result: Everyone / PEGI 3.
   - **Target audience:** 13+ (choosing under-13 brings Families policy requirements you don't need).
   - **News app:** No.
   - **Data safety:** *Does your app collect or share any of the required user data types?* → **No**. Folio has no INTERNET permission, no analytics and no account. Encryption in transit: not applicable. Data deletion: users delete data in the app or by uninstalling.
   - **Government app:** No. **Financial features:** none. **Health:** none.
   - **Category:** Productivity. Tags: Note taking, Productivity, Education. Contact email: required (shown publicly).
3. **Store listing:** you can paste the files from `fastlane/metadata`, or run `just play-metadata` after step 3 below.

## 3. Google Play API access for fastlane (once)

1. In Google Cloud Console, create (or pick) a project and enable the **Google Play Android Developer API**.
2. Create a **service account**, then a JSON key for it. Save it as `apps/mobile/play-service-account.json` (git-ignored), or point `PLAY_JSON_KEY` to it.
3. In Play Console → **Users and permissions** → **Invite new user** → the service account's email. Give it, for the Folio app: *View app information*, *Release to testing tracks*, *Release to production*, *Manage store presence*.
4. Install fastlane once: `brew install fastlane` (or `cd apps/mobile && bundle install` and prefix commands with `bundle exec`).
5. Test: `cd apps/mobile && fastlane run validate_play_store_json_key json_key:play-service-account.json`.

## 4. First upload (manual, once)

The Play API can't upload to an app that has never had a build. For the very first one:

```bash
just aab   # prints apps/mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab
```

In Play Console → **Testing → Internal testing → Create new release**, opt in to **Play App Signing** (use the Google-generated key), upload the `.aab`, add yourself as a tester and roll out. After that, fastlane can do everything.

> **New personal developer accounts** (created after November 2023) must run a **closed test with at least 12 testers for 14 days** before they can apply for production. Use `just play-beta` for that track. Organization accounts skip this.

## 5. Releases with fastlane

All lanes run on your machine (no CI minutes). The version code is computed as *highest code on any track + 1*; the version name comes from `FOLIO_VERSION_NAME` or defaults to `1.0.<code>`.

| Command | What it does |
|---|---|
| `just aab` | Build the signed AAB only |
| `just play-internal` | Build and upload to Internal testing |
| `just play-beta` | Build and upload to Closed testing (`alpha` track) |
| `just play-production` | Build and upload to Production, 20% staged rollout (`ROLLOUT=0.5` to change) |
| `just play-promote` | Promote the latest Internal build to Production without rebuilding |
| `just play-metadata` | Upload title, descriptions, icon, feature graphic and screenshots |

Useful variables: `FOLIO_VERSION_NAME=1.2.0`, `RELEASE_STATUS=draft` (needed while the app is still a draft in Play), `UPDATE_PRIORITY=0..5`.

## In-app updates

`PlayUpdates` (`AppUpdates.kt`) asks Google Play for a newer version at launch:

- **Priority 4–5**, or a version older than **30 days**: an **immediate** full-screen update.
- Otherwise a **flexible** update downloads in the background, then a bar offers **Restart**. If the user declines, Folio asks again after 3 days.

Set the priority per release with `UPDATE_PRIORITY=4 just play-production` (it can't be changed after upload). Updates only appear for installs from Play; to try it, use **Internal app sharing** with two version codes.

## Checklist before production

- [ ] Version code higher than any earlier upload (fastlane does this).
- [ ] `changelogs/default.txt` updated in both languages.
- [ ] Pre-launch report in Play Console has no crashes.
- [ ] Tested on a phone and a tablet from the internal track.
