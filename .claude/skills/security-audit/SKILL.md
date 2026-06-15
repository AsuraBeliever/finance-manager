---
name: security-audit
description: Perform an exhaustive multi-phase security audit of a repository. Detects secrets in git history, static vulnerabilities in current code, dependency CVEs, container/image risks, cloud and IaC misconfigurations, SBOM generation, and license compliance issues. Covers modern attack vectors including prototype pollution, GraphQL injection, deserialization, race conditions, XXE, HTTP request smuggling, DNS-rebinding SSRF, and OAuth misconfigurations. Produces a prioritized report with PoC and concrete fixes for every finding.
---

You are an expert security engineer. When invoked, perform a full security audit following the methodology defined in `guide.md`. **Read `guide.md` in full before doing anything else.**

---

## Arguments (Scoping)

`$ARGUMENTS` is a prompt-level placeholder — it is **not** a shell variable. Before running any command, resolve it as follows:

- If `$ARGUMENTS` is empty → `AUDIT_SCOPE="."`
- If `$ARGUMENTS` contains one or more paths → `AUDIT_SCOPE="<those paths space-separated>"`

Then **export it once at the top of your first bash block** so every subsequent block inherits it:

```bash
# Resolve AUDIT_SCOPE — edit the value if $ARGUMENTS was non-empty
export AUDIT_SCOPE="."   # replace "." with actual paths if provided
echo "Audit scope: $AUDIT_SCOPE"
```

**Resolution examples** (do this mentally before the first bash block):

```
$ARGUMENTS = "src/api src/auth"
→ export AUDIT_SCOPE="src/api src/auth"

$ARGUMENTS = ""
→ export AUDIT_SCOPE="."
```

> **Important**: Regardless of `AUDIT_SCOPE`, always run git history analysis and container/cloud scans against the **full** repository. A secret committed or an image misconfigured outside the scoped path is still a live risk.

---

## Execution Protocol

### 0. Load methodology

Locate `guide.md` using the following priority order. **Do this as a file-read operation** — read files directly, never execute them.

1. `.claude/skills/security-audit/guide.md`
2. `guide.md` at repository root (same directory as `SKILL.md`)
3. `./guide.md` (current working directory — last resort)

If none yield the file, **stop immediately** and tell the user:
> "`guide.md` not found. Place it alongside `SKILL.md` or in `.claude/skills/security-audit/`. Do not proceed without it."

After loading the file, verify it contains the expected section headers (`## Phase 1`, `## Phase 2`, `## Output Format`). If the file exists but does not contain these markers, stop and warn the user — a wrong or tampered `guide.md` produces an unreliable audit.

Do not improvise the methodology if the file is missing or invalid.

---

### Pre-flight checks

Before running any scan, verify that `python3` is available, as several parsing steps depend on it:

```bash
# Verify python3
if ! command -v python3 &>/dev/null; then
  echo "[WARN] python3 not found — JSON parsing steps will be skipped."
  echo "       Install: apt install python3   OR   brew install python3"
  export PYTHON3_AVAILABLE=false
else
  export PYTHON3_AVAILABLE=true
fi
```

---

### 1. Map the attack surface

```bash
echo "=== File type distribution ==="
find "$AUDIT_SCOPE" -type f ! -path '*/.git/*' ! -path '*/node_modules/*' \
  | sed 's/.*\.//' | sort | uniq -c | sort -rn | head -25

echo "=== Manifest files ==="
for f in package.json requirements.txt go.mod Cargo.toml pom.xml Gemfile \
          composer.json build.gradle build.gradle.kts Package.swift CMakeLists.txt \
          pubspec.yaml mix.exs; do
  [ -f "$f" ] && echo "--- $f ---" && cat "$f"
done

echo "=== Environment & infra files ==="
ls -la .env* Dockerfile* docker-compose* .dockerignore 2>/dev/null
ls .github/workflows/ .gitlab-ci.yml .circleci/ Jenkinsfile 2>/dev/null
find . -path '*/.git' -prune -o \( -name '*.tf' -o -name '*.tfvars' \) -print 2>/dev/null | head -20
find . -path '*/.git' -prune -o \( -name '*.yml' -o -name '*.yaml' \) -print \
  | xargs grep -l 'env:\|secret:\|password:\|token:' 2>/dev/null | head -15

echo "=== Exposed entry points ==="
# Web routes
grep -rn --include="*.js" --include="*.ts" --include="*.py" --include="*.go" --include="*.rb" \
  -E "(app\.(get|post|put|delete|patch|all)|router\.(get|post)|@app\.route|func.*Handler)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules | head -40
```

After running these, explicitly state: **languages detected, frameworks, auth mechanism, IaC tooling, container usage, and entry points**.

---

### 2. Phase 1 — Git History Analysis

Prefer dedicated secret-scanning tools — they use entropy analysis and maintained rulesets with far fewer false positives than git-grep.

#### 2a. Preferred: gitleaks or trufflehog

```bash
if command -v gitleaks &>/dev/null; then
  echo "[INFO] Running gitleaks"
  timeout 300 gitleaks detect --source . --report-format json --report-path /tmp/gitleaks-report.json 2>/dev/null
  cat /tmp/gitleaks-report.json

elif command -v trufflehog &>/dev/null; then
  echo "[INFO] Running trufflehog"
  timeout 300 trufflehog git file://. --json 2>/dev/null

else
  echo "[WARN] Neither gitleaks nor trufflehog found."
  echo "       Install: brew install gitleaks   OR   pip install trufflehog"
  echo "       Falling back to git-grep (higher false positive rate)"
  FALLBACK_GIT_GREP=true
fi
```

#### 2b. Fallback: git-grep (only when 2a tools are unavailable)

```bash
if [ "${FALLBACK_GIT_GREP:-false}" = "true" ]; then
  COMMIT_COUNT=$(git log --all --oneline 2>/dev/null | wc -l | tr -d ' ')
  echo "Total commits: $COMMIT_COUNT"
  [ "$COMMIT_COUNT" -gt 2000 ] \
    && echo "[WARN] Large history — processing last 2000 commits only" \
    && DEPTH="--max-count=2000" \
    || DEPTH=""

  # Deleted sensitive files still in history
  git log --all $DEPTH -p --diff-filter=D -- \
    '*.env' '*.pem' '*.key' '*.p12' '*.pfx' '*.jks' 'id_rsa' 'id_ed25519' \
    'credentials' '*.kubeconfig' '*.kube' '*.htpasswd' 2>/dev/null

  # High-signal token patterns
  git log --all $DEPTH -p \
    -S 'AKIA' -S 'ghp_' -S 'ghs_' -S 'sk-' -S 'xoxb-' -S 'xoxp-' \
    -S 'AIza' -S 'SG.' -S 'AC[0-9a-zA-Z]{32}' \
    -S 'BEGIN RSA PRIVATE KEY' -S 'BEGIN EC PRIVATE KEY' \
    -S 'BEGIN OPENSSH PRIVATE KEY' \
    --pickaxe-regex 2>/dev/null | head -600

  # Filenames that should never be committed
  git log --all --name-only --pretty=format: | sort -u | \
    grep -iE '\.(env|pem|key|p12|pfx|cert|crt|jks|pkcs|kubeconfig|htpasswd)$|^id_rsa$|^id_ed25519$|credentials$'
fi
```

#### False positive filter for git phase

Discard a git-grep match only if **all three** of the following are true:
- Value matches a placeholder: `<YOUR_KEY>`, `example`, `changeme`, `xxx`, `dummy`, `test`, `fake`, `YOUR_SECRET`
- File is under `docs/`, `test/`, `__tests__/`, `fixtures/`, `examples/`, or has a `.md`/`.rst` extension
- Commit message contains `example`, `demo`, `template`, or `test`

Report only matches that survive this filter. gitleaks/trufflehog output can be reported as-is.

---

### 3. Phase 2 — Static Analysis

Prefer SAST tools — grep patterns complement them or serve as a last resort, not the primary scanner.

#### 3a. Preferred: Semgrep

```bash
if command -v semgrep &>/dev/null; then
  echo "[INFO] Running semgrep on $AUDIT_SCOPE"
  timeout 600 semgrep scan \
    --config "p/owasp-top-ten" \
    --config "p/secrets" \
    --config "p/javascript" \
    --config "p/python" \
    --config "p/golang" \
    --config "p/java" \
    --config "p/ruby" \
    --config "p/rust" \
    --config "p/kotlin" \
    --json \
    --output /tmp/semgrep-report.json \
    "$AUDIT_SCOPE" 2>/dev/null

  if [ "$PYTHON3_AVAILABLE" = "true" ] && [ -s /tmp/semgrep-report.json ]; then
    python3 - <<'EOF'
import json, sys
try:
    with open('/tmp/semgrep-report.json') as f:
        data = json.load(f)
    results = data.get('results', [])
    high = [r for r in results if r.get('extra', {}).get('severity') in ('ERROR', 'WARNING')]
    for r in high:
        loc = r['path'] + ':' + str(r['start']['line'])
        print(r['extra']['severity'], r['check_id'], loc)
    print(f'\nTotal: {len(results)} findings ({len(high)} high/critical shown above)')
except (json.JSONDecodeError, KeyError) as e:
    print(f'[ERROR] Could not parse semgrep output: {e}', file=sys.stderr)
    sys.exit(1)
EOF
  else
    echo "[WARN] semgrep report missing or empty — check for scan errors above"
  fi

else
  echo "[WARN] semgrep not found. Install: pip install semgrep"
  echo "       Falling back to grep — mark all findings as 'unconfirmed'"
  FALLBACK_GREP=true
fi
```

#### 3b. Language-specific SAST (run when available, always complement semgrep)

```bash
# C / C++ — flawfinder
# FIX: parentheses required around -o predicates to avoid precedence bugs
if find "$AUDIT_SCOPE" \( -name '*.c' -o -name '*.cpp' -o -name '*.h' \) 2>/dev/null | grep -q .; then
  if command -v flawfinder &>/dev/null; then
    echo "[INFO] Running flawfinder (C/C++)"
    timeout 180 flawfinder --minlevel 3 "$AUDIT_SCOPE"
  elif command -v cppcheck &>/dev/null; then
    echo "[INFO] Running cppcheck"
    timeout 180 cppcheck --enable=all --inconclusive "$AUDIT_SCOPE" 2>&1 | grep -v "^\[" | head -60
  else
    echo "[WARN] C/C++ detected but neither flawfinder nor cppcheck found."
    echo "       Install: pip install flawfinder   OR   apt install cppcheck"
  fi
fi

# Kotlin — detekt
if find "$AUDIT_SCOPE" -name '*.kt' 2>/dev/null | grep -q .; then
  if command -v detekt &>/dev/null; then
    echo "[INFO] Running detekt (Kotlin)"
    timeout 180 detekt --input "$AUDIT_SCOPE" --report xml:/tmp/detekt-report.xml
  else
    echo "[WARN] Kotlin detected but detekt not found."
    echo "       Install: brew install detekt   OR   https://detekt.dev/docs/gettingstarted/cli"
  fi
fi

# Swift — swiftlint (security rules)
if find "$AUDIT_SCOPE" -name '*.swift' 2>/dev/null | grep -q .; then
  if command -v swiftlint &>/dev/null; then
    echo "[INFO] Running swiftlint (Swift)"
    timeout 180 swiftlint lint --path "$AUDIT_SCOPE" --reporter json 2>/dev/null \
      | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for w in data:
        if w.get('severity') in ('error', 'warning'):
            print(w['severity'], w['rule_id'], w['file'] + ':' + str(w['line']))
except Exception as e:
    print('[ERROR] swiftlint parse failed:', e, file=sys.stderr)
" 2>/dev/null
  else
    echo "[WARN] Swift detected but swiftlint not found."
    echo "       Install: brew install swiftlint"
  fi
fi

# PHP — psalm or phpstan
if find "$AUDIT_SCOPE" -name '*.php' 2>/dev/null | grep -q .; then
  if command -v psalm &>/dev/null; then
    echo "[INFO] Running psalm (PHP)"
    timeout 300 psalm --taint-analysis "$AUDIT_SCOPE" 2>/dev/null | tail -40
  else
    echo "[WARN] PHP detected but psalm not found."
    echo "       Install: composer global require vimeo/psalm"
  fi
fi
```

#### 3c. Grep patterns (always run as complement; primary if semgrep unavailable)

**Deduplication rule**: After all tools run, consolidate findings by location (`file:line`). If Semgrep and grep report the same location, keep **one** entry marked **Confirmed**. Never list the same vulnerability twice.

```bash
# === Injection ===
grep -rn --include="*.js" --include="*.ts" --include="*.py" --include="*.go" \
  --include="*.rb" --include="*.php" --include="*.java" --include="*.kt" \
  -E "(eval\(|exec\(|os\.system|subprocess\.call|shell=True|\.raw\(|cursor\.execute\(['\"]SELECT)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules

# === Hardcoded secrets — high-signal, low-ambiguity patterns ===
grep -rn -E "(AKIA[0-9A-Z]{16}|ghp_[a-zA-Z0-9]{36}|ghs_[a-zA-Z0-9]{36}|sk-[a-zA-Z0-9]{48}|xoxb-[0-9]{11}|AIza[0-9A-Za-z_-]{35}|SG\.[a-zA-Z0-9_-]{22}\.[a-zA-Z0-9_-]{43})" \
  --include="*.js" --include="*.ts" --include="*.py" --include="*.go" \
  --include="*.java" --include="*.kt" --include="*.swift" --include="*.rb" \
  "$AUDIT_SCOPE" 2>/dev/null

# === XXE — XML External Entity ===
grep -rn --include="*.java" --include="*.py" --include="*.php" --include="*.rb" --include="*.go" \
  -E "(DocumentBuilderFactory|SAXParserFactory|XMLInputFactory|etree\.parse|lxml|defusedxml|parseString|libxml)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules | head -30
# Flag any XML parser instantiation NOT followed by setFeature(XMLConstants.FEATURE_SECURE_PROCESSING)

# === HTTP Request Smuggling indicators ===
grep -rn --include="*.js" --include="*.ts" --include="*.go" --include="*.py" \
  -E "(Transfer-Encoding|Content-Length|chunked)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules | grep -vi test | head -20
# Manual check: any proxy/load balancer config that forwards raw headers without normalization

# === SSRF — including DNS rebinding vectors ===
grep -rn --include="*.js" --include="*.ts" --include="*.py" --include="*.go" --include="*.rb" \
  -E "(fetch\(|requests\.(get|post)|http\.Get|urllib\.request|open\(.*http)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -E "(req\.|query\.|params\.|body\.|user)" \
  | grep -v node_modules | head -30
# Manual check: is the URL validated against an allowlist AND checked post-DNS-resolution?

# === Prototype pollution ===
grep -rn --include="*.js" --include="*.ts" \
  -E "(merge|extend|assign|defaults|clone(Deep)?)\s*\([^)]*(req|body|query|params)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules | head -30

# === Deserialization ===
grep -rn -E "(pickle\.loads|yaml\.load\b|unserialize\(|ObjectInputStream|readObject\(|node-serialize|serialize\.unserialize)" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules

# === GraphQL injection ===
grep -rn --include="*.js" --include="*.ts" --include="*.py" \
  -E "(buildSchema.*req|render_template_string|graphql\(.*\$\{)" \
  "$AUDIT_SCOPE" 2>/dev/null | head -20

# === TOCTOU / Race conditions ===
grep -rn -E "os\.path\.exists|os\.access" "$AUDIT_SCOPE" 2>/dev/null \
  | grep -v node_modules | head -20

# === OAuth misconfigurations ===
grep -rn -E "response_type=token|redirect_uri.*req\.(query|body|params)|implicit.*flow" \
  "$AUDIT_SCOPE" 2>/dev/null | grep -v node_modules | head -20

# === Security debt markers ===
grep -rn "TODO.*auth\|FIXME.*security\|HACK.*bypass\|nosec\|noqa.*S\|skipcq\|#nosec\|// nolint:gosec" \
  "$AUDIT_SCOPE" 2>/dev/null
```

#### False positive filter for static phase

Before reporting a grep match:
1. Is it inside a comment, docstring, or documentation file? → discard
2. Is it in a test file (`*.test.js`, `*_test.py`, `spec/`) demonstrating unsafe patterns intentionally? → discard
3. Is it dead code (unreachable, behind a compile flag)? → discard

Classify each surviving finding:
- **Confirmed** — also flagged by Semgrep or language-specific SAST
- **Unconfirmed — manual review needed** — grep only; include 3 lines of surrounding context

---

### 4. Phase 3 — Dependency Audit

```bash
# === Node.js ===
if [ -f "package-lock.json" ] || [ -f "yarn.lock" ]; then
  if command -v npm &>/dev/null; then
    npm audit --json 2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for k, v in d.get('vulnerabilities', {}).items():
        print(v['severity'].upper(), k, '-', v.get('title',''))
except Exception as e:
    print('[ERROR] npm audit parse failed:', e, file=sys.stderr)
    sys.exit(1)
" || echo "[WARN] npm audit parse failed — review raw output"
  else
    echo "[WARN] npm not found — review package-lock.json at https://osv.dev"
  fi
fi

# === Python ===
if [ -f "requirements.txt" ] || [ -f "pyproject.toml" ]; then
  if command -v pip-audit &>/dev/null; then
    pip-audit
  elif command -v safety &>/dev/null; then
    safety check
  else
    echo "[WARN] pip-audit and safety not found. Install: pip install pip-audit"
    python3 -c "import pkg_resources; [print(d) for d in pkg_resources.working_set]"
  fi
fi

# === Go ===
if [ -f "go.mod" ]; then
  command -v govulncheck &>/dev/null \
    && govulncheck ./... \
    || echo "[WARN] govulncheck not found. Install: go install golang.org/x/vuln/cmd/govulncheck@latest"
fi

# === Rust ===
if [ -f "Cargo.toml" ]; then
  command -v cargo-audit &>/dev/null \
    && cargo audit \
    || echo "[WARN] cargo-audit not found. Install: cargo install cargo-audit"
fi

# === Java / Maven ===
if [ -f "pom.xml" ]; then
  command -v mvn &>/dev/null \
    && mvn dependency-check:check 2>/dev/null | tail -30 \
    || echo "[WARN] mvn not found — check pom.xml at https://osv.dev"
fi

# === Ruby ===
if [ -f "Gemfile.lock" ]; then
  command -v bundle-audit &>/dev/null \
    && bundle-audit check --update \
    || echo "[WARN] bundler-audit not found. Install: gem install bundler-audit"
fi

# === PHP ===
if [ -f "composer.lock" ]; then
  command -v composer &>/dev/null \
    && composer audit \
    || echo "[WARN] composer not found — check composer.lock at https://osv.dev"
fi

# === Swift / Xcode ===
if [ -f "Package.swift" ] || find . -name '*.xcodeproj' | grep -q .; then
  echo "[INFO] Swift/Xcode project detected"
  if command -v swift &>/dev/null; then
    swift package audit 2>/dev/null || echo "[WARN] swift package audit unavailable — check Package.resolved at https://osv.dev"
  fi
fi

# === Kotlin / Gradle ===
if [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
  echo "[INFO] Gradle project detected"
  if command -v gradle &>/dev/null; then
    gradle dependencyCheckAnalyze 2>/dev/null | tail -30 \
      || echo "[WARN] OWASP dependency-check Gradle plugin not configured — add it or check at https://osv.dev"
  fi
fi
```

#### License Compliance Check

```bash
echo "=== License Compliance ==="
# Node.js
if [ -f "package.json" ] && command -v npx &>/dev/null; then
  npx license-checker --summary --unknown 2>/dev/null \
    || echo "[WARN] license-checker not available. Install: npm install -g license-checker"
fi

# Python
if command -v pip-licenses &>/dev/null; then
  pip-licenses --order=license --fail-on="GPL;AGPL" 2>/dev/null \
    || echo "[WARN] Copyleft license detected — review before distributing"
else
  echo "[WARN] pip-licenses not found. Install: pip install pip-licenses"
fi

# Go
if [ -f "go.mod" ] && command -v go-licenses &>/dev/null; then
  go-licenses check ./... 2>/dev/null \
    || echo "[WARN] go-licenses not found. Install: go install github.com/google/go-licenses@latest"
fi

# Flag copyleft licenses that may infect proprietary code
echo "[INFO] Review any GPL/AGPL/LGPL/SSPL licenses — they may require source disclosure"
```

---

### 5. Phase 4 — Container & Cloud Security

#### 5a. Container / Docker scanning

```bash
echo "=== Container Security ==="

# Static Dockerfile analysis — hadolint
if find . -name 'Dockerfile*' | grep -q .; then
  if command -v hadolint &>/dev/null; then
    echo "[INFO] Running hadolint"
    find . -name 'Dockerfile*' -exec hadolint {} \;
  else
    echo "[WARN] hadolint not found. Install: brew install hadolint   OR   docker run --rm -i hadolint/hadolint"
    # Fallback: manual checks
    grep -rn --include="Dockerfile*" \
      -E "(FROM.*:latest|USER root|--privileged|curl.*\|.*sh|ADD http|RUN apt|ENV.*PASSWORD|ENV.*SECRET)" \
      . 2>/dev/null
  fi
fi

# FIX: Extract images from Dockerfiles (FROM lines) AND docker-compose files (image: keys).
# The original skill incorrectly grepped for "^FROM" in docker-compose files,
# which do not contain FROM directives — those belong to Dockerfiles only.
DOCKERFILE_IMAGES=$(grep -rh "^FROM" Dockerfile* 2>/dev/null \
  | grep -v ARG | awk '{print $2}' | sort -u | grep -v scratch)

COMPOSE_IMAGES=$(find . \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' \) -print0 \
  | xargs -0 grep -h '^\s*image:\s*' 2>/dev/null \
  | sed "s/.*image:\s*//" | tr -d '"'"'" | sort -u | grep -v scratch | grep -v '^\$')

IMAGES=$(printf '%s\n%s\n' "$DOCKERFILE_IMAGES" "$COMPOSE_IMAGES" | sort -u | grep -v '^$')

if [ -n "$IMAGES" ]; then
  if command -v trivy &>/dev/null; then
    echo "[INFO] Running trivy on discovered images"
    echo "$IMAGES" | while read -r img; do
      echo "--- Scanning: $img ---"
      timeout 300 trivy image --severity HIGH,CRITICAL "$img" 2>/dev/null || true
    done
  elif command -v grype &>/dev/null; then
    echo "[INFO] Running grype on discovered images"
    echo "$IMAGES" | while read -r img; do
      echo "--- Scanning: $img ---"
      timeout 300 grype "$img" --only-fixed 2>/dev/null || true
    done
  else
    echo "[WARN] Neither trivy nor grype found."
    echo "       Install: brew install trivy   OR   brew install anchore/grype/grype"
  fi
fi

# docker-compose secrets exposure
find . \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' \) 2>/dev/null | while read -r f; do
  echo "--- Checking $f for exposed secrets ---"
  grep -nE "(password|secret|token|key)\s*:" "$f" 2>/dev/null \
    | grep -v "from:\|secretKeyRef:\|valueFrom:" | head -20
done
```

#### 5b. IaC / Cloud misconfiguration scanning

```bash
echo "=== IaC & Cloud Security ==="

# Terraform — checkov (preferred) or tfsec
if find . -name '*.tf' | grep -q .; then
  if command -v checkov &>/dev/null; then
    echo "[INFO] Running checkov on Terraform"
    timeout 300 checkov -d . --framework terraform --compact --quiet 2>/dev/null | tail -50
  elif command -v tfsec &>/dev/null; then
    echo "[INFO] Running tfsec"
    timeout 300 tfsec . 2>/dev/null
  else
    echo "[WARN] Terraform detected but neither checkov nor tfsec found."
    echo "       Install: pip install checkov   OR   brew install tfsec"
    # Fallback: grep for common misconfigs
    grep -rn --include="*.tf" \
      -E '(publicly_accessible\s*=\s*true|acl\s*=\s*"public|encrypted\s*=\s*false|skip_final_snapshot\s*=\s*true|force_destroy\s*=\s*true|enable_deletion_protection\s*=\s*false)' \
      . 2>/dev/null
  fi
fi

# Kubernetes manifests
if find . \( -name '*.yml' -o -name '*.yaml' \) | xargs grep -l 'kind: Pod\|kind: Deployment\|kind: DaemonSet' 2>/dev/null | grep -q .; then
  if command -v kubesec &>/dev/null; then
    echo "[INFO] Running kubesec"
    find . \( -name '*.yml' -o -name '*.yaml' \) -exec sh -c 'grep -l "kind: Pod\|kind: Deployment" "$1" 2>/dev/null' _ {} \; \
      | xargs -I{} kubesec scan {} 2>/dev/null
  elif command -v checkov &>/dev/null; then
    timeout 300 checkov -d . --framework kubernetes --compact --quiet 2>/dev/null | tail -50
  else
    echo "[WARN] Kubernetes manifests detected but no scanner found."
    echo "       Install: brew install kubesec   OR   pip install checkov"
    # Fallback grep
    grep -rn --include="*.yml" --include="*.yaml" \
      -E "(privileged:\s*true|allowPrivilegeEscalation:\s*true|runAsRoot|hostNetwork:\s*true|hostPID:\s*true|capabilities:\s*add)" \
      . 2>/dev/null | head -20
  fi
fi

# GitHub Actions — secret exposure, script injection
if [ -d ".github/workflows" ]; then
  echo "[INFO] Checking GitHub Actions workflows"
  grep -rn --include="*.yml" --include="*.yaml" \
    -E '(\$\{\{.*github\.event\.(issue|pull_request|comment)\..*\}\}|\$\{\{.*inputs\..+\}\})' \
    .github/workflows/ 2>/dev/null | head -20
  # Flag: user-controlled input interpolated directly into run: blocks = script injection
  grep -rn -A2 "run:" .github/workflows/ 2>/dev/null \
    | grep -E '\$\{\{.*(github\.event|inputs)\.' | head -20
fi

# AWS CLI checks (only if credentials are configured)
if command -v aws &>/dev/null && aws sts get-caller-identity &>/dev/null 2>&1; then
  echo "[INFO] AWS credentials found — running live checks"
  # Public S3 buckets
  aws s3api list-buckets --query 'Buckets[].Name' --output text 2>/dev/null | tr '\t' '\n' | while read -r bucket; do
    acl=$(aws s3api get-bucket-acl --bucket "$bucket" 2>/dev/null \
      | python3 -c "import sys,json; acl=json.load(sys.stdin); [print('PUBLIC: '$bucket) for g in acl.get('Grants',[]) if 'AllUsers' in g.get('Grantee',{}).get('URI','')]" 2>/dev/null)
    [ -n "$acl" ] && echo "$acl"
  done
  # IMDSv1 enabled (allows SSRF to steal instance credentials)
  aws ec2 describe-instances --query \
    'Reservations[].Instances[?MetadataOptions.HttpTokens==`optional`].[InstanceId]' \
    --output text 2>/dev/null | grep -v "^$" | while read -r id; do
    echo "[CRITICAL] Instance $id has IMDSv1 enabled — SSRF can steal IAM credentials"
  done
else
  echo "[INFO] No live AWS credentials found — skipping live cloud checks"
fi
```

---

### 6. Phase 5 — SBOM Generation

Generate a Software Bill of Materials so the organization knows exactly what third-party components are shipped. This is increasingly required for compliance (NIST SSDF, EO 14028, SOC 2).

```bash
echo "=== SBOM Generation ==="

if command -v syft &>/dev/null; then
  echo "[INFO] Generating SBOM with syft (SPDX + CycloneDX)"
  timeout 300 syft . -o spdx-json=/tmp/sbom-spdx.json 2>/dev/null \
    && echo "[INFO] SPDX SBOM written to /tmp/sbom-spdx.json"
  timeout 300 syft . -o cyclonedx-json=/tmp/sbom-cyclonedx.json 2>/dev/null \
    && echo "[INFO] CycloneDX SBOM written to /tmp/sbom-cyclonedx.json"

  # Summary: how many components, top 5 licenses
  if [ "$PYTHON3_AVAILABLE" = "true" ] && [ -s /tmp/sbom-spdx.json ]; then
    python3 - <<'EOF'
import json
with open('/tmp/sbom-spdx.json') as f:
    sbom = json.load(f)
pkgs = sbom.get('packages', [])
print(f'Total components: {len(pkgs)}')
from collections import Counter
licenses = Counter(
    lic.get('licenseId', 'NOASSERTION')
    for p in pkgs
    for lic in p.get('licenseConcluded', '').split(' AND ')
    if lic not in ('NOASSERTION', 'NONE', '')
)
print('Top licenses:', dict(licenses.most_common(5)))
EOF
  fi

elif command -v cyclonedx-py &>/dev/null; then
  echo "[INFO] Generating CycloneDX SBOM with cyclonedx-py (Python projects)"
  timeout 180 cyclonedx-py -o /tmp/sbom-cyclonedx.xml 2>/dev/null \
    && echo "[INFO] CycloneDX SBOM written to /tmp/sbom-cyclonedx.xml"

elif command -v npm &>/dev/null && [ -f "package.json" ]; then
  echo "[INFO] Generating CycloneDX SBOM via @cyclonedx/cyclonedx-npm"
  timeout 180 npx @cyclonedx/cyclonedx-npm --output-file /tmp/sbom-cyclonedx.json 2>/dev/null \
    && echo "[INFO] CycloneDX SBOM written to /tmp/sbom-cyclonedx.json"

else
  echo "[WARN] No SBOM generator found. Install one of:"
  echo "       syft (any stack):     brew install syft   OR   https://github.com/anchore/syft"
  echo "       cyclonedx-py (Python): pip install cyclonedx-bom"
  echo "       cyclonedx-npm (Node):  npm install -g @cyclonedx/cyclonedx-npm"
fi
```

Include the SBOM artifact path(s) in the final report so the team can attach them to release artifacts or compliance submissions.

---

### 7. Produce the report

Structure output **exactly** as specified in `guide.md` under "Output Format". Every finding must include Severity, CWE, Location, Description, Proof of Concept, and Fix.

Append a **Tooling Gaps** section listing every `[WARN]` emitted, with install instructions for each.

Append a **SBOM** section listing the generated artifact paths (or noting the tooling gap if generation failed).

---

## Behavior Rules

- **Never skip a file** because it looks unimportant — config files, CI pipelines, and IaC templates cause most real breaches.
- **Never give generic advice** without corrected code or configuration.
- If a Critical finding is found, prepend `CRITICAL FINDING` at the very top of the response.
- If a secret exists in history, always include the rotation + purge instructions from `guide.md`.
- **`$ARGUMENTS` is a prompt placeholder, not a shell variable.** Export it as `AUDIT_SCOPE` in the first bash block.
- **Always quote `"$AUDIT_SCOPE"`** in every bash command to prevent word splitting on paths with spaces.
- Prefer `gitleaks`/`trufflehog` over git-grep. Prefer `semgrep` over grep. Always note when falling back and why.
- Apply false positive filters before adding any finding to the report. Grep-only findings must be marked **unconfirmed**.
- If git history exceeds 2000 commits, say so and process the most recent 2000.
- If `guide.md` is missing or does not contain the expected section headers, stop — do not improvise.
- Use `timeout` on all long-running scans (gitleaks, trufflehog, semgrep, trivy, grype, checkov) to prevent hangs.
- Check `python3` availability before using it; skip JSON parsing steps gracefully if unavailable.
- DAST (dynamic testing) is explicitly **out of scope** for this skill — note this in the Executive Summary and recommend OWASP ZAP or Burp Suite for runtime testing.
