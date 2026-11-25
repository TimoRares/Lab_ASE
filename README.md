# Instakill

Instakill is a Spring Boot 3 / Vue 3 starter for an Instagram-style social feed. The project starts as a monolith but is structured for future extraction into independent services.

## Project layout

- `backend/` – Spring Boot application exposing REST and MVC endpoints, backed by SQLite.
- `frontend/` – Vue 3 SPA that consumes the REST API.
- `scripts/` – helper tooling including Gradle bootstrap.

## First run

Make sure you have Java 23 installed. 

```bash
# From the project root
./scripts/bootstrap-gradle.sh
./backend/gradlew bootRun
```

On Windows PowerShell:

```powershell
./scripts/bootstrap-gradle.ps1
./backend/gradlew.bat bootRun
```

The bootstrap downloads Gradle 8.9 and generates a local wrapper (ignored by git). Subsequent builds can use the wrapper directly.

## Useful endpoints

- Web feed: http://localhost:8080/feed
- Login page: http://localhost:8080/auth/login
- REST OpenAPI UI: http://localhost:8080/api/swagger
- Health: http://localhost:8080/actuator/health

## Authentication

Authentication uses JWT stored in an `ACCESS_TOKEN` HttpOnly cookie. REST clients can authenticate by capturing the cookie returned from `/api/auth/login` or by sending the token manually.

Seed accounts (development profile):

| Username | Email             | Password |
|----------|-------------------|----------|
| alice    | alice@example.com | password |
| bob      | bob@example.com   | password |

## Running tests

```bash
cd backend
./gradlew test
```

The tests cover feed ranking, like toggling, MVC flows, and an end-to-end REST happy-path.

## Frontend

The Vue application lives in `frontend/`. Install dependencies and start the dev server with:

```bash
cd frontend
npm install
npm run dev
```

With default settings, you will be able to access the frontend on `http://localhost:5173/` 
It assumes the backend is running at `http://localhost:8080`.
Check `vite.config.js` for these settings.

## Lab 3 – upload media

- `POST /api/media` (multipart, câmp `file`, necesită login) → `201 {"mediaUrl": "/media/<uuid>.<ext>"}`
- Fișierele se salvează în `media.base-path` (implicit `./media`, creat automat) și sunt servite la `/media/**`
- Limite: `spring.servlet.multipart.max-file-size / max-request-size = 200MB`
- Extensii permise: jpg, jpeg, png, gif, webp, mp4, mov, webm
- Frontend: la alegerea unui fișier se face upload, iar `mediaUrl` devine `imageUrl` al postării (video → `<video controls>`)

## Lab 4 – notificări și procesare imagini

**Notificare la postare nouă (ntfy.sh)**
- `PostService.createPost` publică un `PostCreatedEvent`; `PostNotificationListener` îl primește *după commit* și trimite asincron (executor `notificationExecutor`) un POST HTTP către ntfy.sh cu username, ID-ul postării și timestamp.
- Notificările sunt active doar dacă topicul e setat. Alege un nume greu de ghicit și pornește backend-ul cu:
  ```powershell
  $env:NTFY_TOPIC="instakill-numele-tau-1234"
  ..\.gradle\bootstrap\gradle-8.9\bin\gradle.bat bootRun --args="--server.port=8081"
  ```
  Apoi deschide `https://ntfy.sh/instakill-numele-tau-1234` în browser (sau abonează-te din aplicația ntfy) și creează o postare.
- O notificare eșuată este doar logată, nu afectează crearea postării.

**Procesare imagini la upload**
- `MediaController` trimite imaginile `jpg/jpeg/png` prin `ImageProcessingService` (pachetul `com.instakill.media`, fără dependențe web, deci mutabil ulterior într-un worker): scalare la maximum 300 px lățime cu păstrarea proporțiilor (fără mărire) și watermark „Instakill Demo” în colțul din dreapta-jos.
- Setări în `application.yaml`: `instakill.media.max-width` și `instakill.media.watermark-text`.
- `gif`, `webp` și videoclipurile se salvează neschimbate (un GIF animat și-ar pierde animația). Un fișier cu extensie de imagine dar conținut invalid primește `400`.

## Lab 5 – analytics și populare bază de date

**Analytics** (`GET /api/analytics/me`, doar pentru utilizatorul logat; în frontend, butonul *Analytics*)
- Postări, like-uri și comentarii pe săptămână (săptămâna începe luni; fiecare tip se grupează după data propriei creări)
- Top 5 postări după engagement (like-uri + comentarii)
- Raport like-uri / comentarii pentru toate postările
- Media like-uri / postare pentru poze vs. videoclipuri (tipul se deduce din extensia lui `image_url`: `.mp4`, `.mov`, `.webm` = video)
- Locul în top X% dintre utilizatori după engagement mediu / postare
- Totul se calculează cu SQL în `AnalyticsRepository`; indexurile necesare se creează la pornire (`AnalyticsIndexInitializer`).

**Populare bază de date** (`backend/populate_db.py`, cu backend-ul oprit)
```powershell
pip install faker bcrypt
cd backend
python populate_db.py                    # 10.000 utilizatori, 10-20 postări/utilizator, 0-10 comentarii, 0-20 like-uri
python populate_db.py --users 500        # mai puțini utilizatori
python populate_db.py --help             # toți parametrii
```
Scriptul șterge comentariile, like-urile, postările și utilizatorii (mai puțin `alice` și `bob`), adaugă `charlie` (parola `password`, hash bcrypt) și generează datele în loturi, într-o tranzacție. Toate postările au data de creare în ultima săptămână. La final verifică lipsa înregistrărilor orfane.
