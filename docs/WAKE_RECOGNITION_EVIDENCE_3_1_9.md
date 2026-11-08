# Wake path follow-up 3.1.9

The owner reports that 3.1.8 still does not wake. The physical-phone cause remains unknown; this document does not claim phone recovery.

## Evidence gathered before changing recognition
- Ran the actual packaged Vosk acoustic and speaker models on 16 kHz PCM, generated reproducibly with espeak-ng in US, UK and Indian English, using both restricted and unrestricted decoding.
- All nine original restricted wake phrases produced Jarvis in partial/final results; all three restricted non-wake controls stayed unknown. This rules out an unconditional model/grammar failure for that generated audio, not a phone-specific capture problem.
- Run 42489044943 completed the acoustic step successfully; the remaining debug build was deliberately cancelled after collecting the evidence.

## Confirmed wake-to-session blocker
- The standby engine checked RECORD_AUDIO only, but after a valid wake the active-session gate also demanded notification permission AND battery-optimization exemption. A phone without either was rejected even though recording was permitted.
- That rejection called fullStop(), which persisted powered_off=true. Every later foreground/boot standby attempt respected that flag and never restarted the wake listener. MainActivity also blocked the manual start on the same optional settings. This is a different path from the earlier recorder/cache/threshold repairs.
- Both manual start and wake-to-session now use one microphone-only permission contract. Notification and battery settings remain available in Settings; battery restrictions can still affect real background reliability and no always-on guarantee is made.
- Missing mic permission now stops the service without writing the owner-off flag. Deliberate Stop still writes it. Ambiguous old powered_off flags are NOT automatically cleared: tap Start once after installing if an old failure left Jarvis off.

## Other confirmed corrections
- HomeFragment looked for STANDBY even though the service emits WAKE_LISTENING. It could never display the promised microphone-confirmed state. The status now uses the actual service value.
- jervis was an accepted wake spelling in the matcher and is in the packaged vocabulary, but was absent from the restricted recognizer grammar. Its standalone and prefixed forms now reach the matcher. Other invented out-of-vocabulary aliases are not added.
- Existing standby hint now receives the actual selected input route, an input meter, recognised text, and specific rejection reasons (unclear/short speech, phone-call pause, owner mismatch or insufficient speaker data). Audio and transcript diagnostics remain in memory on the device; no recording, upload or persistence was added.

## Unchanged
Sensitivity thresholds, one-microphone ownership, cache extraction, explicit Stop, owner comparison, unlock logic, active-session networking, TTS and unrelated features are unchanged.

## Regression coverage
- Four JVM cases for grammar alias reachability and rename/boundary behavior.
- Robolectric permission regression: microphone granted + notification denied + battery not exempt must be startable; missing microphone must still be blocked.
- Release CI now runs the real model smoke test: 12 generated positive cases including jervis and three unrelated-speech controls. This runs the Linux Vosk binding with the same model, not an Android hardware test.
- Signed Android compile/unit test/release results will be recorded after completion.

## Device acceptance still required
Install the signed update, open Jarvis and observe the existing standby hint while saying Hey Jarvis. If it fails, that hint identifies whether the phone delivered audio, what the model recognised, or why activation was refused. Physical phone activation is not remotely verified.
