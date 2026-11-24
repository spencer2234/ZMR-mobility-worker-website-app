## What this changes

<!-- One or two sentences. Link the feature issue: "Part of #12" or "Closes #12". -->

## Screenshots

<!-- Required for any change to a screen. Include the Hindi version if text changed. -->

## How it was tested

<!-- Which unit tests cover it, and what you tried by hand on a phone or emulator. -->

## Checklist

- [ ] `./gradlew lintDebug testDebugUnitTest` passes
- [ ] New text is in `values/strings.xml` **and** `values-hi/strings.xml` (nothing hard-coded)
- [ ] Money is handled as paise (`Long`) and shown with `Money.format`
- [ ] Database changed? Version raised, migration added, new `app/schemas/` file committed
- [ ] No real renter data, backup files, keystores or `google-services.json` in the commit
