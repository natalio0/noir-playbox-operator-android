# Noir Operator production hardening backend patch

Copy `app/api/operator` into the existing Next.js website project. It reuses the website's existing `lib/firebase-admin.ts` and does not replace it.

Adds:
- `POST /api/operator/audit`: best-effort lifecycle audit log.
- `POST /api/operator/incidents`: hardware/offline incident log.

Firestore output:
- `cafes/{cafeId}/operatorAuditLogs/{id}`
- `cafes/{cafeId}/operatorIncidents/{id}`

The Android app remains functional if these telemetry routes are not deployed; telemetry is deliberately best-effort.
