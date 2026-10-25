import { useId } from 'react';

/* EduTrace logo mark: mortarboard over a circuit-style "E", inside a navy disc. Neutral artwork, not an official emblem. */
export function LogoMark({ size = 60, title = 'EduTrace logo', decorative = false, flat = false }) {
  const gid = 'lg' + useId().replace(/:/g, '');
  const a11y = decorative ? { 'aria-hidden': true } : { role: 'img', 'aria-label': title };
  return (
    <svg viewBox="0 0 200 200" width={size} height={size} focusable="false" {...a11y}>
      <defs>
        <linearGradient id={gid} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#12284d" />
          <stop offset="1" stopColor="#1f4e9c" />
        </linearGradient>
      </defs>
      <circle cx="100" cy="100" r="98" fill={flat ? '#183564' : `url(#${gid})`} />
      <circle cx="100" cy="100" r="89" fill="none" stroke="#b9c7e6" strokeWidth="2" />
      <path d="M76 78V94Q100 106 124 94V78L100 88Z" fill="#bcd0ee" />
      <path d="M58 68L100 50L142 68L100 86Z" fill="#fff" />
      <path d="M138 70V94" stroke="#fff" strokeWidth="2.5" />
      <circle cx="138" cy="97" r="3.5" fill="#fff" />
      <path d="M76 112V164" stroke="#fff" strokeWidth="8" strokeLinecap="round" />
      <path d="M76 114H104L112 106H119" stroke="#fff" strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="126" cy="106" r="6" stroke="#fff" strokeWidth="4" fill="none" />
      <path d="M76 138H116" stroke="#fff" strokeWidth="6" fill="none" strokeLinecap="round" />
      <circle cx="124" cy="138" r="6" stroke="#fff" strokeWidth="4" fill="none" />
      <path d="M76 162H112" stroke="#fff" strokeWidth="6" fill="none" strokeLinecap="round" />
      <circle cx="120" cy="162" r="6" stroke="#fff" strokeWidth="4" fill="none" />
    </svg>
  );
}

export function Wordmark({ size = 34, light = false }) {
  return (
    <span className="wordmark" style={{ fontSize: size }}>
      <span className={light ? 'wm-a light' : 'wm-a'}>Edu</span>
      <span className={light ? 'wm-b light' : 'wm-b'}>Trace</span>
    </span>
  );
}

export const TAGLINE = 'APAAR ID · AICTE · Verified Learning Journey';
