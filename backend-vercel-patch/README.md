# Noir Tuya Self-Service Backend Patch

Copy these files into the existing Next.js/Vercel backend used by `NOIR_API_BASE_URL`.

## Files
- `app/api/tuya/local-key/route.ts`
- `lib/firebase-admin.ts`
- `lib/tuya-openapi.ts`

## Dependency
```bash
npm install firebase-admin
```

## Vercel environment variables
- `TUYA_ACCESS_ID`
- `TUYA_ACCESS_SECRET`
- `TUYA_API_BASE_URL` (example currently used by Noir: `https://openapi-sg.iot-03.com`)
- `FIREBASE_SERVICE_ACCOUNT_JSON` (the complete Firebase service-account JSON as one environment variable)

Redeploy the Vercel project after adding the route and environment variables.

## Security behavior
The route verifies the Android Firebase ID token, reads `users/{uid}`, requires `role=operational`, and checks that the user's `cafeId` matches the requested cafe before obtaining or returning a local key.

Tuya credentials never go into the Android APK.
