# What the Argon2 password change does to everyday scenarios (RSDEV-894)

This page is the plain-English companion to
[ADR 0011](../adr/0011-argon2id-password-hashing-and-login-concurrency-limit.md). The ADR says
why. This page says what a user, an administrator or a support engineer will actually see, and
which messages are new.

**Keep this table in step with the code.** Every commit on the RSDEV-894 branches (PR #1203,
PR #1204) that changes a limit, a message, a default or a route must update the matching row in the
same commit. A row that no longer matches the code is a bug in this page. When PR #1210
(RSDEV-1546) merges, update the API token row.

Last reconciled with: `RSDEV-894` batched migration commit (review Issue4), `RSDEV-894-stack-2` eae7c934e.

## Settings this page refers to

| Setting | Default | Meaning in plain words |
|---|---|---|
| `login.passwordVerification.maxConcurrent` | 8 | How many password checks may run at the same time, server-wide. One account never holds more than one. |
| `login.passwordVerification.waitSeconds` | 5 | How long a check waits for a free slot before giving up. |
| `password.anonymousEncode.maxConcurrent` | 4 | How many *new* passwords may be hashed at the same time on the pages anyone can reach without logging in. Shared by every route in the "Takes a ticket" rows below. Nothing waits: if no slot is free the request is refused at once. |

A password check or a new-password hash holds about 19 MB of memory and roughly a tenth of a
second of one processor core. These limits exist to cap that cost under a flood.

## Scenarios

"Old message" is what main shows today. "New message" is what this branch shows. Where the new
column is empty, nothing the user sees has changed.

| Scenario | What this change does | Old message | New message | Notes for support |
|---|---|---|---|---|
| Normal login on the web form | The password check takes one of 8 slots and waits up to 5 s for one. One login attempt per account runs at a time, from the lockout check to the failure count, so four wrong passwords lock the account even when sent all at once. | "Invalid username or password, please try again." after a wrong password. Four wrong in 10 minutes locks the account. | Same wording when the server is too busy to check in time. A busy refusal does **not** count toward the lockout. | Security log says `refused` with the username when it was a busy refusal, not a wrong password. An unknown username now costs the same check as a wrong password, so timing does not reveal whether a username exists (best effort on LDAP installs). A locked account takes the same time to refuse as a wrong password. Other public pages that still do are RSDEV-1558. Spellings that differ only in case or accents count as the same account for queueing and for the four-try lockout. On SSO installs the emergency admin login returns to its own page with the usual wrong-credentials text when the server is busy; nothing is counted. |
| Signing, witnessing, operate-as, API key and OAuth app management, changing your own password (anything that asks for your password again) | Same 8 slots as login. | Whatever the dialog shows for a wrong password. | Same wording when busy. Never locks the account. | `ReauthenticatorImpl` logs the busy reason. |
| Getting an API token with username and password (`POST /oauth/token`, password grant) | Same 8 slots as login. No guess counting on this branch. | "Invalid user credentials." | No change on this branch. PR #1210 adds a per-account guess limit and the message "Too many failed attempts for this account. Please try again later."; update this row when it merges. | The public Inventory client credentials are accepted on this route by design. |
| Existing LDAP user logs in | Unchanged. The directory checks the password, not RSpace. | | | Not affected by the server-wide limit; one login attempt per account runs at a time, as for every login. |
| Self sign-up form (standalone, and SSO users completing the form) | Takes a ticket (4 shared) while the new password is hashed and the account saved. Refused at once if none is free; the form is returned with the values kept. | None. | "Too many sign-ups are being processed right now. Please try again in a moment." above the form. | A refused sign-up creates nothing. The signup code and reCAPTCHA remain the controls against unwanted sign-ups. |
| Sign-up succeeded but the server is too busy to log the new user in (also for Google sign-up on Community) | The account is created in full, including PI promotion and the audit event, then the user is sent to the login page. | Generic error page, account half set up. | Login page shows "Your account has been created, but the server is busy and could not sign you in automatically. Please sign in now using the same method you just used to sign up." | Only the automatic login failed. The user logs in normally. Google users sign in again with the Google button; the form-based user with username and password. |
| "Sign up with Google" on Community | Takes a ticket (4 shared). | None. | "Too many sign-ups are being processed right now. Please try again in a moment." in the sign-up dialog, the same message as the sign-up form. | Needs a valid Google token first; a request without one never reaches the ticket. |
| Password-reset link, entering the new password | Takes a ticket (4 shared) while the new password is hashed. Refused at once if none is free; the link stays usable. A link that was already used, has expired or is unknown is refused on submit, not only on open. | Used or expired link on submit: a raw error page. | Busy: "Too many password resets are being processed right now. Please try again in a moment." above the form. Used, expired or unknown link on submit: the same "This link has a problem!" page the link itself shows. | The 5-per-hour limit on *requesting* reset emails is unchanged. The link is marked used in the same database step that changes the password, so two simultaneous submits give one change and one "link has a problem" page. |
| Verification-password reset link (SSO and Google users), entering the new password | Same as the row above, with its own messages of the same wording. | Same as above. | Same as above. | Separate page, same shared tickets. The link is marked used in the same database step that changes the password, as in the row above. |
| Setting a verification password for the first time (SSO and Google users) - **PR #1204 only** | One request per user at a time. A duplicate (double click, two tabs) waits up to 5 s, sees the password is now set, and returns success without hashing. | Two silent successes, two hashes. | Duplicate: "Verification password set successfully" (same as the first). Waited too long: "The server is busy, please try again." | Two tabs with two different passwords: the first to start wins, both see success; on main the last to finish won. |
| Administrator imports users from CSV, or creates a user on the admin screen | No limit, no new message. Each new account's password is hashed one at a time inside the admin's request. | | | About 50 to 100 ms slower per account, so roughly half a minute to a minute extra per 500 rows. No memory spike. Very large imports reach proxy or browser timeouts sooner than before. |
| User changes their own password | The old-password check takes a login slot (row 2). The new password is hashed with no limit. | | | |
| First start after the upgrade | Every stored password is wrapped in Argon2 before the application serves requests. | | | Roughly 50 ms per user, logged with a row count and the time taken. Progress is committed every 100 users. A killed run leaves Liquibase's lock set; once it is released, the next start resumes with the rows not yet wrapped. An ordinary failure releases the lock itself. Rows the migration cannot read are logged at ERROR with the username; those users need an administrator reset. **Irreversible**: a downgrade needs the pre-upgrade database backup. Backups and binary logs from before the upgrade still hold the old SHA-256 hashes; rotate them under the normal retention policy. |
| First-ever login of an LDAP user when self sign-up is on | Takes a ticket (4 shared) while the new account is saved, after the directory has accepted the password. Refused at once if none is free; nothing is created. | None; a double click could show a sign-up error. | Login page shows "Too many sign-ups are being processed right now. Please try again in a moment." A refusal does not count toward the lockout. | Existing LDAP users are not affected (row 4). Needs a valid directory login first. |
| Submitting a password longer than 128 characters at login or any password prompt for an RSpace password - **PR #1204 only** | Refused at once as a wrong password; no hashing runs. Applies to passwords stored in RSpace (login and verification passwords), including the check run for an unknown username; a password checked by a directory (LDAP login and LDAP reauthentication) is sent to the directory unchanged. | None (the server hashed it). | The usual wrong-password message of that screen. | A genuine stored password over 128 characters can only come from a historic CSV import; reset it. |
| Passwords longer than 100 characters - **PR #1204 only** | The maximum password length is 128 on every form, including the promote-to-PI confirm box. | Forms stopped at 50 (100 on the confirm box). | | Existing passwords are unaffected. |


## What users can be told in one sentence

- "Busy" messages mean the server is protecting itself from too many password operations at
  once. Waiting a moment and retrying always works, and nothing was lost or locked.
- A wrong password still locks the login form after four tries in ten minutes. A busy refusal
  never does.
