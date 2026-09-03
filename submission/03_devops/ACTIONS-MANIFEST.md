# GitHub Actions 原始交付清单

下载时间：2026-09-03（Asia/Shanghai）；`expired=false` 表示下载时仍在保留期。
artifact 文件按 workflow 原始名称保存，未改写内容。

## Final SHA：`c56b16f916b4a4c3d33915aa37beab6b05c72888`

| workflow run | conclusion | artifact ID / name | bytes | created | expires | archive path |
| --- | --- | --- | ---: | --- | --- | --- |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872612749 / `ci-validate-workflows-33698399654` | 635 | 2026-09-03T00:11:27Z | 2026-09-17T00:11:26Z | `evidence/actions/current/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872646360 / `ci-backend-gate-33698399654` | 57784 | 2026-09-03T00:12:46Z | 2026-09-17T00:12:45Z | `evidence/actions/current/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872665544 / `ci-contracts-gate-33698399654` | 50532 | 2026-09-03T00:13:30Z | 2026-09-17T00:13:30Z | `evidence/actions/current/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872678847 / `ci-frontend-gate-33698399654` | 27352 | 2026-09-03T00:14:01Z | 2026-09-17T00:14:00Z | `evidence/actions/current/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872746252 / `ci-backend-gate-33698399654` (rerun artifact) | 57708 | 2026-09-03T00:16:40Z | 2026-09-17T00:16:39Z | GitHub artifact metadata only; same-name download path is retained once |
| [33698830921](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698830921) | failure | 9872753140 / `d3-quality-gate-33698830921` | 139365 | 2026-09-03T00:16:56Z | 2026-09-17T00:16:55Z | `evidence/actions/current/d3-33698830921/` |
| [33698830921](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698830921) | failure | 9872757437 / `d3-delivery-outcome-33698830921` | 307 | 2026-09-03T00:17:06Z | 2026-09-17T00:17:05Z | `evidence/actions/current/d3-33698830921/` |

Current D3 outcome is preserved verbatim at
`evidence/actions/current/d3-33698830921/d3-delivery-outcome-33698830921/delivery-outcome.txt`:
`quality_gate=failure`, `build_images=skipped`, `deploy_kind=skipped`.

## 历史成功：旧 D3 三 workload 基线

| run | SHA | artifact ID / name | bytes | archive path |
| --- | --- | --- | ---: | --- |
| [33227922081](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33227922081) | `5cdbe8533991bb0c7cfbe23e08d81b78d47af483` | 9707494541 / `d3-quality-gate-33227922081` | 298377 | `evidence/actions/historical/success-d3-33227922081/quality/` |
| same | same | 9707519513 / `d3-images-33227922081` | 161736021 | not copied; metadata only, avoid binary image tar in source submission |
| same | same | 9707576593 / `d3-kind-delivery-33227922081` | 23556 | `evidence/actions/historical/success-d3-33227922081/kind/` |
| same | same | 9707579130 / `d3-delivery-outcome-33227922081` | 302 | `evidence/actions/historical/success-d3-33227922081/outcome/` |

The successful Kind evidence records image tags and local image digests in
`kind/{backend,frontend}-image-inspect.txt`; it is explicitly historical and
covers the old `backend/frontend/mysql` topology, not the final 9-workload manifest.

## 历史真实失败：Kind 诊断

| run | SHA | artifact ID / name | bytes | archive path |
| --- | --- | --- | ---: | --- |
| [33628385169](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33628385169) | `c66686ff0e011f5ee63e3908683f01afd4f83ebc` | 9845742747 / `d3-quality-gate-33628385169` | 23167746 | metadata only |
| same | same | 9845852949 / `d3-images-33628385169` | 163875357 | metadata only |
| same | same | 9846233179 / `d3-kind-delivery-33628385169` | 22101 | `evidence/actions/historical/failure-d3-33628385169/kind/` |
| same | same | 9846237592 / `d3-delivery-outcome-33628385169` | 306 | `evidence/actions/historical/failure-d3-33628385169/outcome/` |

## 镜像和 revision 追溯

Final SHA 的 D3 没有生成镜像 artifact，因为 source quality gate 失败；因此不能填写
final SHA 的镜像 digest。历史成功 run 的原始 inspect 记录：

- `onlinejudge/backend:5cdbe...` -> local digest `sha256:03f9c8eb...a04af`，OCI revision 同 SHA。
- `onlinejudge/frontend:5cdbe...` -> local digest `sha256:777a4f3d...3388a`，OCI revision 同 SHA。

以上省略号只出现在本摘要，完整值以归档原始 inspect 文件为准。
