# Screenshots without a TV

Every push to `main` draws the scenes off-screen on the build machine (Robolectric, native graphics)
and force-pushes the pictures to the **`screenshots`** branch (1280×720 PNG, always only the latest build).

- Look: https://github.com/anggaimam/movetv/tree/screenshots
- Add a scene: add a test method in `app/src/test/java/com/wiggleplay/app/shots/ScreenshotTest.java` that
  calls `ShotRig.shoot(name, scene, args, players, frames, script)`. The `script` can move the pointer
  (`body.pointerDown/Move`), hold keys (`body.setHeld`) or press remote keys (`input.keyDown/keyUp`).
- Run locally (needs Android SDK + Maven access): `gradle testReleaseUnitTest -Pshots`; pictures land in `app/build/screenshots`.
- Normal test runs skip these tests. A failed screenshot step never blocks the APK.
- Limits: software drawing, no camera picture (scenes get `camera = null`), system fonts of the build machine, no sound.
