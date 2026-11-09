"""Ensure docs cannot accidentally hide code/build changes from CI."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_changes", Path(__file__).resolve().parents[1] / "scripts/ci_changes.py")
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class ChangeClassification(unittest.TestCase):
    def test_docs_only(self):
        self.assertTrue(ci.documentation_only(["README.md", "AGENTS.md", "docs/VISION.md", "docs/design/sketch.png"]))

    def test_code_and_mixed_changes(self):
        for path in ("src/main/content.md", "index.html", "build.sbt", "tests/browser_smoke.py", ".github/workflows/deploy.yml", "scripts/ci_changes.py"):
            with self.subTest(path=path):
                self.assertFalse(ci.documentation_only(["docs/VISION.md", path]))
        self.assertFalse(ci.documentation_only([]))

    def test_deleted_or_renamed_code_is_not_documentation(self):
        self.assertFalse(ci.documentation_only(["src/old.scala", "docs/old.scala"]))
        self.assertTrue(ci.documentation_only(["docs/old.md", "docs/new.md"]))

    def test_pr_merge_base_and_nul_paths(self):
        event = {"pull_request": {"base": {"sha": "base"}, "head": {"sha": "head"}}}
        with patch.object(ci.subprocess, "run") as run:
            run.return_value.stdout = b"docs/a\nfile.md\0src/code.scala\0"
            self.assertEqual(ci.changed_paths("pull_request", event), ["docs/a\nfile.md", "src/code.scala"])
            self.assertEqual(run.call_args.args[0], ["git", "diff", "--name-only", "--no-renames", "-z", "base...head"])

    def test_push_uses_full_commit_range(self):
        with patch.object(ci.subprocess, "run") as run:
            run.return_value.stdout = b"docs/a.md\0"
            ci.changed_paths("push", {"before": "base", "after": "head"})
            self.assertEqual(run.call_args.args[0][-2:], ["base", "head"])
            ci.changed_paths("push", {"before": "0" * 40, "after": "head"})
            self.assertIn("ls-tree", run.call_args.args[0])

    def test_manual_and_unknown_events_run_game_jobs(self):
        self.assertFalse(ci.documentation_only(ci.changed_paths("workflow_dispatch", {})))
        self.assertFalse(ci.documentation_only(ci.changed_paths("unknown", {})))


if __name__ == "__main__":
    unittest.main()
