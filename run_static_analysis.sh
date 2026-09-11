#!/usr/bin/env bash

# Exit on any error
set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[+] Running Semgrep..."
semgrep scan --config p/default --config p/secrets --metrics=off --json --output "$REPO_ROOT/semgrep.json" .

echo "[+] Generating sg target list..."
python3 - <<'PY'
import json, pathlib, sys
art = pathlib.Path('/workspace/sgsm')
semgrep_json = art / 'semgrep.json'
targets_file = art / 'sg-targets.txt'
try:
    data = json.loads(semgrep_json.read_text())
except Exception:
    targets_file.write_text('', encoding='utf-8')
    sys.exit(0)
scanned = data.get('paths', {}).get('scanned') or []
if not scanned:
    scanned = sorted({r.get('path') for r in data.get('results', []) if isinstance(r, dict) and isinstance(r.get('path'), str) and r.get('path')})
bounded = scanned[:4000]
targets_file.write_text('\n'.join(bounded), encoding='utf-8')
print('sg-targets written')
PY

echo "[+] Running ast-grep (sg)..."
xargs -r -n 200 sg run --pattern '$F($$$ARGS)' --json=stream < "$REPO_ROOT/sg-targets.txt" > "$REPO_ROOT/ast-grep.json" 2> "$REPO_ROOT/ast-grep.log" || true

echo "[+] Running Gitleaks..."
gitleaks detect --source "$REPO_ROOT" --report-format json --report-path "$REPO_ROOT/gitleaks.json" || true

echo "[+] Running TruffleHog..."
trufflehog filesystem --no-update --json --no-verification "$REPO_ROOT" > "$REPO_ROOT/trufflehog.json" || true

echo "[+] Running Trivy filesystem scan..."
trivy fs --scanners vuln,misconfig --timeout 30m --offline-scan --format json --output "$REPO_ROOT/trivy-fs.json" "$REPO_ROOT" || true

echo "[+] Static analysis completed."