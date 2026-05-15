import React, { useId } from 'react';
import './SealMailLogo.css';

type SealMailLogoVariant = 'sider' | 'login';

interface SealMailLogoProps {
  collapsed?: boolean;
  className?: string;
  hideMark?: boolean;
  subtitle?: string;
  variant?: SealMailLogoVariant;
}

const SealMailLogo: React.FC<SealMailLogoProps> = ({
  collapsed = false,
  className,
  hideMark = true,
  subtitle = '邮件安全网关',
  variant = 'sider',
}) => {
  const rawId = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const baseGradientId = `sealmail-base-${rawId}`;
  const foldGradientId = `sealmail-fold-${rawId}`;
  const wingGradientId = `sealmail-wing-${rawId}`;
  const coreGradientId = `sealmail-core-${rawId}`;
  const accentGradientId = `sealmail-accent-${rawId}`;

  const classes = [
    'sealmail-logo',
    `sealmail-logo--${variant}`,
    collapsed ? 'sealmail-logo--collapsed' : '',
    hideMark ? 'sealmail-logo--mark-hidden' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes} aria-label="SealMail 邮件安全网关">
      {!hideMark && (
        <svg
          className="sealmail-logo__mark"
          viewBox="0 0 72 72"
          role="img"
          aria-hidden="true"
          focusable="false"
        >
          <defs>
            <linearGradient id={baseGradientId} x1="8" y1="7" x2="63" y2="66" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#0f172a" />
              <stop offset="0.48" stopColor="#123b82" />
              <stop offset="1" stopColor="#1677ff" />
            </linearGradient>
            <linearGradient id={foldGradientId} x1="18" y1="22" x2="52" y2="51" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#e0f2fe" />
              <stop offset="1" stopColor="#93c5fd" />
            </linearGradient>
            <linearGradient id={wingGradientId} x1="20" y1="19" x2="55" y2="48" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#ffffff" />
              <stop offset="0.52" stopColor="#dbeafe" />
              <stop offset="1" stopColor="#60a5fa" />
            </linearGradient>
            <linearGradient id={coreGradientId} x1="28" y1="28" x2="45" y2="46" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#38bdf8" />
              <stop offset="1" stopColor="#1677ff" />
            </linearGradient>
            <radialGradient id={accentGradientId} cx="0" cy="0" r="1" gradientTransform="matrix(18 22 -22 18 24 17)" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#ffffff" />
              <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
            </radialGradient>
          </defs>

          <rect x="7" y="7" width="58" height="58" rx="18" fill={`url(#${baseGradientId})`} />
          <rect x="7" y="7" width="58" height="58" rx="18" fill={`url(#${accentGradientId})`} opacity="0.42" />
          <path d="M12.5 51.5 58 19.5" stroke="#ffffff" strokeOpacity="0.1" strokeWidth="12" strokeLinecap="round" />
          <rect x="8.5" y="8.5" width="55" height="55" rx="16.5" fill="none" stroke="#ffffff" strokeOpacity="0.14" strokeWidth="1.8" />
          <path
            d="M17.5 29.8 54.8 18.4c1.6-.5 2.9 1.2 2.1 2.7L39.3 54c-.8 1.5-3 1.1-3.3-.6L32.7 39 18.4 33c-1.5-.6-1.5-2.7-.9-3.2Z"
            fill={`url(#${wingGradientId})`}
          />
          <path
            d="m32.7 39 23.5-18.5-17 33.5"
            fill={`url(#${foldGradientId})`}
            opacity="0.96"
          />
          <path
            d="m32.7 39 23.5-18.5"
            fill="none"
            stroke="#1d4ed8"
            strokeLinecap="round"
            strokeWidth="2.4"
            opacity="0.42"
          />
          <path
            d="M25.2 43.7h-7.4M22.8 49.2h-4.6"
            fill="none"
            stroke="#93c5fd"
            strokeLinecap="round"
            strokeWidth="2.8"
            opacity="0.76"
          />
          <circle cx="38.3" cy="39.5" r="6.6" fill={`url(#${coreGradientId})`} />
          <circle cx="38.3" cy="39.5" r="9.6" fill="none" stroke="#bfdbfe" strokeOpacity="0.28" strokeWidth="1.5" />
          <path
            d="m35.4 39.6 2 2 4-4.6"
            fill="none"
            stroke="#ffffff"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2.2"
          />
        </svg>
      )}

      {!collapsed && (
        <span className="sealmail-logo__copy" aria-hidden="true">
          <strong className="sealmail-logo__wordmark">
            Seal<span>Mail</span>
          </strong>
          {subtitle && <span className="sealmail-logo__subtitle">{subtitle}</span>}
        </span>
      )}
    </div>
  );
};

export default SealMailLogo;
