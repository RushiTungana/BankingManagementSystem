# Deploy the Banking Management System for free

This project is configured for a free portfolio demo using **GitHub + Render (Java web app) + Aiven (MySQL)**. Free-tier terms can change. Render free web services sleep after inactivity, so the first request may take about a minute. Aiven's free MySQL can be powered off after extended inactivity. This is a demo, not production banking software.

## A. Push this project to GitHub
1. Create a **public** repository named `Banking-Management-System`.
2. Upload the contents of this project (the `Dockerfile`, `render.yaml`, `pom.xml`, `src`, and `database` folders/files) into the repository root.
3. Do not upload passwords or database certificates/secrets.

## B. Create a free MySQL database on Aiven
1. Sign up at https://aiven.io/ and create a **MySQL** service on the Free plan.
2. Open the service's connection information and copy the host, port, database name, username, and password.
3. Open Aiven's SQL console or connect with a database client, and run the table definitions from `database/banking_db.sql`. If the SQL console has already selected the database Aiven created for you, omit the first two lines (`CREATE DATABASE ...` and `USE banking_db;`) and run the `CREATE TABLE` statements.
4. Keep the database password private. Do not place it in GitHub.

## C. Deploy the Java web app on Render
1. Sign up at https://render.com/ and connect your GitHub account.
2. Choose **New → Web Service**, then select the repository.
3. Select **Docker** as the runtime (Render should detect `Dockerfile`). Choose the **Free** instance.
4. In the service's **Environment** settings, add these variables using your Aiven connection details:
   - `DB_URL` = `jdbc:mysql://YOUR_AIVEN_HOST:YOUR_AIVEN_PORT/YOUR_DATABASE?sslMode=REQUIRED&serverTimezone=UTC`
   - `DB_USER` = your Aiven database username
   - `DB_PASSWORD` = your Aiven database password
   Replace the uppercase placeholders with the real values. Do not include angle brackets.
5. Click **Create Web Service** and wait for the build/deploy to finish.
6. Open the `onrender.com` URL shown in the Render dashboard. This is the live URL to put in your portfolio.

## D. Test before adding it to your resume
- Create a test account using an invented name/email.
- Check the account list and balance.
- Deposit a small test amount and check transaction history.
- Test a withdrawal larger than the balance; it should be rejected.
- Check Render logs if the UI reports a database connection error.

## Important limitations
- Free Render services spin down when idle and may take around a minute to start again.
- Free Aiven services are small and may be powered off after extended inactivity; check the Aiven dashboard/email if the database stops responding.
- This demo does not have authentication or authorization. Anyone who can reach the public URL may be able to create accounts and initiate transactions. Do not use real financial or personal data. Add authentication and authorization before making it a serious public app.
