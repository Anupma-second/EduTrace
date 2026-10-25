import { useState, useEffect } from 'react';

export function fmtDate(iso) {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function fmtDay(iso) {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function num(n) {
  return n === null || n === undefined ? '-' : Number(n).toLocaleString('en-IN');
}

const LABELS = {
  VERIFIED: ['Verified', 'b-green'], PENDING: ['Pending', 'b-amber'], DISPUTED: ['Disputed', 'b-red'],
  UNCONFIRMED: ['Unconfirmed', 'b-grey'], REVIEWED: ['Reviewed', 'b-blue'], NOT_REQUIRED: ['Not required', 'b-grey'],
  OPEN: ['Open', 'b-amber'], CLOSED: ['Closed', 'b-grey'], AUTO_CLOSED: ['Auto-closed', 'b-grey'], RESOLVED: ['Resolved', 'b-green'],
  HIGH: ['High', 'b-red'], MEDIUM: ['Medium', 'b-amber'], LOW: ['Low', 'b-green'],
  APPROVED: ['Approved', 'b-green'], DENIED: ['Denied', 'b-red'], EXPIRED: ['Expired', 'b-grey'], REVOKED: ['Revoked', 'b-grey'],
  VALID: ['Valid', 'b-green'], MISSING: ['Missing', 'b-red'], INVALID_FORMAT: ['Invalid format', 'b-red'],
  NOT_FOUND: ['Not found', 'b-red'], MISMATCH: ['Mismatch', 'b-red'],
  ACTIVE: ['Active', 'b-green'], WITHDRAWN: ['Withdrawn', 'b-grey'], GRADUATED: ['Graduated', 'b-blue'],
  OVER_LIMIT: ['Over limit', 'b-red'], FULL: ['Full', 'b-green'], NO_DATA: ['No data yet', 'b-grey'],
  INTAKE_EXCEEDED: ['Above approved intake', 'b-red'], SUPERNUMERARY_EXCEEDED: ['Supernumerary above limit', 'b-amber'],
  ENROLMENT_SPIKE: ['Enrolment spike', 'b-amber'], ERROR: ['Error', 'b-red'], WARNING: ['Warning', 'b-amber'],
  TIER_VERIFIED: ['Verified organisation', 'b-green'], TIER_GENERAL: ['Unverified individual', 'b-amber'],
};

export function Badge({ value, label }) {
  const [text, cls] = LABELS[value] || [value, 'b-grey'];
  return <span className={'badge ' + cls}>{label || text}</span>;
}

export function TierBadge({ tier }) {
  return <Badge value={tier === 'VERIFIED' ? 'TIER_VERIFIED' : 'TIER_GENERAL'} />;
}

export function Msg({ kind = 'info', title, children }) {
  if (!children) return null;
  const role = kind === 'err' ? 'alert' : 'status';
  return (
    <div className={'msg ' + kind} role={role}>
      {title && <strong>{title}</strong>}
      {children}
    </div>
  );
}

export function Meter({ pct, over, full }) {
  const w = Math.max(0, Math.min(100, pct || 0));
  return (
    <span className={'meter' + (over ? ' over' : full ? ' full' : '')} aria-hidden="true">
      <i style={{ width: w + '%' }} />
    </span>
  );
}

export function Panel({ title, actions, children, body = true }) {
  return (
    <section className="panel">
      {title && <h2>{title}{actions && <span className="actions">{actions}</span>}</h2>}
      {body ? <div className="panel-body">{children}</div> : children}
    </section>
  );
}

export function PageHead({ title, sub, children }) {
  return (
    <div className="pagehead">
      <div>
        <h1>{title}</h1>
        {sub && <p className="muted">{sub}</p>}
      </div>
      {children && <div className="actions">{children}</div>}
    </div>
  );
}

function CountUp({ value }) {
  const raw = typeof value === 'number' ? String(value) : typeof value === 'string' ? value : null;
  const ok = raw !== null && /^[\d,]+$/.test(raw);
  const target = ok ? Number(raw.replace(/,/g, '')) : 0;
  const [n, setN] = useState(ok ? 0 : target);
  useEffect(() => {
    if (!ok) return undefined;
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setN(target); return undefined; }
    let raf; const t0 = performance.now(); const dur = 700;
    const step = (t) => { const p = Math.min(1, (t - t0) / dur); setN(Math.round(target * (1 - Math.pow(1 - p, 3)))); if (p < 1) raf = requestAnimationFrame(step); };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [ok, target]);
  if (!ok) return value;
  return Number(n).toLocaleString('en-IN');
}

export function Facts({ items, cols }) {
  return (
    <dl className={'facts' + (cols ? ' c' + cols : '')}>
      {items.map((f) => (
        <div className={'fact' + (f.warn ? ' warn' : '')} key={f.label}>
          <dt>{f.label}</dt>
          <dd><CountUp value={f.value} />{f.sub && <small> {f.sub}</small>}</dd>
        </div>
      ))}
    </dl>
  );
}

export function useLoad(loader, deps = []) {
  const [state, setState] = useState({ loading: true, data: null, error: '' });
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: '' }));
    loader()
      .then((data) => alive && setState({ loading: false, data, error: '' }))
      .catch((e) => alive && setState({ loading: false, data: null, error: e.message }));
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);
  return { ...state, reload: () => setTick((t) => t + 1) };
}

export function Loading({ state }) {
  if (state.loading && !state.data) return <p className="muted" role="status">Loading...</p>;
  if (state.error) return <Msg kind="err">{state.error}</Msg>;
  return null;
}
