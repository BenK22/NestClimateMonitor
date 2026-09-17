# Privacy

Home Climate Monitor stores climate readings locally in its private Android database. It has no developer-operated server, advertising, analytics, or telemetry.

The app communicates with:

- Google's OAuth, Partner Connections Manager, and Smart Device Management API after the user enters their own Device Access project credentials and grants Nest access;
- Google Home APIs, after the user grants access, to read compatible in-home climate devices;
- Android's geocoder when the user changes the outdoor location; and
- Open-Meteo using the selected location's coordinates to retrieve outdoor temperature and humidity.

Google Home readings are not uploaded by this app. Android cloud backup and device-to-device transfer are disabled for all app data, including readings, location settings, and credentials. Use the explicit CSV export/import controls to move climate history between devices. CSV data leaves the app only when the user invokes Android's share sheet. Humidity notifications are calculated locally. Deleting app storage or using **Stored data → Delete all readings** removes the local history.

Device Access requests go directly from the phone to Google; there is no developer-operated intermediary. The Device Access client secret and OAuth tokens are encrypted with Android Keystore, excluded from backup and device transfer, and can be erased from **Settings → Nest Device Access**. They are never included in CSV exports.

Permissions are used only for Google Home and Nest Device Access, internet connectivity, and optional Android notifications.
