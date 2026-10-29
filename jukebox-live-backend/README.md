# JukeBox Live - Backend

Spring Boot 3 + Java 21 + PostgreSQL backend for the JukeBox Live social jukebox platform.

## Local development

- JDK 21
- PostgreSQL 15+
- The checked-in Gradle wrapper targets Gradle 8.14.6, supported by the Spring Boot 3.3.5 plugin; install no separate Gradle distribution.

See the repository [Windows and IntelliJ setup guide](../README.md) for local database setup, ignored local credentials, LAN origins, host provisioning, library scanning, artwork discovery, and real-track playback steps. Never connect this application to a production database: Flyway and Hibernate may update the configured schema at startup.

From this directory in PowerShell, run the tests and backend build:

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

For local startup, create the ignored `src/main/resources/application-local.properties` file, then activate the `local` Spring profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat bootRun
```

The test profile uses H2. Do not put credentials in the tracked `application.properties` file.

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/auth/register` | POST | No | Create a guest account |
| `/api/auth/login` | POST | No | Guest login + join party |
| `/api/auth/admin-login` | POST | No | Host login for an ADMIN account |
| `/api/parties/create` | POST | Admin | Create new party |
| `/api/parties/{id}/guests` | GET | Admin | List party guests |
| `/api/library/search?q=` | GET | Yes | Search tracks |
| `/api/library/stats` | GET | Yes | Track count |
| `/api/library/scan` | POST | Admin | Scan music folder |
| `/api/queue?partyId=` | GET | Yes | Get queue |
| `/api/queue/add` | POST | Yes | Add song (1 credit) |
| `/api/queue/{id}/skip` | DELETE | Admin | Skip song |
| `/api/playback/now-playing` | GET | Yes | Current track |
| `/api/admin/credits/grant` | POST | Admin | Give credits |
| `/ws` | WebSocket | No | STOMP endpoint |

## WebSocket Topics

- `/topic/queue/{partyId}` - Queue updates
- `/topic/now-playing/{partyId}` - Current track
- `/topic/credits/{userId}` - Credit balance updates

## Security

- JWT authentication for protected API operations
- Party requests enforce the same IPv4 `/24` subnet as the host and use the direct connection IP (not forwarded headers)
- HTTP CORS and SockJS/WebSocket use the exact `jukebox.allowed-origins` allowlist; do not set wildcard origins
- Guest track requests spend server-side credits
