import React, { useId } from 'react';
import './SealMailLogo.css';

type SealMailLogoVariant = 'sider' | 'login';

interface SealMailLogoProps {
  collapsed?: boolean;
  className?: string;
  subtitle?: string;
  variant?: SealMailLogoVariant;
}

const SealMailLogo: React.FC<SealMailLogoProps> = ({
  collapsed = false,
  className,
  subtitle = '邮件安全网关',
  variant = 'sider',
}) => {
  const rawId = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const crestGradientId = `sealmail-crest-${rawId}`;
  const ribbonGradientId = `sealmail-ribbon-${rawId}`;
  const lineGradientId = `sealmail-line-${rawId}`;
  const auraGradientId = `sealmail-aura-${rawId}`;

  const classes = [
    'sealmail-logo',
    `sealmail-logo--${variant}`,
    collapsed ? 'sealmail-logo--collapsed' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes} aria-label="SealMail 邮件安全网关">
      <svg
        className="sealmail-logo__mark"
        viewBox="0 0 72 72"
        role="img"
        aria-hidden="true"
        focusable="false"
      >
        <defs>
          <linearGradient id={crestGradientId} x1="9" y1="7" x2="63" y2="66" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#1f6feb" />
            <stop offset="0.42" stopColor="#123b82" />
            <stop offset="1" stopColor="#061528" />
          </linearGradient>
          <linearGradient id={ribbonGradientId} x1="18" y1="15" x2="55" y2="57" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#ffffff" />
            <stop offset="0.52" stopColor="#dbeafe" />
            <stop offset="1" stopColor="#7dd3fc" />
          </linearGradient>
          <linearGradient id={lineGradientId} x1="18" y1="19" x2="54" y2="52" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#93c5fd" />
            <stop offset="1" stopColor="#22d3ee" />
          </linearGradient>
          <radialGradient id={auraGradientId} cx="0" cy="0" r="1" gradientTransform="matrix(31 34 -31 28 23 15)" gradientUnits="userSpaceOnUse">
            <stop offset="0" stopColor="#ffffff" stopOpacity="0.34" />
            <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
          </radialGradient>
        </defs>

        <rect x="6" y="6" width="60" height="60" rx="18" fill={`url(#${crestGradientId})`} />
        <rect x="6" y="6" width="60" height="60" rx="18" fill={`url(#${auraGradientId})`} />
        <path d="M13 52.5 58.5 19" stroke="#ffffff" strokeOpacity="0.1" strokeWidth="13" strokeLinecap="round" />
        <path d="M15.5 55.5 60 22.8" stroke="#1677ff" strokeOpacity="0.2" strokeWidth="5" strokeLinecap="round" />
        <rect x="8.2" y="8.2" width="55.6" height="55.6" rx="16.2" fill="none" stroke="#ffffff" strokeOpacity="0.16" strokeWidth="1.8" />
        <path
          d="M48.5 17.8H31.2c-7.2 0-12.1 4-12.1 9.8 0 6.2 5.1 8.3 13 9.2l6.2.7c6.2.7 9.7 2.5 9.7 6.6 0 5.8-5.6 9.6-14.1 9.6H20.4"
          fill="none"
          stroke={`url(#${ribbonGradientId})`}
          strokeLinecap="round"
          strokeWidth="6.8"
        />
        <path d="M23.7 24.5h24.8" stroke={`url(#${lineGradientId})`} strokeLinecap="round" strokeWidth="2.4" />
        <path d="M21.3 47.5h14.9" stroke={`url(#${lineGradientId})`} strokeLinecap="round" strokeWidth="2.4" opacity="0.72" />
      </svg>

      {!collapsed && (
        <span className="sealmail-logo__copy" aria-hidden="true">
          <strong className="sealmail-logo__wordmark">SealMail</strong>
          {subtitle && <span className="sealmail-logo__subtitle">{subtitle}</span>}
        </span>
      )}
    </div>
  );
};

export default SealMailLogo;
