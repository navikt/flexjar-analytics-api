---
name: tokenx-auth
description: Service-to-service authentication using TokenX token exchange in Nais
---

# TokenX Authentication Skill

`flexjar-analytics-api` does **not** currently use TokenX. Authentication in this repo is based on Azure AD access tokens validated via NAIS Texas introspection.

Use this skill only if the team explicitly decides to introduce TokenX (typically for calling other internal services on behalf of a user).

## Preferred approach for this repo

- For inbound requests: keep using the existing Texas introspection setup in `no.nav.flexjar.config.Auth`.
- For outbound calls: prefer explicit `accessPolicy` + ordinary service-to-service auth mechanisms already adopted by the platform/team.

## If TokenX is introduced (ask first)

### Nais manifest

```yaml
apiVersion: nais.io/v1alpha1
kind: Application
metadata:
  name: flexjar-analytics-api
spec:
  tokenx:
    enabled: true
```

### Implementation boundaries

- Keep TokenX-specific code isolated (e.g. `config/TokenX.kt`) and covered by tests.
- Do not replace Texas introspection for inbound validation unless there is a clear migration plan.
- Never log tokens or include sensitive claims in logs.

## Boundaries

### ✅ Always

- Keep auth aligned with the existing Texas introspection approach unless there is a clear migration plan.

### ⚠️ Ask First

- Adding TokenX support (new auth mechanism + NAIS config)

### 🚫 Never

- Validate JWTs via JWKS in-app “just because” when the repo is already set up for Texas introspection.

