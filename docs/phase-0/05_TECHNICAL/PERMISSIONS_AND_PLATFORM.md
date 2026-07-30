# Permissions and Platform Rules

## Expected permissions

- Notifications: only for reminders/alerts, requested when first enabled
- Camera: only for receipt capture
- Biometric/device credential: through Android authentication APIs
- Exact alarms: avoided unless a user opts into time-critical reminders and platform rules permit it

## Not requested

- Broad storage permission
- Contacts
- SMS
- Phone state
- Location
- Microphone
- Accessibility service
- Always-on background service

## File handling

Use app-private storage for the attachment vault and the Android system picker for user-selected files/folders. Never rely on deprecated unrestricted external-storage access.
