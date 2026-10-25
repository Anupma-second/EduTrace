import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, download } from '../api.js';
import { Badge, Facts, Loading, Meter, Msg, PageHead, Panel, fmtDate, fmtDay, num, useLoad } from '../components/ui.jsx';

export function AdminDashboard() {
  const ov = useLoad(() => api('/api/admin/overview'));
  const inst = useLoad(() => api('/api/admin/institutions'));
  const wl = useLoad(() => api('/api/admin/watchlist'));
  const al = useLoad(() => api('/api/admin/alerts'));
  const o = ov.data;
  const conf = o ? o.confirmation : {};
  const confTotal = o ? Object.values(conf).reduce((a, b) => a + b, 0) : 0;
  return (
    <>
      <PageHead title="AICTE dashboard" sub={o ? `Admissions for the ${o.admissionYear} intake, computed from the records submitted so far.` : ''} />
      <Loading state={ov} />
      {o && (
        <>
          <Facts cols={4} items={[
            { label: 'Institutions', value: num(o.institutions) },
            { label: 'Approved intake', value: num(o.approvedIntake) },
            { label: 'Regular seats filled', value: num(o.regularEnrolled), sub: `(${o.fillPct}%)` },
            { label: 'Supernumerary seats filled', value: num(o.supernumeraryEnrolled) },
            { label: 'Institutions above intake', value: num(o.institutionsAboveIntake), warn: o.institutionsAboveIntake > 0 },
            { label: 'Open enrolment alerts', value: num(o.openAlerts), warn: o.openAlerts > 0 },
            { label: 'Open duplicate cases', value: num(o.openDuplicateCases), warn: o.openDuplicateCases > 0 },
            { label: 'Records with APAAR problems', value: num(o.apaarIssues), warn: o.apaarIssues > 0 },
          ]} />
        </>
      )}

      <div className="grid2">
        <Panel title="Institutions to look at first" actions={<Link to="/admin/watchlist" className="small">Full watchlist</Link>} body={false}>
          <Loading state={wl} />
          {wl.data && (
            <div className="tablewrap"><table>
              <caption className="sr">Highest risk scores</caption>
              <thead><tr><th scope="col">Institution</th><th scope="col" className="num">Score</th><th scope="col">Level</th></tr></thead>
              <tbody>
                {wl.data.filter((w) => w.score > 0).slice(0, 5).map((w) => (
                  <tr key={w.institutionId}><td>{w.name}<br /><span className="muted small">{w.state}</span></td><td className="num">{w.score}</td><td><Badge value={w.level} /></td></tr>
                ))}
                {wl.data.filter((w) => w.score > 0).length === 0 && <tr><td colSpan={3} className="muted">Nothing needs attention right now.</td></tr>}
              </tbody>
            </table></div>
          )}
        </Panel>

        <Panel title="Student confirmation status" body={false}>
          <Loading state={ov} />
          {o && (
            <div className="tablewrap"><table>
              <caption className="sr">Student confirmation</caption>
              <thead><tr><th scope="col">Status</th><th scope="col" className="num">Records</th><th scope="col" className="num">Share</th></tr></thead>
              <tbody>
                {Object.entries(conf).filter(([, v]) => v > 0).map(([k, v]) => (
                  <tr key={k}><td><Badge value={k} /></td><td className="num">{num(v)}</td><td className="num">{confTotal ? Math.round((v * 1000) / confTotal) / 10 : 0}%</td></tr>
                ))}
              </tbody>
            </table></div>
          )}
        </Panel>
      </div>

      <Panel title="Open enrolment alerts" actions={<Link to="/admin/alerts" className="small">All alerts</Link>} body={false}>
        <Loading state={al} />
        {al.data && (
          <div className="tablewrap"><table>
            <caption className="sr">Open alerts</caption>
            <thead><tr><th scope="col">Type</th><th scope="col">Why it was raised</th></tr></thead>
            <tbody>
              {al.data.filter((a) => a.status === 'OPEN').slice(0, 5).map((a) => (
                <tr key={a.id}><td><Badge value={a.type} /></td><td className="wrapcell">{a.explanation}</td></tr>
              ))}
              {al.data.filter((a) => a.status === 'OPEN').length === 0 && <tr><td colSpan={2} className="muted">No open alerts.</td></tr>}
            </tbody>
          </table></div>
        )}
      </Panel>

      <Panel title="Seats filled by institution" actions={<Link to="/admin/institutions" className="small">Details</Link>} body={false}>
        <Loading state={inst} />
        {inst.data && <InstTable rows={inst.data} compact />}
      </Panel>
    </>
  );
}

function InstTable({ rows, compact }) {
  return (
    <div className="tablewrap"><table>
      <caption className="sr">Institutions</caption>
      <thead><tr>
        <th scope="col">Institution</th>{!compact && <th scope="col">State</th>}
        <th scope="col" className="num">Approved intake</th><th scope="col">Regular seats filled</th>
        <th scope="col" className="num">Supernumerary</th>{!compact && <th scope="col" className="num">Courses above intake</th>}
        {!compact && <th scope="col" className="num">APAAR problems</th>}{!compact && <th scope="col" className="num">Student-verified</th>}
      </tr></thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id}>
            <td>{r.name}<br /><span className="muted small">{r.aicteId}</span></td>
            {!compact && <td>{r.state}</td>}
            <td className="num">{num(r.approvedIntake)}</td>
            <td>{r.hasUpload ? (<><Meter pct={r.fillPct} over={r.coursesOverIntake > 0} />{num(r.regularEnrolled)} ({r.fillPct}%)</>) : <span className="muted">No admissions data submitted yet</span>}</td>
            <td className="num">{num(r.supernumeraryEnrolled)}</td>
            {!compact && <td className="num">{r.coursesOverIntake > 0 ? <strong style={{ color: 'var(--red)' }}>{r.coursesOverIntake}</strong> : 0}</td>}
            {!compact && <td className="num">{num(r.apaarIssues)}</td>}
            {!compact && <td className="num">{num(r.studentVerified)} of {num(r.records)}</td>}
          </tr>
        ))}
      </tbody>
    </table></div>
  );
}

export function AdminInstitutions() {
  const st = useLoad(() => api('/api/admin/institutions'));
  return (
    <>
      <PageHead title="Institutions" sub="Approved intake compared with students actually enrolled this admission cycle." />
      <Loading state={st} />
      {st.data && <Panel body={false}><InstTable rows={st.data} /></Panel>}
    </>
  );
}

export function AdminAlerts() {
  const [filter, setFilter] = useState('OPEN');
  const [note, setNote] = useState({});
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const st = useLoad(() => api('/api/admin/alerts'));
  async function setStatus(id, status) {
    setErr(''); setMsg('');
    try {
      await api(`/api/admin/alerts/${id}/status`, { method: 'POST', body: { status, note: note[id] || '' } });
      setMsg('Alert updated.');
      st.reload();
    } catch (e) { setErr(e.message); }
  }
  async function recompute() {
    setErr(''); setMsg('');
    try {
      const r = await api('/api/admin/alerts/recompute', { method: 'POST' });
      setMsg(`Checks re-run. ${r.activeConditions} condition(s) currently apply.`);
      st.reload();
    } catch (e) { setErr(e.message); }
  }
  const rows = (st.data || []).filter((a) => filter === 'ALL' || a.status === filter);
  return (
    <>
      <PageHead title="Enrolment alerts" sub="Raised automatically when enrolment breaks an approved limit or changes sharply. Each alert says why.">
        <button className="btn secondary" onClick={recompute}>Re-run checks</button>
      </PageHead>
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <div className="field" style={{ maxWidth: 260 }}>
        <label htmlFor="f">Show</label>
        <select id="f" value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="OPEN">Open</option><option value="REVIEWED">Reviewed</option><option value="CLOSED">Closed</option>
          <option value="AUTO_CLOSED">Auto-closed</option><option value="ALL">All</option>
        </select>
      </div>
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Enrolment alerts</caption>
            <thead><tr><th scope="col">Type</th><th scope="col">Why it was raised</th><th scope="col">Status</th><th scope="col">Review</th></tr></thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id}>
                  <td><Badge value={a.type} /></td>
                  <td className="wrapcell">{a.explanation}{a.note && <><br /><span className="muted small">Note: {a.note}</span></>}</td>
                  <td><Badge value={a.status} /></td>
                  <td className="wrapcell">
                    {(a.status === 'OPEN' || a.status === 'REVIEWED') ? (
                      <>
                        <label className="sr" style={{ position: 'absolute', left: -9999 }} htmlFor={'n' + a.id}>Note</label>
                        <input id={'n' + a.id} type="text" placeholder="Note (optional)" value={note[a.id] || ''} onChange={(e) => setNote({ ...note, [a.id]: e.target.value })} />
                        <div className="actions" style={{ marginTop: '.4rem' }}>
                          {a.status === 'OPEN' && <button className="btn small secondary" onClick={() => setStatus(a.id, 'REVIEWED')}>Mark reviewed</button>}
                          <button className="btn small" onClick={() => setStatus(a.id, 'CLOSED')}>Close</button>
                        </div>
                      </>
                    ) : <span className="muted small">{fmtDate(a.updatedAt)}</span>}
                  </td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={4} className="muted">No alerts in this view.</td></tr>}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}

export function AdminCases() {
  const dup = useLoad(() => api('/api/admin/duplicates'));
  const dis = useLoad(() => api('/api/admin/disputes'));
  const [note, setNote] = useState({});
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  async function resolve(id, action) {
    setErr(''); setMsg('');
    try {
      await api(`/api/admin/duplicates/${id}/resolve`, { method: 'POST', body: { action, note: note['d' + id] || '' } });
      setMsg('Case resolved.'); dup.reload();
    } catch (e) { setErr(e.message); }
  }
  async function resolveDispute(id, outcome) {
    setErr(''); setMsg('');
    try {
      await api(`/api/admin/disputes/${id}/resolve`, { method: 'POST', body: { outcome, note: note['x' + id] || '' } });
      setMsg('Dispute reviewed.'); dis.reload();
    } catch (e) { setErr(e.message); }
  }
  return (
    <>
      <PageHead title="Duplicates and disputes" sub="Students recorded at two institutions at once, and records that students say are wrong." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>

      <Panel title="Duplicate enrolment cases" body={false}>
        <Loading state={dup} />
        {dup.data && (
          <div className="tablewrap"><table>
            <caption className="sr">Duplicate enrolment cases</caption>
            <thead><tr><th scope="col">APAAR ID</th><th scope="col">Enrolled at</th><th scope="col">Institution responses</th><th scope="col">Due</th><th scope="col">Resolution</th></tr></thead>
            <tbody>
              {dup.data.map((c) => (
                <tr key={c.id} className={c.overdue ? 'hl' : ''}>
                  <td><code>{c.apaarId}</code><br /><Badge value={c.status} /></td>
                  <td className="wrapcell">A: {c.institutionA}<br /><span className="muted small">{c.courseA}</span><br />B: {c.institutionB}<br /><span className="muted small">{c.courseB}</span></td>
                  <td className="wrapcell small">A: {c.responseA || <span className="muted">no response</span>}<br />B: {c.responseB || <span className="muted">no response</span>}</td>
                  <td>{fmtDay(c.deadline)}{c.overdue && <><br /><Badge value="HIGH" label="Overdue" /></>}</td>
                  <td className="wrapcell">
                    {c.status === 'OPEN' ? (
                      <>
                        <label htmlFor={'dn' + c.id} style={{ position: 'absolute', left: -9999 }}>Note</label>
                        <input id={'dn' + c.id} type="text" placeholder="Note" value={note['d' + c.id] || ''} onChange={(e) => setNote({ ...note, ['d' + c.id]: e.target.value })} />
                        <div className="actions" style={{ marginTop: '.4rem' }}>
                          <button className="btn small" onClick={() => resolve(c.id, 'KEEP_A')}>Keep at A</button>
                          <button className="btn small" onClick={() => resolve(c.id, 'KEEP_B')}>Keep at B</button>
                          <button className="btn small secondary" onClick={() => resolve(c.id, 'BOTH_VALID')}>Both valid</button>
                        </div>
                      </>
                    ) : <span className="small">{c.resolution}{c.note ? ' - ' + c.note : ''}</span>}
                  </td>
                </tr>
              ))}
              {dup.data.length === 0 && <tr><td colSpan={5} className="muted">No duplicate-enrolment cases.</td></tr>}
            </tbody>
          </table></div>
        )}
      </Panel>

      <Panel title="Records disputed by students" body={false}>
        <Loading state={dis} />
        {dis.data && (
          <div className="tablewrap"><table>
            <caption className="sr">Disputed records</caption>
            <thead><tr><th scope="col">Student</th><th scope="col">Institution and course</th><th scope="col">What the student says</th><th scope="col">Review</th></tr></thead>
            <tbody>
              {dis.data.map((d) => (
                <tr key={d.recordId}>
                  <td>{d.studentName}<br /><code>{d.apaarId}</code></td>
                  <td className="wrapcell">{d.institution}<br /><span className="muted small">{d.course} ({d.admissionYear})</span></td>
                  <td className="wrapcell">{d.studentSays}</td>
                  <td className="wrapcell">
                    <input type="text" aria-label="Note" placeholder="Note" value={note['x' + d.recordId] || ''} onChange={(e) => setNote({ ...note, ['x' + d.recordId]: e.target.value })} />
                    <div className="actions" style={{ marginTop: '.4rem' }}>
                      <button className="btn small" onClick={() => resolveDispute(d.recordId, 'RETURN_TO_INSTITUTION')}>Return for correction</button>
                      <button className="btn small secondary" onClick={() => resolveDispute(d.recordId, 'DISMISSED')}>Dismiss</button>
                    </div>
                  </td>
                </tr>
              ))}
              {dis.data.length === 0 && <tr><td colSpan={4} className="muted">No disputed records.</td></tr>}
            </tbody>
          </table></div>
        )}
      </Panel>
    </>
  );
}

export function AdminWatchlist() {
  const st = useLoad(() => api('/api/admin/watchlist'));
  return (
    <>
      <PageHead title="Watchlist" sub="Institutions ranked by a transparent risk score. Every point comes from a reason listed in the same row." />
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Institution watchlist</caption>
            <thead><tr><th scope="col">Institution</th><th scope="col" className="num">Score</th><th scope="col">Level</th><th scope="col">Reasons</th></tr></thead>
            <tbody>
              {st.data.map((w) => (
                <tr key={w.institutionId}>
                  <td>{w.name}<br /><span className="muted small">{w.state} | {w.aicteId}</span></td>
                  <td className="num">{w.score}</td>
                  <td><Badge value={w.level} /></td>
                  <td className="wrapcell">
                    {w.reasons.length === 0 ? <span className="muted">No concerns found.</span> : <ul style={{ margin: 0, paddingLeft: '1.1rem' }}>{w.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        </Panel>
      )}
      <p className="muted small">Scoring: over-intake courses add up to 70 points, supernumerary overshoot 15, enrolment spikes 20, duplicate-enrolment cases up to 30,
        a high rate of APAAR problems 20, many student disputes 15, and very low student confirmation 10. The total is capped at 100.</p>
    </>
  );
}

const REPORTS = [
  ['institution-enrolment', 'Institution-wise enrolment', 'Approved intake, seats filled and fill percentage for every institution.'],
  ['alerts', 'Enrolment alerts', 'All alerts with status and explanation.'],
  ['watchlist', 'Institution watchlist', 'Risk scores with reasons.'],
  ['category-distribution', 'Admission category distribution', 'Students admitted this cycle by category.'],
  ['gender-distribution', 'Gender distribution', 'Students admitted this cycle by gender.'],
];

export function AdminReports() {
  const [err, setErr] = useState('');
  async function get(type) {
    setErr('');
    try { await download(`/api/admin/reports/${type}`, `${type}.csv`); } catch (e) { setErr(e.message); }
  }
  return (
    <>
      <PageHead title="Reports" sub="Downloadable CSV files for monitoring, accreditation and policy work." />
      <Msg kind="err">{err}</Msg>
      <Panel body={false}>
        <div className="tablewrap"><table>
          <caption className="sr">Available reports</caption>
          <thead><tr><th scope="col">Report</th><th scope="col">Contents</th><th scope="col">Download</th></tr></thead>
          <tbody>
            {REPORTS.map(([k, n, d]) => (
              <tr key={k}><td>{n}</td><td className="wrapcell">{d}</td><td><button className="btn small" onClick={() => get(k)}>Download CSV</button></td></tr>
            ))}
          </tbody>
        </table></div>
      </Panel>
    </>
  );
}

export function AdminRequesters() {
  const st = useLoad(() => api('/api/admin/requesters'));
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  async function set(id, verified) {
    setErr(''); setMsg('');
    try {
      const r = await api(`/api/admin/requesters/${id}/verify`, { method: 'POST', body: { verified } });
      setMsg(r.message); st.reload();
    } catch (e) { setErr(e.message); }
  }
  return (
    <>
      <PageHead title="Requesters" sub="Organisations and individuals who can ask students for access. Only verified organisations can be offered more than basic enrolment status." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Requesters</caption>
            <thead><tr><th scope="col">Name</th><th scope="col">Organisation</th><th scope="col">Registration no.</th><th scope="col">Email</th><th scope="col">Trust level</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {st.data.map((r) => (
                <tr key={r.id}>
                  <td>{r.fullName}</td><td>{r.organisation || <span className="muted">none</span>}</td><td>{r.orgRegId || '-'}</td><td>{r.email}</td>
                  <td><Badge value={r.tier === 'VERIFIED' ? 'TIER_VERIFIED' : 'TIER_GENERAL'} /></td>
                  <td>{r.tier === 'VERIFIED'
                    ? <button className="btn small secondary" onClick={() => set(r.id, false)}>Remove verification</button>
                    : <button className="btn small" onClick={() => set(r.id, true)} disabled={!r.organisation}>Verify organisation</button>}</td>
                </tr>
              ))}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}

export function AdminPolicy() {
  const st = useLoad(() => api('/api/admin/policy'));
  const [f, setF] = useState(null);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  if (st.data && !f) setF({ ...st.data });
  async function save(e) {
    e.preventDefault(); setErr(''); setMsg('');
    try {
      const r = await api('/api/admin/policy', { method: 'PUT', body: {
        intakeTolerancePct: f.intakeTolerancePct, spikeThresholdPct: f.spikeThresholdPct,
        studentConfirmation: f.studentConfirmation, confirmationDeadlineDays: f.confirmationDeadlineDays } });
      setF({ ...r });
      setMsg('Policy saved and alerts recalculated.');
    } catch (ex) { setErr(ex.message); }
  }
  return (
    <>
      <PageHead title="Policy rules" sub="Thresholds used by the automatic checks. Changes apply at once, without any software change." />
      <Loading state={st} />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      {f && (
        <form onSubmit={save} className="panel" style={{ maxWidth: 640 }}><div className="panel-body">
          <div className="field"><label htmlFor="t1">Intake tolerance (%) <span className="hint">Regular-seat enrolment above approved intake by more than this raises an alert.</span></label>
            <input id="t1" type="number" step="0.5" min="0" max="50" value={f.intakeTolerancePct} onChange={(e) => setF({ ...f, intakeTolerancePct: e.target.value })} /></div>
          <div className="field"><label htmlFor="t2">Enrolment spike threshold (%) <span className="hint">Alert if enrolment is this much higher than the previous year.</span></label>
            <input id="t2" type="number" step="1" min="5" max="500" value={f.spikeThresholdPct} onChange={(e) => setF({ ...f, spikeThresholdPct: e.target.value })} /></div>
          <div className="field"><label htmlFor="t3">Student confirmation</label>
            <select id="t3" value={f.studentConfirmation} onChange={(e) => setF({ ...f, studentConfirmation: e.target.value })}>
              <option value="OFF">Off - students are not asked</option>
              <option value="OPTIONAL">Optional - students are asked, data is accepted meanwhile</option>
              <option value="MANDATORY">Mandatory - unconfirmed records are flagged after the deadline</option>
            </select></div>
          <div className="field"><label htmlFor="t4">Confirmation deadline (days)</label>
            <input id="t4" type="number" min="1" max="365" value={f.confirmationDeadlineDays} onChange={(e) => setF({ ...f, confirmationDeadlineDays: e.target.value })} /></div>
          <button className="btn">Save policy</button>
        </div></form>
      )}
    </>
  );
}

export function AdminAudit() {
  const st = useLoad(() => api('/api/admin/audit'));
  const [chain, setChain] = useState(null);
  async function verify() {
    try { setChain(await api('/api/admin/audit/verify')); } catch (e) { setChain({ error: e.message }); }
  }
  return (
    <>
      <PageHead title="Audit log" sub="Latest 200 entries. Each entry includes a hash of the one before it.">
        <button className="btn" onClick={verify}>Verify hash chain</button>
      </PageHead>
      {chain && chain.error && <Msg kind="err">{chain.error}</Msg>}
      {chain && chain.valid === true && <Msg kind="ok" title="Chain intact. ">All {num(chain.entries)} entries match their hashes, so none has been altered or removed.</Msg>}
      {chain && chain.valid === false && <Msg kind="err" title="Chain broken. ">Entry {chain.brokenAtEntryId} does not match, which means the log was modified.</Msg>}
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Audit entries</caption>
            <thead><tr><th scope="col">When</th><th scope="col">Who</th><th scope="col">Action</th><th scope="col">Target</th><th scope="col">Details</th></tr></thead>
            <tbody>
              {st.data.map((a) => (
                <tr key={a.id}><td>{fmtDate(a.createdAt)}</td><td>{a.actor}<br /><span className="muted small">{a.role}</span></td><td>{a.action}</td><td>{a.target}</td><td className="wrapcell small">{a.details}</td></tr>
              ))}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}

export function AdminStudents() {
  const [q, setQ] = useState('');
  const [inst, setInst] = useState('');
  const [year, setYear] = useState('');
  const [issue, setIssue] = useState('');
  const [page, setPage] = useState(0);
  const [hist, setHist] = useState(null);
  const [err, setErr] = useState('');
  const size = 25;
  const insts = useLoad(() => api('/api/admin/institutions'));
  const qs = `q=${encodeURIComponent(q)}${inst ? '&institutionId=' + inst : ''}${year ? '&year=' + year : ''}${issue ? '&issue=' + issue : ''}&page=${page}&size=${size}`;
  const st = useLoad(() => api('/api/admin/students?' + qs), [q, inst, year, issue, page]);
  const d = st.data;
  const pages = d ? Math.max(1, Math.ceil(d.total / size)) : 1;
  async function showHistory(r) {
    setErr('');
    try { setHist({ r, rows: await api(`/api/admin/students/${r.id}/history`) }); } catch (ex) { setErr(ex.message); }
  }
  return (
    <>
      <PageHead title="Students" sub="Student-wise records from every institution. Read only: corrections are made by the institution." />
      <Msg kind="err">{err}</Msg>
      <div className="row" style={{ marginBottom: '1rem' }}>
        <div className="field"><label htmlFor="aq">Search <span className="hint">name, APAAR ID or roll number</span></label>
          <input id="aq" type="search" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} /></div>
        <div className="field"><label htmlFor="ai">Institution</label>
          <select id="ai" value={inst} onChange={(e) => { setInst(e.target.value); setPage(0); }}>
            <option value="">All institutions</option>
            {(insts.data || []).map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
          </select></div>
        <div className="field"><label htmlFor="ay">Admission year</label>
          <select id="ay" value={year} onChange={(e) => { setYear(e.target.value); setPage(0); }}>
            <option value="">All years</option>{[2026, 2025, 2024, 2023].map((y) => <option key={y} value={y}>{y}</option>)}
          </select></div>
        <div className="field"><label htmlFor="as">Show</label>
          <select id="as" value={issue} onChange={(e) => { setIssue(e.target.value); setPage(0); }}>
            <option value="">All records</option><option value="APAAR">APAAR problems</option>
            <option value="DISPUTED">Disputed by student</option><option value="DUPLICATE">Duplicate enrolment</option>
          </select></div>
      </div>
      <Loading state={st} />
      {d && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Student records</caption>
            <thead><tr><th scope="col">Student</th><th scope="col">APAAR ID</th><th scope="col">Institution</th><th scope="col">Course</th><th scope="col">APAAR check</th><th scope="col">Student confirmation</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {d.rows.map((r) => (
                <tr key={r.id}>
                  <td><div className="cell-main">{r.name}</div><div className="cell-sub">{fmtDay(r.dob)} &middot; {r.rollNo || 'no roll no.'}</div></td>
                  <td><code>{r.apaarId || '-'}</code></td>
                  <td className="wrapcell">{r.institution}</td>
                  <td className="wrapcell"><div className="cell-main">{r.course}</div><div className="cell-sub">{r.admissionYear} &middot; {r.seatType}</div><div className="cell-badges"><Badge value={r.status} /></div></td>
                  <td><div className="cell-badges"><Badge value={r.apaarStatus} />{r.flags && r.flags.includes('DUPLICATE_ENROLMENT') && <Badge value="HIGH" label="Duplicate case" />}</div></td>
                  <td><div className="cell-badges"><Badge value={r.confirmationStatus} />{r.confirmationNote && r.confirmationStatus === 'DISPUTED' && <span className="small muted">{r.confirmationNote}</span>}</div></td>
                  <td><button className="btn small secondary" onClick={() => showHistory(r)}>History</button></td>
                </tr>
              ))}
              {d.rows.length === 0 && <tr><td colSpan={7} className="muted">No records match.</td></tr>}
            </tbody>
          </table></div>
          <div className="panel-body actions">
            <button className="btn small secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span className="small">Page {page + 1} of {pages} ({num(d.total)} records)</span>
            <button className="btn small secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </Panel>
      )}
      {hist && (
        <Panel title={`History: ${hist.r.name}`} actions={<button className="linkbtn" onClick={() => setHist(null)}>Close</button>} body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Record history</caption>
            <thead><tr><th scope="col">When</th><th scope="col">Field</th><th scope="col">Was</th><th scope="col">Now</th><th scope="col">Reason</th><th scope="col">By</th></tr></thead>
            <tbody>
              {hist.rows.map((h) => (<tr key={h.id}><td>{fmtDate(h.changedAt)}</td><td>{h.fieldName}</td><td>{h.oldValue}</td><td>{h.newValue}</td><td className="wrapcell">{h.reason}</td><td>{h.changedBy}</td></tr>))}
              {hist.rows.length === 0 && <tr><td colSpan={6} className="muted">No changes recorded.</td></tr>}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}
