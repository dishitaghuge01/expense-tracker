"""Offline fault injection for the real, no-argument backend installer.

Run (verification role only):
    python3 -m unittest discover -s ops/tests -p 'test_backend_bootstrap.py' -v

Every case copies the actual installer into a disposable miniature repository.
Only that repository's pin file uses synthetic checksums. curl and sleep are
local Python fakes; archive/hash/extraction/install commands remain real Linux
utilities. No downloaded or installed executable is ever run by these tests.
"""

import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile


REPOSITORY = Path(__file__).resolve().parents[2]
DENO_VERSION = "2.9.8"
SUPABASE_VERSION = "3.0.0"
# Exact assets from official tag metadata for Deno v2.9.8 / Supabase v3.0.0.
DENO_GITHUB_ASSET_ID = "662551650"
SUPABASE_GITHUB_ASSET_ID = "701653806"
URLS = {
    "deno": (
        "https://api.github.com/repos/denoland/deno/releases/assets/"
        f"{DENO_GITHUB_ASSET_ID}"
    ),
    "supabase": (
        "https://api.github.com/repos/supabase/cli/releases/assets/"
        f"{SUPABASE_GITHUB_ASSET_ID}"
    ),
}
ERROR_BODY = "SYNTHETIC_UPSTREAM_BODY_MUST_NOT_APPEAR_IN_LOGS"

# These scripts are written, not executed, when this module is imported.
FAKE_CURL = r'''
import json
import os
from pathlib import Path
import stat
import sys

args = sys.argv[1:]
values = {}
urls = []
flags = []
value_options = {
    "--output", "--write-out", "--url", "--connect-timeout", "--max-time",
    "--retry", "--retry-delay", "--retry-max-time", "--proto",
    "--proto-redir", "--request", "--header", "--user-agent", "--cacert",
    "--capath", "--max-redirs", "--speed-time", "--speed-limit",
}
i = 0
while i < len(args):
    arg = args[i]
    key, equal, value = arg.partition("=")
    if key in value_options:
        if not equal:
            i += 1
            value = args[i]
        values.setdefault(key, []).append(value)
    elif arg in ("-o", "-w", "-H"):
        i += 1
        key = {"-o": "--output", "-w": "--write-out", "-H": "--header"}[arg]
        values.setdefault(key, []).append(args[i])
    elif arg.startswith("-o") and len(arg) > 2:
        values.setdefault("--output", []).append(arg[2:])
    elif arg.startswith("-w") and len(arg) > 2:
        values.setdefault("--write-out", []).append(arg[2:])
    elif arg.startswith("-H") and len(arg) > 2:
        values.setdefault("--header", []).append(arg[2:])
    elif arg.startswith("--"):
        flags.append(key)
    elif arg.startswith("-"):
        flags.extend("-" + char for char in arg[1:])
    else:
        urls.append(arg)
    i += 1
urls += values.get("--url", [])
log = Path(os.environ["BOOTSTRAP_CURL_LOG"])
previous = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
url = urls[0] if len(urls) == 1 else "INVALID_URL_LIST"
number = sum(call["url"] == url for call in previous)
plan = json.loads(Path(os.environ["BOOTSTRAP_PLAN"]).read_text())
known = json.loads(os.environ["BOOTSTRAP_FIXTURES"])
sequence = plan.get(url)
if url not in known or (sequence is not None and number >= len(sequence)):
    response = {"exit": 97, "status": "000", "body": "unexpected fake curl call"}
else:
    response = sequence[number] if sequence is not None else {"exit": 0, "status": "200", "body": "valid"}
output = values.get("--output", [None])[-1]
record = {"url": url, "urls": urls, "args": args, "values": values, "flags": flags,
          "output": output, "exit": response["exit"], "status": response["status"]}
if output and output != "-":
    destination = Path(output)
    if not destination.is_absolute():
        destination = Path.cwd() / destination
    record["output"] = str(destination)
    body = response.get("body", "error")
    if body == "valid":
        payload = Path(known[url]).read_bytes()
    elif body == "partial":
        payload = Path(known[url]).read_bytes()[:23]
    elif body == "json":
        # A successful API metadata response is JSON, not the requested archive.
        payload = json.dumps({
            "id": int(url.rsplit("/", 1)[-1]),
            "content_type": "application/octet-stream",
            "message": "SYNTHETIC_UPSTREAM_BODY_MUST_NOT_APPEAR_IN_LOGS",
        }).encode()
    else:
        payload = ("SYNTHETIC_UPSTREAM_BODY_MUST_NOT_APPEAR_IN_LOGS:" + body).encode()
    destination.write_bytes(payload)
    record["output_mode"] = stat.S_IMODE(destination.stat().st_mode)
    record["output_device"] = destination.stat().st_dev
else:
    record["invalid_output"] = True
with log.open("a") as stream:
    stream.write(json.dumps(record) + "\n")
with open(os.environ["BOOTSTRAP_EVENTS"], "a") as stream:
    stream.write(json.dumps({"operation": "curl", **record}) + "\n")
if not output or output == "-":
    sys.exit(97)
format_string = values.get("--write-out", [""])[-1]
sys.stdout.write(format_string.replace("%{http_code}", response["status"])
                 .replace("%{response_code}", response["status"]))
if response["exit"]:
    sys.stderr.write("curl: synthetic network/HTTP failure\n")
sys.exit(response["exit"])
'''

FAKE_SLEEP = r'''
import json
import os
import sys
with open(os.environ["BOOTSTRAP_SLEEP_LOG"], "a") as stream:
    stream.write(json.dumps(sys.argv[1:]) + "\n")
with open(os.environ["BOOTSTRAP_EVENTS"], "a") as stream:
    stream.write(json.dumps({"operation": "sleep", "args": sys.argv[1:]}) + "\n")
'''

FAKE_UNAME = r'''
import sys
values = {"-s": "Linux", "-m": "x86_64", "-sm": "Linux x86_64"}
if len(sys.argv) == 2 and sys.argv[1] in values:
    print(values[sys.argv[1]])
else:
    sys.exit(98)
'''

# Instrument real utilities, without simulating their results. In particular,
# failed checksum/extraction commands cannot accidentally look successful.
UTILITY_WRAPPER = r'''
import json
import os
from pathlib import Path
import subprocess
import sys
name = Path(sys.argv[0]).name
real = json.loads(os.environ["BOOTSTRAP_REAL_UTILITIES"])[name]
def record(operation, **extra):
    with open(os.environ["BOOTSTRAP_EVENTS"], "a") as stream:
        stream.write(json.dumps({"operation": operation, "args": sys.argv[1:], **extra}) + "\n")
record(name)
result = subprocess.run([real, *sys.argv[1:]], timeout=10, check=False)
record(name + ":done", exit=result.returncode)
sys.exit(result.returncode)
'''


def response(exit_code=0, status="200", body="valid"):
    return {"exit": exit_code, "status": status, "body": body}


class MiniRepository:
    """All mutable files, including fixture pins and tool installations, are private."""

    def __init__(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="backend-bootstrap-test-")
        self.base = Path(self.temporary.name)
        self.root = self.base / "repository with spaces"
        tooling = self.root / "ops/tooling"
        tooling.mkdir(parents=True)
        self.installer = tooling / "install-backend.sh"
        shutil.copyfile(REPOSITORY / "ops/tooling/install-backend.sh", self.installer)
        self.cache = self.root / ".tools/backend/downloads"
        self.backend = self.root / ".tools/backend"
        self.archives = {
            "deno": self.cache / f"deno-{DENO_VERSION}-linux-x86_64.zip",
            "supabase": self.cache / f"supabase-{SUPABASE_VERSION}-linux-amd64.tar.gz",
        }
        self.installed = {
            "deno": self.backend / f"deno-{DENO_VERSION}/deno",
            "supabase": self.backend / f"supabase-{SUPABASE_VERSION}/supabase",
        }
        self.payloads = {
            name: (
                "#!/bin/sh\n"
                f"printf '%s\\n' '{name} must not execute' >> \"$BOOTSTRAP_EXECUTED\"\n"
                "exit 99\n"
            ).encode()
            for name in URLS
        }
        fixtures = self.base / "fixtures"
        fixtures.mkdir()
        self.fixtures = {"deno": fixtures / "deno.zip", "supabase": fixtures / "supabase.tar.gz"}
        with zipfile.ZipFile(self.fixtures["deno"], "w") as archive:
            info = zipfile.ZipInfo("deno")
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o755) << 16
            archive.writestr(info, self.payloads["deno"])
        with tarfile.open(self.fixtures["supabase"], "w:gz") as archive:
            info = tarfile.TarInfo("supabase")
            info.size = len(self.payloads["supabase"])
            info.mode = 0o755
            archive.addfile(info, io.BytesIO(self.payloads["supabase"]))
        self.hashes = {
            name: hashlib.sha256(path.read_bytes()).hexdigest()
            for name, path in self.fixtures.items()
        }
        (tooling / "backend-versions.env").write_text(
            f"DENO_VERSION={DENO_VERSION}\n"
            f"DENO_GITHUB_ASSET_ID={DENO_GITHUB_ASSET_ID}\n"
            f"DENO_LINUX_X64_SHA256={self.hashes['deno']}\n"
            f"SUPABASE_VERSION={SUPABASE_VERSION}\n"
            f"SUPABASE_GITHUB_ASSET_ID={SUPABASE_GITHUB_ASSET_ID}\n"
            f"SUPABASE_LINUX_X64_SHA256={self.hashes['supabase']}\n"
        )
        self.pin_bytes = (tooling / "backend-versions.env").read_bytes()
        self.bin = self.base / "fake-bin"
        self.bin.mkdir()
        self.curl_log = self.base / "curl.jsonl"
        self.sleep_log = self.base / "sleep.jsonl"
        self.events_log = self.base / "events.jsonl"
        self.executed = self.base / "executed-binary"
        self.plan = self.base / "plan.json"
        self.plan.write_text("{}")
        self.bash = shutil.which("bash")
        if not self.bash:
            raise RuntimeError("These tests require the supported Linux Bash host")
        utilities = {}
        for name in ("sha256sum", "unzip", "tar", "install"):
            real = shutil.which(name)
            if not real:
                raise RuntimeError(f"Required real fixture utility missing: {name}")
            utilities[name] = real
            self.script(name, UTILITY_WRAPPER)
        self.script("curl", FAKE_CURL)
        self.script("sleep", FAKE_SLEEP)
        self.script("uname", FAKE_UNAME)
        # A minimal environment avoids user curl/Bash startup configuration,
        # credentials, proxies, exported shell functions and unrelated tool paths.
        self.environment = {
            "PATH": str(self.bin) + os.pathsep + "/usr/bin:/bin",
            "HOME": str(self.base / "home"),
            "TMPDIR": str(self.base / "tmp"),
            "LC_ALL": "C",
            "BOOTSTRAP_CURL_LOG": str(self.curl_log),
            "BOOTSTRAP_SLEEP_LOG": str(self.sleep_log),
            "BOOTSTRAP_EVENTS": str(self.events_log),
            "BOOTSTRAP_PLAN": str(self.plan),
            "BOOTSTRAP_EXECUTED": str(self.executed),
            "BOOTSTRAP_FIXTURES": json.dumps({URLS[n]: str(p) for n, p in self.fixtures.items()}),
            "BOOTSTRAP_REAL_UTILITIES": json.dumps(utilities),
        }
        Path(self.environment["HOME"]).mkdir()
        Path(self.environment["TMPDIR"]).mkdir()

    def script(self, name, source):
        path = self.bin / name
        path.write_text(f"#!{sys.executable}\n" + source)
        path.chmod(0o755)

    def close(self):
        self.temporary.cleanup()

    def configure(self, **plans):
        self.plan.write_text(json.dumps({URLS[name]: sequence for name, sequence in plans.items()}))

    @staticmethod
    def records(path):
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    @property
    def calls(self):
        return self.records(self.curl_log)

    @property
    def events(self):
        return self.records(self.events_log)

    @property
    def delays(self):
        # sleep accepts seconds with or without its optional 's' suffix.
        return [sum(float(arg.rstrip("s")) for arg in args) for args in self.records(self.sleep_log)]

    def clear_logs(self):
        for path in (self.curl_log, self.sleep_log, self.events_log):
            path.unlink(missing_ok=True)

    def seed_cache(self, name, payload=None):
        self.cache.mkdir(parents=True, exist_ok=True)
        self.archives[name].write_bytes(self.fixtures[name].read_bytes() if payload is None else payload)

    def seed_installed(self):
        sentinels = {}
        for name, path in self.installed.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"existing installed {name}: preserve on failure\n".encode())
            path.chmod(0o751)
            sentinels[name] = (path.read_bytes(), path.stat().st_mode, path.stat().st_ino)
        return sentinels

    def invoke(self):
        # Intentionally not repository cwd: the public entrypoint is root-relative.
        return subprocess.run(
            [self.bash, str(self.installer)], cwd=self.base,
            env=self.environment, capture_output=True, text=True,
            timeout=20, check=False,
        )


class BackendBootstrapTests(unittest.TestCase):
    def setUp(self):
        self.fixture = self.new_fixture()

    def new_fixture(self):
        fixture = MiniRepository()
        self.addCleanup(fixture.close)
        return fixture

    def invoke(self, fixture=None, success=True):
        fixture = fixture or self.fixture
        result = fixture.invoke()
        details = f"exit={result.returncode}\nstdout={result.stdout}\nstderr={result.stderr}"
        if success:
            self.assertEqual(result.returncode, 0, details)
        else:
            self.assertNotEqual(result.returncode, 0, details)
            self.assertTrue((result.stdout + result.stderr).strip(), "Failure must be visible")
        self.assertLess(len(result.stdout) + len(result.stderr), 16384, "Unbounded installer logging")
        self.assertNotIn(ERROR_BODY, result.stdout + result.stderr)
        self.assertFalse(fixture.executed.exists(), "Installer executed an untrusted installed binary")
        self.assertEqual(
            (fixture.root / "ops/tooling/backend-versions.env").read_bytes(), fixture.pin_bytes,
            "Installer must not rewrite pins on failure or success",
        )
        self.assert_curl_protocol(fixture)
        return result

    def assert_curl_protocol(self, fixture):
        for call in fixture.calls:
            with self.subTest(url=call["url"], args=call["args"]):
                self.assertIn(call["url"], URLS.values())
                self.assertEqual(call["urls"], [call["url"]], "One official artifact per invocation")
                flags, values = call["flags"], call["values"]
                for long_option, short_option in (
                    ("--fail", "f"), ("--location", "L"),
                    ("--silent", "s"), ("--show-error", "S"),
                ):
                    self.assertTrue(long_option in flags or "-" + short_option in flags, long_option)
                self.assertEqual([float(v) for v in values.get("--connect-timeout", [])], [20])
                self.assertEqual([float(v) for v in values.get("--max-time", [])], [180])
                self.assertTrue(all(float(v) == 0 for v in values.get("--retry", [])), "No curl-internal retries")
                headers = {}
                for header in values.get("--header", []):
                    name, separator, value = header.partition(":")
                    self.assertEqual(separator, ":", "Headers must be explicit, not file-based")
                    headers.setdefault(name.strip().lower(), []).append(value.strip())
                self.assertEqual(headers.get("accept"), ["application/octet-stream"])
                self.assertEqual(headers.get("x-github-api-version"), ["2022-11-28"])
                self.assertNotIn("authorization", headers)
                self.assertNotIn("proxy-authorization", headers)
                self.assertNotIn("--location-trusted", flags)
                for option in ("--user", "-u", "--oauth2-bearer", "--netrc", "--netrc-optional"):
                    self.assertNotIn(option, flags, "Public assets must not require authentication")
                self.assertNotIn("--insecure", flags)
                self.assertNotIn("--proxy-insecure", flags)
                self.assertNotIn("--doh-insecure", flags)
                self.assertNotIn("-k", flags, "TLS verification must not be disabled")
                self.assertTrue(any(
                    "%{http_code}" in value or "%{response_code}" in value
                    for value in values.get("--write-out", [])
                ), "HTTP status must be captured separately from response bodies")
                self.assertNotIn("invalid_output", call)
                output = Path(call["output"])
                self.assertNotIn(output, fixture.archives.values(), "Never curl directly into a cache entry")
                self.assertTrue(output.is_relative_to(fixture.backend), "Download must be private to this repository")
                self.assertEqual(call["output_device"], fixture.cache.stat().st_dev, "Atomic cache promotion requires same filesystem")
                self.assertEqual(call["output_mode"] & 0o077, 0, "Download temporary file must be private")
                self.assertFalse(output.exists(), "Temporary download was not cleaned up/promoted")

    def assert_installed(self, fixture=None):
        fixture = fixture or self.fixture
        for name, path in fixture.installed.items():
            self.assertTrue(path.is_file(), str(path))
            self.assertEqual(path.read_bytes(), fixture.payloads[name])
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o755)

    def assert_no_install(self, fixture=None):
        fixture = fixture or self.fixture
        for path in fixture.installed.values():
            self.assertFalse(path.exists(), f"Failed bootstrap installed {path}")
        self.assertFalse(any(event["operation"] == "install" for event in fixture.events))

    def assert_sentinels(self, sentinels, fixture=None):
        fixture = fixture or self.fixture
        for name, (data, mode, inode) in sentinels.items():
            path = fixture.installed[name]
            self.assertTrue(path.exists(), "Failure deleted an existing installed binary")
            self.assertEqual((path.read_bytes(), path.stat().st_mode, path.stat().st_ino), (data, mode, inode))

    def assert_cache_only(self, names, fixture=None):
        fixture = fixture or self.fixture
        expected = {fixture.archives[name] for name in names}
        actual = {path for path in fixture.cache.rglob("*") if path.is_file()} if fixture.cache.exists() else set()
        self.assertEqual(actual, expected, "Partial/error/temp files must not survive in archive cache")
        for name in names:
            self.assertEqual(fixture.archives[name].read_bytes(), fixture.fixtures[name].read_bytes())
        allowed = expected | {path for path in fixture.installed.values() if path.exists()}
        remaining = {path for path in fixture.backend.rglob("*") if path.is_file()} if fixture.backend.exists() else set()
        self.assertEqual(remaining, allowed, "Extraction/download temporary files must be cleaned up")

    def assert_no_extraction(self, fixture=None):
        fixture = fixture or self.fixture
        self.assertFalse(any(event["operation"] in ("unzip", "tar", "install") for event in fixture.events))

    def assert_call_names(self, names, fixture=None):
        fixture = fixture or self.fixture
        self.assertEqual([call["url"] for call in fixture.calls], [URLS[name] for name in names])

    def test_cold_bootstrap_installs_exact_fixture_binaries_and_cache_paths(self):
        self.invoke()
        self.assert_call_names(["deno", "supabase"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_warm_cache_is_reverified_without_network_even_if_binaries_are_missing(self):
        self.invoke()
        self.fixture.clear_logs()
        for path in self.fixture.installed.values():
            path.unlink()
        result = self.invoke()
        self.assert_call_names([])
        self.assertEqual(self.fixture.delays, [])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})
        self.assertTrue(any(event["operation"] == "sha256sum:done" and event["exit"] == 0
                            for event in self.fixture.events), "Warm reuse must verify bytes")
        self.assertIn("cache", (result.stdout + result.stderr).lower(), "Report cache provenance")

    def test_first_archive_504_then_success_uses_same_official_source(self):
        self.fixture.configure(deno=[response(22, "504", "error"), response()])
        self.invoke()
        self.assert_call_names(["deno", "deno", "supabase"])
        self.assertEqual(self.fixture.delays, [15])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_http_504_metadata_is_retried_even_if_curl_returns_zero(self):
        self.fixture.configure(deno=[response(0, "504", "error"), response()])
        self.invoke()
        self.assert_call_names(["deno", "deno", "supabase"])
        self.assertEqual(self.fixture.delays, [15])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_second_archive_504_then_success(self):
        self.fixture.configure(supabase=[response(22, "504", "error"), response()])
        self.invoke()
        self.assert_call_names(["deno", "supabase", "supabase"])
        self.assertEqual(self.fixture.delays, [15])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_success_on_third_attempt_uses_both_fixed_delays(self):
        self.fixture.configure(deno=[response(22, "504", "error"), response(22, "504", "error"), response()])
        self.invoke()
        self.assert_call_names(["deno", "deno", "deno", "supabase"])
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_persistent_504_stops_after_three_and_never_installs_partial_body(self):
        self.fixture.configure(deno=[response(22, "504", "partial")] * 3)
        self.invoke(success=False)
        self.assert_call_names(["deno"] * 3)
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_no_install()
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_persistent_504_preserves_existing_installed_sentinels(self):
        sentinels = self.fixture.seed_installed()
        self.fixture.configure(deno=[response(22, "504", "error")] * 3)
        self.invoke(success=False)
        self.assert_call_names(["deno"] * 3)
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_sentinels(sentinels)
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_transport_timeout_then_recovery(self):
        self.fixture.configure(deno=[response(28, "000", "partial"), response()])
        self.invoke()
        self.assert_call_names(["deno", "deno", "supabase"])
        self.assertEqual(self.fixture.delays, [15])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})

    def test_other_documented_transient_transport_failures_recover(self):
        for code in (6, 7, 18, 35, 52, 55, 56, 92):
            with self.subTest(curl_exit=code):
                fixture = self.new_fixture()
                fixture.configure(deno=[response(code, "000", "partial"), response()])
                self.invoke(fixture)
                self.assert_call_names(["deno", "deno", "supabase"], fixture)
                self.assertEqual(fixture.delays, [15])
                self.assert_installed(fixture)
                self.assert_cache_only({"deno", "supabase"}, fixture)

    def test_other_transient_http_statuses_recover(self):
        for status in ("408", "429", "500", "502", "503", "599"):
            with self.subTest(http_status=status):
                fixture = self.new_fixture()
                fixture.configure(deno=[response(22, status, "error"), response()])
                self.invoke(fixture)
                self.assert_call_names(["deno", "deno", "supabase"], fixture)
                self.assertEqual(fixture.delays, [15])
                self.assert_installed(fixture)
                self.assert_cache_only({"deno", "supabase"}, fixture)

    def test_permanent_404_fails_immediately_without_delay(self):
        self.fixture.configure(deno=[response(22, "404", "error")])
        self.invoke(success=False)
        self.assert_call_names(["deno"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_no_install()
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_permanent_403_fails_immediately_and_preserves_installed_binaries(self):
        sentinels = self.fixture.seed_installed()
        self.fixture.configure(deno=[response(22, "403", "error")])
        self.invoke(success=False)
        self.assert_call_names(["deno"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_sentinels(sentinels)
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_certificate_trust_failure_is_not_retried(self):
        sentinels = self.fixture.seed_installed()
        self.fixture.configure(deno=[response(60, "000", "error")])
        self.invoke(success=False)
        self.assert_call_names(["deno"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_sentinels(sentinels)
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_wrong_download_checksum_fails_without_retry_or_cache_promotion(self):
        sentinels = self.fixture.seed_installed()
        self.fixture.configure(deno=[response(0, "200", "wrong checksum")])
        self.invoke(success=False)
        self.assert_call_names(["deno"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_sentinels(sentinels)
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_http_200_api_json_fails_checksum_without_retry_cache_or_install(self):
        for name in URLS:
            for existing_binaries in (False, True):
                with self.subTest(archive=name, existing_binaries=existing_binaries):
                    fixture = self.new_fixture()
                    sentinels = fixture.seed_installed() if existing_binaries else None
                    fixture.configure(**{name: [response(0, "200", "json")]})
                    result = self.invoke(fixture, success=False)
                    self.assert_call_names(["deno"] if name == "deno" else ["deno", "supabase"], fixture)
                    self.assertEqual(fixture.delays, [])
                    self.assertIn("checksum", (result.stdout + result.stderr).lower())
                    self.assertTrue(any(
                        event["operation"] == "sha256sum:done" and event["exit"] != 0
                        for event in fixture.events
                    ), "HTTP 200 JSON must fail archive SHA-256 verification")
                    self.assert_no_extraction(fixture)
                    if existing_binaries:
                        self.assert_sentinels(sentinels, fixture)
                    else:
                        self.assert_no_install(fixture)
                    # The valid first archive may persist, but never the JSON body.
                    self.assert_cache_only(set() if name == "deno" else {"deno"}, fixture)

    def test_partial_transport_failure_cleans_up_after_exhaustion(self):
        self.fixture.configure(deno=[response(18, "200", "partial")] * 3)
        self.invoke(success=False)
        self.assert_call_names(["deno"] * 3)
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_no_install()
        self.assert_no_extraction()
        self.assert_cache_only(set())

    def test_first_verified_archive_persists_when_second_fails_neither_installs(self):
        self.fixture.configure(supabase=[response(22, "504", "partial")] * 3)
        self.invoke(success=False)
        self.assert_call_names(["deno", "supabase", "supabase", "supabase"])
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_no_install()
        self.assert_no_extraction()
        self.assert_cache_only({"deno"})

    def test_second_archive_failure_does_not_replace_either_existing_binary(self):
        sentinels = self.fixture.seed_installed()
        self.fixture.configure(supabase=[response(22, "504", "error")] * 3)
        self.invoke(success=False)
        self.assert_call_names(["deno", "supabase", "supabase", "supabase"])
        self.assertEqual(self.fixture.delays, [15, 45])
        self.assert_sentinels(sentinels)
        self.assert_no_extraction()
        self.assert_cache_only({"deno"})

    def test_wrong_second_archive_checksum_leaves_first_cached_but_no_install(self):
        self.fixture.configure(supabase=[response(0, "200", "wrong checksum")])
        self.invoke(success=False)
        self.assert_call_names(["deno", "supabase"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_no_install()
        self.assert_no_extraction()
        self.assert_cache_only({"deno"})

    def test_corrupt_existing_cache_fails_without_network_or_extraction(self):
        for corrupt_name in URLS:
            with self.subTest(corrupt_archive=corrupt_name):
                fixture = self.new_fixture()
                sentinels = fixture.seed_installed()
                for name in URLS:
                    fixture.seed_cache(name, b"corrupt cached archive" if name == corrupt_name else None)
                before = {name: path.read_bytes() for name, path in fixture.archives.items()}
                self.invoke(fixture, success=False)
                self.assert_call_names([], fixture)
                self.assertEqual(fixture.delays, [])
                self.assert_no_extraction(fixture)
                self.assert_sentinels(sentinels, fixture)
                self.assertEqual({name: path.read_bytes() for name, path in fixture.archives.items()}, before,
                                 "Corrupt cache must not be silently deleted/replaced")

    def test_corrupt_second_cache_prevents_network_even_when_first_archive_uncached(self):
        # Fail closed for preexisting corruption; do not download another archive
        # first and only then discover that operator review is required.
        self.fixture.seed_cache("supabase", b"corrupt second archive")
        sentinels = self.fixture.seed_installed()
        self.invoke(success=False)
        self.assert_call_names([])
        self.assertEqual(self.fixture.delays, [])
        self.assert_no_extraction()
        self.assert_sentinels(sentinels)
        self.assertEqual(self.fixture.archives["supabase"].read_bytes(), b"corrupt second archive")
        self.assertFalse(self.fixture.archives["deno"].exists())

    def test_tampering_after_success_is_detected_for_each_cached_archive(self):
        for corrupt_name in URLS:
            with self.subTest(tampered_archive=corrupt_name):
                fixture = self.new_fixture()
                self.invoke(fixture)
                sentinels = {
                    name: (path.read_bytes(), path.stat().st_mode, path.stat().st_ino)
                    for name, path in fixture.installed.items()
                }
                fixture.archives[corrupt_name].write_bytes(b"tampered after successful installation")
                fixture.clear_logs()
                self.invoke(fixture, success=False)
                self.assert_call_names([], fixture)
                self.assertEqual(fixture.delays, [])
                self.assert_no_extraction(fixture)
                self.assert_sentinels(sentinels, fixture)
                self.assertEqual(fixture.archives[corrupt_name].read_bytes(), b"tampered after successful installation")

    def test_valid_cached_first_archive_is_reused_after_second_download_recovers(self):
        self.fixture.configure(supabase=[response(22, "504", "error")] * 3)
        self.invoke(success=False)
        self.assert_cache_only({"deno"})
        self.fixture.configure()
        self.fixture.clear_logs()
        self.invoke()
        self.assert_call_names(["supabase"])
        self.assertEqual(self.fixture.delays, [])
        self.assert_installed()
        self.assert_cache_only({"deno", "supabase"})


if __name__ == "__main__":
    unittest.main()
