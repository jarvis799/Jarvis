"""Decode generated speech with the actual packaged model, not a mocked transcript.
Synthetic speech is a repeatable smoke test, never a claim about phone microphones.
"""
import json
import pathlib
import re
import subprocess
import tempfile
import wave
from vosk import Model, KaldiRecognizer, SpkModel, SetLogLevel

SetLogLevel(-1)
root = pathlib.Path(__file__).resolve().parents[1]
model = Model(str(root / 'app/src/main/assets/model-en-us'))
spk = SpkModel(str(root / 'app/src/main/assets/model-spk'))
service = (root / 'app/src/main/java/com/jarvis/assistant/background/JarvisConversationService.kt').read_text()
block = service.split('private val DEFAULT_WAKE_PHRASES = setOf(', 1)[1].split(')', 1)[0]
phrases = re.findall(r'"([^"\n]+)"', block)
phrases += [re.sub(r'\bjarvis\b', 'jervis', p) for p in list(phrases) if 'jarvis' in p]
grammar = json.dumps(phrases + ['[unk]'])
checks = []
with tempfile.TemporaryDirectory() as tmp:
    for voice in ['en-us', 'en-gb', 'en-in']:
        for phrase in ['hey jarvis', 'jarvis', 'hey jervis', 'hello jarvis are you there', 'the weather is nice']:
            wav = str(pathlib.Path(tmp) / 'speech.wav')
            pcm = str(pathlib.Path(tmp) / 'speech.raw')
            subprocess.run(['espeak-ng', '-v', voice, '-s', '145', '-w', wav, phrase], check=True)
            subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', wav, '-ar', '16000', '-ac', '1', '-f', 's16le', pcm], check=True)
            audio = b'\x00' * 32000 + pathlib.Path(pcm).read_bytes() + b'\x00' * 64000
            for restricted in [True, False]:
                rec = KaldiRecognizer(model, 16000, grammar) if restricted else KaldiRecognizer(model, 16000)
                rec.SetWords(True)
                rec.SetSpkModel(spk)
                results, partials = [], []
                for i in range(0, len(audio), 6400):
                    chunk = audio[i:i + 6400]
                    if rec.AcceptWaveform(chunk):
                        result = json.loads(rec.Result())
                        if result.get('text'): results.append(result)
                    else:
                        text = json.loads(rec.PartialResult()).get('partial', '')
                        if text and (not partials or partials[-1] != text): partials.append(text)
                result = json.loads(rec.FinalResult())
                if result.get('text'): results.append(result)
                print(json.dumps({'voice': voice, 'spoken': phrase, 'restricted': restricted, 'partials': partials, 'finals': [{'text': r.get('text'), 'words': r.get('result'), 'speaker_frames': r.get('spk_frames'), 'has_speaker': bool(r.get('spk'))} for r in results]}), flush=True)

                if restricted:
                    heard = ' '.join([r.get('text', '') for r in results] + partials)
                    accepted = bool(re.search(r'\b(?:jarvis|jervis)\b', heard))
                    expected = phrase != 'the weather is nice'
                    checks.append(accepted == expected)
                    assert accepted == expected, (voice, phrase, heard)
assert len(checks) == 15 and all(checks), checks
print('WAKE_ACOUSTIC_CHECKS_PASSED=15', flush=True)
