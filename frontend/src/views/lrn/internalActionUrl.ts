export function sanitizeInternalActionUrl(actionUrl: string | null) {
  if (!actionUrl || !actionUrl.startsWith('/') || actionUrl.startsWith('//')) {
    return null;
  }

  try {
    const target = new URL(actionUrl, window.location.origin);
    if (target.origin !== window.location.origin || !isKnownApplicationPath(target.pathname)) {
      return null;
    }
    target.searchParams.delete('role');
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return null;
  }
}

function isKnownApplicationPath(pathname: string) {
  const parts = pathname.split('/').filter(Boolean);
  if (parts.length === 1) {
    return ['login', 'register', '403', '404', 'session-expired', 'account-disabled', 'courses', 'profile', 'learning', 'notifications'].includes(parts[0]);
  }
  if (parts[0] === 'profile') {
    return parts.length === 2 && parts[1] === 'password';
  }
  if (parts[0] === 'admin') {
    return parts.length === 2 && parts[1] === 'auth';
  }
  if (parts[0] === 'learning') {
    return parts.length === 2 && ['tasks', 'progress', 'statistics', 'reminders'].includes(parts[1]);
  }
  if (parts[0] !== 'courses' || !isPositiveId(parts[1])) {
    return false;
  }
  if (parts.length === 2) {
    return true;
  }
  if (parts[2] === 'grades') {
    return parts.length === 3 || (
      parts.length === 5 && parts[3] === 'manage' && ['items', 'table'].includes(parts[4])
    );
  }
  if (!['labs', 'homeworks'].includes(parts[2])) {
    return false;
  }
  if (parts.length === 3) {
    return true;
  }
  if (['manage', 'new'].includes(parts[3])) {
    return parts.length === 4;
  }
  if (!isPositiveId(parts[3])) {
    return false;
  }
  if (parts.length === 4) {
    return true;
  }
  if (['submit', 'result', 'edit'].includes(parts[4])) {
    return parts.length === 5;
  }
  if (parts[4] === 'submissions') {
    return parts.length === 5 || (
      parts.length === 7 && isPositiveId(parts[5]) && parts[6] === 'result'
    );
  }
  if (parts[4] !== 'manage') {
    return false;
  }
  return parts.length === 5 || (
    parts.length === 6 && ['submissions', 'statistics'].includes(parts[5])
  ) || (
    parts.length === 7 && parts[5] === 'submissions' && isPositiveId(parts[6])
  );
}

function isPositiveId(value: string | undefined) {
  return !!value && /^[1-9]\d*$/.test(value);
}
