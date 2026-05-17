import { Empty, Table } from 'antd';
import type { TableProps } from 'antd';
import { DEFAULT_PAGE_SIZE_OPTIONS, formatListTotal } from './listUtils';

const defaultLocale = {
  emptyText: (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description="暂无数据"
    />
  ),
};

const normalizePagination = (
  pagination: TableProps<object>['pagination'],
): TableProps<object>['pagination'] => {
  if (pagination === false) {
    return false;
  }

  return {
    showSizeChanger: true,
    showQuickJumper: true,
    pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
    showTotal: formatListTotal,
    ...(pagination === undefined ? {} : pagination),
  };
};

const DataTable = <RecordType extends object>({
  className,
  locale,
  pagination,
  ...props
}: TableProps<RecordType>) => (
  <Table<RecordType>
    className={['data-table', className].filter(Boolean).join(' ')}
    locale={{ ...defaultLocale, ...locale }}
    pagination={normalizePagination(pagination as TableProps<object>['pagination']) as TableProps<RecordType>['pagination']}
    {...props}
  />
);

export default DataTable;
