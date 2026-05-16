import { Modal } from 'antd';
import type { ModalFuncProps } from 'antd';
import type React from 'react';

interface ConfirmActionOptions extends Omit<ModalFuncProps, 'okButtonProps' | 'okText' | 'cancelText'> {
  danger?: boolean;
  okText?: string;
}

export const confirmAction = ({
  danger = false,
  okText,
  ...options
}: ConfirmActionOptions) => Modal.confirm({
  cancelText: '取消',
  centered: true,
  okButtonProps: { danger },
  okText: okText || (danger ? '确认执行' : '确认'),
  ...options,
});

export const confirmDeleteAction = (
  title: string,
  onOk: ModalFuncProps['onOk'],
  content?: React.ReactNode,
) => confirmAction({
  title,
  content,
  danger: true,
  okText: '删除',
  onOk,
});
