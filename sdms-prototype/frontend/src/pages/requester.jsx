import { useState } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { api } from '../api.js';
import { Badge, Loading, Msg, PageHead, Panel, TierBadge, fmtDate, useLoad } from '../components/ui.jsx';
import { FIELD_LABELS } from './student.jsx';

export function RequestNew() {
  const me = useLoad(() => api('/api/access/me'));
  const [f, setF] = useState({ apaarId: '', purpose: '', fields: ['enrolled'], durationDays: 1 });
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const m = me.data;
  const toggle = (k) => setF({ ...f, fields: f.fields.includes(k) ? f.fields.filter((x) => x !== k) : [...f.fields, k] });
  async function submit(e) {
    e.preventDefault(); setErr(''); setMsg(''); setBusy(true);
    try {
      const r = await api('/api/access/requests', { method: 'POST', body: { ...f, durationDays: Number(f.durationDays) } });
      setMsg(r.message);
      setF({ ...f, apaarId: '', purpose: '' });
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  return (
    <>
      <PageHead title="Request access to a student's details" sub="Your request appears in the student's own profile and they decide. Nothing is shown until they approve." />
      <Loading state={me} />
      {m && (
        <div className="center-col">
          <p>Signed in as <strong>{m.fullName}</strong>{m.organisation ? `, ${m.organisation}` : ''} <TierBadge tier={m.tier} /></p>
          {m.tier !== 'VERIFIED' && (
            <Msg kind="warn" title="Limited access. ">As an unverified individual you can ask only whether a student is enrolled and at which institution, for up to {m.maxDays} day, and send a small number of requests per day.
              Organisations can ask an AICTE administrator to verify them for fuller access.</Msg>
          )}
          <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
          <form className="panel" onSubmit={submit}><div className="panel-body">
            <div className="field"><label htmlFor="ap">Student's APAAR ID <span className="hint">(12 digits)</span></label>
              <input id="ap" type="text" inputMode="numeric" maxLength={12} value={f.apaarId} onChange={(e) => setF({ ...f, apaarId: e.target.value.replace(/\D/g, '') })} required /></div>
            <div className="field"><label htmlFor="pu">Purpose <span className="hint">The student will read this, so be specific.</span></label>
              <textarea id="pu" maxLength={400} value={f.purpose} onChange={(e) => setF({ ...f, purpose: e.target.value })} required /></div>
            <fieldset style={{ border: 0, padding: 0, margin: '0 0 .9rem' }}>
              <legend style={{ fontWeight: 600, marginBottom: '.25rem' }}>What do you need to see?</legend>
              <div className="checks">
                {m.allowedFields.map((k) => (
                  <label key={k}><input type="checkbox" checked={f.fields.includes(k)} onChange={() => toggle(k)} />{FIELD_LABELS[k] || k}</label>
                ))}
              </div>
            </fieldset>
            <div className="field" style={{ maxWidth: 220 }}><label htmlFor="du">For how long</label>
              <select id="du" value={f.durationDays} onChange={(e) => setF({ ...f, durationDays: e.target.value })}>
                {[1, 3, 7, 14, 30].filter((n) => n <= m.maxDays).map((n) => <option key={n} value={n}>{n} day{n > 1 ? 's' : ''}</option>)}
              </select></div>
            <button className="btn" disabled={busy || f.fields.length === 0}>Send request</button>
          </div></form>
        </div>
      )}
    </>
  );
}

function DataView({ id }) {
  const st = useLoad(() => api(`/api/access/requests/${id}/data`));
  const d = st.data;
  return (
    <div style={{ marginTop: '.75rem' }}>
      <Loading state={st} />
      {d && (
        <div className="grid2">
          <div>
            <h3>Details shared with you</h3>
            <div className="tablewrap"><table>
              <caption className="sr">Shared details</caption>
              <tbody>
                {'enrolled' in d.payload && <tr><th scope="row">Currently enrolled</th><td>{d.payload.enrolled ? 'Yes' : 'No'}</td></tr>}
                {'name' in d.payload && <tr><th scope="row">Name</th><td>{d.payload.name}</td></tr>}
              </tbody>
            </table></div>
            {d.payload.records && d.payload.records.length > 0 && (
              <div className="tablewrap" style={{ marginTop: '.5rem' }}><table>
                <caption className="sr">Enrolment records</caption>
                <thead><tr>{Object.keys(d.payload.records[0]).map((k) => <th key={k} scope="col">{FIELD_LABELS[k] ? k.replace(/([A-Z])/g, ' $1') : k.replace(/([A-Z])/g, ' $1')}</th>)}</tr></thead>
                <tbody>{d.payload.records.map((r, i) => <tr key={i}>{Object.values(r).map((v, j) => <td key={j}>{String(v)}</td>)}</tr>)}</tbody>
              </table></div>
            )}
            <p className="muted small" style={{ marginTop: '.5rem' }}>Valid until {fmtDate(d.payload.validUntil)}. Each view is recorded in the student's activity log.</p>
          </div>
          <div>
            <h3>Signed confirmation</h3>
            <QRCodeSVG value={window.location.origin + d.verifyPath} size={128} level="M" />
            <p className="muted small">Anyone can scan this to check the details above were issued by this system and not edited. It stops working when access ends.</p>
          </div>
        </div>
      )}
    </div>
  );
}

export function RequestList() {
  const st = useLoad(() => api('/api/access/requests'));
  const [open, setOpen] = useState(null);
  return (
    <>
      <PageHead title="My requests" sub="Status of every request you have sent. Approved requests can be opened until access ends.">
        <button className="btn secondary" onClick={st.reload}>Refresh</button>
      </PageHead>
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">My requests</caption>
            <thead><tr><th scope="col">Sent</th><th scope="col">APAAR ID</th><th scope="col">Purpose</th><th scope="col">Status</th><th scope="col">Access</th></tr></thead>
            <tbody>
              {st.data.map((r) => (
                <tr key={r.id}>
                  <td>{fmtDate(r.createdAt)}</td>
                  <td><code>{r.apaarId}</code></td>
                  <td className="wrapcell">{r.purpose}<br /><span className="muted small">Asked for: {r.requestedFields.map((f) => FIELD_LABELS[f] || f).join('; ')}</span></td>
                  <td><Badge value={r.status} />{r.status === 'PENDING' && <><br /><span className="small muted">Waiting for the student</span></>}</td>
                  <td className="wrapcell">
                    {r.status === 'APPROVED' ? (
                      <>
                        <div className="small">Until {fmtDate(r.expiresAt)}</div>
                        <button className="btn small" onClick={() => setOpen(open === r.id ? null : r.id)}>{open === r.id ? 'Hide' : 'View details'}</button>
                        {open === r.id && <DataView id={r.id} />}
                      </>
                    ) : <span className="muted small">{r.status === 'DENIED' ? 'The student declined or did not respond.' : r.status === 'PENDING' ? '' : 'No access'}</span>}
                  </td>
                </tr>
              ))}
              {st.data.length === 0 && <tr><td colSpan={5} className="muted">You have not sent any requests.</td></tr>}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}
