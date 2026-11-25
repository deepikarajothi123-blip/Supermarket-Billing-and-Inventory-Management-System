# Supermarket Billing and Inventory Management System

A Java-first supermarket management application built with Vaadin, Spring Boot, JDBC and PostgreSQL.

## Features

- Dashboard with today's revenue, product count, category count and low-stock count
- Product create, edit and delete
- Category management
- Stock replenishment and stock checks
- Billing across four named counters
- Percentage and fixed-amount discounts
- Transactional checkout: stock is rechecked and reduced when a sale is saved
- Sales history and bill details
- PostgreSQL persistence on Render
- H2 file database for local development

## Flat repository layout

Keep these files together in the repository root:

- `Main.java`
- `pom.xml`
- `application.properties`
- `Dockerfile`
- `render.yaml`
- `README.md`
- `.gitignore`

There is deliberately no `src/` or `docker/` directory. Maven is configured to compile `Main.java` directly from the repository root. Render is configured to build `./Dockerfile` with `.` as its Docker context.

## Run locally

Requirements: JDK 21 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

The local database is stored in `supermarket-local-db` in the project directory. Do not commit that database file.

## Build locally

```bash
mvn -DskipTests package
```

The executable JAR is created under `target/`.

## Push to GitHub

1. Create an empty GitHub repository named `SupermarketBillingSystem`.
2. Put the seven files above directly in the repository root.
3. Open a terminal in that directory and run:

```bash
git init
git add Main.java pom.xml application.properties Dockerfile render.yaml README.md .gitignore
git commit -m "Build supermarket billing and inventory application"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/SupermarketBillingSystem.git
git push -u origin main
```

Replace `YOUR-USERNAME` with your GitHub username.

## Deploy on Render

Recommended first deployment: use the Blueprint flow.

1. Push the files to GitHub.
2. In Render, choose **New → Blueprint**.
3. Connect the GitHub repository.
4. Render detects `render.yaml`. Review the web service and database, then apply.
5. Wait for the Docker build and deployment to finish.
6. Open the `onrender.com` URL shown by Render.

The web service uses the root `Dockerfile`; no `/src` or `/docker` path is referenced.

## Important free-plan notes

- Render Free web services can spin down after inactivity. The first request after a spin-down can take noticeably longer. No code setting can guarantee an instant cold start on the free plan.
- Render's free PostgreSQL database has an expiry/availability limit. Check the current Render database terms before relying on it for real business records. Export backups regularly.
- This starter is intended for a student/demo project. Before real retail use, add authentication/roles, audit logging, tax rules, receipt printing, backups and stronger concurrency controls.

## Database connection

Render's Blueprint maps the database host, port, name, username and password into environment variables. The application builds the JDBC PostgreSQL URL from those values.
