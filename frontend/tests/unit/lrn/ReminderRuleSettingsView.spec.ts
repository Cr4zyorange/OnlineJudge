import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as reminderRulesApi from '../../../src/api/lrn/reminderRules';
import type { ReminderRuleOverview } from '../../../src/types/lrn';
import ReminderRuleSettingsView from '../../../src/views/lrn/ReminderRuleSettingsView.vue';

vi.mock('../../../src/api/lrn/reminderRules');

const overview: ReminderRuleOverview = {
  rules: [
    {
      reminderType: 'HOMEWORK_DEADLINE',
      sourceModule: 'HWK',
      aheadMinutes: 1440,
      enabled: true,
      required: false
    },
    {
      reminderType: 'HOMEWORK_DEADLINE',
      sourceModule: 'HWK',
      aheadMinutes: 60,
      enabled: true,
      required: false
    },
    {
      reminderType: 'EXPERIMENT_DEADLINE',
      sourceModule: 'LAB',
      aheadMinutes: 1440,
      enabled: true,
      required: false
    },
    {
      reminderType: 'EXPERIMENT_DEADLINE',
      sourceModule: 'LAB',
      aheadMinutes: 60,
      enabled: false,
      required: false
    }
  ],
  settings: {
    enableExperiment: true,
    enableHomework: true,
    enableGrade: true,
    enableAnnouncement: true,
    enableNonCriticalReminder: true
  }
};

describe('ReminderRuleSettingsView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/learning/reminders');
  });

  it('renders reminder rules and saves notification preferences', async () => {
    vi.mocked(reminderRulesApi.getReminderRules).mockResolvedValueOnce(overview);
    vi.mocked(reminderRulesApi.saveReminderRules).mockResolvedValueOnce({
      ...overview,
      settings: {
        ...overview.settings,
        enableHomework: false
      }
    });

    const wrapper = mount(ReminderRuleSettingsView);
    await flushPromises();

    expect(wrapper.text()).toContain('提醒规则设置');
    expect(wrapper.text()).toContain('作业截止提醒');
    expect(wrapper.text()).toContain('提前 24 小时');
    expect(wrapper.text()).toContain('提前 1 小时');

    await wrapper.get('[data-testid="setting-enableHomework"]').setValue(false);
    await wrapper.get('[data-testid="save-reminder-rules"]').trigger('click');
    await flushPromises();

    expect(reminderRulesApi.saveReminderRules).toHaveBeenCalledWith(expect.objectContaining({
      settings: expect.objectContaining({
        enableHomework: false
      })
    }));
    expect(wrapper.text()).toContain('提醒规则已保存');
  });

  it('shows a retry path when reminder settings fail to load', async () => {
    vi.mocked(reminderRulesApi.getReminderRules)
      .mockRejectedValueOnce(new Error('reminder load failed'))
      .mockResolvedValueOnce(overview);

    const wrapper = mount(ReminderRuleSettingsView);
    await flushPromises();

    expect(wrapper.text()).toContain('reminder load failed');

    await wrapper.get('[data-testid="retry-reminder-rules"]').trigger('click');
    await flushPromises();

    expect(reminderRulesApi.getReminderRules).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('作业截止提醒');
  });
});
