# REST API v2 audit snapshot limits

The REST API v2 item-audit endpoint reads the file-backed audit trail into a daily bounded-consistency snapshot before it paginates. To keep that full scan bounded, `api.v2.audit.resultCeiling` limits the number of authorized matching events to 1,000. At 1,001 matches the endpoint stops collecting and returns a localized 400 response asking for a narrower date range.

This is not a retention limit and does not change the legacy Activity API. It applies only to one REST API v2 resource-audit request.

## Measurement method

`ApiV2AuditSearchStressTest` is opt-in:

```bash
mvn test -Dtest=ApiV2AuditSearchStressTest -Dfast=true -Daudit.stress=true
```

The test generates four rolled audit files with realistic booking-configuration payloads, duplicate timestamps, and irrelevant events. After warm-up it measures three first-page scans and three independent later-page fingerprint-verification scans at concurrency 1, 4, and 8. First and later scans are separate GC windows because they are separate HTTP requests. Peak additional heap is the sampled aggregate increase during one pass.

Measurements below were taken on 2026-08-27 with Temurin 17.0.20.1, a 1.5 GiB maximum heap, 10 available processors, and APFS storage. The request-time budget used by the harness was 30 seconds.

| Matches | Concurrency | Samples (ms) | p50 (ms) | Max (ms) | Events/s | Peak additional heap |
|---:|---:|---|---:|---:|---:|---:|
| 1,000 | 1 | 88, 50, 48 | 50 | 88 | 20,000 | 35.0 MiB |
| 1,000 | 4 | 96, 88, 70 | 88 | 96 | 45,455 | 59.2 MiB |
| 1,000 | 8 | 86, 67, 55 | 67 | 86 | 119,403 | 151.8 MiB |
| 5,000 | 1 | 97, 91, 90 | 91 | 97 | 54,945 | 104.8 MiB |
| 5,000 | 4 | 123, 110, 114 | 114 | 123 | 175,439 | 185.0 MiB |
| 5,000 | 8 | 166, 165, 156 | 165 | 166 | 242,424 | 324.3 MiB |
| 10,000 | 1 | 166, 170, 176 | 170 | 176 | 58,824 | 178.2 MiB |
| 10,000 | 4 | 242, 243, 240 | 242 | 243 | 165,289 | 336.7 MiB |
| 10,000 | 8 | 317, 336, 328 | 328 | 336 | 243,902 | 459.4 MiB |
| 25,000 | 1 | 418, 424, 414 | 418 | 424 | 59,809 | 184.4 MiB |
| 25,000 | 4 | 534, 537, 536 | 536 | 537 | 186,567 | 446.8 MiB |
| 25,000 | 8 | 835, 850, 817 | 835 | 850 | 239,521 | 682.8 MiB |
| 50,000 | 1 | 922, 908, 860 | 908 | 922 | 55,066 | 238.9 MiB |
| 50,000 | 4 | 1,041, 1,072, 1,041 | 1,041 | 1,072 | 192,123 | 600.0 MiB |
| 50,000 | 8 | 1,581, 1,555, 1,529 | 1,555 | 1,581 | 257,235 | 883.0 MiB |

## Ceiling choice

The safety rule requires twice the observed request time to remain below half the 30-second request budget, and twice the per-request peak heap to remain below the smaller of 64 MiB or 5% of maximum heap. The memory budget is therefore 64 MiB.

At concurrency 8, 1,000 matches used about 19.0 MiB per request (`151.8 / 8`); its 2× safety value is about 38.0 MiB. The maximum time was 86 ms; its 2× value is 172 ms. Both fit.

At 5,000 matches, the per-request heap was about 40.5 MiB and the 2× value was about 81.1 MiB, so it fails the memory rule. Higher counts also fail it. The largest tested qualifying count is therefore 1,000.

Timing is recorded evidence rather than a CI assertion. Exact unit tests enforce the configured ceiling and ceiling + 1.
