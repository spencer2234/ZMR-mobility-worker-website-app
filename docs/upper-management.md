# Upper management accounts

Only the people listed in `app/src/main/assets/upper_management.txt` can tap **Set up as upper
management**. Each one picks their name on their own phone and types the PIN ZMR gave them. The
PIN is checked against the hash in the file, and the account is then made on that phone only.

The file holds real phone numbers, so it is **not in git** (see `.gitignore`). Keep a copy
somewhere safe. A build made without the file still works, but nobody can set up as upper
management on it.

## File format

One person per line. Lines starting with `#` are notes.

```
# employee ID | name | mobile | email (may be empty) | PIN salt | PIN hash
7 | Test Person | 9000000000 | test@example.com | <32 hex characters> | <64 hex characters>
```

## Adding a person or changing a PIN

Make the salt and hash with Python (the same PBKDF2 settings as `domain/Pin.kt`), then paste the
printed line into the file and build a new APK:

```python
import hashlib, os
code, name, phone, email, pin = "3", "New Person", "9000000000", "", "1234"
salt = os.urandom(16)
h = hashlib.pbkdf2_hmac("sha256", pin.encode(), salt, 60000, 32)
print(" | ".join([code, name, phone, email, salt.hex(), h.hex()]))
```

Changing the file only affects phones that have not set up yet. A person who has already set up
keeps the PIN they set up with.
