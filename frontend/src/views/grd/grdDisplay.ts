const SOURCE_LABELS: Record<string, string> = {
  LAB: '实验任务',
  HWK: '作业任务',
  OTHER_COURSE_ITEM: '课程内其他成绩项'
};

const FINAL_STATUS_LABELS: Record<string, string> = {
  CALCULATED: '已计算',
  INCOMPLETE: '待补全',
  ADJUSTED: '已调整'
};

const PUBLISH_STATUS_LABELS: Record<string, string> = {
  UNPUBLISHED: '未发布',
  PUBLISHED: '已发布'
};

const GRADE_STATUS_LABELS: Record<string, string> = {
  SCORED: '已评分',
  UNSUBMITTED: '未提交',
  UNGRADED: '待评分',
  MISSING: '缺失',
  ADJUSTED: '已调整'
};

const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  APPROVED: '已同意',
  REJECTED: '已驳回',
  CLOSED: '已关闭'
};

const CHANGE_TYPE_LABELS: Record<string, string> = {
  RECORD_ADJUST: '单项成绩调整',
  FINAL_ADJUST: '课程总评调整'
};

const PUBLISH_SCOPE_LABELS: Record<string, string> = {
  COURSE: '全班',
  PARTIAL_STUDENTS: '指定学生',
  PARTIAL_ITEMS: '指定成绩项'
};

const NOTIFICATION_STATUS_LABELS: Record<string, string> = {
  SENT: '已发送',
  FAILED: '发送失败'
};

export function gradeSourceLabel(value: string | null | undefined) {
  return labelFor(SOURCE_LABELS, value, '未知成绩来源');
}

export function finalStatusLabel(value: string | null | undefined) {
  return labelFor(FINAL_STATUS_LABELS, value, '未知总评状态');
}

export function publishStatusLabel(value: string | null | undefined) {
  return labelFor(PUBLISH_STATUS_LABELS, value, '未知发布状态');
}

export function gradeStatusLabel(value: string | null | undefined) {
  return labelFor(GRADE_STATUS_LABELS, value, '未知成绩状态');
}

export function reviewStatusLabel(value: string | null | undefined) {
  return labelFor(REVIEW_STATUS_LABELS, value, '未知复核状态');
}

export function changeTypeLabel(value: string | null | undefined) {
  return labelFor(CHANGE_TYPE_LABELS, value, '未知变更类型');
}

export function publishScopeLabel(value: string | null | undefined) {
  return labelFor(PUBLISH_SCOPE_LABELS, value, '未知发布范围');
}

export function notificationStatusLabel(value: string | null | undefined) {
  return labelFor(NOTIFICATION_STATUS_LABELS, value, '未知通知状态');
}

function labelFor(labels: Record<string, string>, value: string | null | undefined, fallback: string) {
  if (!value) {
    return fallback;
  }
  return labels[value] ?? fallback;
}
