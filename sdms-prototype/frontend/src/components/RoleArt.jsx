import { memo } from 'react';

/* Flat illustrations for the four user types. Shared by the home page and the sign-in page. */
const Art = ({ children }) => (
  <svg viewBox="0 0 200 110" className="hm-role-art" aria-hidden="true" focusable="false">
    <rect width="200" height="110" fill="#f2f6fc" />{children}
  </svg>
);

const ART = {
  institution: (
    <Art>
      <rect x="40" y="46" width="120" height="50" fill="#fff" stroke="#183564" strokeWidth="2" /><path d="M30 46L100 16L170 46Z" fill="#183564" />
      {[56, 86, 116, 136].map((x) => <rect key={x} x={x - 6} y="56" width="12" height="40" fill="#e6eef9" stroke="#1f4e9c" />)}
      <rect x="92" y="70" width="16" height="26" fill="#1f4e9c" /><circle cx="100" cy="34" r="5" fill="#f2b84b" /></Art>
  ),
  admin: (
    <Art>
      <rect x="30" y="20" width="140" height="74" rx="4" fill="#fff" stroke="#183564" strokeWidth="2" />
      {[[48, 60], [72, 44], [96, 52], [120, 34], [144, 46]].map(([x, y]) => <rect key={x} x={x} y={y} width="14" height={90 - y} fill="#1f4e9c" />)}
      <circle cx="152" cy="30" r="9" fill="#a11a1a" /><path d="M152 25v6M152 34v.5" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" /></Art>
  ),
  student: (
    <Art>
      <circle cx="100" cy="38" r="16" fill="#1f4e9c" /><path d="M64 96c2-22 16-32 36-32s34 10 36 32z" fill="#183564" />
      <path d="M80 28L100 19L120 28L100 37Z" fill="#fff" stroke="#12284d" /><rect x="136" y="56" width="34" height="26" rx="3" fill="#fff" stroke="#17632d" strokeWidth="2" /><path d="M144 69l5 5 10-11" fill="none" stroke="#17632d" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" /></Art>
  ),
  requester: (
    <Art>
      <rect x="50" y="42" width="100" height="52" rx="4" fill="#fff" stroke="#183564" strokeWidth="2" /><path d="M80 42v-8a6 6 0 016-6h28a6 6 0 016 6v8" fill="none" stroke="#183564" strokeWidth="2" />
      <path d="M50 64h100" stroke="#183564" strokeWidth="2" /><rect x="92" y="58" width="16" height="12" fill="#f2b84b" stroke="#183564" />
      <circle cx="152" cy="30" r="13" fill="#e6f4ea" stroke="#17632d" strokeWidth="2" /><path d="M146 30l5 5 8-10" fill="none" stroke="#17632d" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" /></Art>
  ),
};

function RoleArt({ kind }) {
  return ART[kind] || null;
}
export default memo(RoleArt);
