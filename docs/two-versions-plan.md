# Plan: two versions of the ZMR Rent app

## Context

Today's app (repo `ZMR-mobility-worker-website-app`, branch `feature/rent-tracker-v0.1`) keeps
every record on one phone and moves data by Excel files over WhatsApp or email. Spencer wants
two products:

- **Version 1, "ZMR Rent Local"**: stays phone-only. People are added by a link sent from
  their manager or upper management. The link downloads the APK and sets up their account.
- **Version 2, "ZMR Rent Cloud"**: data lives on a secure server (Supabase, Mumbai region).
  Employees and managers see and add renters, vehicles and documents only for **their own
  city**. Upper management sees all cities. Phones keep working offline and sync later.

Decisions already made:
- Two **separate projects**.
- **Supabase** for the server.
- Employees and managers are limited to their city; upper management sees all cities.
- The server version works **offline and syncs later**.
- V2 logins use **email** (free, and widely used in India), not SMS codes.
- **Managers see only their own city.** Upper management sees all cities.
- **Supabase costs are on hold** until V2 starts.
- V1 uses the **invite page**, a static page on GitHub Pages ([invite-page/](../invite-page/)).

**Status (2026-11-25):** Version 1 is built (see [release-v1.md](release-v1.md)). Version 2 is
on hold until ZMR decides on paying for Supabase.

---

## Which features go in each version

| Feature | V1 Local | V2 Cloud |
|---|---|---|
| Register renter or lessee, edit, on hold | Yes | Yes (saved to the server, tagged with the city) |
| Renters list with past due | Own phone's renters | Everyone in my city (upper management: all cities, with a city filter) |
| Inventory with city filter | Own phone's vehicles | My city's vehicles (upper management: all) |
| Six documents | Stored on the phone, sent as a zip | Uploaded to private server storage; viewed through short-lived links |
| First payment and rent payment (ZMR QR, bank, cash) | Yes | Yes. Same append-only rule, enforced on the server too |
| Accounts | Invite **link**, PIN on the phone | Server login (email + password, then PIN), created by manager or upper management |
| Reports | Excel sent by WhatsApp or email, merged upward | Live on the server. "Export Excel" kept for my city, or all cities for upper management |
| Weekly submitted / pending | From the Excel files received | Worked out from server data (who added or collected this week) |
| Languages (EN, HI, TA, KN) | Yes | Yes |
| Backup | Android auto-backup only | Server daily backups |
| Guest mode | Removed from release builds | Removed |

---

## Version 1: ZMR Rent Local (this repo)

**Goal:** finish the current app so it can be handed out by link, with no server holding data.

### Steps

1. **Release build and signing**
   - Make a release keystore, kept out of git (`keystore.properties` and `*.jks` are already in `.gitignore`).
   - Add `signingConfigs.release` to `app/build.gradle.kts`.
   - Build `assembleRelease` and test the shrunk (R8) build fully on the emulator.
   - Raise `versionCode` on every APK sent out.
2. **Remove guest mode from release builds.**
   - In `ui/login/WelcomeScreen` and `ui/ZmrApp.kt`, show "Continue as guest" only when `BuildConfig.DEBUG` is true.
   - Turn on `buildFeatures.buildConfig`.
3. **Invite link instead of pasting a code**
   - A static page on **GitHub Pages** (free; it stores no data), e.g. `https://<user>.github.io/zmr-rent/i#ZMR1.…`.
     - The invite code sits after `#`, so browsers never send it to GitHub.
     - The page shows two buttons: **Download app** (the APK link) and **Open invite**.
   - The page's **Open invite** button opens the app through `zmrrent://invite?c=<code>` (an intent-filter in `AndroidManifest.xml`). *Built this way instead of an App Link: an App Link needs a file at the root of the web address, which a GitHub project page cannot serve.*
   - `MainActivity` passes the code on, and the invite setup screen opens already filled in.
     - Reuse `InviteCodec` (`domain/Invite.kt`), `InviteSetupViewModel.onInviteTextChange` (`ui/login/SetupViewModels.kt`) and `Routes.SETUP_INVITE`.
   - The message sent by `Sending.textByWhatsApp` / `textByEmail` (`util/Sending.kt`) becomes: "1. Install: <APK link>  2. Then tap: <invite link>".
   - Pasting the code by hand still works as a fallback.
4. **Where the APK is downloaded from.**
   - The APK contains `upper_management.txt`, which holds Javed's and Gaurav's numbers. It must **not** go on a public GitHub Release.
   - Use a private Google Drive link (or make the repo private) and put that link in the invite message.
   - The link is set by upper management under Company settings (formerly Payment details), and travels inside invites like the payment details do today (`Invite.payment`).
5. **Updates.** Managers send the new APK link to their team. Android installs it over the old app and keeps the data, as long as it is signed with the same key (step 1).
6. **Tag `v1.0.0`** after a full emulator test. Update README, `docs/decisions.md` and `docs/open-questions.md`.

**Files touched (V1):**
- `app/build.gradle.kts`
- `AndroidManifest.xml`
- `MainActivity.kt`
- `ui/ZmrApp.kt`
- `ui/login/WelcomeScreen.kt` and `ui/login/SetupViewModels.kt`
- `util/Sending.kt`
- `domain/Invite.kt` (adds a download-link field)
- `domain/PaymentSettings.kt`
- the new folder `invite-page/` (`index.html` and the employees' `README.md`)

---

## Version 2: ZMR Rent Cloud (new repo `ZMR-rent-cloud`)

**Goal:** the same screens backed by Supabase, limited by city, and working offline.

### Step 1: New project
- Copy this repo at tag `v1.0.0` into a new GitHub repo, **private** because it will hold server keys and settings.
- Change `applicationId` to `com.zmr.rent.cloud` and the app name to "ZMR Rent Cloud", so both apps can sit on one phone.
- Remove what the server replaces:
  - invite codes (`domain/Invite.kt`);
  - Excel merging (`ReportMerger`) and Sources/About sheets;
  - `report_submissions`;
  - the upper_management asset and guest mode.
- Keep:
  - Money, IndianPhone, VehicleNumber, IndiaTime;
  - RentLedger (past due);
  - Documents types;
  - Upi/QR;
  - the xlsx writer (used for export);
  - every screen in `ui/`.

### Step 2: Supabase project (Mumbai, `ap-south-1`)
- Owned by ZMR's account. Use the **Pro plan** (about $25/month): the free plan has no daily backups and pauses when idle.
- **Tables**, all with `id uuid` and `created_at`/`updated_at`, written as SQL migration files in the repo under `supabase/migrations/`:
  - `cities (id, name)`
  - `profiles (user_id → auth.users, name, employee_code unique, phone, email, role EMPLOYEE|MANAGER|UPPER, city_id, reports_to)`
  - `renters (…same columns as today's Renter…, city_id, created_by)`
  - `vehicles (…, city_id, renter_id unique)`, with `vehicle_number` unique
  - `payments (…, city_id)`: append-only, with triggers that refuse update and delete (copied from `data/Triggers.kt`)
  - `renter_documents (renter_id, slot, type, storage_path, added_by)`
  - `company_settings` (ZMR UPI ID, bank details, deposit and advance amounts)
  - `audit_log`, filled by triggers (who changed what, and when)
- **Row Level Security** on every table:
  - Two helper functions, `my_role()` and `my_city()`, read the caller's profile.
  - SELECT/INSERT/UPDATE are allowed when `city_id = my_city()`, or when `my_role() = 'UPPER'`.
  - Payments have INSERT only. Settings are written only by MANAGER or UPPER.
- **Storage:** a private bucket `documents` with path `{city_id}/{renter_id}/{slot}.{ext}`.
  - The storage policy checks the first path part against `my_city()`.
  - Phones view a file through a signed link that expires after 5 minutes.
  - Aadhaar stays masked-copy only.
- **Logins:** email + password (Supabase Auth, free). The new person gets an email to set their password.
  - After the first login, a 4-digit PIN unlocks the app on that phone (reuse `domain/Pin.kt` and `ui/login/PinField.kt`).
- **Creating accounts:** an Edge Function `create-user`.
  - It holds the service key on the server only; the key is never in the app.
  - It checks the caller's role: managers create employees in their own city, and upper management creates managers or employees in any city.
  - It creates the login and profile, then Supabase emails the person a link to set their password. The manager also gets a message to share: the download link + "log in with your email".
  - This replaces `AuthRepository.createInvite` (`data/repository/AuthRepository.kt`). `ui/team/AddMemberScreen` stays.
- **First upper management:** Javed Ali and Gaurav are added once, by hand, in the Supabase dashboard.

### Step 3: Offline sync in the app
- **Room stays as the phone's copy.**
  - Primary keys change from `Long` to a `String` UUID made on the phone, so records can be created offline. This is a Room migration in `data/Migrations.kt`.
  - Add `syncState` (PENDING / SYNCED / FAILED) and `updatedAt` to renters, vehicles, payments and documents.
- **Writing:** repositories (`RenterRepository`, `PaymentRepository`, `DocumentRepository`) save to Room exactly as now and mark the row PENDING. Screens do not change.
- **SyncWorker (WorkManager):** runs when there is a network connection, after every save and every 15 minutes.
  1. **Push** PENDING rows with upsert. The UUID makes a retry safe: a payment is never doubled.
  2. **Upload** pending document files to Storage.
  3. **Pull** rows changed since the last sync. RLS already limits them to the person's city.
- **Conflicts:**
  - Renters and vehicles: the latest `updatedAt` wins.
  - Payments never conflict, because they are append-only.
  - If the server refuses a row (for example, two phones took the same vehicle while offline), it is marked FAILED and shown on the renter's page as "Needs attention".
- **Library:** `supabase-kt` (postgrest, auth, storage) with the Ktor Android engine. Pin the exact versions in `gradle/libs.versions.toml` at build time.
- On the home screen, a small "Last synced 10:42 / 3 waiting to upload" line under the user's name.

### Step 4: Screens
- **Renters and Inventory:**
  - Same screens, now showing the whole city.
  - For upper management, the city `PickerField` (reused from `ui/inventory/InventoryScreen.kt`) filters by city.
  - New renters and vehicles are tagged with the user's city automatically. Upper management picks the city.
- **Documents screen:** shows "uploaded" or "waiting", and opens files through signed links.
- **Send data** becomes **Export Excel**: `ReportWorkbook` builds the file from the data the person can see.
- **Weekly status:** worked out from the server (who recorded anything this week, by city).
- **Other → Add employee login / Add manager login** now call `create-user`. Payment details save to `company_settings`.

### Step 5: Moving data from Version 1
- Add an upper-management **Import Excel** screen.
  - It reads V1 report files with the existing reader (`ReportRepository.read`, `domain/report/ReportWorkbook.kt`).
  - It uploads the renters, vehicles and payments to the matching city.
- V1 documents are imported from their zip.

### Step 6: Security and legal before launch
- No service key in the app (anon key + RLS only).
- HTTPS only.
- Test backups by actually restoring one.
- DPDP consent wording on the Register customer screen.
- A rule for how long documents are kept, with a deletion process.
- See `docs/server-requirements.md` (to be updated with this design).

---

## Verification

**V1**
- The release APK installs on the emulator (as the `.e2etest` copy).
- Tapping the invite link in a WhatsApp or SMS test opens the app with the invite filled in.
- A manager-to-employee setup finishes.
- Guest mode is absent from the release build.
- The existing 107 unit tests pass.

**V2**
- **RLS tests** (SQL run with the Supabase CLI):
  - A Delhi employee can't read or write Lucknow rows or documents.
  - Upper management can read both cities.
  - Updating or deleting a payment is refused.
- **Sync tests** (Robolectric plus a fake server):
  - Something saved offline uploads once when the connection returns.
  - A repeated upload doesn't duplicate a payment.
  - A conflicting vehicle is marked FAILED.
- **End to end on two emulators,** a Delhi employee and upper management:
  - add a renter with airplane mode on, turn it off, and see the renter appear on the upper-management phone;
  - a Lucknow employee never sees that renter.
- **Import test:** the Excel file made by V1 imports with the right counts.

## Decisions made (2026-11-25)
1. **V2 login method:** email + password. It is free, and many people in India use email.
2. **Manager access:** managers see only the cities they are in.
3. **Cost:** Supabase is on hold. Nothing is paid for until V2 starts.
4. **Invite page:** yes, for V1. It is the static page in [invite-page/](../invite-page/), with a
   README for the employees who receive the link.
