# THREE_SERVICE_BASE_SHA（#306）

`THREE_SERVICE_BASE_SHA=921af331e785551107466c8267d5f988436e1d14`

该 SHA 是 #306 三业务服务基线的可复现内容提交：Course（CRS+LRN）、Assessment（LAB+HWK API+Worker）、Grade（GRD）以及 Identity 支撑；部署清单为 9 workloads、4 个有序 migration jobs、4 个 schema/runtime accounts。后续 issue 必须以此 SHA 或其合入 `dev` 的等价祖先为契约比较基线，不得恢复独立 Learning 服务、`oj_learning` 或第十个 workload。

`BASELINE_READY`：#355 #357 #356 #339 #317 #318 #319 #320 #340 #307 #321 #304。
