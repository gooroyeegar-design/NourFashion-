# Piano Studio

A low-latency Android piano focused on natural playability.

## Audio
The grand-piano voice uses real Yamaha C5 recordings from the Salamander Grand Piano V3 library by Alexander Holm. The sample library is licensed CC BY 3.0. The build downloads the compact Tone.js-converted sample set at build time so the APK contains the sounds offline.

Attribution:
Salamander Grand Piano V3 — Alexander Holm
https://github.com/sfzinstruments/SalamanderGrandPiano
License: Creative Commons Attribution 3.0 Unported.

## Features
- 88-key A0-C8 range
- Independent polyphonic voices: every pointer/touch has its own voice
- Fast pointer-event handling with glissando
- Touch-position velocity
- Sustain pedal latch
- 1.5–6 octave zoom
- Note labels and octave labels
- Metronome with BPM
- Performance recording/playback using timestamped note events
- Reverb and master volume
- Dark studio UI
- Fully offline after installation
