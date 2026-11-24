# Requirements (Version 1)

> This is a summary taken from the project timeline document. ZMR's original requirement text
> should be pasted here word for word so the two cannot drift apart.

ZMR's priority for Version 1 is **reliable rent tracking and a clear daily report**. Repair and
spare-parts tracking are out of scope.

## Feature 1: Renter and vehicle details

- Add and edit a renter: name, phone, vehicle number, rent day (Mon to Sun), weekly rent
  (normally ₹1,400), status (Active / On hold).
- Each vehicle is linked to one renter.
- Putting a renter on hold records a reason: Missed payments, Repair, Returned, Other.
- An "All renters" list with search by name, phone or vehicle number.

Done when:

- [ ] A renter can be added in under 60 seconds with only name, phone and vehicle number typed.
      (Only those three fields need typing; the 60 seconds still has to be timed with the worker.)
- [x] Trying to assign one vehicle to two renters shows a clear error.
- [x] A test proves the database rejects a duplicate vehicle.

## Feature 2: Rent collection

- A daily list of renters whose rent is due today, with name, phone, vehicle number and amount.
- One-tap status: Paid, Not paid, Promised to pay, Could not contact, Vehicle on hold.
- A payment records amount, date and time, Cash or UPI/Bank, and a receipt or reference number.
  A payment cannot be saved without the method and the reference.
- Every entry records who made it and when. Payments are never edited or deleted.

## Feature 3: Initial rental amounts

- A new rental records a deposit (₹2,000) and a rental advance (₹1,400) as two separate payments.

## Feature 4: Daily summary for the founder

- Renters due, total due, total received, unpaid list, deposits and advances, cash total,
  UPI/Bank total, payments missing a reference, vehicles on hold with the reason.
- One tap sends the report to the founder on WhatsApp.

## Feature 5: Simple screens

- Four large home buttons: Today's rent, All renters, Add renter, Daily report.
- Large text and buttons, very little typing.

## Feature 6: Data and access

- Worker and Founder/Finance roles.
- Nobody can delete a payment; corrections stay visible in the history.
- Backup and restore.
- Sync so the founder sees updates on another phone (last step).
