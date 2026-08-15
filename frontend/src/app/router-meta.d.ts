import 'vue-router';

export {};

declare module 'vue-router' {
  interface RouteMeta {
    title: string;
    shell: 'public' | 'platform' | 'course';
    requiresAuth?: boolean;
    platformRoles?: string[];
    courseAccess?: 'member' | 'manage';
    uiIds?: string[];
    draftProtection?: boolean;
    legacy?: boolean;
  }
}
