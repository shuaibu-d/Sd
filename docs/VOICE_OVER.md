# Voice-over recorder and loudness normalization

- Records scene narration with Android `MediaRecorder` to app-private AAC/MPEG-4 `.m4a` files (mono, requested 44.1 kHz, 128 kbps).
- Requests microphone permission at point of use and provides a recovery path when permission is denied.
- Supports a three-second countdown, live input meter, waveform, playback, re-recording, deletion, and sequential scene recording.
- Stops safely on app backgrounding/audio-focus interruption and retains a valid partial take.
- Stores the original recording and scene duration locally. Scene duration uses voice length + 400 ms unless the user applies a manual duration.

## Loudness normalization

- Uses ITU-R BS.1770 / EBU R128 K-weighted integrated loudness measurement, including 400 ms blocks, 75% overlap, the -70 LUFS absolute gate and -10 LU relative gate.
- The app speech target is -16 LUFS stereo-equivalent. Because scene takes are mono and are normally duplicated to left/right during video playback, the measurement adds the BS.1770 dual-mono +3.0103 dB compensation before planning gain. This is an application target, not the EBU broadcast reference level (-23 LUFS).
- Measures inter-sample true peak with four-phase windowed-sinc oversampling.
- Applies static linear gain only; gain is limited by a -1.5 dBTP PCM ceiling and capped at +18 dB to avoid excessive amplification of room noise.
- Decodes the original AAC take with Android `MediaExtractor`/`MediaCodec`, processes PCM off the main thread, then encodes AAC-LC at 44.1 kHz mono / 128 kbps into a new M4A file.
- Re-decodes and measures the encoded result. It can make up to two gain corrections from the original PCM and re-encode, then verifies the final AAC true peak is at or below -1.0 dBTP. If that safety check fails, it rejects the derivative and retains the original.
- The original take is never overwritten. The scene points to the normalized derivative and retains the original path so the user can restore it. Re-recording/deleting a take cleans up both derivatives.
- If a take is already within 0.7 LU of target and under the PCM peak ceiling, normalization skips re-encoding to avoid needless AAC generation loss.
- This is static loudness normalization, not a compressor/noise remover. When the true-peak limit prevents reaching -16 LUFS, the app prioritizes the peak ceiling and reports that the target could not safely be reached.

## Validation

- JVM tests cover amplitude-to-LUFS behaviour, true-peak constrained gain planning, and the +18 dB boost ceiling.
- The K-weighting coefficients were checked against the ITU-R BS.1770-4 reference formulation; a 1 kHz, -20 dBFS sine measured approximately -23.05 LUFS before dual-mono compensation, matching FFmpeg `loudnorm` reference output in a local comparison.
- Device-specific MediaCodec decode/encode behaviour still requires a real Android device test. Android codec implementations vary, so the post-encode verification is part of the runtime workflow rather than assuming the encoder output matches the PCM estimate.
