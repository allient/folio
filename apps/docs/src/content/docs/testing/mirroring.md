---
title: Mirroring the tablet
description: See and control the physical tablet from the Mac while testing Folio.
---

Testing handwriting is easier when the tablet screen is on the Mac: you can record it, screenshot it and show it in a call. Use [scrcpy](https://github.com/Genymobile/scrcpy).

```bash
brew install scrcpy
scrcpy -d
```

`-d` selects the physical device connected over USB. Without it, scrcpy stops with "more than one device or emulator" whenever an emulator is also running. Other useful selectors:

| Flag | Target |
|---|---|
| `-d` | The only physical device (the tablet) |
| `-e` | The only running emulator |
| `-s <serial>` | A specific device from `adb devices` |

The repo has a shortcut: `just mirror` runs `scrcpy -d`.

## Tips

- Pen input from the Mac mouse is not pressure sensitive. To test pressure, tilt and the eraser end, use the stylus on the tablet itself and only watch the mirror.
- `scrcpy -d --no-control` mirrors without letting the Mac send input.
- `scrcpy -d --record folio-test.mp4` records the session.
- Keep the tablet awake while mirroring: `scrcpy -d --stay-awake`.
