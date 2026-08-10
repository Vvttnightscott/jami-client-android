# Adding Contacts Across iOS and Android — Troubleshooting

**Symptom:** Android users find each other fine, but an iPhone (App Store) user
and an Android user cannot find each other when searching by name — in either
direction.

On the **default** Jami servers, iOS and Android share the same name directory
and *are* meant to find each other. So this is almost never a network problem —
it is usually a mix-up between two different kinds of "name."

## The two names in Jami (only one is searchable)

| Name | What it is | Searchable by strangers? |
|------|------------|--------------------------|
| **Profile / display name** | A free-form label on your account (e.g. "Grandma"). Set during account creation. | ❌ **No** — only shown *after* you are already connected. |
| **Registered public username** | A unique, permanent, lowercase name registered on the name server. | ✅ **Yes** — this is what username search resolves. |

If a user only set a **profile name**, **nobody** can find them by typing it —
not iOS, not Android. Android-to-Android often *appears* to work because those
people added each other by **QR code** (or were already contacts), so they see
each other's profile names — username search was never actually involved.

## Isolate the fault in two checks

1. **Check for a Registered Name.** On each account (iOS and Android), open the
   account/profile screen. Does it show a **"Registered Name"**? If instead it
   offers **"Register public username,"** that account is **not** searchable.
2. **Add by QR code / full Jami ID** across platforms (not by username). If that
   connects (it should), the network is healthy and the issue is purely
   username registration.

```
Can you add across iOS/Android by QR code?
├─ YES  → network is fine. The "can't find by name" issue is missing/mismatched
│         registered usernames. Register a public username (below).
└─ NO   → not a username issue. Check that both sides use the SAME name server
          AND bootstrap under Account → Advanced settings (defaults:
          https://ns.jami.net and bootstrap.jami.net). A mismatch there, or a
          connectivity/push problem, is the cause.
```

## The fix

- **Register a public username for each user:** Account settings →
  **Register public username** → choose a unique **lowercase** name. Once it
  shows as a **Registered Name**, others can find them by searching that exact
  name — across iOS and Android.
- **Or skip usernames entirely (most reliable):** add each other by **QR code**.
  Scanning works across iOS ↔ Android regardless of usernames. Sit together
  once, scan each other's codes, done. This is the recommended path for
  non-technical users.

## Tips

- Registered usernames are **lowercase and exact** — search `alice`, not `Alice`.
- Registration can take a few seconds and needs internet; confirm it succeeded
  (it becomes the account's "Registered Name").
- A registered username is **permanent and unique** — it cannot be changed or
  reused later, so choose deliberately.

## When it really *is* a server issue

Only if the **QR/Jami-ID** test also fails, or if your deployment uses
**custom** infrastructure: make sure every client (iOS included) points at the
**same** name server, bootstrap, and DHT proxy under **Account → Advanced
settings**. Cross-platform discovery only works when both platforms query the
same directory. For a private, consistent directory across a mixed iOS/Android
group, see `self-hosting-jams.md`.
