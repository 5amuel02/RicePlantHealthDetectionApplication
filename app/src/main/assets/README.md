# On-device model files

`LeafHealthAnalyzerTFLite` (see `analysis/LeafHealthAnalyzerTFLite.kt`) looks for two
files here at runtime:

- `model.tflite` — the trained rice-leaf disease classifier
- `labels.txt` — one class name per line, in the same order the model outputs them

**Neither file is committed to this repo** — binary model weights don't belong in
source control by default, and there's a legitimate manual step to produce them
(training on a real dataset).

## How to get them

Run [`training/train_rice_disease_tflite.ipynb`](../../../../training/train_rice_disease_tflite.ipynb)
in Google Colab (free GPU, ~15 minutes): open it, **Runtime → Change runtime type → GPU**,
then **Runtime → Run All**. The last cell downloads `model.tflite` and `labels.txt` to
your computer — drop both files directly into this folder.

Until you do that, the app works fine without them: `LeafHealthAnalyzerTFLite.isAvailable`
is `false`, and `CameraActivity` automatically falls back to the heuristic
`LeafHealthAnalyzerV3` analyzer, which needs no model file.
