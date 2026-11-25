"""Check portable executable launching for the local developer tools."""
import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location(
    "balance", Path(__file__).resolve().parents[1] / "scripts/balance.py"
)
balance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(balance)


class ExecutableCommand(unittest.TestCase):
    def test_windows_batch_launcher_uses_command_processor(self):
        found = {
            "sbt": r"C:\tools\sbt\bin\sbt.bat",
            "cmd.exe": r"C:\Windows\System32\cmd.exe",
        }

        command = balance.executable_command(
            "sbt",
            "set ThisBuild / offline := true",
            "balance/fastOptJS",
            platform="nt",
            finder=found.get,
        )

        self.assertEqual(command, [
            r"C:\Windows\System32\cmd.exe",
            "/d",
            "/c",
            r"C:\tools\sbt\bin\sbt.bat",
            "set ThisBuild / offline := true",
            "balance/fastOptJS",
        ])

    def test_native_and_unix_launchers_run_directly(self):
        command = balance.executable_command(
            "sbt", "balance/fastOptJS", platform="posix",
            finder=lambda _: "/usr/local/bin/sbt",
        )
        self.assertEqual(command, ["/usr/local/bin/sbt", "balance/fastOptJS"])

    def test_missing_launcher_has_actionable_error(self):
        with self.assertRaisesRegex(FileNotFoundError, "add it to PATH"):
            balance.executable_command(
                "definitely-missing-antidle-sbt",
                platform="nt",
                finder=lambda _: None,
            )


if __name__ == "__main__":
    unittest.main()
