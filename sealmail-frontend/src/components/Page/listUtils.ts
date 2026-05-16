import type { TablePaginationConfig } from 'antd';

export interface ListPaginationState {
  page: number;
  size: number;
  total: number;
}

export const DEFAULT_PAGE_SIZE = 20;
export const DEFAULT_PAGE_SIZE_OPTIONS = ['10', '20', '50', '100'];

export const formatListTotal = (total: number, range?: [number, number]) => {
  if (!range || total === 0) {
    return `共 ${total} 条`;
  }

  return `第 ${range[0]}-${range[1]} 条 / 共 ${total} 条`;
};

export const createListPagination = (
  pagination: ListPaginationState,
  onChange: (page: number, size: number) => void,
): TablePaginationConfig => ({
  current: pagination.page,
  pageSize: pagination.size,
  total: pagination.total,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
  showTotal: formatListTotal,
  onChange: (page, size) => onChange(page, size),
});
