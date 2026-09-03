import { h } from 'vue';
import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import PageHeader from '../../../src/components/foundation/PageHeader.vue';
import SummaryStrip from '../../../src/components/foundation/SummaryStrip.vue';
import PageState from '../../../src/components/foundation/PageState.vue';
import StatusBadge from '../../../src/components/foundation/StatusBadge.vue';
import FilterBar from '../../../src/components/foundation/FilterBar.vue';
import DataTable from '../../../src/components/foundation/DataTable.vue';
import pageHeaderSource from '../../../src/components/foundation/PageHeader.vue?raw';
import summaryStripSource from '../../../src/components/foundation/SummaryStrip.vue?raw';
import pageStateSource from '../../../src/components/foundation/PageState.vue?raw';
import statusBadgeSource from '../../../src/components/foundation/StatusBadge.vue?raw';
import filterBarSource from '../../../src/components/foundation/FilterBar.vue?raw';
import dataTableSource from '../../../src/components/foundation/DataTable.vue?raw';
import platformNavSource from '../../../src/components/foundation/PlatformNav.vue?raw';

describe('foundation display components', () => {
  it('renders a semantic page heading with context, metadata, and primary actions', () => {
    const wrapper = mount(PageHeader, {
      props: {
        eyebrow: '数据结构·实训',
        title: '基础排序实验',
        subtitle: '完成代码后提交评测，截止前可重复提交。',
        headingLevel: 1
      },
      slots: {
        meta: '<span data-testid="page-meta">截止 8 月 31 日 23:59</span>',
        actions: '<button type="button">提交答案</button>'
      }
    });

    expect(wrapper.get('header').classes()).toContain('foundation-page-header');
    expect(wrapper.get('h1').text()).toBe('基础排序实验');
    expect(wrapper.get('[data-testid="page-header-eyebrow"]').text()).toContain('数据结构');
    expect(wrapper.get('[data-testid="page-header-subtitle"]').text()).toContain('可重复提交');
    expect(wrapper.get('[data-testid="page-meta"]').text()).toContain('23:59');
    expect(wrapper.get('[aria-label="页面操作"] button').text()).toBe('提交答案');
  });

  it('renders summary display models as a semantic description list and supports item slots', () => {
    const items = [
      { key: 'deadline', label: '截止时间', value: '8 月 31 日 23:59', hint: '剩余 16 天', tone: 'warning' as const },
      { key: 'score', label: '最高得分', value: '92', hint: '满分 100', tone: 'success' as const }
    ];
    const wrapper = mount(SummaryStrip, {
      props: { items, ariaLabel: '任务摘要' },
      slots: {
        item: ({ item }) => h(
          'span',
          { 'data-testid': `summary-${item.key}` },
          `${item.label}：${item.value}`
        )
      }
    });

    expect(wrapper.get('dl').attributes('aria-label')).toBe('任务摘要');
    expect(wrapper.findAll('.summary-strip__item [data-testid^="summary-"]')).toHaveLength(2);
    expect(wrapper.get('[data-testid="summary-deadline"]').text()).toBe('截止时间：8 月 31 日 23:59');
    expect(wrapper.get('[data-summary-tone="warning"]').attributes('data-summary-tone')).toBe('warning');
  });

  it('announces page states and exposes an explicit retry interaction for recoverable errors', async () => {
    const wrapper = mount(PageState, {
      props: {
        state: 'error',
        title: '加载实训失败',
        message: '请检查网络后重试。',
        retryLabel: '重新加载'
      },
      slots: {
        actions: '<a href="/courses" data-testid="state-fallback">返回课程</a>'
      }
    });

    expect(wrapper.get('[data-testid="page-state"]').attributes('role')).toBe('alert');
    expect(wrapper.get('[data-testid="page-state"]').attributes('aria-live')).toBe('assertive');
    expect(wrapper.text()).toContain('加载实训失败');
    expect(wrapper.get('[data-testid="state-fallback"]').text()).toBe('返回课程');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    expect(wrapper.emitted('retry')).toHaveLength(1);
  });

  it('marks loading states as busy without turning non-live badges into alerts', () => {
    const loading = mount(PageState, { props: { state: 'loading' } });
    const badge = mount(StatusBadge, { props: { label: '已发布', tone: 'success' } });

    expect(loading.get('[data-testid="page-state"]').attributes('aria-busy')).toBe('true');
    expect(loading.get('[data-testid="page-state"]').attributes('role')).toBe('status');
    expect(loading.text()).toContain('正在加载');
    expect(badge.get('[data-testid="status-badge"]').text()).toBe('已发布');
    expect(badge.get('[data-testid="status-badge"]').attributes('role')).toBeUndefined();
    expect(badge.get('[data-testid="status-badge"]').attributes('data-tone')).toBe('success');
  });

  it('updates search and select fields through an immutable filter display model', async () => {
    const wrapper = mount(FilterBar, {
      props: {
        ariaLabel: '实训筛选',
        fields: [
          { key: 'query', label: '搜索', kind: 'search', placeholder: '搜索实训名称' },
          {
            key: 'status',
            label: '状态',
            kind: 'select',
            options: [
              { value: '', label: '全部状态' },
              { value: 'open', label: '可提交' }
            ]
          }
        ],
        modelValue: { query: '', status: '' }
      }
    });

    const search = wrapper.get('input[type="search"][name="query"]');
    const select = wrapper.get('select[name="status"]');
    expect(search.attributes('placeholder')).toBe('搜索实训名称');
    expect(wrapper.get(`label[for="${search.attributes('id')}"]`).text()).toContain('搜索');

    await search.setValue('排序');
    expect(wrapper.emitted('update:modelValue')?.[0]?.[0]).toEqual({ query: '排序', status: '' });

    await select.setValue('open');
    expect(wrapper.emitted('update:modelValue')?.[1]?.[0]).toEqual({ query: '', status: 'open' });

    await wrapper.get('form').trigger('submit');
    expect(wrapper.emitted('submit')).toHaveLength(1);
  });

  it('supports custom filter actions without imposing domain behavior', async () => {
    const wrapper = mount(FilterBar, {
      props: {
        fields: [{ key: 'query', label: '关键词', kind: 'search' }],
        modelValue: { query: '' }
      },
      slots: {
        actions: '<button type="button" data-testid="saved-view">保存视图</button>'
      }
    });

    expect(wrapper.find('[data-testid="filter-submit"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="saved-view"]').text()).toBe('保存视图');
  });

  it('renders a semantic desktop table and a default mobile card view from display rows', () => {
    const wrapper = mount(DataTable, {
      props: {
        caption: '实训任务列表',
        rowKey: 'id',
        columns: [
          { key: 'title', label: '实训名称' },
          { key: 'deadline', label: '截止时间' },
          { key: 'statusLabel', label: '状态' }
        ],
        rows: [
          { id: '950211', title: '基础排序实验', deadline: '8 月 31 日 23:59', statusLabel: '可提交' }
        ]
      },
      slots: {
        'cell-statusLabel': ({ value }: { value: unknown }) => h(StatusBadge, {
          label: String(value),
          tone: 'success'
        })
      }
    });

    expect(wrapper.get('table caption').text()).toBe('实训任务列表');
    expect(wrapper.findAll('th[scope="col"]')).toHaveLength(3);
    expect(wrapper.get('tbody tr').text()).toContain('基础排序实验');
    expect(wrapper.get('tbody [data-testid="status-badge"]').text()).toBe('可提交');
    expect(wrapper.get('[data-testid="data-table-mobile"]').attributes('data-mobile-layout')).toBe('cards');
    expect(wrapper.get('[data-testid="data-table-mobile"]').text()).toContain('截止时间');
    expect(wrapper.get('[data-testid="data-table-mobile"]').text()).toContain('8 月 31 日 23:59');
  });

  it('lets consumers replace a mobile row with a purpose-built card and renders an accessible empty state', () => {
    const wrapper = mount(DataTable, {
      props: {
        caption: '提交列表',
        columns: [{ key: 'student', label: '学生' }],
        rows: [{ student: '张同学' }]
      },
      slots: {
        'mobile-card': ({ row }: { row: Record<string, unknown> }) => h(
          'a',
          { href: '/submissions/1', 'data-testid': 'submission-card' },
          String(row.student)
        )
      }
    });

    expect(wrapper.get('[data-testid="submission-card"]').text()).toBe('张同学');

    const empty = mount(DataTable, {
      props: {
        caption: '提交列表',
        columns: [{ key: 'student', label: '学生' }],
        rows: [],
        emptyTitle: '暂无提交',
        emptyMessage: '学生提交后会出现在这里。'
      }
    });
    expect(empty.get('[data-testid="data-table-empty"]').attributes('role')).toBe('status');
    expect(empty.text()).toContain('暂无提交');
    expect(empty.text()).toContain('学生提交后');
  });

  it('keeps the shared layer domain-free and defines the 390px responsive layout in scoped styles', () => {
    const sources = [
      pageHeaderSource,
      summaryStripSource,
      pageStateSource,
      statusBadgeSource,
      filterBarSource,
      dataTableSource
    ];

    for (const source of sources) {
      expect(source).not.toMatch(/(?:\.\.\/)+api\//);
      expect(source).not.toMatch(/(?:\.\.\/)+types\/(?:lab|hwk)/);
      expect(source).not.toMatch(/\b(?:PUBLISHED|SCORE_PUBLISHED|WRONG_ANSWER|ACCEPTED)\b/);
    }

    expect(pageHeaderSource).toContain('@media (max-width: 640px)');
    expect(filterBarSource).toContain('@media (max-width: 640px)');
    expect(dataTableSource).toContain('@media (max-width: 640px)');
    expect(dataTableSource).toMatch(/\.data-table__desktop[\s\S]*display:\s*none\s*!important/);
    expect(dataTableSource).toMatch(/\.data-table__mobile[\s\S]*display:\s*grid/);
    expect(platformNavSource).toContain('platform-nav__mobile-label');
    expect(platformNavSource).not.toContain('clip: rect(0, 0, 0, 0)');
  });
});
