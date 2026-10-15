# Wi-Fi refresh recovery follow-up

Base: upstream main 761fdb1. This is an incremental follow-up to PR #18, not a replacement for the author's tint or text-size changes.

## Reproduction and device validation

- The user reported WLAN disabled while Wi-Fi arcs remained on the official 1.6.10 APK. The pulled APK SHA256 matched the release asset: 048e482eeae8566edacb40c4364e4f64c5d82c6ddb1e9b31dfa77e0e8b35d2c4.
- test3 introduced the Wi-Fi changes in this PR. The user reported Wi-Fi switching normal, but identified a text-size regression in the older branch used for that test build.
- test4 restored the prior text-size fix; test5 additionally isolated tint caches in that older branch. Those other edits are NOT part of this PR: upstream already has equivalent region isolation and dp conversion.
- The user reported all tested functions normal through 2026-10-06 15:15:38 Asia/Shanghai. Logs captured at 15:18:11 confirmed the installed version 1.6.10-wifi-test5 and SystemUI PID 28102. No fatal exception or SystemUI ANR matched the captured logcat lines from 15:00 onward.
- Device screenshots and raw logs are kept locally, not published here because they include unrelated device/app information. Absence of crashes does not itself prove Wi-Fi correctness; switching was confirmed by the user.

## Changes

- Register the protected framework Wi-Fi broadcasts with RECEIVER_EXPORTED. Broadcast payloads are not trusted; actual state is queried. The broadcast UID explanation is a compatibility risk, NOT a proven root cause on this device (its captured sticky Wi-Fi state broadcast had originalCallingUid=1000).
- Publish a definitive disabled result before querying ConnectivityManager. A failure in the latter cannot prevent clearing stale presence/level.
- Publish connected/disconnected state before optional RSSI access; a failed RSSI query cannot prevent presence updates.
- Keep a single main-thread 3-second recovery check alive even when listener registration fails. It is not in the drawing path. This intentionally introduces steady IPC overhead; power impact has not been measured. Normal event-driven updates remain immediate; the watchdog bounds recovery after missed events, not after persistent API failure.

## Coverage and remaining work

- Tested: 18 production resolver combinations, 3 reconnect transitions, production publish clearing/repaint/idempotence, source guards for broadcast flags and radio-off query ordering.
- Device: user-observed Wi-Fi switches and subsequent normal use in test3/test5.
- Not covered: Android instrumentation of receiver delivery, permission failures and watchdog scheduling; VPN/multi-network behavior; cross-ROM testing; power measurements; prolonged endurance. The desktop stubs do not exercise Android APIs.
- The PR's rebased artifact is built separately; it has not been installed on the device, which remains on test5.
