"""Conservative documentation-only detection shared by test and deploy workflows."""
import json
import os
from pathlib import Path
import subprocess


def documentation_only(paths):
    return bool(paths) and all(
        path.startswith("docs/") or ("/" not in path and path.lower().endswith(".md"))
        for path in paths
    )


def changed_paths(event_name, event):
    if event_name == "pull_request":
        base = event["pull_request"]["base"]["sha"]
        head = event["pull_request"]["head"]["sha"]
        # Compare the PR branch since its merge base, excluding unrelated main changes.
        args = ["diff", "--name-only", "--no-renames", "-z", f"{base}...{head}"]
    elif event_name == "push":
        base, head = event["before"], event["after"]
        if not base.strip("0"):
            args = ["ls-tree", "-r", "--name-only", "-z", head]
        else:
            args = ["diff", "--name-only", "--no-renames", "-z", base, head]
    else:
        # Manual dispatch always runs validation; unknown events also fail open.
        return []
    result = subprocess.run(["git", *args], check=True, capture_output=True)
    return [os.fsdecode(path) for path in result.stdout.split(b"\0") if path]


def main():
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        event_name = os.environ["GITHUB_EVENT_NAME"]
        paths = changed_paths(event_name, event)
        print(f"Compared {len(paths)} changed paths for {event_name}.")
        skip = documentation_only(paths)
    except (KeyError, ValueError, OSError, subprocess.CalledProcessError):
        skip = False
        print("Change detection unavailable; running game jobs conservatively.")
    value = "false" if skip else "true"
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"run_game_jobs={value}\n")
    print("Documentation-only: skipping game jobs." if skip else "Running game jobs.")


if __name__ == "__main__":
    main()
