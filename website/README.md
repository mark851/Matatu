# Amatatu Web Parlour

Responsive website edition of **Amatatu** for desktop, laptop, tablet, and mobile web browsers, synchronized live with the same Firebase project (`gen-lang-client-0429387427`) and Firestore database (`ai-studio-android-amatatu-62169e37-c47a-4d67-8810-00cb6776f596`) used by the Android app.

## Website Layout vs Android App Preview

- This workspace's AI Studio Dev/Shared URL previews the **Android application** inside a Streaming Android Emulator.
- The browser **Website** lives inside `website/` and uses standard multi-section web navigation (sticky header bar, top responsive dropdown menu on narrow screens, scrollable homepage & embedded card arena, and website footer) with no native-app bottom tab bars or standalone PWA wrappers.

## Running Locally

Serve `website/` over HTTP rather than opening via `file://` so native ES modules load cleanly:

```bash
python3 -m http.server 8080 --directory website
# or
npx serve website
```

To connect to local Firebase Emulators (`firestore:8085`, `auth:9099`), append `?emulator=1`:

```
http://127.0.0.1:8080/?emulator=1
```

## Deploying to Firebase Hosting (Share Across Other Devices)

`firebase.json` is pre-configured with `"hosting": { "public": "website" }`:

```bash
firebase deploy --only hosting
```

Ensure your deployed hostname is listed under **Firebase Console → Authentication → Settings → Authorized domains** for Google Sign-In on other devices.

## Running Automated Tests

```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test website/tests/website.test.mjs
```
