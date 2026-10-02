# Background battery measurement

The app records each worker's elapsed runtime, run count, and average runtime under **Settings → Sampling health**. These figures reveal expensive or stuck samples but do not directly measure electrical energy.

Android's per-UID battery accounting is the best practical energy estimate without laboratory hardware. Use a controlled test on a normally charged phone:

1. Install the intended APK and confirm 15-minute logging is enabled.
2. Record the package UID with `adb shell dumpsys package ca.humiditylogger`.
3. Save a pre-test report with `adb shell dumpsys batterystats --checkin ca.humiditylogger`.
4. If resetting system battery statistics is acceptable, run `adb shell dumpsys batterystats --reset`. This resets battery-accounting history for the entire phone, not app data.
5. Disconnect USB, leave the phone screen off for at least six hours, and avoid charging it.
6. Reconnect and save both:
   - `adb shell dumpsys batterystats ca.humiditylogger`
   - `adb shell dumpsys batterystats --checkin ca.humiditylogger`
7. Compare estimated UID power, background CPU time, jobs, network bytes, and wake locks against elapsed screen-off time and recorded worker runs.

A useful acceptance target is that the app has no foreground-service time or long wake locks and that roughly one short worker execution appears per eligible 15-minute interval. WorkManager can batch or defer runs in Doze, so fewer executions are normal.
