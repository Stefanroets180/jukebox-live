# JukeBox Live

JukeBox Live is a Next.js frontend in the repository root and a Spring Boot API in `jukebox-live-backend/`. The backend stores the library index and party data in PostgreSQL and reads audio files from the host computer; it does not copy or edit music files.

## Required software

| Software | Project requirement |
| --- | --- |
| JDK | Java 21 (set both IntelliJ Project SDK and Gradle JVM to 21) |
| Gradle | The checked-in wrapper targets Gradle 8.14.6, supported by the Spring Boot 3.3.5 plugin; no global Gradle install is needed. `gradlew.bat` and the matching wrapper JAR are included. |
| Node.js | 20.9.0 or newer, required by the installed Next.js 16.4.3. Node 22 LTS is a suitable local choice. |
| pnpm | 12.3.4, pinned in the root `package.json`. |
| PostgreSQL | 15 or newer, as stated by the backend project guide. The application does not pin an exact server minor version. |
| IntelliJ IDEA | Use a current edition with Java and Gradle support. The repository does not pin an IntelliJ version. |

On Windows, install Node.js, then enable the pinned pnpm version in PowerShell:

```powershell
corepack enable
corepack prepare pnpm@12.3.4 --activate
pnpm --version
```

If your Node installation does not include Corepack, install the same pinned version with `npm install --global pnpm@12.3.4`.

## Protect the database and credentials

Create a **new local development database** named `jukebox_live_dev`, owned by a local login role named `jukebox_dev` (use pgAdmin or PostgreSQL commands). Do not point this application at a production database. If you need data from an existing database, back it up and restore a copy into a separate development database first.

The backend runs Flyway migrations and has `spring.jpa.hibernate.ddl-auto=update`; starting it can change the schema of the database in `DB_URL`. The default URL now points to `localhost:5432/jukebox_live_dev` specifically to avoid silently selecting an existing `jukebox_live` database. These steps keep any existing or production database untouched as long as you use the dedicated dev URL. Scanning a folder inserts its metadata into that dev database; the backend does not edit the audio files.

For local development, create this **untracked, ignored** file:

`jukebox-live-backend/src/main/resources/application-local.properties`

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/jukebox_live_dev
spring.datasource.username=jukebox_dev
spring.datasource.password=REPLACE_WITH_YOUR_LOCAL_DATABASE_PASSWORD
jukebox.jwt.secret=REPLACE_WITH_A_UNIQUE_RANDOM_LOCAL_SECRET_OF_32_OR_MORE_CHARACTERS
jukebox.allowed-origins=http://localhost:3000,http://127.0.0.1:3000
```

Replace both placeholder values locally; do not commit this file or copy production credentials into it. `application-local.properties` is already ignored by the backend `.gitignore`. In the IntelliJ backend Run Configuration, set the environment variable `SPRING_PROFILES_ACTIVE=local`. Alternatively, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and `JUKEBOX_ALLOWED_ORIGINS` directly in that Run Configuration; keep `DB_PASSWORD` and `JWT_SECRET` out of frontend variables and out of source control.

The tracked backend defaults require `DB_PASSWORD` and `JWT_SECRET` rather than supplying committed fallbacks. `DB_URL` defaults to the local `jukebox_live_dev` database, `DB_USERNAME` defaults to `postgres`, and `JUKEBOX_ALLOWED_ORIGINS` defaults to the two localhost frontend origins.

The frontend optionally accepts a root `.env.local` containing a **public URL only**:

```dotenv
NEXT_PUBLIC_API_URL=http://localhost:8082
```

This is optional: without it, the browser derives the API host from the page hostname and uses port 8082. `.env.local` is ignored. Never put database passwords, JWT signing keys, or other secrets in a `NEXT_PUBLIC_*` variable.

## Install, test, build, and run

From the repository root, install the frontend dependencies using the committed lockfile:

```powershell
pnpm install --frozen-lockfile
```

Frontend tests and production build:

```powershell
pnpm test
pnpm build
```

Backend commands run from `jukebox-live-backend/` in PowerShell. The Gradle wrapper downloads Gradle 8.14.6 the first time it is run and verifies its SHA-256 checksum. Tests use the `test` profile and its in-memory H2 database, so they do not need your local PostgreSQL database.

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

For a local backend run, activate the ignored `local` profile before starting `bootRun`:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

`clean build` is the backend production build and runs the tests; `bootRun` starts the development API. For a normal IntelliJ workflow, open/import `jukebox-live-backend/build.gradle` as a Gradle project, select JDK 21 as the Gradle JVM, and run the `test`, `build`, or `bootRun` Gradle task. Keep `SPRING_PROFILES_ACTIVE=local` in the backend Run Configuration.

Run both services in separate terminals or IDE run configurations:

1. Start PostgreSQL and the backend with the `local` profile.
2. From the repository root, start the frontend with `pnpm dev`.
3. Open `http://localhost:3000`. The API listens on `http://localhost:8082`.

## Sign in as a host and play a real library track

The public registration endpoint always creates a `USER`; the repository does not seed an administrator. If you already have a local `ADMIN` account, use it. Otherwise, only in your new local development database:

1. Start both services and register an account at `http://localhost:3000/join?mode=register`.
2. In pgAdmin, connected specifically to `jukebox_live_dev`, promote that local account:

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE username = 'your_local_username';
   ```

   Sign in again after the role change. Never run this against production or a database you intend to keep unchanged.
3. Sign in at `http://localhost:3000/join?mode=admin`, then open `/host` and create a party. Enter the Windows music-folder path, for example `C:\Users\YourName\Music`.
4. Open `/admin/library`, enter the same folder path, and scan it. Party creation stores the folder path but does **not** scan it automatically. The scanner recognizes `.mp3`, `.m4a`, `.flac`, and `.wav`; begin with an MP3 for a playback check. The backend only reads/indexes the files.
5. Use a separate browser profile or a second device to register a guest, then sign in at `/join?party=PARTYCODE`. A new guest starts with zero credits. From the host session, open `/admin/party-settings` and grant that guest credits by username. In the guest session, search `/dashboard` and queue a track.
6. Return to the host session and open `/player`. Keep only one dedicated player tab open for the party because playback uses a single-player lease. If the browser blocks autoplay, press the player’s Play button.

## LAN access without relaxing the party checks

The backend already binds to `0.0.0.0:8082`. Next.js 16.4.3’s `next dev` also defaults to `0.0.0.0`; to make the intent explicit, run this from the repository root:

```powershell
pnpm exec next dev --hostname 0.0.0.0 --port 3000
```

Find the Windows PC’s private IPv4 address with `ipconfig` (for example, `192.168.1.23`). Add the **exact** frontend origin to `jukebox.allowed-origins` in the ignored local properties file, then restart the backend:

```properties
jukebox.allowed-origins=http://localhost:3000,http://127.0.0.1:3000,http://192.168.1.23:3000
```

Replace the example address with the PC’s actual address. If root `.env.local` sets `NEXT_PUBLIC_API_URL` to `localhost`, change it to `http://192.168.1.23:8082` for LAN testing, or remove the override so the browser derives the API host from the page hostname; restart Next.js after changing it. On Windows, allow inbound TCP ports 3000 and 8082 on the **Private** firewall profile only. On another device on the same Wi-Fi, browse to `http://192.168.1.23:3000`. Do not use `*` origins, disable `PartyPresenceFilter`, expose these ports on a public network, or add router port-forwarding.

For a party joined from other devices, open the host app using the PC’s LAN URL **before** creating the party. The backend records the request’s direct host IP and checks party traffic against the same IPv4 `/24` subnet; it does not trust `X-Forwarded-For`. IPv6 subnet matching is not implemented. Keeping the host and guests on the same local IPv4 subnet preserves the existing party restriction.

## Artwork discovery

No artwork API key or extra environment variable is required. The backend queries the public MusicBrainz and Cover Art Archive services, so the backend computer needs outbound internet access. Library scans do not automatically perform artwork lookups: scan first, then use **Find missing artwork** on `/admin/library` and review/select matches. MusicBrainz requests are rate-limited by the backend to about one request per second.

The optional `MUSICBRAINZ_USER_AGENT` environment variable (or `jukebox.artwork.musicbrainz-user-agent` property in the local profile) overrides the default identifying User-Agent; it is not a credential. `MUSIC_PATH` is not used by the current scan endpoint—the administrator supplies the path in the library screen, and the path must be readable by the backend process.

## What the source download contains

The project download contains the Next.js source, `package.json` and `pnpm-lock.yaml`; the Spring Boot source, tests, database migrations, Gradle wrapper scripts/properties/JAR; and this setup guide plus the backend guide. It does not include a PostgreSQL database, your music files, `.env.local`, or `application-local.properties`. Recreate those local settings after extracting; keep all database and JWT secrets on the PC.
