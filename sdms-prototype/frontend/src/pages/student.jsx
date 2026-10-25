import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { LogoMark, Wordmark } from '../components/Logo.jsx';
import { api } from '../api.js';
import { Badge, Loading, Msg, PageHead, Panel, TierBadge, fmtDate, fmtDay, useLoad } from '../components/ui.jsx';

export const FIELD_LABELS = {
  enrolled: 'Whether you are currently enrolled',
  institution: 'Name of your institution',
  course: 'Your course',
  admissionYear: 'Year of admission',
  status: 'Enrolment status (active, withdrawn, graduated)',
  confirmation: 'Whether you have confirmed the record',
  name: 'Your name',
};

export function StudentRecords() {
  const st = useLoad(() => api('/api/student/records'));
  const [open, setOpen] = useState(null);
  const [reason, setReason] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  async function confirm(id) {
    setErr(''); setMsg('');
    try { const r = await api(`/api/student/records/${id}/confirm`, { method: 'POST' }); setMsg(r.message); st.reload(); } catch (e) { setErr(e.message); }
  }
  async function dispute(e, id) {
    e.preventDefault(); setErr(''); setMsg('');
    try {
      const r = await api(`/api/student/records/${id}/dispute`, { method: 'POST', body: { reason } });
      setMsg(r.message); setOpen(null); setReason(''); st.reload();
    } catch (ex) { setErr(ex.message); }
  }
  const d = st.data;
  return (
    <>
      <PageHead title="My records" sub="The enrolment entries institutions have made against your APAAR ID. Please check them and confirm or report a mismatch." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <Loading state={st} />
      {d && (
        <>
          <Panel title="Your details (from the APAAR registry)">
            <dl className="kv">
              <dt className="muted">APAAR ID</dt><dd style={{ margin: 0 }}><code>{d.apaarId}</code></dd>
              <dt className="muted">Name</dt><dd style={{ margin: 0 }}>{d.name}</dd>
              <dt className="muted">Date of birth</dt><dd style={{ margin: 0 }}>{fmtDay(d.dob)}</dd>
              <dt className="muted">Email on record</dt><dd style={{ margin: 0 }}>{d.email}</dd>
            </dl>
          </Panel>
          {d.openDuplicateCases > 0 && (
            <Msg kind="warn" title="Two enrolments found. ">Your APAAR ID is listed as actively enrolled at two institutions. Both institutions and AICTE have been asked to resolve this. If one of them is wrong, report a mismatch on that row.</Msg>
          )}
          <Panel body={false}>
            <div className="tablewrap"><table>
              <caption className="sr">My enrolment records</caption>
              <thead><tr><th scope="col">Institution</th><th scope="col">Course</th><th scope="col">Year</th><th scope="col">Status</th><th scope="col">Your confirmation</th><th scope="col">Action</th></tr></thead>
              <tbody>
                {d.records.map((r) => (
                  <tr key={r.id}>
                    <td>{r.institution}{r.duplicateFlag && <><br /><Badge value="HIGH" label="Duplicate case open" /></>}</td>
                    <td>{r.course}<br /><span className="muted small">{r.rollNo}</span></td>
                    <td>{r.admissionYear}</td>
                    <td><Badge value={r.status} /></td>
                    <td><Badge value={r.confirmationStatus} />{r.confirmationStatus === 'DISPUTED' && r.confirmationNote && <><br /><span className="small muted">{r.confirmationNote}</span></>}</td>
                    <td className="wrapcell">
                      {r.status === 'WITHDRAWN' ? <span className="muted small">No action needed</span> : (
                        <div className="actions">
                          {r.confirmationStatus !== 'VERIFIED' && <button className="btn small" onClick={() => confirm(r.id)}>Confirm</button>}
                          {r.confirmationStatus !== 'DISPUTED' && <button className="btn small secondary" onClick={() => { setOpen(open === r.id ? null : r.id); setReason(''); }}>Report a mismatch</button>}
                        </div>
                      )}
                      {open === r.id && (
                        <form onSubmit={(e) => dispute(e, r.id)} style={{ marginTop: '.6rem' }}>
                          <label htmlFor={'rs' + r.id}>What is wrong?</label>
                          <textarea id={'rs' + r.id} value={reason} onChange={(e) => setReason(e.target.value)} required />
                          <div className="actions" style={{ marginTop: '.4rem' }}><button className="btn small">Send to AICTE</button></div>
                        </form>
                      )}
                    </td>
                  </tr>
                ))}
                {d.records.length === 0 && <tr><td colSpan={6} className="muted">No institution has recorded an enrolment against your APAAR ID.</td></tr>}
              </tbody>
            </table></div>
          </Panel>
        </>
      )}
    </>
  );
}

function RequestCard({ r, highlight, onChanged, setMsg, setErr }) {
  const allowed = r.allowedFields || [];
  const offered = r.requestedFields.filter((f) => allowed.includes(f));
  const [fields, setFields] = useState(offered);
  const maxDays = Math.min(r.durationDays, r.tier === 'VERIFIED' ? 30 : 1);
  const [days, setDays] = useState(maxDays);
  useEffect(() => { if (highlight) document.getElementById('req' + r.id)?.scrollIntoView({ block: 'center' }); }, [highlight, r.id]);

  async function decide(approve) {
    setErr(''); setMsg('');
    try {
      await api(`/api/student/requests/${r.id}/decision`, { method: 'POST', body: { approve, fields, durationDays: Number(days) } });
      setMsg(approve ? 'Access approved for the fields and period you chose.' : 'Request denied. Nothing was shared.');
      onChanged();
    } catch (e) { setErr(e.message); }
  }
  async function revoke() {
    setErr(''); setMsg('');
    try { await api(`/api/student/requests/${r.id}/revoke`, { method: 'POST' }); setMsg('Access revoked.'); onChanged(); } catch (e) { setErr(e.message); }
  }
  async function block() {
    if (!window.confirm(`Block ${r.requesterName}? Their pending and active requests will be withdrawn and future requests denied.`)) return;
    setErr(''); setMsg('');
    try { await api('/api/student/blocks', { method: 'POST', body: { requesterId: r.requesterId } }); setMsg('Requester blocked.'); onChanged(); } catch (e) { setErr(e.message); }
  }
  const toggle = (f) => setFields(fields.includes(f) ? fields.filter((x) => x !== f) : [...fields, f]);

  return (
    <article className={'req-card' + (highlight ? ' hl' : '')} id={'req' + r.id}>
      <header>
        <div>
          <strong>{r.requesterName}</strong>{r.organisation ? ` - ${r.organisation}` : ''} <TierBadge tier={r.tier} />
        </div>
        <div><Badge value={r.status} /> <span className="muted small">Asked {fmtDate(r.createdAt)}</span></div>
      </header>
      <div className="body">
        <p><strong>Purpose:</strong> {r.purpose}</p>
        {r.tier !== 'VERIFIED' && r.status === 'PENDING' && (
          <Msg kind="warn" title="Unverified individual. ">Their identity has been checked by email only. They can be offered only whether you are enrolled and at which institution, for one day at most.</Msg>
        )}
        {r.status === 'PENDING' ? (
          <>
            <fieldset style={{ border: 0, padding: 0, margin: '0 0 .75rem' }}>
              <legend style={{ fontWeight: 600, marginBottom: '.25rem' }}>Choose what to share</legend>
              <div className="checks">
                {r.requestedFields.map((f) => {
                  const ok = allowed.includes(f);
                  return (
                    <label key={f}>
                      <input type="checkbox" disabled={!ok} checked={fields.includes(f)} onChange={() => toggle(f)} />
                      {FIELD_LABELS[f] || f}{!ok && <span className="muted small"> (not available at their trust level)</span>}
                    </label>
                  );
                })}
              </div>
            </fieldset>
            <div className="row" style={{ marginBottom: '.75rem' }}>
              <div className="field" style={{ maxWidth: 220 }}>
                <label htmlFor={'d' + r.id}>Share for</label>
                <select id={'d' + r.id} value={days} onChange={(e) => setDays(e.target.value)}>
                  {Array.from({ length: maxDays }, (_, i) => i + 1).filter((n) => maxDays <= 7 || [1, 3, 7, 14, 30].includes(n) || n === maxDays).map((n) => (
                    <option key={n} value={n}>{n} day{n > 1 ? 's' : ''}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="actions">
              <button className="btn" onClick={() => decide(true)} disabled={fields.length === 0}>Approve</button>
              <button className="btn secondary" onClick={() => decide(false)}>Deny</button>
              <button className="btn secondary" onClick={block}>Deny and block</button>
            </div>
            <p className="muted small" style={{ marginTop: '.5rem' }}>If you do nothing, this request expires on {fmtDate(r.decideBy)} and nothing is shared.</p>
          </>
        ) : (
          <>
            <p className="small">Fields: {(r.approvedFields.length ? r.approvedFields : r.requestedFields).map((f) => FIELD_LABELS[f] || f).join('; ')}</p>
            {r.status === 'APPROVED' && <p className="small">Access ends {fmtDate(r.expiresAt)}. Viewed {r.viewCount} time(s).</p>}
            <div className="actions">
              {r.status === 'APPROVED' && <button className="btn secondary small" onClick={revoke}>Revoke access</button>}
              {!r.blocked && <button className="btn secondary small" onClick={block}>Block requester</button>}
              {r.blocked && <span className="badge b-grey">Blocked</span>}
            </div>
          </>
        )}
      </div>
    </article>
  );
}

export function StudentRequests() {
  const { id } = useParams();
  const st = useLoad(() => api('/api/student/requests'));
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const pending = (st.data || []).filter((r) => r.status === 'PENDING');
  const others = (st.data || []).filter((r) => r.status !== 'PENDING');
  return (
    <>
      <PageHead title="Access requests" sub="Nobody can see your details unless you approve. You choose what is shared and for how long." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <Loading state={st} />
      {st.data && (
        <>
          <h2>Waiting for your decision ({pending.length})</h2>
          {pending.length === 0 && <p className="muted">No requests are waiting.</p>}
          {pending.map((r) => <RequestCard key={r.id} r={r} highlight={String(r.id) === id} onChanged={st.reload} setMsg={setMsg} setErr={setErr} />)}
          <h2 style={{ marginTop: '1.5rem' }}>Earlier requests ({others.length})</h2>
          {others.map((r) => <RequestCard key={r.id} r={r} highlight={String(r.id) === id} onChanged={st.reload} setMsg={setMsg} setErr={setErr} />)}
        </>
      )}
    </>
  );
}

export function StudentActivity() {
  const blocked = useLoad(() => api('/api/student/blocked'));
  const log = useLoad(() => api('/api/student/access-log'));
  const [err, setErr] = useState('');
  async function unblock(id) {
    try { await api(`/api/student/blocks/${id}`, { method: 'DELETE' }); blocked.reload(); } catch (e) { setErr(e.message); }
  }
  return (
    <>
      <PageHead title="Activity" sub="Every request, decision and view involving your APAAR ID." />
      <Msg kind="err">{err}</Msg>
      <Panel title="Blocked requesters" body={false}>
        <Loading state={blocked} />
        {blocked.data && (
          <div className="tablewrap"><table>
            <caption className="sr">Blocked requesters</caption>
            <thead><tr><th scope="col">Requester</th><th scope="col">Organisation</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {blocked.data.map((b) => (<tr key={b.id}><td>{b.requesterName}</td><td>{b.organisation || '-'}</td><td><button className="btn small secondary" onClick={() => unblock(b.id)}>Unblock</button></td></tr>))}
              {blocked.data.length === 0 && <tr><td colSpan={3} className="muted">You have not blocked anyone.</td></tr>}
            </tbody>
          </table></div>
        )}
      </Panel>
      <Panel title="Access log" body={false}>
        <Loading state={log} />
        {log.data && (
          <div className="tablewrap"><table>
            <caption className="sr">Access log</caption>
            <thead><tr><th scope="col">When</th><th scope="col">By</th><th scope="col">What happened</th><th scope="col">Details</th></tr></thead>
            <tbody>
              {log.data.map((a) => (<tr key={a.id}><td>{fmtDate(a.createdAt)}</td><td>{a.actor === (JSON.parse(localStorage.getItem('sdms_user') || '{}').apaarId) ? 'You' : a.actor}<br /><span className="muted small">{a.role}</span></td><td>{a.action.replaceAll('_', ' ').toLowerCase()}</td><td className="wrapcell small">{a.details}</td></tr>))}
              {log.data.length === 0 && <tr><td colSpan={4} className="muted">Nothing has happened yet.</td></tr>}
            </tbody>
          </table></div>
        )}
      </Panel>
    </>
  );
}

export function StudentCertificate() {
  const recs = useLoad(() => api('/api/student/records'));
  const [sel, setSel] = useState('');
  const cert = useLoad(() => (sel ? api(`/api/student/certificate/${sel}`) : Promise.resolve(null)), [sel]);
  const list = (recs.data ? recs.data.records : []).filter((r) => r.status !== 'WITHDRAWN');
  useEffect(() => { if (!sel && list.length) setSel(String(list[0].id)); }, [list.length]); // eslint-disable-line
  const c = cert.data;
  return (
    <>
      <PageHead title="Enrolment record with APAAR ID" sub="Shows how a degree-awarding body prints the APAAR ID beside the institution's own student number, with a QR code that checks the record.">
        {c && <button className="btn noprint" onClick={() => window.print()}>Print</button>}
      </PageHead>
      <Loading state={recs} />
      {recs.data && list.length === 0 && <p className="muted">You have no active enrolment records.</p>}
      {list.length > 1 && (
        <div className="field noprint" style={{ maxWidth: 420 }}>
          <label htmlFor="cr">Choose a record</label>
          <select id="cr" value={sel} onChange={(e) => setSel(e.target.value)}>
            {list.map((r) => <option key={r.id} value={r.id}>{r.course}, {r.institution} ({r.admissionYear})</option>)}
          </select>
        </div>
      )}
      {c && (
        <>
        <div className="cert" id="cert">
          <div className="cert-frame">
            <div className="cert-wm" aria-hidden="true"><LogoMark size={340} decorative flat /></div>
            <header className="cert-head">
              <LogoMark size={64} decorative />
              <div className="cert-brand"><Wordmark size={30} /><span>APAAR ID &middot; AICTE &middot; Verified Learning Journey</span></div>
            </header>
            <h2>Enrolment Verification Certificate</h2>
            <p className="cert-lead">This is to certify that</p>
            <p className="cert-name">{c.studentName}</p>
            <p className="cert-lead">is enrolled in the following programme and is recorded against the APAAR ID shown below.</p>
            <div className="ids">
              <div><span>APAAR ID</span><strong>{c.payload.apaarId}</strong></div>
              <div><span>Student / enrolment number</span><strong>{c.payload.enrolmentNo || 'Not recorded'}</strong></div>
            </div>
            <dl>
              <dt>Institution</dt><dd>{c.payload.institution}</dd>
              <dt>Programme</dt><dd>{c.payload.course}</dd>
              <dt>Admission year</dt><dd>{c.payload.admissionYear}</dd>
            </dl>
            <div className="foot">
              <div className="cert-seal">
                <svg width="74" height="74" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
                  <circle cx="50" cy="50" r="46" fill="none" stroke="#17632d" strokeWidth="3" />
                  <circle cx="50" cy="50" r="38" fill="none" stroke="#17632d" strokeWidth="1.2" strokeDasharray="3 3" />
                  <path d="M33 52l12 12 22-26" fill="none" stroke="#17632d" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <div><strong>Digitally signed</strong><span>Genuine only if the QR check says so</span></div>
              </div>
              <div className="cert-qr">
                <QRCodeSVG value={window.location.origin + c.verifyPath} size={116} level="M" />
                <span>Scan to verify this record</span>
              </div>
            </div>
          </div>
        </div>
        <div className="noprint center-col" style={{ marginTop: '-.75rem' }}>
          <label htmlFor="vlink" style={{ fontWeight: 600, fontSize: '.875rem' }}>Verification link</label>
          <div style={{ display: 'flex', gap: '.5rem', marginTop: '.3rem' }}>
            <input id="vlink" readOnly value={window.location.origin + c.verifyPath} style={{ flex: 1, minWidth: 0 }} onFocus={(e) => e.target.select()} />
            <button type="button" className="btn small" onClick={() => navigator.clipboard && navigator.clipboard.writeText(window.location.origin + c.verifyPath)}>Copy</button>
          </div>
        </div>
        </>
      )}
    </>
  );
}
