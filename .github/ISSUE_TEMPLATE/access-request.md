---
name: Request API Access
about: Request access to submit feedback via flexjar-analytics-api
title: "[ACCESS] Request access for [YOUR-APP-NAME]"
labels: access-request
assignees: ''

---

## Application Details

**Application Name:** 
<!-- e.g., my-frontend -->

**Namespace (Team):** 
<!-- e.g., team-esyfo -->

**Cluster:** 
<!-- e.g., dev-gcp, prod-gcp -->

**Environment(s) needed:**
- [ ] dev-gcp
- [ ] prod-gcp

## Checklist

Before submitting this request, ensure you have:

- [ ] Added `flexjar-analytics-api` to your application's **outbound** access policy in your `nais.yaml`:

```yaml
spec:
  accessPolicy:
    outbound:
      rules:
        - application: flexjar-analytics-api
          namespace: team-esyfo
```

## Additional Context

<!-- Any additional information about your use case -->
