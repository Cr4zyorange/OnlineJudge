#!/usr/bin/env sh

set -eu

PATH="/mingw64/bin:/usr/bin:$PATH"
export PATH

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
if [ -n "${NODE_BIN:-}" ]; then
  node_bin=$NODE_BIN
elif command -v node >/dev/null 2>&1; then
  node_bin=$(command -v node)
elif [ -x '/mnt/d/Program Files/nodejs/node.exe' ]; then
  node_bin='/mnt/d/Program Files/nodejs/node.exe'
elif [ -x '/d/Program Files/nodejs/node.exe' ]; then
  node_bin='/d/Program Files/nodejs/node.exe'
else
  echo 'missing node executable; set NODE_BIN to run Mermaid render verification' >&2
  exit 1
fi

to_node_path() {
  if printf '%s' "$node_bin" | grep -Eq '\.exe$' && command -v wslpath >/dev/null 2>&1; then
    wslpath -w "$1"
  elif printf '%s' "$node_bin" | grep -Eq '\.exe$' && command -v cygpath >/dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s\n' "$1"
  fi
}

render_tmp=$(mktemp -d "${TMPDIR:-/tmp}/oj-hwk-doc-test-closure.XXXXXX")
trap 'rm -rf "$render_tmp"' EXIT HUP INT TERM

require_file() {
  relative_path=$1
  if [ ! -f "$repo_root/$relative_path" ]; then
    echo "missing required file: $relative_path" >&2
    exit 1
  fi
}

require_text() {
  relative_path=$1
  expected=$2
  if ! grep -Fq -- "$expected" "$repo_root/$relative_path"; then
    echo "missing required text in $relative_path: $expected" >&2
    exit 1
  fi
}

reject_text() {
  relative_path=$1
  rejected=$2
  if grep -Fq -- "$rejected" "$repo_root/$relative_path"; then
    echo "rejected text is still present in $relative_path: $rejected" >&2
    exit 1
  fi
}

reject_file() {
  relative_path=$1
  if [ -e "$repo_root/$relative_path" ]; then
    echo "obsolete combined HWK diagram still exists: $relative_path" >&2
    exit 1
  fi
}

srs='docs/最终提交/软件需求规格说明书.md'
overview='docs/最终提交/软件概要设计说明书.md'
detail='docs/最终提交/软件详细设计说明书.md'
closure='docs/过程/测试/D2-HWK业务场景与测试闭环.md'
e2e='frontend/tests/e2e/hwk/homework-lifecycle.spec.ts'
submission_ssd='docs/diagrams/srs/fig_4_14a_hwk_submission_ssd.mmd'
publish_ssd='docs/diagrams/srs/fig_4_14b_hwk_publish_ssd.mmd'
submission_component='docs/diagrams/arch/fig_5_2_hwk_01_submission_component.mmd'
publish_object='docs/diagrams/dsd/fig_3_5_3a_hwk_publish_object.mmd'
submission_object='docs/diagrams/dsd/fig_3_5_3b_hwk_submission_object.mmd'
svg_mutation_test='scripts/test/verify-mermaid-svg.test.mjs'

require_file "$svg_mutation_test"
"$node_bin" "$(to_node_path "$repo_root/$svg_mutation_test")"

for pair in \
  'docs/diagrams/srs/fig_4_14a_hwk_submission_ssd.mmd|docs/最终提交/assets/fig_4_14a_hwk_submission_ssd.svg' \
  'docs/diagrams/srs/fig_4_14b_hwk_publish_ssd.mmd|docs/最终提交/assets/fig_4_14b_hwk_publish_ssd.svg' \
  'docs/diagrams/arch/fig_5_2_hwk_01_submission_component.mmd|docs/最终提交/assets/fig_5_2_hwk_01_submission_component.svg' \
  'docs/diagrams/arch/fig_5_2_hwk_02_publish_component.mmd|docs/最终提交/assets/fig_5_2_hwk_02_publish_component.svg' \
  'docs/diagrams/dsd/fig_3_5_3a_hwk_publish_object.mmd|docs/最终提交/assets/fig_3_5_3a_hwk_publish_object.svg' \
  'docs/diagrams/dsd/fig_3_5_3b_hwk_submission_object.mmd|docs/最终提交/assets/fig_3_5_3b_hwk_submission_object.svg'
do
  source=${pair%%|*}
  asset=${pair#*|}
  require_file "$source"
  require_text "$source" 'sequenceDiagram'
  require_text "$source" 'alt '
  require_text "$source" 'else '
  require_file "$asset"
  rendered="$render_tmp/$(basename "$asset")"
  "$node_bin" "$(to_node_path "$repo_root/scripts/dev/render-mermaid.mjs")" "$(to_node_path "$repo_root/$source")" "$(to_node_path "$rendered")"
  "$node_bin" "$(to_node_path "$repo_root/scripts/dev/verify-mermaid-svg.mjs")" "$(to_node_path "$repo_root/$asset")" "$(to_node_path "$rendered")"
done
require_file "$closure"
require_file "$e2e"
reject_file 'docs/diagrams/srs/fig_4_14_homework_flow_ssd.mmd'
reject_file 'docs/最终提交/assets/fig_4_14_homework_flow_ssd.svg'
reject_file 'docs/diagrams/srs-uml/uc-hwk-01-sequence.puml'
reject_file 'docs/diagrams/srs-uml/uc-hwk-02-sequence.puml'
reject_file 'docs/diagrams/tikz/hwk/hwk-tikz-style.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-01-sequence.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-02-sequence.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-01-component-sequence.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-02-component-sequence.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-01-object-sequence.tex'
reject_file 'docs/diagrams/tikz/hwk/uc-hwk-02-object-sequence.tex'

require_text "$srs" '图 4-14A 学生提交作业并触发自动评测系统顺序图'
require_text "$srs" '图 4-14B 教师创建并发布作业系统顺序图'
require_text "$srs" 'assets/fig_4_14a_hwk_submission_ssd.svg'
require_text "$srs" 'assets/fig_4_14b_hwk_publish_ssd.svg'
reject_text "$srs" 'assets/fig_4_14_homework_flow_ssd.svg'
reject_text "$submission_ssd" 'System->>System'
reject_text "$publish_ssd" 'System->>System'

require_text "$overview" 'UC-HWK-02 教师创建并发布作业组件顺序图'
require_text "$overview" 'UC-HWK-01 学生提交作业并触发自动评测组件顺序图'
require_text "$overview" 'assets/fig_5_2_hwk_02_publish_component.svg'
require_text "$overview" 'assets/fig_5_2_hwk_01_submission_component.svg'
require_text "$detail" 'UC-HWK-02 教师创建并发布作业对象顺序图'
require_text "$detail" 'UC-HWK-01 学生提交作业并触发自动评测对象顺序图'
require_text "$detail" 'assets/fig_3_5_3a_hwk_publish_object.svg'
require_text "$detail" 'assets/fig_3_5_3b_hwk_submission_object.svg'
require_text "$submission_component" '当前 CODE 结果读取路径（FR-HWK-04 缺口）'
reject_text "$publish_object" 'Question/TestCase Repository'
require_text "$publish_object" '保存 DRAFT 聚合（含题目、测试用例和评测配置）'
require_text "$submission_object" '当前 CODE 结果读取路径（FR-HWK-04 缺口）'

require_text "$closure" '教师批阅/重评 | 扩展路径'
require_text "$closure" '共享 E2E #267 | `homework-lifecycle.spec.ts`'
require_text "$closure" 'runner 通过不等于 FR-HWK-04 通过'
require_text "$closure" 'GRD 来源成绩真实链路 | PASS'
require_text "$closure" '通知投递失败设计/实现一致性 | PASS'
require_text "$closure" 'CODE 提交后独立后台评测'
require_text "$closure" 'Issue #264 结论：存在产品 FAIL'
require_text "$closure" 'HomeworkControllerTest#publishRollsBackHomeworkWhenRequiredNotificationDeliveryFails'
require_text "$closure" '#281 / PR #285 已合并'
reject_text "$closure" '本 Issue 验收范围内无 FAIL 或 BLOCKED 项'
reject_text "$closure" '#264 完整闭环 PASS'
reject_text "$closure" '通知投递失败设计/实现一致性 | FAIL'
require_text "$e2e" '@hwk'
require_text "$e2e" '教师创建发布作业后学生提交并获得已发布评语'
require_text "$e2e" '多类型提交暴露代码后台评测缺口'
require_text "$e2e" "pendingCodeSubmission.evaluationStatus).toBe('PENDING')"
reject_text "$e2e" 'waitForTerminalEvaluation'
require_text "$e2e" 'includedInFinal: true'
require_text "$e2e" 'gradeItemId=${gradeItemId}'
require_text "$e2e" 'sourceId: homeworkId'
require_text "$e2e" 'Number(gradeRecord?.rawScore)'

echo 'HWK document and test closure contract: PASS'
