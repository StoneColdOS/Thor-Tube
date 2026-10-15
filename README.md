# Thor-Tube

An unofficial YouTube app made for the bottom screen of the AYN Thor. Touch it or use the controller.

Not made by, or affiliated with, YouTube, Google, or AYN.

## Install

Grab **Thor-Tube-latest.apk** from [Releases](../../releases/tag/latest) on the Thor and open it. It installs next to CRT-Tube rather than replacing it. Opening Thor-Tube puts it on the bottom screen.

## Using it

- **Rail on the left:** Home, Subs, Search, Library, CRT, Setup.
- **Home:** tabs across the top (For You plus topics), a grid of videos below. Scroll with your finger, or move the highlight with the D-pad or left stick and press A. L1/R1 switch tabs.
- **Subs** (signed in): the newest from everyone you follow, with your channels across the top to narrow it to one.
- **Search:** type with the on-screen keyboard; results show in the same grid.
- **Library** (signed in): History, Watch later, Liked, and your playlists.
- **Player:** tap the video for controls. Under it: title, creator (with Subscribe, Like and Save to Watch later when signed in), and Up next / Comments tabs. Comments load as you scroll; tap one to read its replies; Expand gives them the whole screen.
  - **Full screen:** ⛶, Y, or swipe up on the video. B, ⤡, or swipe down to leave.
  - **Minimize:** B, ⌄, or swipe down on the video. It keeps playing in the corner while you browse; tap it or press X to bring it back, ✕ to stop.
  - Controller: A pauses, left/right or L1/R1 skip 10 seconds, down moves to the tabs below. When a video ends, the next one plays.
- **CRT:** an optional retro picture effect on every video: a curved 90s tube or a flat 2000s HD tube.
- **Start** opens Setup.

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
