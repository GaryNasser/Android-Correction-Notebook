# Player Fixture

`player-test.ts` is a generated four-second moving test pattern, with no audio
or third-party media. It is packaged only in the instrumentation test APK.

Regenerate with:

```sh
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi \
  -i testsrc2=size=160x90:rate=15 -t 4 -an -c:v libx264 \
  -preset veryslow -crf 32 -g 30 -pix_fmt yuv420p -f mpegts player-test.ts
```
