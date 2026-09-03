# Issue #307 单体与三服务同条件性能对比

- 生成时间：2026-09-02T20:55:48.161Z
- 单体 SHA：`78715f21288782a2c7ef1d9c23f933c46569b108`
- 三服务 SHA：`c66686ff0e011f5ee63e3908683f01afd4f83ebc`
- 机器指纹：`033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`
- 数据集 SHA-256：`733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6`

## 原始轮次指标

| Architecture | Scenario | Round | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 1 | 1199 | 1199 | 17.793 | 20.542 | 9.914 | 9.914 | 0 | 18.393 | 26.71 | 759.502 | 763.91 |
| monolith | course-list | 2 | 1199 | 1199 | 17.125 | 19.995 | 9.911 | 9.911 | 0 | 17.26 | 25.57 | 764.824 | 770.39 |
| monolith | course-list | 3 | 1199 | 1199 | 16.395 | 19.918 | 9.913 | 9.913 | 0 | 15.831 | 21 | 799.977 | 802.27 |
| monolith | homework-submission | 1 | 1199 | 1199 | 15.925 | 19.273 | 9.913 | 9.913 | 0 | 16.313 | 24.83 | 813.604 | 815.68 |
| monolith | homework-submission | 2 | 1199 | 1199 | 14.682 | 19.107 | 9.916 | 9.916 | 0 | 14.602 | 23.53 | 820.126 | 821.45 |
| monolith | homework-submission | 3 | 1199 | 1199 | 14.967 | 17.63 | 9.916 | 9.916 | 0 | 14.053 | 17.09 | 824.086 | 835.11 |
| monolith | my-grades | 1 | 1199 | 1199 | 10.912 | 13.391 | 9.914 | 9.914 | 0 | 10.459 | 13.57 | 825.54 | 826.71 |
| monolith | my-grades | 2 | 1199 | 1199 | 10.706 | 13.02 | 9.913 | 9.913 | 0 | 10.509 | 13.29 | 827.572 | 828.49 |
| monolith | my-grades | 3 | 1199 | 1199 | 10.165 | 12.621 | 9.914 | 9.914 | 0 | 10.103 | 16.23 | 829.156 | 833 |
| three-service | course-list | 1 | 1199 | 1199 | 28.46 | 33.403 | 9.914 | 9.914 | 0 | 38.465 | 57.31 | 1826.392 | 1905.634 |
| three-service | course-list | 2 | 1199 | 1199 | 28.137 | 33.347 | 9.914 | 9.914 | 0 | 35.372 | 57.45 | 1850.371 | 1924.514 |
| three-service | course-list | 3 | 1199 | 1199 | 27.866 | 32.802 | 9.914 | 9.914 | 0 | 34.554 | 53.07 | 1898.738 | 1966.01 |
| three-service | homework-submission | 1 | 1199 | 1199 | 11.337 | 18.065 | 9.915 | 9.915 | 0 | 30.07 | 45.02 | 1965.233 | 2032.464 |
| three-service | homework-submission | 2 | 1199 | 1199 | 11.013 | 16.854 | 9.914 | 9.914 | 0 | 26.581 | 43.7 | 1976.182 | 2043.206 |
| three-service | homework-submission | 3 | 1199 | 1199 | 10.875 | 16.109 | 9.916 | 9.916 | 0 | 26.287 | 42.31 | 1991.119 | 2052.107 |
| three-service | my-grades | 1 | 1199 | 1199 | 11.707 | 18.664 | 9.915 | 9.915 | 0 | 30.521 | 48.12 | 2009.719 | 2082.144 |
| three-service | my-grades | 2 | 1199 | 1199 | 10.569 | 17.032 | 9.915 | 9.915 | 0 | 28.444 | 42.93 | 2063.884 | 2123.246 |
| three-service | my-grades | 3 | 1199 | 1199 | 10.374 | 16.347 | 9.915 | 9.915 | 0 | 28.601 | 44.81 | 2067.854 | 2124.626 |

## 全量聚合

| Architecture | Scenario | Rounds | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 3 | 3597 | 3597 | 17.104 | 20.16 | 9.913 | 9.913 | 0 | 17.151 | 26.71 | 774.966 | 802.27 |
| monolith | homework-submission | 3 | 3597 | 3597 | 15.191 | 18.958 | 9.915 | 9.915 | 0 | 14.975 | 24.83 | 819.324 | 835.11 |
| monolith | my-grades | 3 | 3597 | 3597 | 10.594 | 13.027 | 9.914 | 9.914 | 0 | 10.355 | 16.23 | 827.436 | 833 |
| three-service | course-list | 3 | 3597 | 3597 | 28.154 | 33.171 | 9.914 | 9.914 | 0 | 36.13 | 57.45 | 1858.5 | 1966.01 |
| three-service | homework-submission | 3 | 3597 | 3597 | 11.075 | 17.238 | 9.915 | 9.915 | 0 | 27.646 | 45.02 | 1977.511 | 2052.107 |
| three-service | my-grades | 3 | 3597 | 3597 | 10.883 | 17.455 | 9.915 | 9.915 | 0 | 29.189 | 48.12 | 2047.152 | 2124.626 |

## 差异与解释边界

- course-list：三服务相对单体 P95 差异 64.539%，总请求吞吐差异 0.01%，成功请求吞吐差异 0.01%，错误率差异 0 个百分点，CPU 平均差异 18.979 个百分点，内存平均差异 1083.534 MiB。
- homework-submission：三服务相对单体 P95 差异 -9.073%，总请求吞吐差异 0%，成功请求吞吐差异 0%，错误率差异 0 个百分点，CPU 平均差异 12.671 个百分点，内存平均差异 1158.187 MiB。
- my-grades：三服务相对单体 P95 差异 33.991%，总请求吞吐差异 0.01%，成功请求吞吐差异 0.01%，错误率差异 0 个百分点，CPU 平均差异 18.834 个百分点，内存平均差异 1219.716 MiB。
- Total request throughput and P95 include failed responses; use successful throughput for business-capacity comparison.
- Any scenario with a nonzero error rate is not evidence of successful business capacity at that load.
- The report records observed deltas; it does not claim an unmeasured cause.
- Candidate causes must be supported by process, network, serialization, connection-pool or cache evidence.
- All configured rounds are included; favorable rounds are never selected or discarded.
