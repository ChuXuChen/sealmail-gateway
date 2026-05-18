import React from 'react';
import { Alert, Collapse, Space, Typography } from 'antd';

const { Text } = Typography;

type Tone = 'default' | 'success' | 'warning' | 'danger' | 'info';

interface SectionPanelProps {
  children: React.ReactNode;
  className?: string;
  compact?: boolean;
  description?: React.ReactNode;
  extra?: React.ReactNode;
  title?: React.ReactNode;
}

interface StatusSummaryItem {
  description?: React.ReactNode;
  extra?: React.ReactNode;
  key: React.Key;
  label: React.ReactNode;
  tone?: Tone;
  value: React.ReactNode;
}

interface StatusSummaryProps {
  className?: string;
  items: StatusSummaryItem[];
}

interface TaskStepItem {
  description?: React.ReactNode;
  key: React.Key;
  status?: 'done' | 'current' | 'pending';
  title: React.ReactNode;
}

interface TaskStepsProps {
  className?: string;
  items: TaskStepItem[];
}

interface AdvancedSectionProps {
  children: React.ReactNode;
  className?: string;
  defaultOpen?: boolean;
  description?: React.ReactNode;
  title: React.ReactNode;
}

interface DangerZoneProps {
  action?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  description?: React.ReactNode;
  title: React.ReactNode;
}

interface FieldHintProps {
  children: React.ReactNode;
  className?: string;
}

export const SectionPanel: React.FC<SectionPanelProps> = ({
  children,
  className,
  compact,
  description,
  extra,
  title,
}) => (
  <section className={['section-panel', compact ? 'section-panel--compact' : '', className].filter(Boolean).join(' ')}>
    {(title || description || extra) ? (
      <div className="section-panel__header">
        <div className="section-panel__heading">
          {title ? <div className="section-panel__title">{title}</div> : null}
          {description ? <Text className="section-panel__description">{description}</Text> : null}
        </div>
        {extra ? <div className="section-panel__extra">{extra}</div> : null}
      </div>
    ) : null}
    <div className="section-panel__body">{children}</div>
  </section>
);

export const StatusSummary: React.FC<StatusSummaryProps> = ({ className, items }) => (
  <div className={['status-summary', className].filter(Boolean).join(' ')}>
    {items.map((item) => (
      <div className={['status-summary__item', `status-summary__item--${item.tone || 'default'}`].join(' ')} key={item.key}>
        <div className="status-summary__label">{item.label}</div>
        <div className="status-summary__value">{item.value}</div>
        {(item.description || item.extra) ? (
          <div className="status-summary__meta">
            {item.description ? <span>{item.description}</span> : null}
            {item.extra}
          </div>
        ) : null}
      </div>
    ))}
  </div>
);

export const TaskSteps: React.FC<TaskStepsProps> = ({ className, items }) => (
  <ol className={['task-steps', className].filter(Boolean).join(' ')}>
    {items.map((item, index) => (
      <li className={['task-steps__item', `task-steps__item--${item.status || 'pending'}`].join(' ')} key={item.key}>
        <div className="task-steps__index">{index + 1}</div>
        <div className="task-steps__content">
          <div className="task-steps__title">{item.title}</div>
          {item.description ? <div className="task-steps__description">{item.description}</div> : null}
        </div>
      </li>
    ))}
  </ol>
);

export const AdvancedSection: React.FC<AdvancedSectionProps> = ({
  children,
  className,
  defaultOpen,
  description,
  title,
}) => (
  <Collapse
    className={['advanced-section', className].filter(Boolean).join(' ')}
    defaultActiveKey={defaultOpen ? ['content'] : []}
    items={[
      {
        children,
        key: 'content',
        label: (
          <Space direction="vertical" size={2}>
            <span className="advanced-section__title">{title}</span>
            {description ? <Text className="advanced-section__description">{description}</Text> : null}
          </Space>
        ),
      },
    ]}
  />
);

export const DangerZone: React.FC<DangerZoneProps> = ({
  action,
  children,
  className,
  description,
  title,
}) => (
  <Alert
    action={action}
    className={['danger-zone', className].filter(Boolean).join(' ')}
    description={children || description}
    message={title}
    showIcon
    type="error"
  />
);

export const FieldHint: React.FC<FieldHintProps> = ({ children, className }) => (
  <div className={['field-hint', className].filter(Boolean).join(' ')}>
    {children}
  </div>
);
