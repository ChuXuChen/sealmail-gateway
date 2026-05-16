import React from 'react';

interface PageShellProps {
  children: React.ReactNode;
  className?: string;
}

const PageShell: React.FC<PageShellProps> = ({ children, className }) => {
  const classes = ['page-shell', className].filter(Boolean).join(' ');

  return <div className={classes}>{children}</div>;
};

export default PageShell;
