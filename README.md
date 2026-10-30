# myTV

A Google TV-style home screen for the Google TV Streamer, with an LCARS theme.

myTV is a native Android TV launcher in Kotlin and Compose. It's built for the remote's D-pad, OK, Back and Home.

- **Home, Movies, Shows and Apps** tabs, frosted glass over a rotating banner, Top picks and round app icons.
- **Your library** from AllMedia, through its signature-protected provider. myTV shows its rows and opens titles in
  AllMedia by their `allmedia://` links.
- **Who's watching**: choose one of AllMedia's people, and AllMedia plays as them.
- **Media sources**: per-app control over Continue watching, Top picks and rows, down to each channel an app publishes.
- **Popular titles** from TMDB, shown only when they're in the library.
- **Apps**: launches, installs (an APK upload or URL from the phone setup page) and opens the Play Store.
- **The Home button**: takes it back from Google TV with an accessibility service. A press opens myTV and a long press
  opens its settings.
- **Screensaver**: sets the screensaver and timeouts, and has its own ambient screensaver.

## Themes

Settings → Theme picks one.

- **Google TV**: the stock Google TV look.
- **LCARS**: the home screen as a computer panel from the Enterprise-D, with elbows, running number columns, blinking
  lights, a stardate and chirps synthesised on the device. Two films are hidden somewhere in the frame.

## Building and installing

You need JDK 17 or later and the Android SDK, and a TV running Android TV 8.0 (API 26) or later with network debugging
on.

```bash
./gradlew assembleRelease
adb connect <tv address>
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with `~/.android/debug.keystore` when there is one. myTV and AllMedia share the library and
who's watching only when they're signed with the same key.

Some settings need two permissions that only adb can grant. Grant them once:

```bash
adb shell pm grant com.vwillcox.mytv android.permission.READ_TV_LISTINGS
adb shell pm grant com.vwillcox.mytv android.permission.WRITE_SECURE_SETTINGS
```

Then, on the TV, in myTV's settings:

- **Home button**: switch on "Home button opens myTV", and choose "Make this the default home app".
- **Permissions**: shows what's been granted.
- **Popular titles**: add a TMDB key from the phone setup page (scan the QR code).

## Licences

| What | Licence |
| --- | --- |
| myTV's code | [MIT](LICENSE) |
| Antonio font, `app/src/main/res/font/antonio.ttf` | [SIL Open Font License 1.1](licenses/licenses-Antonio-OFL.txt), © The Antonio Project Authors |
| Google Sans Flex font, `app/src/main/res/font/google_sans_flex.ttf` | [SIL Open Font License 1.1](licenses/licenses-GoogleSansFlex-OFL.txt), © The Google Sans Flex Authors |
| Tribute film, `app/src/main/res/raw/tribute.mp4`, and its photos, `tools/tribute-portrait.png` and `tools/tribute-picard.png` | [CC BY-SA 3.0](licenses/licenses-TributePortrait-CC-BY-SA-3.0.txt) |
| Warp film, `app/src/main/res/raw/warp.mp4` | Rendered from a BlenderKit model under BlenderKit's Royalty Free licence (see below) |
| Gradle wrapper | Apache 2.0 |

The tribute's photos, both from Wikimedia Commons:

- "Patrick Stewart speaking at the 2019 San Diego Comic-Con International" by
  [Gage Skidmore](https://commons.wikimedia.org/wiki/File:Patrick_Stewart_by_Gage_Skidmore_2.jpg), CC BY-SA 3.0.
- "Jean-Luc Picard 2.jpg" by [Loupeznik](https://commons.wikimedia.org/wiki/File:Jean-Luc_Picard_2.jpg), CC BY-SA 3.0:
  a collage of a public-domain photo by Cdt. Patrick Caughey, U.S. Army, and Derek Springer's photo of the Enterprise-D
  crew quarters, CC BY-SA 2.0.

Both are cut out of their backgrounds and cropped, padded or turned. The cut-outs and the film they're in are shared
under the same licence, CC BY-SA 3.0. The licence notice has the details.

The Enterprise-D in both films is "USS Enterprise - NCC-1701-D" by Joachim Bornemann, a free model on BlenderKit. The
model itself isn't included here, only renders of it. `tools/tribute.py` is the Blender scene that renders the tribute.

The libraries, which Gradle downloads and this repository doesn't include, are under their own licences: AndroidX,
Compose, Media3, OkHttp, Coil, Haze, ZXing and kotlinx under Apache 2.0, and NanoHTTPD under BSD 3-Clause.

## Star Trek, LCARS and Paramount

The LCARS theme is a non-commercial fan tribute to Star Trek: The Next Generation, and to Sir Patrick Stewart
(1940–2026).

Star Trek, Star Trek: The Next Generation, LCARS, the U.S.S. Enterprise NCC-1701-D, Jean-Luc Picard and all related
names, designs, marks and logos are trademarks and copyrights of CBS Studios Inc. and Paramount. LCARS was designed by
Michael Okuda for the series. This project is not made, endorsed, sponsored or approved by CBS Studios or Paramount,
and isn't connected with them in any way. The MIT licence covers myTV's own code, not the Star Trek elements it
depicts, which aren't mine to licence.

No sound, footage or artwork from the series is included. The chirps are synthesised on the device, the Enterprise-D is
a fan-made model, and the photos are the Wikimedia Commons ones credited above. Sir Patrick Stewart's likeness appears
in tribute only, and implies no endorsement.

If you hold rights in any of this and want something changed or removed, please open an issue.

## Other trademarks

Google TV, Google TV Streamer and Android TV are trademarks of Google LLC. myTV is not a Google product.

This product uses the TMDB API but is not endorsed or certified by TMDB.
