# Glossary

French driving terms and project-specific vocabulary, defined plainly. Useful for non-French collaborators and for keeping translations consistent.

## Driving and the French permit system

**AAC (apprentissage anticipé de la conduite)** — French driving scheme where a learner aged 15 or older starts driving with an instructor, then drives with an adult accompagnateur for at least one year and 3000 km before taking the permit exam. The earliest a learner can begin the AAC phase is age 15; the exam can be taken at 17 with the permit becoming valid at 18.

**Conduite supervisée** — Alternative scheme for adults (18+). Same idea as AAC but with shorter minimums: 3 months and 1000 km accompanied driving after failing the permit exam or after the auto-école judges the student is not ready for the standalone exam.

**Conduite encadrée** — A third, less common variant for students enrolled in professional driving training programs. Not specifically supported yet.

**Accompagnateur** — The accompanying adult during AAC or conduite supervisée driving. Must hold a French driving permit for at least 5 years uninterrupted, have no driving offenses that suspended their permit, and be approved by the learner's insurance. Most often a parent.

**Apprenti / élève conducteur** — The learner driver. In the app, this is "the driver."

**Livret d'apprentissage** — The paper logbook the auto-école provides at the start of training. The accompagnateur signs entries after each accompanied drive. This is the document the examiner sees. Kilomètre is a parallel record, not a replacement.

**Auto-école** — Driving school. In France, you must enroll in one to learn to drive legally. The auto-école provides the instructor lessons (typically 20 hours minimum) and certifies the student as ready for accompanied driving.

**Code de la route** — The theory portion of the driving exam. A separate test from the practical exam.

**Examen pratique / épreuve pratique** — The practical driving test. 32 minutes of driving with an examiner. Kilomètre helps the learner be ready by tracking real practice, but doesn't influence the test directly.

**Permis B** — The standard car driving permit in France. The end goal of AAC and conduite supervisée.

**Rendez-vous pédagogiques** — Mandatory check-in lessons during AAC. Three sessions: one after about 1000 km, one before the practical exam, and one final review. Kilomètre's progress tab makes spotting these milestones easy.

**Disque A** — The red "A" sticker (for "apprenti") that goes on the back of any car driven by a permit holder in their first three years (or first two years for AAC graduates). Also applies during AAC and conduite supervisée drives. Not enforced by the app, just mentioned for context.

## Roads (highway tags used in road classification)

OpenStreetMap classifies roads using `highway=*` tags. Kilomètre groups them for display:

**Autoroute** — `highway=motorway` and `highway=motorway_link`. French autoroutes (often paid, 130 km/h limit). The big ones for AAC milestones because new drivers often avoid them until later in training.

**Route nationale / départementale** — `highway=primary`, `highway=primary_link`, sometimes `highway=trunk`. Major non-autoroute roads outside cities. Typical limits 80-110 km/h.

**Route** — `highway=secondary`, `highway=tertiary`, plus their `_link` variants. The "country road" category.

**Ville** — `highway=residential`, `highway=living_street`, `highway=unclassified` within urban areas. City streets, 30-50 km/h limits.

**Other** — `highway=service`, `highway=track`, `highway=path`. Parking lots, service roads, off-road. Usually a tiny fraction.

## Permissions (Android-specific)

**ACCESS_FINE_LOCATION** — Permission to read precise GPS. Required for session recording.

**ACCESS_BACKGROUND_LOCATION** — Permission to read location while the app is not in the foreground. NOT requested by Kilomètre.

**FOREGROUND_SERVICE_LOCATION** — Permission required on Android 14+ to declare a foreground service that uses location.

**POST_NOTIFICATIONS** — Permission required on Android 13+ to show notifications, including the persistent service notification.

**REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** — Permission required to prompt the user to whitelist the app from Doze and aggressive battery saving.

## Project-specific terms

**Session** — One driving event from START to STOP (manual or auto). The atomic unit of the app.

**Driver** — The learner. The current version supports one driver per app installation.

**Accompagnateur** — As defined above, but in the app it's a record with name, relation, and optional default signature.

**State** — A session's status: ACTIVE, DRAFT, SIGNED, or DISCARDED.

**Hash chain** — The cryptographic structure linking SIGNED sessions so tampering is detectable.

**contentHash** — SHA-256 of a session's canonical payload, including the previous session's contentHash.

**signatureHash** — SHA-256 of the contentHash + signature PNG bytes + signedAt timestamp.

**prevHash** — A session's `contentHash` value of the previous SIGNED session in the chain.

**gpsPointsHash** — Separate SHA-256 of all GPS points in a session, included in the canonical payload.

**Canonical payload** — A deterministic string representation of a session's data, used for hashing.

**metadataCore** — Session metadata included in the hash chain.

**metadataEnrichment** — Session metadata added after signing (weather, road type), NOT in the hash chain.

**Auto-pause** — A session enters paused state after 5 minutes of speed < 3 km/h. Paused time excluded from duration.

**Auto-stop** — A session automatically stops after 90 minutes of sustained speed < 3 km/h.

**Adaptive sampling** — GPS sampling rate adjusts to speed.

## Acronyms used throughout the docs

- **AAC** — Apprentissage anticipé de la conduite (see above).
- **AGP** — Android Gradle Plugin.
- **AOSP** — Android Open Source Project.
- **API** — Application Programming Interface OR Android API level (depending on context).
- **APK** — Android Package, the file format for app distribution.
- **DAO** — Data Access Object (Room concept).
- **DI** — Dependency Injection.
- **DRM** — Digital Rights Management. Explicitly rejected for this project.
- **FOSS** — Free and Open Source Software.
- **GDPR** — General Data Protection Regulation (EU privacy law).
- **GPS** — Global Positioning System.
- **GPX** — GPS Exchange Format, an XML-based file format for GPS tracks.
- **JNI** — Java Native Interface, used to call C/C++ from JVM languages.
- **JVM** — Java Virtual Machine.
- **KSP** — Kotlin Symbol Processing, the modern Kotlin annotation processor (replaces KAPT).
- **MVI** — Model-View-Intent, a UI architecture pattern (rejected).
- **MVVM** — Model-View-ViewModel, the chosen UI architecture pattern.
- **OEM** — Original Equipment Manufacturer. Samsung, Xiaomi, etc.
- **OSM** — OpenStreetMap.
- **PBKDF2** — Password-Based Key Derivation Function 2, used to derive encryption keys from passphrases.
- **PII** — Personally Identifiable Information.
- **PNG** — Portable Network Graphics, the image format for signatures.
- **ROM** — In Android context, a system image (often custom, e.g. LineageOS).
- **SAF** — Storage Access Framework, the Android API for letting users pick files and folders.
- **SDK** — Software Development Kit.
- **SHA-256** — Cryptographic hash function used throughout.
- **SQL / SQLite** — Database query language / the embedded database engine Android uses.
- **TLD** — Top-Level Domain (.com, .dev, .fr).
- **URI** — Uniform Resource Identifier.
- **UTC** — Coordinated Universal Time.
- **WPA2** — WiFi Protected Access 2 (mentioned for unrelated context — not used by this app).
