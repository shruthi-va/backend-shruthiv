# Insurance Claims API

A Java 17, Spring Boot, and Maven backend for submitting and processing insurance claims. The initial workflow is `SUBMITTED` -> `ASSIGNED` -> `REVIEWED` -> `APPROVED` or `DENIED`.

See the [AI Journal](./AI%20Journal.md) for project conversations and decisions.

| Role | Username | Access |
|---|---|---|
| Claimant | `claimant` | Submit claims and view their own claims and final decisions |
| Claims staff | `staff` | View assigned claims and recommend approval or denial |
| Manager | `manager` | View all claims, assign staff, make final decisions, and see who changed each status and when |

## Run locally

Install JDK 17 and set `JAVA_HOME`. The Maven Wrapper downloads Maven on first use. Set three distinct local passwords in PowerShell, then start the application:

```powershell
$env:CLAIMS_CLAIMANT_PASSWORD = '<choose a claimant password>'
$env:CLAIMS_STAFF_PASSWORD = '<choose a staff password>'
$env:CLAIMS_MANAGER_PASSWORD = '<choose a manager password>'
.\mvnw.cmd spring-boot:run
```

The REST API listens on `http://127.0.0.1:8080`. An embedded H2 database stores claims under `data`; no external database setup is needed.

Run the integration tests with `.\mvnw.cmd verify`. Tests use a separate in-memory database.

## Endpoints

Every request uses HTTP Basic authentication. Before a `POST`, call `GET /api/csrf` with the same credentials, then send the returned token using its `headerName` and preserve the session cookie.

| Method and path | Request body or parameters |
|---|---|
| `GET /api/claims` | `page` (zero-based) and `size` (default 20, max 100) |
| `GET /api/claims/{id}` | Returns a single visible claim |
| `POST /api/claims` | `{"policyNumber":"POL-123","incidentDate":"2024-01-15","description":"Example loss"}` |
| `POST /api/claims/{id}/assignment` | `{"assignee":"staff"}` |
| `POST /api/claims/{id}/review` | `{"recommendation":"APPROVE","reason":"Coverage verified"}` |
| `POST /api/claims/{id}/decision` | `{"decision":"APPROVE","reason":"Covered under policy"}` |

Both `recommendation` and `decision` accept `APPROVE` or `DENY`. Review notes are internal; the final decision reason is visible to the claimant.

### Submit an example claim from PowerShell

```powershell
$base = 'http://127.0.0.1:8080'
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("claimant:$env:CLAIMS_CLAIMANT_PASSWORD"))
$headers = @{ Authorization = "Basic $encoded" }
$csrf = Invoke-RestMethod "$base/api/csrf" -Headers $headers -SessionVariable claimsSession
$headers[$csrf.headerName] = $csrf.token
$body = @{ policyNumber = 'POL-123'; incidentDate = '2024-01-15'; description = 'Example loss' } | ConvertTo-Json
$claim = Invoke-RestMethod "$base/api/claims" -Method Post -Headers $headers -WebSession $claimsSession -ContentType 'application/json' -Body $body
$claim
```

For staff or manager operations, repeat the token request with that account's credentials and a separate session.

This starter has one local account per role and does not include a UI, policy verification, document uploads, or payments. Use synthetic claim data only. Before a deployment with real claim data, replace the local accounts with managed identities, configure HTTPS and a production database with migrations, and define data protection and retention requirements.
