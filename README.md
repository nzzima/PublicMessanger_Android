# PublicMessanger for Android

An Android messenger built on Firebase, with messages encrypted on the device before they
ever reach the database.

Jetpack Compose, MVVM, Koin. Around 12 000 lines of Kotlin, 379 JVM tests and 15
instrumented ones.

It is the second client of [PublicMessanger for iOS](https://github.com/nzzima/PublicMessanger_iOS),
not a port of it. No line of code is shared. What is shared is the Firebase project, the
document schema and the encryption protocol — the two apps talk to each other, and that
has been proven in both directions, attachments included.

---

## What works

| | |
|---|---|
| **Sign in** | Email and password, plus a biometric lock on launch and on returning after a minute away |
| **Registration** | Unique logins, held by a registry rather than by a client-side check |
| **Conversations** | One-to-one and groups, with members added, removed, and left |
| **Encryption** | Sealed on the device; the key is rotated when someone is removed |
| **Attachments** | Voice notes, photos and a point on a map — all inside Firestore |
| **Profiles** | Login, name, note and avatar, editable, with the login rename handled |
| **Presence** | "Online" and when someone was last seen — a dot in the chat list, a second line in the conversation title |
| **Receipts** | One tick once the message is in the database, two once everyone else has read it |
| **Failures** | Every sentence a person is shown is Russian, word for word the same as on iOS |

Three screens exist here that iOS has no need for: one that finishes a registration which
broke halfway, one that says the session is dead rather than pretending the network
dropped, and the fork that appears when an account already carries someone else's public
key.

The theme is dark and there is only one; a light scheme is absent on purpose, as on iOS.

## No key transfer, and what that costs

**Android accounts are new accounts.** There is no way to carry a key over from an iPhone,
the `SMK1` transfer format was never implemented here, and the matching feature was deleted
from the iOS app on 2026-09-07. The rule holds on both sides.

The price is stated rather than hidden. The master key lives in the Android Keystore, which
is bound to the hardware, and Auto Backup does not restore it; there is no iCloud Keychain
equivalent here. **Uninstall the app or change the phone, and the old conversations on
Android do not open again.** Nothing about that is a bug report.

A consequence worth knowing before signing in: the first sign-in publishes your public half
to the profile. On an account that already carries a different one, the app stops and asks
rather than overwriting it — overwriting would make that account's history unreadable for
good.

## Architecture

MVVM, one package per feature, and inside each the same three floors:

```
app/src/main/java/com/nzzima/secretmessanger/
├─ auth/  chats/  contacts/  messanger/  profile/
│  ├─ data/impl/       Firestore and the platform
│  ├─ domain/api/      the interfaces the layer promises
│  ├─ domain/impl/     the work itself
│  ├─ domain/models/   types and typed failures
│  └─ ui/              Screen + UiState + ViewModel
├─ crypto/             keys, sealing, the conversation key
├─ avatar/ photo/ voice/ presence/ lock/ session/
├─ di/                 Koin: Data / Repository / Interactor / ViewModel modules
├─ ui/                 navigation, theme, shared composables
└─ utils/
```

A screen never reaches past its ViewModel, a ViewModel never reaches past an interface in
`domain/api`, and Firestore is touched only from `data/impl`. Koin wires the four module
files and nothing else knows what implements what.

Comments here are strict contract documentation — what a thing is, what it takes, what it
returns, which invariants hold. The reasoning behind a decision is not in the code; it is
in the project's own notes, where it can be argued with.

## Data in Firestore

The schema is shared with the iOS client and defined by it:

```
users/{uid}              login, name, someInfo, publicKey, avatarVersion
logins/{login}           uid                      — a document per taken name
avatars/{uid}            data (bytes), version
presence/{uid}           lastSeen                 — a heartbeat while the app is unlocked
conversation/{id}        users, owner, logins, lastMessage, date,
                         convoKeys, keyVersion, readUpTo
   messages/{id}         senderId, message, type, enc, v, date
   audio/{id}            senderId, data (bytes)   — voice notes
   images/{id}           senderId, data (bytes)   — photos
```

Media lives inside documents rather than in Cloud Storage: Storage rules cannot read
Firestore, so "only members of this conversation may open this file" cannot be expressed
there at all. Bytes sit in their own subcollection, never in the message, because a chat
re-reads its last fifty messages on every change.

## Encryption

Every user has a permanent Curve25519 key pair. The private half is sealed under a master
key from the Android Keystore and never leaves the device; the public half is published to
the profile.

X25519 comes from BouncyCastle, and that is the whole reason the dependency is here: AES-GCM
and the HMAC that HKDF is built on come from the system `javax.crypto` and exist on every
version. Waiting for the platform to supply X25519 would have meant `minSdk 34`, cutting out
half the phones in use for one primitive. Tink was declined for wrapping keys and ciphertext
in a format of its own, when the requirement is to match CryptoKit byte for byte; libsodium
for dragging native libraries along for every architecture.

Each conversation has a symmetric key, kept in the conversation header sealed separately
for every member:

```
convoKeys: { "<uid>_<version>": "<ephemeral public key>.<ciphertext>" }
keyVersion: <current version>
```

The conversation id and the key version are mixed into the HKDF output, so a sealed key
cannot be moved to another conversation or replayed as an older version. Removing someone
from a group issues a new version for everyone else; older versions stay in the header so
that history remains readable.

**Interoperability with CryptoKit is tested, not assumed.** Kotlin opens a fixture sealed
by CryptoKit and CryptoKit opens what Kotlin sealed — and since 2026-09-11 that includes
attachments: a photo and a voice note sent from Android open and play on an iPhone.

> **What encryption does not hide:** `senderId`, timestamps, the member list, logins, the
> avatar, and the fact and frequency of a conversation. Firestore sees all of it. The
> content is closed; the structure is not.

## Security rules

The rules and the composite index live in the iOS repository — one database serves both
apps, and two copies of a rule file would eventually disagree. See `firestore.rules` and
`firestore.indexes.json` in
[PublicMessanger_iOS](https://github.com/nzzima/PublicMessanger_iOS).

`google-services.json` is committed on purpose. It holds project identifiers, not secrets,
and what protects the database is the rules together with Firebase Auth — not the file
being hard to find. The same file sits openly in the iOS repository.

## Building

`compileSdk 37` · `targetSdk 37` · `minSdk 26` · JVM target 17. Versions are pinned in
`gradle/libs.versions.toml`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/` at roughly 11 MB.

> **AGP 9 carries Kotlin support itself.** The `org.jetbrains.kotlin.android` plugin is not
> applied; adding it back makes the build fail with a direct instruction to remove it.

## Tests

379 JVM tests run without a device:

```bash
./gradlew testDebugUnitTest
```

Fifteen more need real hardware, because what they check does not exist off it: the
Keystore-backed identity key, and the avatar and photo encoders with their size budgets.

> **Install and run those by hand.** `connectedDebugAndroidTest` uninstalls both packages
> when it finishes, and that takes the session and the permanent key with it — on a phone
> holding a live account this means the history on it is gone for good.
>
> ```bash
> ./gradlew assembleDebugAndroidTest
> adb install -r app/build/outputs/apk/debug/app-debug.apk
> adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
> adb shell am instrument -w com.nzzima.secretmessanger.test/androidx.test.runner.AndroidJUnitRunner
> ```

The JVM suite covers the same ground as the iOS one, deliberately: crypto (mostly the
negative cases), the encoders and their budgets, login rules, the deterministic
conversation id, presence wording, read receipts, the order in which a conversation is
erased, the Russian text of every failure, and the document schema in both directions.

Not covered: anything that actually talks to Firestore, and the screens.
