# Design decisions

Why the app is built the way it is. Add an entry when a decision would otherwise have to be
re-argued later. Newest decisions go at the bottom of each section.

## Platform and architecture

**Native Android with Kotlin and Jetpack Compose.** Google's recommended stack, and Material 3
makes large buttons and simple screens straightforward.

**Offline first; Room is the source of truth.** Every record is written to the on-phone database
first, so a collection is never lost to a weak signal. Screens read from Room through Kotlin
Flow and update on their own. Cloud sync (Week 9) is layered on top and can slip without
breaking the app.

**MVVM with a small hand-written container instead of a DI library.** `AppContainer` builds the
database and repositories once. The app has a handful of objects; Hilt would add build time and
concepts for no gain at this size.

**Database schema is exported and committed** (`app/schemas/`). Schema changes are then visible
in pull requests, and migrations can be tested. Destructive migration is never allowed: the
database holds the workshop's money records.

**Room tests run on the JVM with Robolectric** (`app/src/test`), not on a device. They run in CI
on every pull request with no emulator. The timeline document placed DAO tests under
`androidTest`; that folder is still the place for Compose UI tests when they are added.

## Built for use in India

**Money is whole paise in a `Long`.** ₹1,400 is stored as `140000`. Decimals are never used, so
totals cannot pick up rounding errors.

**Amounts are displayed with the rupee sign and Indian digit grouping** (`₹1,00,000`). The
formatting is our own code in `Money`, not the phone's locale, so every phone shows the same
thing whatever language or region it is set to.

**Phone numbers are 10-digit Indian mobile numbers**, stored without the `+91` country code and
shown as `+91 98765 43210`. A number must start with 6, 7, 8 or 9, which catches most typing
slips. Pasted numbers written as `+91 …`, `91…` or `0…` are cleaned automatically. Landlines and
foreign numbers are not accepted; revisit if a renter ever needs one.

**Vehicle numbers are stored in one canonical form** (capital letters and digits, no spaces or
dashes), so `dl 1s-ab 1234` and `DL1SAB1234` are the same vehicle. Validation is deliberately
loose (4 to 17 characters with at least one digit) rather than the strict RTO plate pattern,
because rental EVs may carry a fleet or chassis number instead of a plate. Tighten this once
ZMR confirms what the vehicles carry (see open questions).

**Business time is Indian Standard Time.** "Today" and the rent day are worked out in
`Asia/Kolkata` through `IndiaTime`, not the phone's time zone, so the answer is the same on the
worker's phone, the founder's phone and a developer's emulator abroad. Timestamps are stored as
UTC milliseconds.

**All user-facing text is in string resources, with English and Hindi.** The Hindi file is a
draft and must be reviewed by a native speaker during the field test. Day names and labels are
resources rather than taken from the system so the wording can be chosen to suit the worker.
Renter names can be typed in any script.

**Android 8.0 (API 26) is the minimum.** Old and low-cost phones are common. The app ships no
images or custom fonts (icons are small vector drawings, text uses the system font, which
already covers Indian scripts), and release builds are shrunk with R8, so the download stays small.

**One fixed light, high-contrast theme.** No dark mode and no wallpaper-based colours, so every
phone looks the same and stays readable in daylight. Body text is at least 18 sp and buttons at
least 64 dp tall.

## Data rules

**One vehicle, one renter is enforced by the database.** Unique indexes on
`vehicles.vehicleNumber` and `vehicles.renterId` make a duplicate impossible even if screen code
has a bug. The form checks first so the worker gets a clear message instead of a crash.

**Renters are never deleted.** There is no delete query. A renter who leaves is put on hold with
the reason "Returned", so their history survives. Payments (Feature 2) will follow the same
rule, backed by database triggers.

**Validate twice.** The form validates for fast feedback and the repository validates again, so
no code path can save an incomplete record.

**Who and when on every record.** `createdBy` and `createdAt` are set by the repository from the
session and the clock, never by the screen, and never change on edit. Records made by someone
who chose "Continue as guest" are stamped "Guest" (a reserved name nobody can register).

**The rent day comes from the rental start date.** The worker picks the start date on a calendar
and the rent is due every week on that weekday. Both are stored: `startDate` for the record,
`rentDay` so "who is due today" stays a simple query.

**Three account types, set up by invite code.** Employee, Manager and CEO. The CEO sets up
their own account; the CEO adds managers and managers add employees. Adding someone creates an
invite code (sent by WhatsApp or email) holding their name, employee ID, mobile, email, area,
who they report to, and ZMR's payment details. They paste it on their own phone and choose
their own PIN. There is no server, so this was the only way to create an account on a phone
the inviter never touches. The code has a checksum against typing and copying errors but is
not signed: anyone holding it can set up that account, so it must only be sent to that person.

**Upper management is built into the APK.** The CEO screen was open to anyone, so the upper
management people (name, employee ID, mobile, email, PIN hash) are now listed in a file shipped
inside the APK. Each picks their name on their own phone and types their PIN. The file is kept
out of git because it holds real phone numbers ([upper-management.md](upper-management.md)).
Email may be empty; anyone logged in can add or change it under Other > My email.

**Logins stay on the phone.** Only a salted PBKDF2 hash of each PIN is stored. The login screen
lists the accounts on that phone to tap. Nothing is remembered after the app closes.
"Continue as guest" is a temporary override for testing; guests cannot send reports or add people.

**No click sounds.** Compose's click sound is switched off for the whole app
(`SoundEffectOnInteraction(false)` in `ZmrApp`), and the theme turns off sounds for any
classic Android views. The phone's keyboard may still click; that is a keyboard setting.

**Enums are stored by name** (`MONDAY`, `ON_HOLD`), using Room's built-in support. The database
and the future JSON backup stay readable. Enum entries must not be renamed without a migration.

**Defaults live in one place.** The standard weekly rent (₹1,400) is a constant on `Renter` for
now and moves to a founder-editable settings table with Feature 3.

## Vehicles and inventory

**A vehicle can exist without a renter.** Inventory vehicles have no renter until one is
registered with that number plate; the renter then takes that vehicle (and its RC number).
Switching a renter to another free vehicle sends the old one back to the inventory. A vehicle
rented to someone else is refused. Each renter has an optional agreed return date.

## ZMR's rental sheet

**The Excel's first sheet copies ZMR's columns and order.** A row above the table says whose data
it is and when it was sent, and **Submitted By** is the far-right column so combined sheets show
each row's source. Extra columns (customer type, city, RC and so on) sit between them.

**Past due = full weeks late x weekly rent, less part-payments, plus Pending Paisa.** Challans are
a separate count. Rent stops building up after the agreed return or lease end date.

**Documents are optional and sent as a zip beside the Excel,** as ZMR chose. A manager's combined
send includes only documents on the manager's phone. Aadhaar is accepted as a masked copy only and
the app does not verify it.

**ZMR's Paytm QR is used exactly as issued** (`zmrmobility@ptaxis`, no amount inside). The renter
types the amount shown on screen, because unsigned merchant requests with an amount can be refused.

## Payments

**Payments are an append-only ledger.** The payments table has no update or delete in code, and
two SQLite triggers (`payments_no_update`, `payments_no_delete`) refuse them in the database
itself. Corrections will be new rows pointing at the original (`correctsPaymentId`).

**The first payment is two rows.** The deposit and the rent advance are saved as separate
payments (types DEPOSIT and ADVANCE) with the same method and reference, so the daily report
can list them separately. They can only be recorded once per renter.

**UPI by QR code, made on the phone.** The First payment screen shows a UPI QR code holding a
`upi://pay` link with ZMR's UPI ID, the amount and a note naming the vehicle. Any UPI app
(Google Pay, PhonePe, Paytm, BHIM, bank apps) can scan it. The code is drawn on the phone with
ZXing, so it works without internet, and the screen goes to full brightness while it shows.
The app does not talk to any payment gateway and cannot see whether money arrived: the worker
checks the renter's payment screen (or ZMR's account) and types the transaction reference
(UTR). Confirming payments automatically would need a payment gateway account (Razorpay,
Cashfree, PhonePe for Business...) and a server; that is a decision for ZMR.

**Bank transfer shows ZMR's account details** (name, account number, IFSC, bank) for the
renter to send money by NEFT/IMPS. The details can be selected and copied.

**Only managers and the CEO set where money goes.** ZMR's UPI ID, bank account and the deposit
and advance amounts are on the Payment details screen, reachable only by Manager and CEO
logins. An employee changing the UPI ID to their own would divert renters' payments. An
employee's phone gets the details from their invite. The amounts are stored in a
settings table, so they change without a new app version.

## Reports

**Reports are Excel files sent by WhatsApp or email, and the app does not show them.** ZMR
chose this over a cloud service. Each level combines the files from the level below: an
employee's report goes to their manager; the manager adds their employees' files to their own
data and sends a team report to the CEO; the CEO combines the team reports into a company
report grouped by city or area. When the same person's file is added twice, only the newest
counts, so nothing is double counted.

**Each file is readable by people and by the app.** Sheets: Summary (one line per person, area
subtotals, totals, and who has not reported), Renters, Payments, and two sheets the app uses to
combine files (Sources, About). Sheet and column names are fixed English, because the app finds
the data by them. Money is written as real numbers so Excel can add it up.

**The Excel writer and reader are our own small code** (`domain/xlsx`), not a spreadsheet
library: Apache POI would add several megabytes and does not run well on Android. They handle
the files the app writes, and those files after being saved again by Excel or Google Sheets.

**What a report contains.** The renters a person added and the payments they recorded on that
phone (matched by their name on the record). The money columns in the summary cover the
person's report day only; the Payments sheet lists everything.

**Sending uses the phone's own apps.** "Send by WhatsApp" asks WhatsApp to open the right chat
directly; this uses a widely supported but unofficial WhatsApp feature, and if it is not
honoured the person picks the chat. "Send by email" fills in the address, subject and file.
Invites go by WhatsApp's official wa.me link or an email draft.

## Android automatic backup

Android's own backup (to the worker's Google account) and phone-to-phone transfer are left
**on**, limited to the database. Until the app's own backup and restore exist (Week 7) this is
the only protection against a lost or broken phone. Revisit when Feature 6 lands: once the
app's backup file and cloud sync are the official copies, an automatic restore of an older
copy could be confusing and this may be better turned off.

## Tooling

**Gradle version catalog** (`gradle/libs.versions.toml`) holds every dependency version.

**The Android Gradle Plugin's built-in Kotlin support is used**, so there is no separate Kotlin
Android plugin applied; it is declared in the root build file only to pin the Kotlin version.
