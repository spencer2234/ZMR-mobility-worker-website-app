# ZMR Rent Tracker

Android app for ZMR Mobility's workshop worker to record weekly vehicle rent and send the
founder a clear daily report. Version 1 covers rent only; repairs and spare parts are out of scope.

The app is built for one setting: a workshop in India, a low-cost Android phone, a patchy
network, and a worker who should never have to type more than a few words.

## Status

| # | Feature | State |
|---|---------|-------|
| 1 | Renter and vehicle details | **Done**: add, edit, one vehicle per renter, start date from a calendar, Active / On hold, searchable list |
| 2 | Rent collection | Started: record rent payments from a renter's page; past due is calculated |
| 3 | Initial rental amounts (deposit, advance) | **Done**: after adding a renter, collect ₹2,000 deposit + ₹1,400 advance by UPI QR code, bank transfer or cash; saved as two separate payments with the reference |
| 4 | Daily summary for the founder | **Done as Excel reports**: employee report to manager, team report to CEO, company report; sent by WhatsApp or email |
| 5 | Simple screens | Started: home screen, large type and buttons, no click sounds, English and draft Hindi |
| 6 | Data and access (roles, backup, sync) | Started: Employee / Manager / CEO accounts set up by invite code, 4-digit PIN, guest mode. Backup and cloud sync not started |

The full plan and schedule are in the project's timeline document. Requirements, design
decisions and open questions are in [docs/](docs/).

## Build and run

You need [Android Studio](https://developer.android.com/studio) (2026.1 or newer). It includes
the JDK and the Android SDK.

1. Open this folder in Android Studio and let Gradle sync.
2. Pick a phone or emulator and press **Run**.

From a terminal (set `JAVA_HOME` to a JDK 17 or newer first; Android Studio's bundled one works):

```sh
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # run all unit tests (no phone needed)
./gradlew lintDebug            # Android lint
./gradlew installDebug         # install on the connected phone or emulator
```

On Windows use `gradlew.bat` in place of `./gradlew`.

To see the Hindi version, change the phone's language to Hindi, or on Android 13 and newer go to
Settings > Apps > ZMR Rent > Language.

## How the code is organised

```
app/src/main/java/com/zmr/rent/
  ZmrApplication.kt, AppContainer.kt   app start-up and shared objects
  MainActivity.kt                      the single activity; hosts all Compose screens
  data/
    AppDatabase.kt                     Room database (the source of truth, on the phone)
    entity/                            tables: Renter, Vehicle, User, TeamMember, Payment, Setting
    dao/                               database queries
    repository/                        the only way screens read or change data
    session/                           who is using the app
  domain/                              plain Kotlin rules, no Android: money, phone and
                                       vehicle numbers, invites, Excel reports (xlsx/, report/),
                                       validation, search, India time
  ui/
    ZmrApp.kt                          navigation between screens
    login/  home/  renters/  addrenter/  inventory/  payment/  settings/  team/  reports/
                                       one folder per screen
    components/                        shared pieces: BigButton, ChoiceGrid, DateField, top bar
    theme/                             colours and the large type scale
  util/Sending.kt                      sending files and invites through WhatsApp and email
app/src/main/res/
  values/strings.xml                   every word shown to the user (English)
  values-hi/strings.xml                Hindi (draft)
app/src/test/                          unit tests, including Room tests run on the JVM
app/schemas/                           the database schema for each version, kept in git
docs/                                  requirements, decisions, open questions
.github/                               CI workflow, issue and pull request templates
```

Stack: Kotlin, Jetpack Compose with Material 3, Room, Navigation Compose, MVVM. Library
versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Screens

- **First screen:** Log in, or Continue as guest. A guest picks the Employee, Manager or Upper
  management view (temporary, for testing).
- **Main screen (everyone):** Register customer (renter or lessee, then six optional documents),
  Renters (past due beside each name), Inventory (filtered by city), Send data, Other (⋯).
- **Other:** Home, Language (English, Hindi, Tamil, Kannada) and My email for everyone. Managers also get
  Weekly report status, Add employee login info and Send manager data; upper management also
  gets Add manager login.

## Accounts and reports

There is no server. Each phone keeps its own data, and the company hierarchy works through
invite codes and Excel files sent over WhatsApp or email:

1. Upper management (listed in the APK, see [docs/upper-management.md](docs/upper-management.md))
   taps **Set up as upper management** on their own phone, picks their name and types their PIN.
2. The CEO taps **Add manager** (name, employee ID, mobile, email, city/area) and sends the
   invite code. The manager taps **I have an invite code** on their own phone and picks a PIN.
3. A manager adds employees the same way. Employees are placed in the manager's area.
4. An employee taps **Send report**: the app builds an Excel file of the renters and payments
   they entered and opens WhatsApp or email addressed to their manager.
5. The manager opens **Team report**, adds the files employees sent, and sends one combined
   Excel file to the CEO. The screen lists who has not sent a report.
6. The CEO opens **Company report**, adds the managers' files, and gets one Excel file for the
   whole company, with a subtotal per city or area.

The app never shows the figures on screen; they are read in Excel, Google Sheets or WPS Office.

## Rules this codebase follows

These come from ZMR's requirements and from where the app is used. Details and reasons are in
[docs/decisions.md](docs/decisions.md).

- **The phone's database is the source of truth.** Everything works with no network.
- **Money is whole paise in a `Long`.** ₹1,400 is `140000`. No floats or doubles for money, ever.
- **Amounts are shown the Indian way**: `₹1,00,000`, not `₹100,000`. Use `Money.format`.
- **Phone numbers are 10-digit Indian mobiles**, stored without `+91`. Use `IndianPhone`.
- **Dates and "today" use Indian Standard Time**, whatever the phone is set to. Use `IndiaTime`.
- **Vehicle numbers are stored as capitals and digits only**, so one vehicle cannot be entered
  twice with different spacing. Use `VehicleNumber.normalize`.
- **One vehicle, one renter**, enforced by unique indexes in the database, not just by the screen.
- **Records are never deleted.** A renter who leaves is put on hold.
- **No hard-coded text in screens.** Every string goes in `strings.xml` and `values-hi/strings.xml`.
- **Big and simple.** Body text at least 18 sp, buttons at least 64 dp tall, an icon with a word,
  tap-to-choose instead of typing wherever possible.
- **No real renter data in git.** Tests use made-up names and numbers.

## Working on the project

- One GitHub issue per feature (F1 to F6), one branch per feature (`feature/f2-collection`), and
  a pull request into `main` with screenshots.
- CI ([.github/workflows/android.yml](.github/workflows/android.yml)) runs lint, the unit tests
  and a debug build on every pull request and uploads the APK.
- When a database table changes: raise the version in `AppDatabase`, add a migration, and commit
  the new file Room writes to `app/schemas/`.
