import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import NotificationCenterView from '../../../src/views/lrn/NotificationCenterView.vue';
import * as notificationsApi from '../../../src/api/lrn/notifications';
import type { NotificationPage } from '../../../src/types/lrn';

vi.mock('../../../src/api/lrn/notifications');

const notificationPage: NotificationPage = {
  records: [
    {
      notificationId: 10,
      courseId: 101,
      title: '新作业发布：Java 编程题',
      content: '作业截止时间：2026-06-10 23:59',
      type: 'TASK',
      priority: 2,
      isRead: false,
      sourceModule: 'HWK',
      sourceId: 501,
      actionUrl: '/courses/101/homeworks/501',
      createdAt: '2026-06-02 09:00:00',
      readAt: null
    },
    {
      notificationId: 11,
      courseId: 101,
      title: '课程成绩已发布',
      content: '请查看本课程成绩。',
      type: 'GRADE',
      priority: 3,
      isRead: true,
      sourceModule: 'GRD',
      sourceId: 801,
      actionUrl: '/courses/101/grades?role=student',
      createdAt: '2026-06-02 10:00:00',
      readAt: '2026-06-02 10:05:00'
    }
  ],
  total: 2,
  page: 1,
  size: 20,
  unreadCount: 1
};

describe('NotificationCenterView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/notifications');
  });

  it('renders categorized notifications with unread highlight and action links', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValueOnce(notificationPage);

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    expect(notificationsApi.listNotifications).toHaveBeenCalledWith(expect.objectContaining({
      page: 1,
      size: 20
    }));
    expect(wrapper.text()).toContain('消息通知中心');
    expect(wrapper.find('[data-testid="lrn-home-entry"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('UI-LRN-04');
    expect(wrapper.text()).not.toContain('API-LRN-06');
    expect(wrapper.text()).toContain('未读 1');
    expect(wrapper.text()).toContain('任务通知');
    expect(wrapper.text()).toContain('成绩通知');
    expect(wrapper.text()).toContain('新作业发布：Java 编程题');
    expect(wrapper.text()).toContain('作业任务');
    expect(wrapper.text()).toContain('成绩中心');
    expect(wrapper.text()).not.toContain('HWK #501');
    expect(wrapper.text()).not.toContain('GRD #801');
    expect(wrapper.get('[data-testid="notification-card-10"]').classes()).toContain('notification-card--unread');
    expect(wrapper.get('a[href="/courses/101/homeworks/501"]').text()).toContain('查看详情');
    expect(wrapper.get('a[href="/courses/101/grades"]').text()).toContain('查看详情');
  });

  it('renders a friendly unavailable state instead of a dead hash link', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValueOnce({
      records: [{
        ...notificationPage.records[0],
        notificationId: 12,
        sourceModule: 'SYSTEM',
        sourceId: null,
        actionUrl: null
      }],
      total: 1,
      page: 1,
      size: 20,
      unreadCount: 1
    });

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    expect(wrapper.text()).toContain('入口已失效');
    expect(wrapper.find('a[href="#"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="notification-fallback-12"]').attributes('href')).toBe('/learning/tasks');
  });

  it('replaces an obsolete same-origin route with a recoverable course destination', async () => {
    vi.mocked(notificationsApi.listNotifications).mockResolvedValueOnce({
      records: [{
        ...notificationPage.records[0],
        notificationId: 13,
        sourceModule: 'LAB',
        actionUrl: '/deleted/labs/501'
      }],
      total: 1,
      page: 1,
      size: 20,
      unreadCount: 1
    });

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    expect(wrapper.text()).toContain('入口已失效');
    expect(wrapper.find('a[href="/deleted/labs/501"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="notification-fallback-13"]').attributes('href')).toBe('/courses/101');
  });

  it('reloads from the first page when filters change and shows empty state', async () => {
    vi.mocked(notificationsApi.listNotifications)
      .mockResolvedValueOnce(notificationPage)
      .mockResolvedValueOnce({
        records: [],
        total: 0,
        page: 1,
        size: 20,
        unreadCount: 0
      });

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    await wrapper.get('[name="type"]').setValue('GRADE');
    await flushPromises();

    expect(notificationsApi.listNotifications).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'GRADE',
      page: 1
    }));
    expect(wrapper.text()).toContain('暂无符合条件的通知');
  });

  it('shows loading failures and retries the notification query', async () => {
    vi.mocked(notificationsApi.listNotifications)
      .mockRejectedValueOnce(new Error('通知加载失败'))
      .mockResolvedValueOnce(notificationPage);

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    expect(wrapper.text()).toContain('通知加载失败');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(notificationsApi.listNotifications).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('新作业发布：Java 编程题');
  });

  it('marks selected notifications as read and deletes a notification from the page', async () => {
    const afterReadPage: NotificationPage = {
      ...notificationPage,
      records: notificationPage.records.map((notification) => (
        notification.notificationId === 10
          ? { ...notification, isRead: true, readAt: '2026-06-02 10:06:00' }
          : notification
      )),
      unreadCount: 0
    };
    const afterDeletePage: NotificationPage = {
      ...afterReadPage,
      records: afterReadPage.records.filter((notification) => notification.notificationId !== 10),
      total: 1
    };
    vi.mocked(notificationsApi.listNotifications)
      .mockResolvedValueOnce(notificationPage)
      .mockResolvedValueOnce(afterReadPage)
      .mockResolvedValueOnce(afterDeletePage);
    vi.mocked(notificationsApi.markNotificationsRead).mockResolvedValueOnce({ updatedCount: 1 });
    vi.mocked(notificationsApi.deleteNotification).mockResolvedValueOnce({ updatedCount: 1 });

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    await wrapper.get('[data-testid="notification-select-10"]').setValue(true);
    await wrapper.get('[data-testid="mark-selected-read"]').trigger('click');
    await flushPromises();

    expect(notificationsApi.markNotificationsRead).toHaveBeenCalledWith({
      notificationIds: [10],
      readAll: false
    });
    expect(wrapper.get('[data-testid="notification-card-10"]').classes()).not.toContain('notification-card--unread');

    await wrapper.get('[data-testid="delete-notification-10"]').trigger('click');
    await flushPromises();

    expect(notificationsApi.deleteNotification).toHaveBeenCalledWith(10);
    expect(wrapper.find('[data-testid="notification-card-10"]').exists()).toBe(false);
  });

  it('marks all unread notifications as read from the bulk action', async () => {
    const afterReadAllPage: NotificationPage = {
      ...notificationPage,
      records: notificationPage.records.map((notification) => ({
        ...notification,
        isRead: true,
        readAt: notification.readAt ?? '2026-06-02 10:06:00'
      })),
      unreadCount: 0
    };
    vi.mocked(notificationsApi.listNotifications)
      .mockResolvedValueOnce(notificationPage)
      .mockResolvedValueOnce(afterReadAllPage);
    vi.mocked(notificationsApi.markNotificationsRead).mockResolvedValueOnce({ updatedCount: 1 });

    const wrapper = mount(NotificationCenterView);
    await flushPromises();

    await wrapper.get('[data-testid="mark-all-read"]').trigger('click');
    await flushPromises();

    expect(notificationsApi.markNotificationsRead).toHaveBeenCalledWith({
      notificationIds: [],
      readAll: true
    });
    expect(wrapper.text()).toContain('未读 0');
    expect(wrapper.get('[data-testid="mark-all-read"]').attributes('disabled')).toBeDefined();
  });
});
