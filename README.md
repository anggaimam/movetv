# MoveTV: proof of concept

Your own body-tracking app for Fire TV. The USB webcam feeds Google's MediaPipe Pose,
which runs entirely on the Fire TV. It shows a skeleton over the camera image, live speed
numbers, and a balloon you pop with your hands.

**Privacy:** the app has no internet permission, so Android blocks it from sending anything.
Nothing is uploaded or saved.

## Install on Fire TV
Downloader link (built automatically on every change):
https://github.com/khoiapp-web/movetv/releases/latest/download/movetv.apk

- Close Zoom, then open MoveTV and choose **Allow** for the camera.
- Stand back 2-3 m so your whole body is in view.

## Remote
| Button | Does |
|---|---|
| OK | Camera picture on/off (skeleton only) |
| Menu ≡ | 1 or 2 players |
| ⏪ Rewind | Lite or Full tracking model |
| ⏩ Fast-forward | CPU or GPU |

The top bar shows **Tracking fps** (aim for 15+) and **AI ms** (how long each frame takes).
