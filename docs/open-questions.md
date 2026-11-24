# Open questions for ZMR

Questions that change what gets built. Record the answer and the date under each one when it
is confirmed, then update the code or [decisions.md](decisions.md).

## From the project plan

1. **Deposit and refund rules.** When is the ₹2,000 deposit refunded, and can it be used to
   cover unpaid rent?
2. **Overdue renters.** Should renters who missed an earlier rent day still appear on today's
   list as "Overdue"?
3. **Partial payments.** If a renter pays ₹1,000 of ₹1,400, should the app accept it and carry
   ₹400 forward?
4. **Language.** Which language should the worker see: English, Hindi, or another regional
   language? (English and draft Hindi are in the app now.)
5. **Phone.** Which phone and Android version does the worker use?
6. **Users and roles.** Who uses the app (worker names, founder, finance person)?
7. **Day close.** What time should the day "close" for the daily report?

## Raised while building Feature 1

8. **What identifies a vehicle?** An RTO number plate (`DL 1S AB 1234`), or a fleet or chassis
   number for vehicles without a plate? The app currently accepts either. If every vehicle has a
   plate, validation can be made strict.
9. **Can a renter's phone be a non-mobile or non-Indian number?** The app currently accepts only
   10-digit Indian mobile numbers.
10. **Can two renters share one phone number** (for example family members)? The app currently
    allows it without a warning.
11. **What happens to the vehicle when a renter returns it?** Today the renter is put on hold
    with the reason "Returned" and the vehicle stays linked to them, so it cannot be given to a
    new renter until the old renter's vehicle number is changed. Re-renting a returned vehicle
    probably needs its own flow.
12. **Is the weekly rent always ₹1,400?** The form pre-fills ₹1,400 and lets the worker change
    it. Should only the founder be able to change it?
13. **Is the rent day always the weekday the rental starts?** The app now works this way: the
    rental start date is picked on a calendar and rent is due every week on that weekday.
14. **Should "Continue as guest" stay in the released app?** It is there for testing now.
15. **Who may set up as CEO?** On a phone with no CEO account, anyone can tap "Set up as CEO".
    That only creates a CEO on that one phone, but it should be limited before release.
   *Answered 2026-11-24:* only the people built into the APK (Javed Ali, Gaurav) can set up as
   upper management, with the PIN ZMR gave them. See [upper-management.md](upper-management.md).
16. **What are ZMR's UPI ID and bank account?** The founder enters them under Payment details.
    A business (merchant) UPI ID is best: some personal UPI IDs limit payments from QR codes.
17. **Should payments be confirmed automatically?** Today the worker types the UPI reference.
    Automatic confirmation needs a payment gateway account and a server.
18. **Accounts made before the CEO / Manager / Employee change** were given placeholder employee
    IDs ("LOCAL-1") and no phone or email. There is no screen to edit an account yet; set up
    the real CEO account fresh, or ask for an "Edit my details" screen.
19. **Should reports cover only the report day, or everything?** Today the Renters and Payments
    sheets hold everything the person ever entered, and the summary money columns cover the
    report day. For a large fleet a date range may be better.
20. **Re-sending invites.** An invite can be created once per employee ID. If it is lost, the
    manager needs a "Send invite again" option (not built yet).
21. **What did "Aadhaar verification using Sahamati" mean?** Sahamati runs the Account Aggregator
    network and does not appear to verify Aadhaar. Official checks go through UIDAI OVSE registration.
22. **What do the green rows in ZMR's sheet mean?** The sheet colours some riders green with no
    legend, so the app does not copy that colour yet.
23. **Is "Pending Challan" a count or rupees?** The app treats it as a count.
24. **Should rent keep building up while a vehicle is on hold?** Today it does, until the end date.
25. **Should managers' combined sends include their employees' document zips?** Today they do not.
