# Apple TV theme and tvOS features

This is a personal version of [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT, by Gathin) with an
**Apple TV theme** and a set of tvOS-style features built on top of its launcher. It was made for and tested on a
**Xiaomi TV A Pro (MiTV-MZTU0, Google TV / Android 14)**; most of it works on any Android TV, the remote-button
mappings are specific to that Xiaomi remote.

## What is in it

**Look**
- *Apple TV theme* (`themes/apple-tv`, the only theme bundled in the launcher): top shelf with the focused app's
  artwork, a white frosted dock, a soft blue-purple backdrop, and the top buttons in one glass capsule with the time.
- *Bigger tiles and tvOS focus*: the focused app grows, rises on a shadow, tilts a few degrees the way focus moved,
  the logo floats with parallax and a glass glint sweeps across (Customize > Motion > Advanced effects).
- *Dark / Light / Auto appearance* for the glass surfaces (Customize > Display).
- *Jiggle mode*: long-press OK on an app > Move, every tile wobbles while you rearrange.

**Control Center** (the toggle button in the capsule, or a remote button over any app)
- Wi-Fi, Sleep timer, Power, Now Playing with previous / play-pause / next, Backlight and Volume sliders that follow
  the remote live, Audio output, Night Shift, Customize and Settings.
- Frosted glass that blurs whatever is behind it, also over a playing video. Drops down from the capsule; the same
  button closes it. Opened from the capsule, the button keeps the focus: Down goes in, Up comes back.

**Now Playing**
- While music plays, the album art fills the top of Home like a tvOS top shelf (other layouts: Card, Compact, Tile,
  Cover), and it stays as "Paused" for 30 minutes after a pause.

**Widgets** (Customize > Widgets)
- Weather (Open-Meteo, no account) and Date, each placeable anywhere, Clean or Glass style.
  Set your town in `launcher/app/src/main/java/com/gathin/tvhome/Widgets.java` (`CITY` and the coordinates;
  it ships with Cupertino).

**Night Shift** (Customize > Display): the backlight goes down in the evening and back up in the morning.

**Fixes for this Xiaomi TV**
- The TV's picture service resets the brightness to ~45% on its own; the launcher puts your level back.
- Xiaomi's Settings pauses music; it plays on when you are back on Home.
- Held volume keeps ramping with the key helper on; 0% backlight is dim instead of black.

## Install

Follow the main [README](README.md) to build and install the launcher with the key helper
(`build-and-install` with `-InputKeyService`). Then grant, over ADB:

```
cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener 0
pm grant com.gathin.tvhome android.permission.READ_LOGS
appops set com.gathin.tvhome WRITE_SETTINGS allow
```

Remote buttons (`launcher/app/src/main/res/values/config.xml`, `app_keys`): on the Xiaomi remote the Prime Video
button (keycode 194) opens Control Center, the Settings button opens Settings, ★ opens SmartTube. Change them for
your own remote; `getevent -lt` over ADB shows a button's code.
