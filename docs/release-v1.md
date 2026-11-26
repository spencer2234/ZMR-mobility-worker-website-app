# Releasing Version 1 (ZMR Rent Local)

How to build the APK, host it, publish the invite page, and get the first people set up.
Version 1 keeps every record on the phone; see [two-versions-plan.md](two-versions-plan.md).

## Files that are not in git

| File | What it is | Where |
|---|---|---|
| `zmr-release.jks` | The release signing key | `Desktop/ZMR/release-key/` |
| `keystore.properties` | The key's location and passwords | repo root (copy in `release-key/`) |
| `upper_management.txt` | Upper management accounts and PIN hashes | `app/src/main/assets/` ([upper-management.md](upper-management.md)) |

**Back up `release-key/` (for example to a private Google Drive folder).** Every update must be
signed with the same key. If the key is lost, phones refuse the update, and installing fresh
means uninstalling first, which deletes the phone's records.

## 1. Build the APK

```sh
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Before each new release, raise `versionCode` in `app/build.gradle.kts` (and `versionName`).
Release builds have no "Continue as guest".

## 2. Host the APK privately

The APK holds upper management's phone numbers (from `upper_management.txt`), so it must not go
on a public website or a public GitHub release.

1. Rename the file to `ZMR-Rent.apk` and upload it to Google Drive.
2. Share it as **Anyone with the link → Viewer**, and copy the link.

That link is the **app download link**. Send it only to ZMR staff.

## 3. Publish the invite page (one time)

GitHub Pages is free only for public repositories, and this repo is private, so the page lives in
its own small public repo. The page holds no personal data: the invite code and download link
travel after `#` in each link, and browsers never send that part to GitHub.

1. On GitHub, create a **public** repository named `zmr-rent`.
2. Upload `invite-page/index.html` and `invite-page/README.md` from this repo.
3. Go to **Settings → Pages**, choose **Deploy from a branch**, branch `main`, folder `/ (root)`, and save.
4. After a minute the page is at `https://spencer2234.github.io/zmr-rent/`. This is the app's
   default **invite page link**.

## 4. First setup on upper management's phones

Send each person, privately: the **app download link**, their **PIN**, and the setup guide.
On their phone:

1. Install the APK and open ZMR Rent.
2. Tap **Log in → Set up as upper management**, tap their name, type their PIN, and tap **Set up this phone**.
3. Open **Company settings** (the gear at the top of the home screen):
   - paste the **app download link**;
   - check that the invite page link is `https://spencer2234.github.io/zmr-rent/`;
   - tap **Save**.

From then on, every invite they send carries both links.

## 5. Adding managers and employees

1. Upper management goes to **Other → Add manager login**, fills in the person's details, and taps
   **Send by WhatsApp**.
2. The message has one link. The person taps it, downloads the app, and taps **Open my invite in
   ZMR Rent**. Their account is filled in and they choose their own PIN. The steps they see are
   in [invite-page/README.md](../invite-page/README.md).
3. Managers add employees the same way under **Other → Add employee login info**.

A lost invite, a new phone or a forgotten PIN is fixed by sending a new invite to the same person
(same employee ID and name).

## 6. Sending an update

1. Build with a higher `versionCode` and replace the file on Google Drive. Use **Manage versions**
   so the link stays the same.
2. Tell people to download and install it again. Their records and logins stay.
