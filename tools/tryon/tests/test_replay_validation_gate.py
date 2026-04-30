import json
import subprocess
import tempfile
import unittest
from pathlib import Path


class ReplayValidationGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repo_root = Path(__file__).resolve().parents[3]
        self.script = self.repo_root / "tools" / "tryon" / "replay_validation.py"
        self.tmpdir = Path(tempfile.mkdtemp(prefix="tryon-replay-gate-"))
        self.images_dir = self.tmpdir / "images"
        self.images_dir.mkdir(parents=True, exist_ok=True)
        self.report_dir = self.tmpdir / "reports"
        self.report_dir.mkdir(parents=True, exist_ok=True)

    def tearDown(self) -> None:
        for path in sorted(self.tmpdir.rglob("*"), reverse=True):
            if path.is_file():
                path.unlink()
            else:
                path.rmdir()

    def _run(self, annotation: dict, predictions: dict, extra_args: list[str]) -> subprocess.CompletedProcess:
        annotation_path = self.tmpdir / "annotations.json"
        predictions_path = self.tmpdir / "predictions.json"
        annotation_path.write_text(json.dumps(annotation), encoding="utf-8")
        predictions_path.write_text(json.dumps(predictions), encoding="utf-8")
        command = [
            "python",
            str(self.script),
            "--annotations",
            str(annotation_path),
            "--image-dir",
            str(self.images_dir),
            "--predictions",
            str(predictions_path),
            "--report-dir",
            str(self.report_dir),
            "--strict-gate",
            *extra_args,
        ]
        return subprocess.run(command, cwd=self.repo_root, capture_output=True, text=True, check=False)

    def test_strict_gate_fails_when_insufficient_steady_pairs(self):
        annotation = {
            "schemaVersion": 1,
            "media": {"type": "image", "frameWidth": 320, "frameHeight": 240},
            "frames": [
                {
                    "file": "f0.png",
                    "ringFingerZone": {"centerX": 120, "centerY": 120, "widthPx": 60, "rotationDeg": 90},
                    "visibleFinger": True,
                    "annotationQuality": "good",
                }
            ],
        }
        predictions = {
            "frames": [
                {
                    "file": "f0.png",
                    "predictedRingFingerZone": {"centerX": 120, "centerY": 120, "widthPx": 60, "rotationDeg": 90},
                    "updateAction": "Apply",
                }
            ]
        }

        result = self._run(annotation, predictions, ["--min-steady-pair-count", "2", "--skip-frame-read"])
        self.assertEqual(result.returncode, 2, msg=result.stdout + result.stderr)

    def test_strict_gate_fails_when_center_ratio_low(self):
        annotation = {
            "schemaVersion": 1,
            "media": {"type": "image", "frameWidth": 320, "frameHeight": 240},
            "frames": [
                {"file": "f0.png", "ringFingerZone": {"centerX": 100, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "f1.png", "ringFingerZone": {"centerX": 100, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "f2.png", "ringFingerZone": {"centerX": 100, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
            ],
        }
        predictions = {
            "frames": [
                {"file": "f0.png", "predictedRingFingerZone": {"centerX": 220, "centerY": 220, "widthPx": 60, "rotationDeg": 90}, "updateAction": "Apply"},
                {"file": "f1.png", "predictedRingFingerZone": {"centerX": 220, "centerY": 220, "widthPx": 60, "rotationDeg": 90}, "updateAction": "Apply"},
                {"file": "f2.png", "predictedRingFingerZone": {"centerX": 220, "centerY": 220, "widthPx": 60, "rotationDeg": 90}, "updateAction": "Apply"},
            ]
        }

        result = self._run(annotation, predictions, ["--min-steady-pair-count", "0", "--skip-frame-read"])
        self.assertEqual(result.returncode, 2, msg=result.stdout + result.stderr)

    def test_strict_gate_passes_when_metrics_and_steady_pairs_ok(self):
        annotation = {
            "schemaVersion": 1,
            "media": {"type": "image", "frameWidth": 320, "frameHeight": 240},
            "frames": [
                {"file": "f0.png", "ringFingerZone": {"centerX": 100, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "f1.png", "ringFingerZone": {"centerX": 104, "centerY": 102, "widthPx": 61, "rotationDeg": 89}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "f2.png", "ringFingerZone": {"centerX": 108, "centerY": 104, "widthPx": 62, "rotationDeg": 89}, "visibleFinger": True, "annotationQuality": "good"},
            ],
        }
        predictions = {
            "frames": [
                {"file": "f0.png", "predictedRingFingerZone": {"centerX": 101, "centerY": 101, "widthPx": 60, "rotationDeg": 90}, "updateAction": "Apply"},
                {"file": "f1.png", "predictedRingFingerZone": {"centerX": 104, "centerY": 103, "widthPx": 61, "rotationDeg": 89}, "updateAction": "Apply"},
                {"file": "f2.png", "predictedRingFingerZone": {"centerX": 109, "centerY": 104, "widthPx": 62, "rotationDeg": 89}, "updateAction": "Apply"},
            ]
        }

        result = self._run(annotation, predictions, ["--min-steady-pair-count", "2", "--skip-frame-read"])
        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)

    def test_skip_frame_read_allows_ci_gate_without_video_decode(self):
        annotation = {
            "schemaVersion": 1,
            "media": {"file": "video_fixture.mp4", "type": "video", "frameWidth": 320, "frameHeight": 240},
            "frames": [
                {"file": "video_fixture.mp4", "frameIndex": 0, "ringFingerZone": {"centerX": 100, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "video_fixture.mp4", "frameIndex": 1, "ringFingerZone": {"centerX": 103, "centerY": 101, "widthPx": 61, "rotationDeg": 90}, "visibleFinger": True, "annotationQuality": "good"},
                {"file": "video_fixture.mp4", "frameIndex": 2, "ringFingerZone": {"centerX": 106, "centerY": 102, "widthPx": 62, "rotationDeg": 89}, "visibleFinger": True, "annotationQuality": "good"},
            ],
        }
        predictions = {
            "frames": [
                {"file": "video_fixture.mp4", "frameIndex": 0, "predictedRingFingerZone": {"centerX": 101, "centerY": 100, "widthPx": 60, "rotationDeg": 90}, "updateAction": "Apply"},
                {"file": "video_fixture.mp4", "frameIndex": 1, "predictedRingFingerZone": {"centerX": 104, "centerY": 101, "widthPx": 61, "rotationDeg": 90}, "updateAction": "Apply"},
                {"file": "video_fixture.mp4", "frameIndex": 2, "predictedRingFingerZone": {"centerX": 107, "centerY": 102, "widthPx": 62, "rotationDeg": 89}, "updateAction": "Apply"},
            ]
        }

        result = self._run(annotation, predictions, ["--min-steady-pair-count", "2", "--skip-frame-read"])
        self.assertEqual(result.returncode, 0, msg=result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
