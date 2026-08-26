#!/usr/bin/env sh

set -eu

PATH="/mingw64/bin:/usr/bin:$PATH"
export PATH

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

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
    echo "obsolete combined HWK diagram is still referenced in $relative_path: $rejected" >&2
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

for diagram in \
  uc-hwk-01-sequence \
  uc-hwk-02-sequence \
  uc-hwk-01-component-sequence \
  uc-hwk-02-component-sequence \
  uc-hwk-01-object-sequence \
  uc-hwk-02-object-sequence
do
  source="docs/diagrams/tikz/hwk/$diagram.tex"
  require_file "$source"
  require_text "$source" '\begin{tikzpicture}'
  require_text "$source" '\hwkguard'
  require_text "$source" '\hwkoperand'
  require_file "docs/最终提交/assets/tikz/hwk/$diagram.png"
done
require_file 'docs/diagrams/tikz/hwk/hwk-tikz-style.tex'
require_file "$closure"
require_file "$e2e"
reject_file 'docs/diagrams/srs/fig_4_14_homework_flow_ssd.mmd'
reject_file 'docs/最终提交/assets/fig_4_14_homework_flow_ssd.svg'
reject_file 'docs/diagrams/srs-uml/uc-hwk-01-sequence.puml'
reject_file 'docs/diagrams/srs-uml/uc-hwk-02-sequence.puml'

require_text "$srs" '图 4-14A 学生提交作业并触发自动评测系统顺序图'
require_text "$srs" '图 4-14B 教师创建并发布作业系统顺序图'
reject_text "$srs" 'assets/fig_4_14_homework_flow_ssd.svg'

require_text "$overview" 'UC-HWK-02 教师创建并发布作业组件顺序图'
require_text "$overview" 'UC-HWK-01 学生提交作业并触发自动评测组件顺序图'
require_text "$overview" 'assets/tikz/hwk/uc-hwk-02-component-sequence.png'
require_text "$overview" 'assets/tikz/hwk/uc-hwk-01-component-sequence.png'
require_text "$detail" 'UC-HWK-02 教师创建并发布作业对象顺序图'
require_text "$detail" 'UC-HWK-01 学生提交作业并触发自动评测对象顺序图'
require_text "$detail" 'assets/tikz/hwk/uc-hwk-02-object-sequence.png'
require_text "$detail" 'assets/tikz/hwk/uc-hwk-01-object-sequence.png'

require_text "$closure" '教师批阅/重评 | 扩展路径'
require_text "$closure" '共享 E2E #267 | PASS'
require_text "$closure" 'GRD 来源成绩真实链路 | PASS'
require_text "$closure" '通知投递失败设计/实现一致性 | FAIL'
reject_text "$closure" '本 Issue 验收范围内无 BLOCKED 项'
require_text "$e2e" '@hwk'
require_text "$e2e" '教师创建发布作业后学生提交并获得已发布评语'
require_text "$e2e" 'includedInFinal: true'
require_text "$e2e" 'gradeItemId=${gradeItemId}'
require_text "$e2e" 'sourceId: homeworkId'
require_text "$e2e" 'Number(gradeRecord?.rawScore)'

echo 'HWK document and test closure contract: PASS'
