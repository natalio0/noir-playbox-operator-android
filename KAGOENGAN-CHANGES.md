# Kagoengan Studio Playbox - operator changes

- App branding, launcher icon, splash, and in-app logo use the supplied Kagoengan Studio logo.
- Payment screen displays QRIS directly with a fixed Rp12.000 / hour rate.
- Replace `app/src/main/res/drawable/qris_bank.png` with the bank QRIS image, keeping the same filename.
- Rental package catalog is restricted to `1h / 60 min / Rp12.000`.
- TinyTuya LAN discovery is reduced from 12s to 5s and registry resolution runs in parallel.
- Operator app includes a 60-minute PREPARING fallback watchdog while the app is running.

Backend: deploy the updated web project and set `CRON_SECRET` in Vercel. `vercel.json` now schedules `/api/system/preparing-watchdog` every 5 minutes, so server-side auto shutdown remains active even when the Android app is closed.
