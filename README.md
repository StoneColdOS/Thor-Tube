# Thor-Tube

An unofficial YouTube app made for the bottom screen of the AYN Thor. Touch it or use the controller.

Not made by, or affiliated with, YouTube, Google, or AYN.

## Install

Grab **Thor-Tube-latest.apk** from [Releases](../../releases/tag/latest) on the Thor and open it. It installs next to CRT-Tube rather than replacing it. Opening Thor-Tube puts it on the bottom screen.

## Using it

- **Rail on the left:** Home, Subs, Search, Library, CRT, Setup.
- **Home:** tabs across the top (For You plus topics), a grid of videos below. Scroll with your finger, or move the highlight with the D-pad or left stick and press A.
- **Player:** tap the video for controls. With the controller, A pauses, left/right or L1/R1 skip 10 seconds, down moves to Up next, B goes back. When a video ends, the next one plays.
- **L1/R1 on Home** switch tabs. **Start** opens Setup.

## Setup

- **Theme:** Dark (default), Grey, OLED Black, Light, or Match the Thor.
- **YouTube account (optional):** sign in with a code at youtube.com/activate. For You becomes your YouTube home, and what you watch goes into your YouTube history. Your password never touches the app; the sign-in is stored encrypted on the device and can be revoked from your Google account (Security, third-party connections, "YouTube on TV"). This uses the YouTube TV app's sign-in, which is unofficial and against YouTube's terms; a spare account is safest.

## Something wrong?

Logs are on the Thor in `Download/Thor-Tube-logs/` (`thor.log`, `thor-previous.log`, `thor-crash.txt`).

## How it works

- Videos and search come from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); playback is [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer).
- Signed in, your home feed and watch history use the same requests as the YouTube TV app.
- Built on the engine from CRT-Tube.

## Building

Every push builds a test APK with GitHub Actions and updates the "latest" pre-release. No Android Studio needed.

## License

GPL-3.0. See LICENSE.
