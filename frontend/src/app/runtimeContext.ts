import { shallowRef } from 'vue';
import type { AuthUser } from '../api/auth/auth';
import type { Course } from '../types/crs';

export const currentUser = shallowRef<AuthUser | null>(null);
export const currentCourse = shallowRef<Course | null>(null);

export function resetRuntimeContext() {
  currentUser.value = null;
  currentCourse.value = null;
}
