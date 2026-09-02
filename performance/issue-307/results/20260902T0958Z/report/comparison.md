# Issue #307 单体与三服务同条件性能对比

- 生成时间：2026-09-02T10:54:18.138Z
- 单体 SHA：`78715f21288782a2c7ef1d9c23f933c46569b108`
- 三服务 SHA：`bb4d83ee7a0891490869960370670a2dd03e9962`
- 机器指纹：`033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`
- 数据集 SHA-256：`733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6`

## 原始轮次指标

| Architecture | Scenario | Round | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 1 | 29137 | 29137 | 41.174 | 90.386 | 242.64 | 242.64 | 0 | 191.491 | 212.09 | 798.208 | 804.76 |
| monolith | course-list | 2 | 29888 | 29888 | 40.128 | 89.675 | 249.05 | 249.05 | 0 | 186.831 | 193.23 | 832.695 | 833.7 |
| monolith | course-list | 3 | 30174 | 30174 | 39.749 | 89.533 | 251.426 | 251.426 | 0 | 187.777 | 197.27 | 835.616 | 836.75 |
| monolith | homework-submission | 1 | 57911 | 9870 | 20.688 | 71.956 | 482.573 | 82.247 | 82.957 | 199.691 | 211.2 | 906.503 | 913.68 |
| monolith | homework-submission | 2 | 55211 | 9591 | 21.7 | 73.228 | 460.074 | 79.922 | 82.628 | 201.6 | 209.76 | 934.083 | 936.88 |
| monolith | homework-submission | 3 | 58827 | 10346 | 20.366 | 71.91 | 490.205 | 86.213 | 82.413 | 194.922 | 204 | 939.936 | 945.68 |
| monolith | my-grades | 1 | 81853 | 81853 | 14.634 | 66.936 | 682.081 | 682.081 | 0 | 259.009 | 271.95 | 1080.424 | 1083.25 |
| monolith | my-grades | 2 | 79258 | 79258 | 15.114 | 68.056 | 660.451 | 660.451 | 0 | 258.865 | 267.83 | 1081.299 | 1087.42 |
| monolith | my-grades | 3 | 82246 | 82246 | 14.567 | 67.772 | 685.378 | 685.378 | 0 | 261.18 | 275.4 | 1089.926 | 1091.63 |
| three-service | course-list | 1 | 565075 | 3640 | 2.122 | 1.019 | 4708.466 | 30.33 | 99.356 | 136.891 | 170.68 | 1858.721 | 1921.981 |
| three-service | course-list | 2 | 653310 | 3600 | 1.835 | 1.109 | 5440.966 | 29.982 | 99.449 | 142.956 | 170.02 | 1875.97 | 1958.31 |
| three-service | course-list | 3 | 649050 | 3601 | 1.848 | 1.133 | 5405.944 | 29.993 | 99.445 | 141.142 | 168.89 | 1907.752 | 1969.995 |
| three-service | homework-submission | 1 | 663652 | 1200 | 1.805 | 1.362 | 5530.329 | 10 | 99.819 | 51.279 | 75.71 | 1956.375 | 2027.212 |
| three-service | homework-submission | 2 | 690267 | 1200 | 1.735 | 1.241 | 5752.204 | 10 | 99.826 | 48.622 | 77.64 | 1979.629 | 2044.204 |
| three-service | homework-submission | 3 | 665754 | 1200 | 1.799 | 1.36 | 5547.927 | 10 | 99.82 | 47.727 | 73.53 | 1993.006 | 2051.42 |
| three-service | my-grades | 1 | 719420 | 0 | 1.665 | 1.258 | 5995.097 | 0 | 100 | 44.256 | 65.32 | 2036.534 | 2104.498 |
| three-service | my-grades | 2 | 722333 | 0 | 1.659 | 1.173 | 6019.306 | 0 | 100 | 40.072 | 64.51 | 2041.121 | 2106.056 |
| three-service | my-grades | 3 | 727559 | 0 | 1.647 | 1.171 | 6062.967 | 0 | 100 | 40.709 | 64.65 | 2067.843 | 2141.299 |

## 全量聚合

| Architecture | Scenario | Rounds | Requests | Successful requests | Average (ms) | P95 (ms) | Throughput (requests/second) | Successful throughput (requests/second) | Error rate (%) | CPU avg (%) | CPU max (%) | Memory avg (MiB) | Memory max (MiB) |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| monolith | course-list | 3 | 89199 | 89199 | 40.341 | 89.85 | 247.704 | 247.704 | 0 | 188.699 | 212.09 | 822.173 | 836.75 |
| monolith | homework-submission | 3 | 171949 | 29807 | 20.903 | 72.32 | 477.617 | 82.794 | 82.665 | 198.715 | 211.2 | 926.783 | 945.68 |
| monolith | my-grades | 3 | 243357 | 243357 | 14.767 | 67.505 | 675.97 | 675.97 | 0 | 259.679 | 275.4 | 1083.855 | 1091.63 |
| three-service | course-list | 3 | 1867435 | 10841 | 1.926 | 1.092 | 5185.198 | 30.102 | 99.419 | 140.329 | 170.68 | 1880.814 | 1969.995 |
| three-service | homework-submission | 3 | 2019673 | 3600 | 1.779 | 1.321 | 5610.153 | 10 | 99.822 | 49.209 | 77.64 | 1976.337 | 2051.42 |
| three-service | my-grades | 3 | 2169312 | 0 | 1.657 | 1.2 | 6025.79 | 0 | 100 | 41.679 | 65.32 | 2048.499 | 2141.299 |

## 差异与解释边界

- course-list：三服务相对单体 P95 差异 -98.785%，总请求吞吐差异 1993.304%，成功请求吞吐差异 -87.848%，错误率差异 99.419 个百分点，CPU 平均差异 -48.37 个百分点，内存平均差异 1058.641 MiB。
- homework-submission：三服务相对单体 P95 差异 -98.173%，总请求吞吐差异 1074.613%，成功请求吞吐差异 -87.922%，错误率差异 17.157 个百分点，CPU 平均差异 -149.506 个百分点，内存平均差异 1049.554 MiB。
- my-grades：三服务相对单体 P95 差异 -98.222%，总请求吞吐差异 791.429%，成功请求吞吐差异 -100%，错误率差异 100 个百分点，CPU 平均差异 -218 个百分点，内存平均差异 964.644 MiB。
- Total request throughput and P95 include failed responses; use successful throughput for business-capacity comparison.
- Any scenario with a nonzero error rate is not evidence of successful business capacity at that load.
- The report records observed deltas; it does not claim an unmeasured cause.
- Candidate causes must be supported by process, network, serialization, connection-pool or cache evidence.
- All configured rounds are included; favorable rounds are never selected or discarded.
