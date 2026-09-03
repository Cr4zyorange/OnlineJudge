# GitHub Actions 原始交付清单

下载时间：2026-09-03（Asia/Shanghai）；`expired=false` 表示下载时仍在保留期。
artifact 文件按 workflow 原始名称保存，未改写内容。

## 前一 dev 基线 SHA：`c56b16f916b4a4c3d33915aa37beab6b05c72888`

| workflow run | conclusion | artifact ID / name | bytes | created | expires | archive path |
| --- | --- | --- | ---: | --- | --- | --- |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872612749 / `ci-validate-workflows-33698399654` | 635 | 2026-09-03T00:11:27Z | 2026-09-17T00:11:26Z | `evidence/actions/historical/baseline-c56/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872646360 / `ci-backend-gate-33698399654` | 57784 | 2026-09-03T00:12:46Z | 2026-09-17T00:12:45Z | `evidence/actions/historical/baseline-c56/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872665544 / `ci-contracts-gate-33698399654` | 50532 | 2026-09-03T00:13:30Z | 2026-09-17T00:13:30Z | `evidence/actions/historical/baseline-c56/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872678847 / `ci-frontend-gate-33698399654` | 27352 | 2026-09-03T00:14:01Z | 2026-09-17T00:14:00Z | `evidence/actions/historical/baseline-c56/ci-33698399654/` |
| [33698399654](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698399654) | failure | 9872746252 / `ci-backend-gate-33698399654` (rerun artifact) | 57708 | 2026-09-03T00:16:40Z | 2026-09-17T00:16:39Z | GitHub artifact metadata only; same-name download path is retained once |
| [33698830921](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698830921) | failure | 9872753140 / `d3-quality-gate-33698830921` | 139365 | 2026-09-03T00:16:56Z | 2026-09-17T00:16:55Z | `evidence/actions/historical/baseline-c56/d3-33698830921/` |
| [33698830921](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33698830921) | failure | 9872757437 / `d3-delivery-outcome-33698830921` | 307 | 2026-09-03T00:17:06Z | 2026-09-17T00:17:05Z | `evidence/actions/historical/baseline-c56/d3-33698830921/` |

Previous D3 outcome is preserved verbatim at
`evidence/actions/historical/baseline-c56/d3-33698830921/d3-delivery-outcome-33698830921/delivery-outcome.txt`:
`quality_gate=failure`, `build_images=skipped`, `deploy_kind=skipped`.

## 前一 final SHA：`ce87dfabd54239b9d4138736cbb93b06e6c9b260`

| workflow run | conclusion at archive update | artifact / status |
| --- | --- | --- |
| [33710740174](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710740174) | failure | backend Grade MySQL contract expected 5 root/admin/migration queries but found 4; Disposable delivery skipped |
| [33710760915](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710760915) | failure | consumed source run [33710071217](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33710071217) with `cancelled`; build and Kind jobs skipped, not a valid final delivery result |

当前失败链已保留的 artifact：

| artifact ID / name | bytes | archive path |
| --- | ---: | --- |
| 9876858615 / `ci-backend-gate-33710740174` | 156117 | `evidence/actions/historical/baseline-ce87/ci-33710740174/` |
| 9876824611 / `d3-delivery-outcome-33710760915` | 305 | `evidence/actions/historical/baseline-ce87/d3-33710760915/` |
| 9876822160 / `d3-quality-gate-33710760915` | 10740482 | GitHub artifact metadata only; large consumed-gate output not copied |

该 SHA 的成功 CI/D3 原始证据尚未进入归档；上述 run 只记录前一基线阻断链，不能填充
final image digest。

## 当前 origin/dev final SHA：`3a26ed2fe9399305b5e44eeae581911e6d32710e`

#388 的 Grade MySQL 静态契约修复、#386 的 D3 JWKS 修复和后续 dev 合入已包含在该 SHA。
同步后的 PR quality-gate 已成功，但它是 PR 事件，不是该 SHA 合入 `dev` 后的正式
`d3-delivery`；最终镜像、部署和回滚证据仍待正式 D3。

## 最新同步后 PR 候选 CI：`2552a2a2d3f30ed7c48770469c161ce3b42554e0`

Run [33727688910](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33727688910)
为 PR 事件上的 `ci-quality-gate`，六个 job（workflow contracts、backend、frontend、
repo contracts、browser E2E、Disposable delivery）全部成功。artifact 保留在 GitHub，
其交付 manifest/image records 的内部构建 SHA 为
`5158533131c85bab24405821092fb7bd0a247630`；仅作候选质量证据，不计入 final D3：

| artifact ID / name | bytes | digest | archive status |
| --- | ---: | --- | --- |
| 9882602195 / `ci-validate-workflows-33727688910` | 680 | `sha256:2796fc694c86339d2d3837fe24787fe7a11d0992b692133053b326d859b5d356` | GitHub artifact only |
| 9882658369 / `ci-contracts-gate-33727688910` | 50879 | `sha256:d5deeea5ed78ff56da9bc35aa09ee75c6b6c4789cdfe61f3ad3e5a69dac7751a` | GitHub artifact only |
| 9882649422 / `ci-frontend-gate-33727688910` | 27805 | `sha256:b82084c9de47dd4c00e6ee647b4e623eeff8e39c8ed275a8cb64bb10e5bb8039` | GitHub artifact only |
| 9882719887 / `ci-backend-gate-33727688910` | 650817 | `sha256:16edb52c65b7191a420aec90849a86c5ba48667ebb3dc34f05471b7bf20f9e96` | GitHub artifact only |
| 9883239131 / `ci-browser-e2e-gate-33727688910` | 14420632 | `sha256:84fe3d483847d897040320a62686a0b984908421a3014ec281f814dce20fa605` | GitHub artifact only |
| 9883594340 / `ci-delivery-33727688910` | 15514386 | `sha256:717b32b3339275b366db5c6c115fa1925303e383561b4f1bfcc098527d01cef7` | GitHub artifact only |

该 run 不能替代合入 `dev` 后的 `d3-quality-gate-*`、`d3-images-*`、
`d3-kind-delivery-*` 和 `d3-delivery-outcome-*` 原始 artifact。

## 上一轮同步后 PR 候选 CI：`5b59e8258248865de9414e2c1a5b45a8f2ec0604`

Run [33724384655](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33724384655)
及其六个成功 artifact 保留为上一轮候选记录；其内部构建 SHA 为
`6ab2db7884df697760da370c66c356cc0e63e608`，不能替代本轮或 final SHA。

## 更早 PR 候选 head SHA：`82dd58d10eb49f1ceacec7965f7932c123891a1a`

Run [33707236357](https://github.com/Cr4zyorange/OnlineJudge/actions/runs/33707236357)
是 PR 事件上的 `ci-quality-gate`，不是合规的 `d3-delivery` workflow。它的所有质量门禁
和内置 `Disposable delivery` job 成功，但 `d3-delivery` 的触发契约只接受 `dev` push，
所以以下记录只作为候选校验，不计入最终 `success`：

| artifact ID / name | bytes | created | expires | archive status |
| --- | ---: | --- | --- | --- |
| 9875644224 / `ci-validate-workflows-33707236357` | 680 | 2026-09-03T02:20:21Z | 2026-09-17T02:20:21Z | GitHub artifact only |
| 9875689023 / `ci-contracts-gate-33707236357` | 50589 | 2026-09-03T02:22:16Z | 2026-09-17T02:22:16Z | GitHub artifact only |
| 9875709674 / `ci-frontend-gate-33707236357` | 27360 | 2026-09-03T02:23:10Z | 2026-09-17T02:23:09Z | GitHub artifact only |
| 9875756328 / `ci-backend-gate-33707236357` | 640232 | 2026-09-03T02:25:16Z | 2026-09-17T02:25:15Z | GitHub artifact only |
| 9875829062 / `ci-browser-e2e-gate-33707236357` | 2607052 | 2026-09-03T02:28:32Z | 2026-09-17T02:28:31Z | GitHub artifact only |
| 9876041415 / `ci-delivery-33707236357` | 15510164 | 2026-09-03T02:38:29Z | 2026-09-17T02:38:26Z | GitHub artifact only |

这些候选 artifact 不放入 `current/`，避免把 PR 事件结果误标为 final `d3-delivery`。
候选 `ci-delivery` 的 `artifact-manifest.json` 和 `image-records.tsv` 内部记录的
`gitSha`/镜像 tag 是 `7402fc614933242f7982c2b68c44cb40dfa67045`，与 PR head
`82dd…` 不同；候选镜像 digest 不作为 final SHA 的发布证据。
合入 `dev` 后应下载新的 `d3-quality-gate-*`、`d3-images-*`、`d3-kind-delivery-*`
和 `d3-delivery-outcome-*` 原始 artifact，替换本节的候选记录。

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

最新 final SHA 的 D3 尚未形成成功镜像 artifact；因此不能填写 final SHA 的镜像
digest。历史成功 run 的原始 inspect 记录：

- `onlinejudge/backend:5cdbe...` -> local digest `sha256:03f9c8eb...a04af`，OCI revision 同 SHA。
- `onlinejudge/frontend:5cdbe...` -> local digest `sha256:777a4f3d...3388a`，OCI revision 同 SHA。

以上省略号只出现在本摘要，完整值以归档原始 inspect 文件为准。
