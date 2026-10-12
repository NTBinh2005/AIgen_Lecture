import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from pydantic import ValidationError


SERVICE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_DIR))
os.environ.setdefault("AVATAR_ENGINE", "fallback")

import app  # noqa: E402


class AvatarServiceTest(unittest.TestCase):
    def test_job_id_cannot_escape_output_directory(self):
        with self.assertRaises(ValidationError):
            app.TalkRequest(jobId="../outside", slideIndex=0)

    def test_remote_assets_must_use_http(self):
        with self.assertRaises(ValueError):
            app._safe_remote_url("file:///etc/passwd")

    def test_fallback_video_uses_bundled_teacher_and_h264(self):
        self.assertTrue(app.DEFAULT_AVATAR_IMAGE.exists())
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "avatar.mp4"
            app.generate_fallback_avatar_video(
                audio_path=None,
                image_path=app.DEFAULT_AVATAR_IMAGE,
                output_path=output,
                requested_duration_ms=1000,
            )
            self.assertTrue(output.exists())
            self.assertGreater(output.stat().st_size, 10_000)

    def test_teaching_actions_select_full_body_pose_assets(self):
        explain = app._teacher_pose_image("EXPLAIN", "EXPLAIN")
        point = app._teacher_pose_image("POINT", "EXAMPLE")
        question = app._teacher_pose_image("QUESTION", "CHECK")

        self.assertEqual(explain.name, "avatar_teacher_explain.png")
        self.assertEqual(point.name, "avatar_teacher_point.png")
        self.assertEqual(question.name, "avatar_teacher_question.png")
        self.assertTrue(all(path.exists() for path in (explain, point, question)))

    def test_talk_request_accepts_pedagogical_metadata(self):
        request = app.TalkRequest(
            jobId="lesson-1",
            slideIndex=2,
            lessonPhase="CHECK",
            teacherAction="QUESTION",
            teachingGoal="Learners explain the concept",
        )
        self.assertEqual(request.lessonPhase, "CHECK")
        self.assertEqual(request.teacherAction, "QUESTION")

    def test_media_tools_are_resolved_for_sadtalker_subprocess(self):
        self.assertTrue(Path(app.FFMPEG_BIN).exists())
        self.assertTrue(Path(app.FFPROBE_BIN).exists())

    def test_existing_avatar_output_is_reused_without_running_inference(self):
        request = app.TalkRequest(jobId="cached-avatar-test", slideIndex=3)
        output = app.OUTPUT_DIR / "cached-avatar-test_slide_3.mp4"
        output.write_bytes(b"0" * 10_001)
        try:
            with patch.object(app, "_use_sadtalker", return_value=True), patch.object(
                app, "run_sadtalker"
            ) as run_sadtalker:
                response = app.generate_talk(request)

            self.assertEqual(response["engine"], "sadtalker")
            self.assertIn(output.name, response["avatarVideoUrl"])
            run_sadtalker.assert_not_called()
        finally:
            output.unlink(missing_ok=True)

    def test_sadtalker_source_is_bounded_for_720p_composition(self):
        import cv2

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "optimized.png"
            prepared = app._prepare_sadtalker_source(app.DEFAULT_AVATAR_IMAGE, output)
            image = cv2.imread(str(prepared))

            self.assertEqual(prepared, output)
            self.assertIsNotNone(image)
            self.assertLessEqual(max(image.shape[:2]), app.SADTALKER_MAX_SOURCE_SIZE)


if __name__ == "__main__":
    unittest.main()
