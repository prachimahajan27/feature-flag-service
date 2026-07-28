## Example: how a consuming app calls this service

### cURL

```bash
# Create a flag
curl -X POST http://localhost:8080/flags \
  -H "X-Tenant-ID: acme-corp" \
  -H "Content-Type: application/json" \
  -d '{"name": "dark_mode", "enabled": true}'

# Evaluate a flag for a user
curl "http://localhost:8080/eval?flag=dark_mode&user=user-42" \
  -H "X-Tenant-ID: acme-corp"
```

### Java client snippet

```java
public class FeatureFlagClient {
    private final RestClient restClient;
    private final String tenantId;

    public FeatureFlagClient(String baseUrl, String tenantId) {
        this.restClient = RestClient.create(baseUrl);
        this.tenantId = tenantId;
    }

    public boolean isEnabled(String flagName, String userId) {
        EvalResponse response = restClient.get()
                .uri("/eval?flag={flag}&user={user}", flagName, userId)
                .header("X-Tenant-ID", tenantId)
                .retrieve()
                .body(EvalResponse.class);
        return response.on();
    }
}

// Usage in another app:
FeatureFlagClient client = new FeatureFlagClient("http://localhost:8080", "acme-corp");
if (client.isEnabled("dark_mode", "user-42")) {
    // show new UI
}
```