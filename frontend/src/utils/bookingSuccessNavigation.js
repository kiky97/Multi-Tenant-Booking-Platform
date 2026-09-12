export const FORCE_HOME_TOP_STORAGE_KEY = 'forceHomeTop';

export function markHomeTopScrollPending(storage = window.sessionStorage) {
    storage.setItem(FORCE_HOME_TOP_STORAGE_KEY, '1');
}
