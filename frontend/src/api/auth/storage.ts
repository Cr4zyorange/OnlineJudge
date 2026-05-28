const fallbackStorage = new Map<string, string>();

export function readAuthStorage(key: string) {
  const storage = browserStorage();
  if (storage) {
    return storage.getItem(key);
  }
  return fallbackStorage.get(key) ?? null;
}

export function writeAuthStorage(key: string, value: string) {
  const storage = browserStorage();
  if (storage) {
    storage.setItem(key, value);
    return;
  }
  fallbackStorage.set(key, value);
}

export function removeAuthStorage(key: string) {
  const storage = browserStorage();
  if (storage) {
    storage.removeItem(key);
    return;
  }
  fallbackStorage.delete(key);
}

function browserStorage() {
  if (typeof window === 'undefined') {
    return null;
  }
  const storage = window.localStorage;
  if (
    typeof storage?.getItem === 'function'
    && typeof storage.setItem === 'function'
    && typeof storage.removeItem === 'function'
  ) {
    return storage;
  }
  return null;
}
