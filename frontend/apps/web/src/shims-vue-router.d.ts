import 'vue-router';

declare module 'vue-router' {
  interface RouteMeta {
    /** 公开路由(免登录):/login、/register。 */
    public?: boolean;
  }
}
