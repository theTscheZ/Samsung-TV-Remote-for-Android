# Samsung TV Remote for Android

Native Android app for Samsung Tizen televisions, initially targeting Samsung QE55Q64RATXZG.

## Features
- Kotlin / Jetpack Compose / Material 3 with edge-to-edge window insets
- Consistent 8dp spacing grid, 20dp page margins
- Remote controls over local Samsung WebSocket (WSS port 8002)
- TV pairing and saved connection
- SSDP device discovery plus manual IP address
- Navigation, volume, channels, sources, media keys and power off
- Installed-app discovery (model/firmware dependent), app launching and customizable shortcuts
- No Rakuten shortcut; add Crunchyroll from the TV apps list
- Optional Wake-on-LAN (requires MAC address; power on not guaranteed)

## Build
Open the repository in Android Studio (JDK 17, Android SDK 35) and use Build > Build APK(s).
The initial implementation is untested on the physical TV, and the build has not yet been validated.

## Notes
Phone and TV must be on the same LAN. Pairing requires accepting a prompt on the television.
Samsung TV app listing and direct app launching are firmware-dependent.
The local-TV TLS certificate is self-signed, therefore the client trusts it only when connecting to a validated private IPv4 host. Never expose TV port 8002 to the internet.
