# Issue #307 单体与三服务同条件性能对比

- 生成时间：2026-09-02T23:43:28.032Z
- 单体 SHA：`78715f21288782a2c7ef1d9c23f933c46569b108`
- 三服务 SHA：`c66686ff0e011f5ee63e3908683f01afd4f83ebc`
- 机器指纹：`033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`
- 数据集 SHA-256：`733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6`

## 原始轮次指标

| Architecture | Scenario | Round | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 1 | 1199 | 1199 | 15.683 | 19.437 | 9.916 | 9.916 | 0 | 16.313 | 22.54 | 781.907 | 789.12 |
| monolith | course-list | 2 | 1199 | 1199 | 16.808 | 20.202 | 9.914 | 9.914 | 0 | 16.071 | 23.81 | 789.125 | 805.93 |
| monolith | course-list | 3 | 1199 | 1199 | 16.172 | 19.337 | 9.915 | 9.915 | 0 | 15.104 | 18.65 | 821.419 | 826.488 |
| monolith | homework-submission | 1 | 1199 | 1199 | 15.328 | 19.162 | 9.915 | 9.915 | 0 | 15.78 | 23.16 | 829.846 | 831.41 |
| monolith | homework-submission | 2 | 1199 | 1199 | 14.551 | 17.835 | 9.916 | 9.916 | 0 | 14.215 | 20.62 | 835.442 | 836.43 |
| monolith | homework-submission | 3 | 1199 | 1199 | 14.289 | 17.63 | 9.916 | 9.916 | 0 | 13.978 | 18.82 | 836.643 | 837.42 |
| monolith | my-grades | 1 | 1199 | 1199 | 10.224 | 12.762 | 9.914 | 9.914 | 0 | 10.257 | 13.82 | 841.261 | 845.97 |
| monolith | my-grades | 2 | 1199 | 1199 | 10.079 | 12.644 | 9.914 | 9.914 | 0 | 10.099 | 19.18 | 841.328 | 844.43 |
| monolith | my-grades | 3 | 1199 | 1199 | 10.052 | 12.255 | 9.914 | 9.914 | 0 | 10.079 | 14.14 | 841.941 | 851.36 |
| three-service | course-list | 1 | 1199 | 1199 | 27.987 | 32.734 | 9.914 | 9.914 | 0 | 35.652 | 59.69 | 1840.251 | 1916.98 |
| three-service | course-list | 2 | 1199 | 1199 | 27.838 | 32.746 | 9.914 | 9.914 | 0 | 34.548 | 53.81 | 1874.965 | 1958.325 |
| three-service | course-list | 3 | 1199 | 1199 | 27.799 | 32.879 | 9.913 | 9.913 | 0 | 35.853 | 54.66 | 1924.986 | 1989.849 |
| three-service | homework-submission | 1 | 1199 | 1199 | 11.154 | 17.391 | 9.914 | 9.914 | 0 | 27.198 | 43.7 | 1971.18 | 2046.017 |
| three-service | homework-submission | 2 | 1199 | 1199 | 10.277 | 15.255 | 9.914 | 9.914 | 0 | 25.336 | 39.89 | 1993.769 | 2063.985 |
| three-service | homework-submission | 3 | 1199 | 1199 | 10.186 | 15.4 | 9.915 | 9.915 | 0 | 24.627 | 42.42 | 2004.306 | 2067.056 |
| three-service | my-grades | 1 | 1199 | 1199 | 11.372 | 17.828 | 9.914 | 9.914 | 0 | 30.78 | 52.61 | 2055.092 | 2120.048 |
| three-service | my-grades | 2 | 1199 | 1199 | 10.069 | 16.055 | 9.915 | 9.915 | 0 | 27.146 | 41.76 | 2065.656 | 2130.625 |
| three-service | my-grades | 3 | 1199 | 1199 | 7.117 | 11.27 | 9.917 | 9.917 | 0 | 21.483 | 41.65 | 2074.534 | 2137.86 |

## 全量聚合

| Architecture | Scenario | Rounds | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 3 | 3597 | 3597 | 16.221 | 19.674 | 9.915 | 9.915 | 0 | 15.83 | 23.81 | 797.475 | 826.488 |
| monolith | homework-submission | 3 | 3597 | 3597 | 14.723 | 18.366 | 9.916 | 9.916 | 0 | 14.652 | 23.16 | 833.998 | 837.42 |
| monolith | my-grades | 3 | 3597 | 3597 | 10.118 | 12.58 | 9.914 | 9.914 | 0 | 10.144 | 19.18 | 841.506 | 851.36 |
| three-service | course-list | 3 | 3597 | 3597 | 27.875 | 32.758 | 9.914 | 9.914 | 0 | 35.351 | 59.69 | 1880.067 | 1989.849 |
| three-service | homework-submission | 3 | 3597 | 3597 | 10.539 | 16.463 | 9.914 | 9.914 | 0 | 25.72 | 43.7 | 1989.752 | 2067.056 |
| three-service | my-grades | 3 | 3597 | 3597 | 9.519 | 16.314 | 9.916 | 9.916 | 0 | 26.47 | 52.61 | 2065.094 | 2137.86 |

## 差异与解释边界

- course-list：三服务相对单体 P95 差异 66.504%，总请求吞吐差异 -0.01%，成功请求吞吐差异 -0.01%，错误率差异 0 个百分点，CPU 平均差异 19.521 个百分点，内存平均差异 1082.592 MiB。
- homework-submission：三服务相对单体 P95 差异 -10.362%，总请求吞吐差异 -0.02%，成功请求吞吐差异 -0.02%，错误率差异 0 个百分点，CPU 平均差异 11.068 个百分点，内存平均差异 1155.754 MiB。
- my-grades：三服务相对单体 P95 差异 29.682%，总请求吞吐差异 0.02%，成功请求吞吐差异 0.02%，错误率差异 0 个百分点，CPU 平均差异 16.326 个百分点，内存平均差异 1223.588 MiB。
- Total request throughput and P95 include failed responses; use successful throughput for business-capacity comparison.
- Any scenario with a nonzero error rate is not evidence of successful business capacity at that load.
- The report records observed deltas; it does not claim an unmeasured cause.
- Candidate causes must be supported by process, network, serialization, connection-pool or cache evidence.
- All configured rounds are included; favorable rounds are never selected or discarded.
