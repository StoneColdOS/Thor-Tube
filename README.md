# Thor-Tube

An unofficial YouTube app made for the AYN Thor. It lives on the bottom screen, moves to the top screen with a swipe, and works by touch or with the controller.

Not made by, or affiliated with, YouTube, Google, or AYN.

## Install

Grab the newest **Thor-Tube-vX.Y.Z.apk** from [Releases](../../releases) on the Thor and open it. Updates install over it.

Test builds (**Thor-Tube-latest.apk**, the "latest" pre-release) install as a separate app, so you can try one without touching your main install.

## Using it

- **Sidebar:** Home, Subs, Search, Library, CRT, Setup, and your account at the bottom.
  - **Swipe down the sidebar** to send the app to the bottom screen, **up** to send it to the top. It buzzes, does an old-TV switch-off and switch-on, and the video keeps playing.
- **Home:** For You (your YouTube home when signed in) plus tabs you add yourself.
  - **+ Add** makes a tab from any search, a quick pick, or one of your subscriptions (newest uploads).
  - **Hold a tab** (or highlight it and press Y) to rename, move, or remove it.
- **Subs** (signed in): the newest from everyone you follow, with your channels across the top to narrow it to one.
- **Search:** type with the on-screen keyboard; results show in the same grid.
- **Library** (signed in): History, Watch later, Liked, and your playlists.
- **CRT:** an optional picture effect: a curved 90s tube, a sharp flat 2000s set, or a fuzzy black and white garage set.

### Watching

- **Tap** the video for controls. **Double-tap** the left or right side to skip 10 seconds; keep tapping to skip more.
- **⚙** sets quality (Auto, up to 1080p), speed (0.5x to 2x) and captions. **CC** turns captions on and off. Your quality and caption language are remembered.
- **Bottom screen:** under the video are the title, creator (with Subscribe, Like and Save when signed in), and Up next / Comments.
- **Top screen:** the video fills the screen. Push down, swipe up, or tap the "Up next & comments" tab to pull up a panel with the rest; B, swipe down, or tap the video to put it away.
- **Full screen:** ⛶, Y, or swipe up. **Minimize:** swipe down or B; it keeps playing in the corner while you browse. Tap it or press X to bring it back.
- When a video ends, the next one plays.

### Controller

| Button | What it does |
| --- | --- |
| D-pad / left stick | Move the highlight (hold to repeat) |
| A / B | Open / back |
| L1 / R1 | Switch tabs; in a video, back / ahead 10 s |
| L2 / R2 (tap) | Jump a page up / down |
| L2 or R2 + stick | In a video: scrub, let go to jump there |
| Right stick | Scroll smoothly |
| Y | Video full screen; on a Home tab, edit it |
| X | Bring back the small video |
| Start | Setup |

The same list is in Setup > Controls.

## Setup

- **Theme:** Dark (default), Grey, OLED Black, Light, or Match the Thor.
- **YouTube account (optional):** sign in with a code at youtube.com/activate. For You becomes your YouTube home, and what you watch goes into your YouTube history. Your password never touches the app; the sign-in is stored encrypted on the device, is never backed up or transferred, and can be revoked from your Google account (Security, third-party connections, "YouTube on TV"). This uses the YouTube TV app's sign-in, which is unofficial and against YouTube's terms; a spare account is safest.

## Privacy and security

- Logs stay in the app's private storage and never contain video links, titles, searches or account details. Setup > **Share logs** sends them through Android's share menu if something goes wrong.
- Encrypted connections only; plain HTTP is blocked.
- Release builds are signed with a private key and aren't debuggable.
- Release builds check this repo's public releases page for a newer version, at most every 6 hours. The check sends nothing about you or the device, and only shows a Download / Later banner; nothing installs by itself.

## How it works

- Videos, search, comments and captions come from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor); playback is [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer).
- Signed in, your home feed, subscriptions, library, likes and watch history use the same requests as the YouTube TV app.
- Built on the engine from CRT-Tube.

## Building and releasing

- Every push builds a test APK with GitHub Actions and updates the "latest" pre-release. No Android Studio needed.
- Pushing a branch named `release/vX.Y.Z` builds a signed release, checks its signature and that it isn't debuggable, and publishes it as vX.Y.Z. It needs the `THOR_KEYSTORE_BASE64` and `THOR_KEYSTORE_PASSWORD` repository secrets.

## License

GPL-3.0. See LICENSE.
