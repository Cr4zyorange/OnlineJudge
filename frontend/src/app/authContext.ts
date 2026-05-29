import { configureAuthContext } from '../api/http';

export function configureDefaultAuthContext() {
  configureAuthContext(null);
}
