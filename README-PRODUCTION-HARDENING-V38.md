# v3.8 Production Hardening

- Active-rental offline watchdog (2 consecutive fast LAN misses) without auto-stopping billing.
- Best-effort operator audit + incident telemetry via backend routes.
- UI action in-flight gate prevents duplicate lifecycle taps.
- Recovery refresh on Android Activity resume.
- Friendly error mapping for LAN/backend/auth/TinyTuya failures.
- Hardware setup and Advanced local diagnostics hidden/blocked for unauthorized roles.
- Debug/release BuildConfig separates verbose API logging from production.

## Production signing
This patch does not include a private signing key. Keep keystores outside Git. Configure Android signing in the local/CI environment when preparing the final release APK/AAB.

## Backend telemetry
Copy `backend-vercel-hardening-patch/app/api/operator` into the existing Noir Next.js website project, then build and deploy. Do NOT replace the website's existing `lib/firebase-admin.ts`.
