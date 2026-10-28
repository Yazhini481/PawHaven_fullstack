# PawHaven Backend

The backend is a Java 17 Jakarta Servlet application for Tomcat 10.1, packaged as `pawhaven.war`. Tomcat serves the existing HTML/CSS/JavaScript and the `/api/*` endpoints from the same origin.

## Requirements

- JDK 17 or newer
- Apache Maven 3.9+
- Apache Tomcat 10.1+
- PostgreSQL 14 or newer

## Database setup

Create the database, then apply the schema and starter pet records:

```sql
CREATE DATABASE pawhaven;
```

Run `psql -d pawhaven -f db/schema.sql`. Set these environment variables for the Tomcat process:

- `PAWHAVEN_DB_URL` (default `jdbc:postgresql://localhost:5432/pawhaven`)
- `PAWHAVEN_DB_USER` (default `postgres`)
- `PAWHAVEN_DB_PASSWORD` (default `postgres`)

Use a dedicated database user and a non-default password outside local development.

When updating an existing installation, rerun `db/schema.sql` as `pawhaven_app`; it adds the shelter/applicant ownership columns, migrates the old rescue `condition` column, and enables the `admin` role without deleting existing records. For example, from PowerShell:

```powershell
psql -h localhost -U pawhaven_app -d pawhaven -v ON_ERROR_STOP=1 -f .\db\schema.sql
```

Admin privileges are not available through public registration. Register a trusted account, promote it manually as a database administrator, then log out and back in so the session picks up the new role:

```sql
UPDATE users SET role = 'admin' WHERE email = 'trusted-admin@example.com';
```

To make existing pets visible to a shelter, assign them to that shelter account. Unassigned pets and rescue reports remain visible only to admins; adoption applications follow their pet's shelter assignment.

```sql
UPDATE pets
SET shelter_id = (SELECT id FROM users WHERE email = 'shelter@example.com' AND role = 'shelter')
WHERE id IN (1, 2, 3);
```

## Build and deploy

Run `mvn clean package`, then copy `target/pawhaven.war` to Tomcat's `webapps` directory and start Tomcat. Open `http://localhost:8080/pawhaven/`.

Uploaded rescue images are limited to 5 MB and stored under `${catalina.base}/pawhaven-uploads`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/pets` | List pets; optional `search`, `species`, `breed`, `gender`, and `location` filters |
| GET | `/api/pets/{id}` | Pet profile |
| POST | `/api/auth/register` | Create adopter or shelter account and start a session |
| POST | `/api/auth/login` | Start a session |
| POST | `/api/auth/logout` | End the current session |
| GET | `/api/auth/me` | Current session user |
| GET | `/api/dashboard/shelter` | Shelter-owned counts and recent owned-pet applications/assigned rescues (shelter session required) |
| GET | `/api/dashboard/admin` | Platform-wide counts and recent applications/rescues (admin session required) |
| GET | `/api/dashboard/user` | Current adopter's applications and rescue reports (user session required) |
| POST | `/api/shelter/pets` | Add a pet owned by the signed-in shelter |
| POST | `/api/dashboard/adoptions/{id}/status` | Approve/reject an application as admin or its pet's shelter |
| POST | `/api/dashboard/rescues/{id}/status` | Update a rescue assigned to the signed-in shelter, or any rescue as admin |
| POST | `/api/dashboard/rescues/{id}/assign` | Assign a rescue to a shelter (admin session required) |
| POST | `/api/dashboard/pets/{id}/assign` | Assign an existing pet to a shelter (admin session required) |
| POST | `/api/adoption-requests` | Save an adoption application |
| POST | `/api/rescue-reports` | Save a rescue report and optional image |
| POST | `/api/contact-messages` | Save a contact message |

JSON endpoints return JSON errors with an `error` field. Rescue reports use multipart form data when an image is attached.