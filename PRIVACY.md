# Privacy

Home Climate Monitor stores climate readings locally in its private Android database. It has no developer-operated server, advertising, analytics, or telemetry.

The app communicates with:

- Google Home APIs, after the user grants access, to read compatible in-home climate devices;
- Android's geocoder when the user changes the outdoor location; and
- Open-Meteo using the selected location's coordinates to retrieve outdoor temperature and humidity.

Google Home readings are not uploaded by this app. CSV data leaves the app only when the user explicitly invokes Android's share sheet. Humidity notifications are calculated locally. Deleting app storage or using **Stored data → Delete all readings** removes the local history.

Permissions are used only for Google Home access, internet connectivity, and optional Android notifications.
