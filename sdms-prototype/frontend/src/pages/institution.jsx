import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, download } from '../api.js';
import { Badge, Facts, Loading, Meter, Msg, PageHead, Panel, fmtDate, fmtDay, num, useLoad } from '../components/ui.jsx';

export function InstDashboard() {
  const st = useLoad(() => api('/api/institution/summary'));
  const d = st.data;
  const tracker = d ? d.seatTracker : [];
  const anyData = tracker.some((r) => r.status !== 'NO_DATA');
  const conf = d ? d.confirmation : {};
  return (
    <>
      <PageHead title={d ? d.institution.name : 'Institution dashboard'}
        sub={d ? `${d.institution.aicteId} | ${d.institution.district}, ${d.institution.state} | Admissions ${d.admissionYear}` : ''}>
        <Link className="btn" to="/institution/upload">Upload students</Link>
      </PageHead>
      <Loading state={st} />
      {d && (
        <>
          {!anyData && (
            <Msg kind="info" title="No admissions submitted yet. ">After admissions close, upload your student records. The system checks them as soon as you choose the file.</Msg>
          )}
          <Facts items={[
            { label: 'Records this year', value: num(d.records) },
            { label: 'Records with APAAR problems', value: num(d.apaarIssues), warn: d.apaarIssues > 0 },
            { label: 'Confirmed by students', value: num(conf.VERIFIED || 0) },
            { label: 'Awaiting student', value: num(conf.PENDING || 0) },
            { label: 'Disputed by students', value: num(conf.DISPUTED || 0), warn: (conf.DISPUTED || 0) > 0 },
          ]} />

          <Panel title="Seat tracker" body={false}>
            <div className="tablewrap"><table>
              <caption className="sr">Seats against approved intake</caption>
              <thead><tr>
                <th scope="col">Course</th><th scope="col" className="num">Approved intake</th><th scope="col">Regular seats filled</th>
                <th scope="col" className="num">Seats left</th><th scope="col" className="num">Supernumerary (limit)</th>
                <th scope="col" className="num">NRI</th><th scope="col" className="num">OCI</th><th scope="col" className="num">FN</th><th scope="col">Status</th>
              </tr></thead>
              <tbody>
                {tracker.map((r) => (
                  <tr key={r.courseId}>
                    <td>{r.name}<br /><span className="muted small">{r.code}</span></td>
                    <td className="num">{num(r.approvedIntake)}</td>
                    <td><Meter pct={r.fillPct} over={r.status === 'OVER_LIMIT' && r.regularFilled > r.approvedIntake} full={r.status === 'FULL'} />{num(r.regularFilled)} ({r.fillPct}%)</td>
                    <td className="num">{num(r.remainingRegular)}</td>
                    <td className="num">{num(r.supernumeraryFilled)} ({num(r.supernumeraryLimit)})</td>
                    <td className="num">{num(r.nri)}</td><td className="num">{num(r.oci)}</td><td className="num">{num(r.fn)}</td>
                    <td><Badge value={r.status === 'OPEN' ? 'OPEN' : r.status} label={r.status === 'OPEN' ? 'Seats available' : undefined} /></td>
                  </tr>
                ))}
              </tbody>
            </table></div>
          </Panel>

          <div className="grid2">
            <Panel title="Alerts for your institution" body={false}>
              <div className="tablewrap"><table>
                <caption className="sr">Alerts</caption>
                <thead><tr><th scope="col">Type</th><th scope="col">Why</th></tr></thead>
                <tbody>
                  {d.alerts.filter((a) => a.status === 'OPEN' || a.status === 'REVIEWED').map((a) => (
                    <tr key={a.id}><td><Badge value={a.type} /></td><td className="wrapcell">{a.explanation}</td></tr>
                  ))}
                  {d.alerts.filter((a) => a.status === 'OPEN' || a.status === 'REVIEWED').length === 0 && <tr><td colSpan={2} className="muted">No active alerts.</td></tr>}
                </tbody>
              </table></div>
            </Panel>
            <Panel title="Recent uploads" body={false}>
              <div className="tablewrap"><table>
                <caption className="sr">Upload history</caption>
                <thead><tr><th scope="col">When</th><th scope="col">File</th><th scope="col" className="num">Rows</th><th scope="col" className="num">New</th><th scope="col" className="num">Rejected</th></tr></thead>
                <tbody>
                  {d.batches.map((b) => (
                    <tr key={b.id}><td>{fmtDate(b.createdAt)}</td><td>{b.fileName}</td><td className="num">{b.totalRows}</td><td className="num">{b.accepted}</td><td className="num">{b.rejected}</td></tr>
                  ))}
                  {d.batches.length === 0 && <tr><td colSpan={5} className="muted">Nothing uploaded yet.</td></tr>}
                </tbody>
              </table></div>
            </Panel>
          </div>
        </>
      )}
    </>
  );
}

export function InstUpload() {
  const fileRef = useRef(null);
  const [file, setFile] = useState(null);
  const [check, setCheck] = useState(null);
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [show, setShow] = useState('ALL');

  async function send(f, dryRun) {
    const form = new FormData();
    form.append('file', f);
    return api(`/api/institution/upload?dryRun=${dryRun}`, { method: 'POST', form });
  }
  async function choose(e) {
    const f = e.target.files && e.target.files[0];
    setDone(null); setCheck(null); setErr(''); setFile(f || null);
    if (!f) return;
    setBusy(true);
    try { setCheck(await send(f, true)); } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  async function submit() {
    setBusy(true); setErr('');
    try {
      const r = await send(file, false);
      setDone(r); setCheck(null); setFile(null);
      if (fileRef.current) fileRef.current.value = '';
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  async function template() {
    try { await download('/api/institution/template', 'student-upload-template.csv'); } catch (ex) { setErr(ex.message); }
  }
  const res = done || check;
  const issues = res ? res.issues.filter((i) => show === 'ALL' || i.severity === show) : [];
  const canSubmit = check && (check.created + check.updated) > 0;
  return (
    <>
      <PageHead title="Upload students" sub="One row per student, identified by APAAR ID. The file is checked as soon as you select it; nothing is saved until you submit." />
      <Msg kind="err">{err}</Msg>
      <div className="grid2">
        <Panel title="1. Prepare the file">
          <p>Use the CSV template. Required columns: apaar_id, name, dob (YYYY-MM-DD), gender, course_code, admission_year, category, seat_type, residency.
            Optional: admission_mode, status, roll_no.</p>
          <p className="actions"><button className="btn secondary" onClick={template}>Download template</button></p>
          <p className="muted small">Systems that already hold this data can send it directly instead, using the JSON endpoint <code>POST /api/institution/students/bulk</code>{' '}
            with the same checks.</p>
        </Panel>
        <Panel title="2. Choose the file">
          <div className="field">
            <label htmlFor="file">CSV file</label>
            <input id="file" ref={fileRef} type="file" accept=".csv,text/csv" onChange={choose} />
          </div>
          {busy && <p role="status" className="muted">Checking...</p>}
        </Panel>
      </div>

      {done && (
        <Msg kind="ok" title="Submitted. ">
          {done.created} new record(s) saved, {done.updated} updated, {done.unchanged} unchanged, {done.rejected} rejected.
          {' '}Students with a registered email have been asked to confirm. <Link to="/institution/students">View students</Link>
        </Msg>
      )}

      {res && (
        <>
          <Facts items={[
            { label: 'Rows read', value: num(res.totalRows) },
            { label: done ? 'New records saved' : 'New records', value: num(res.created) },
            { label: 'Updates to existing', value: num(res.updated) },
            { label: 'Rejected rows', value: num(res.rejected), warn: res.rejected > 0 },
            { label: 'Warnings', value: num(res.warnings), warn: res.warnings > 0 },
          ]} />

          {res.seats.length > 0 && (
            <Panel title={done ? 'Seats after this upload' : 'Seats if you submit this file'} body={false}>
              <div className="tablewrap"><table>
                <caption className="sr">Seat projection</caption>
                <thead><tr><th scope="col">Course</th><th scope="col" className="num">Approved intake</th><th scope="col" className="num">Regular seats</th><th scope="col" className="num">Supernumerary (limit)</th><th scope="col">Result</th></tr></thead>
                <tbody>
                  {res.seats.map((s) => (
                    <tr key={s.course}>
                      <td>{s.course}</td><td className="num">{s.approvedIntake}</td><td className="num">{s.regularAfterUpload}</td>
                      <td className="num">{s.supernumeraryAfterUpload} ({s.supernumeraryLimit})</td>
                      <td>{s.exceedsIntake || s.exceedsSupernumerary
                        ? <Badge value="HIGH" label={s.exceedsIntake ? 'Exceeds approved intake' : 'Exceeds supernumerary limit'} />
                        : <Badge value="LOW" label="Within limits" />}</td>
                    </tr>
                  ))}
                </tbody>
              </table></div>
            </Panel>
          )}

          <Panel title={`Findings (${res.issues.length})`} actions={
            <select aria-label="Filter findings" value={show} onChange={(e) => setShow(e.target.value)} style={{ width: 'auto' }}>
              <option value="ALL">All</option><option value="ERROR">Errors only</option><option value="WARNING">Warnings only</option>
            </select>} body={false}>
            <div className="tablewrap"><table>
              <caption className="sr">Validation findings</caption>
              <thead><tr><th scope="col" className="num">Row</th><th scope="col">APAAR ID</th><th scope="col">Severity</th><th scope="col">Check</th><th scope="col">What to do</th></tr></thead>
              <tbody>
                {issues.map((i, k) => (
                  <tr key={k}><td className="num">{i.row || '-'}</td><td><code>{i.apaarId || '-'}</code></td><td><Badge value={i.severity} /></td><td><code className="small">{i.code}</code></td><td className="wrapcell">{i.message}</td></tr>
                ))}
                {issues.length === 0 && <tr><td colSpan={5} className="muted">No findings. Every row passed all checks.</td></tr>}
              </tbody>
            </table></div>
          </Panel>

          {check && (
            <Panel>
              {check.rejected > 0 && <Msg kind="warn" title="Rejected rows will be skipped. ">Fix them in the file and upload again, or submit now and correct them later. Rows with APAAR warnings are saved as "APAAR pending" so they can be completed.</Msg>}
              <div className="actions">
                <button className="btn" onClick={submit} disabled={busy || !canSubmit}>Submit {check.created + check.updated} valid record(s)</button>
                <button className="btn secondary" onClick={() => { setCheck(null); setFile(null); if (fileRef.current) fileRef.current.value = ''; }}>Discard</button>
              </div>
            </Panel>
          )}
        </>
      )}
    </>
  );
}

export function InstStudents() {
  const [q, setQ] = useState('');
  const [year, setYear] = useState('');
  const [issue, setIssue] = useState('');
  const [page, setPage] = useState(0);
  const size = 25;
  const qs = `q=${encodeURIComponent(q)}${year ? '&year=' + year : ''}${issue ? '&issue=' + issue : ''}&page=${page}&size=${size}`;
  const st = useLoad(() => api('/api/institution/students?' + qs), [q, year, issue, page]);
  const [edit, setEdit] = useState(null);
  const [form, setForm] = useState({});
  const [reason, setReason] = useState('');
  const [hist, setHist] = useState(null);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');

  function open(r) {
    setEdit(r); setReason(''); setMsg(''); setErr('');
    setForm({ name: r.name, dob: r.dob, category: r.category, seatType: r.seatType, residency: r.residency, status: r.status, apaarId: r.apaarId || '' });
  }
  async function save(e) {
    e.preventDefault(); setErr(''); setMsg('');
    const changes = {};
    ['name', 'dob', 'category', 'seatType', 'residency', 'status'].forEach((k) => { if (String(form[k]) !== String(edit[k])) changes[k] = form[k]; });
    if (form.apaarId && form.apaarId !== (edit.apaarId || '')) changes.apaarId = form.apaarId;
    if (Object.keys(changes).length === 0) { setErr('Nothing was changed.'); return; }
    try {
      await api(`/api/institution/students/${edit.id}`, { method: 'PATCH', body: { changes, reason } });
      setMsg('Correction saved. The previous values are kept in the record history.');
      setEdit(null); st.reload();
    } catch (ex) { setErr(ex.message); }
  }
  async function showHistory(r) {
    try { setHist({ r, rows: await api(`/api/institution/students/${r.id}/history`) }); } catch (ex) { setErr(ex.message); }
  }
  const d = st.data;
  const pages = d ? Math.max(1, Math.ceil(d.total / size)) : 1;
  return (
    <>
      <PageHead title="Students" sub="Records submitted by your institution. Corrections need a reason and are kept in the record history." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <div className="row" style={{ marginBottom: '1rem' }}>
        <div className="field"><label htmlFor="q">Search <span className="hint">name, APAAR ID or roll number</span></label>
          <input id="q" type="search" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} /></div>
        <div className="field"><label htmlFor="y">Admission year</label>
          <select id="y" value={year} onChange={(e) => { setYear(e.target.value); setPage(0); }}>
            <option value="">All years</option>{[2026, 2025, 2024, 2023].map((y) => <option key={y} value={y}>{y}</option>)}
          </select></div>
        <div className="field"><label htmlFor="i">Show</label>
          <select id="i" value={issue} onChange={(e) => { setIssue(e.target.value); setPage(0); }}>
            <option value="">All records</option><option value="APAAR">APAAR problems</option>
            <option value="DISPUTED">Disputed by student</option><option value="DUPLICATE">Duplicate enrolment</option>
          </select></div>
      </div>
      <Loading state={st} />
      {d && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Student records</caption>
            <thead><tr><th scope="col">Student</th><th scope="col">APAAR ID</th><th scope="col">Course</th><th scope="col">Seat / category</th><th scope="col">APAAR check</th><th scope="col">Student confirmation</th><th scope="col">Action</th></tr></thead>
            <tbody>
              {d.rows.map((r) => (
                <tr key={r.id}>
                  <td><div className="cell-main">{r.name}</div><div className="cell-sub">{fmtDay(r.dob)} &middot; {r.rollNo || 'no roll no.'}</div></td>
                  <td><code>{r.apaarId || '-'}</code></td>
                  <td className="wrapcell"><div className="cell-main">{r.course}</div><div className="cell-sub">{r.admissionYear}</div><div className="cell-badges"><Badge value={r.status} /></div></td>
                  <td><div className="cell-main">{r.seatType}</div><div className="cell-sub">{r.category} &middot; {r.residency}</div></td>
                  <td><div className="cell-badges"><Badge value={r.apaarStatus} />{r.flags && r.flags.includes('DUPLICATE_ENROLMENT') && <Badge value="HIGH" label="Duplicate case" />}</div></td>
                  <td><div className="cell-badges"><Badge value={r.confirmationStatus} />{r.confirmationNote && r.confirmationStatus === 'DISPUTED' && <span className="small muted">{r.confirmationNote}</span>}</div></td>
                  <td><div className="actions"><button className="btn small secondary" onClick={() => open(r)}>Correct</button><button className="btn small secondary" onClick={() => showHistory(r)}>History</button></div></td>
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

      {edit && (
        <Panel title={`Correct record: ${edit.name}`}>
          <form onSubmit={save}>
            <div className="row">
              <div className="field"><label htmlFor="en">Name</label><input id="en" type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
              <div className="field"><label htmlFor="ed">Date of birth</label><input id="ed" type="text" value={form.dob} onChange={(e) => setForm({ ...form, dob: e.target.value })} /></div>
              <div className="field"><label htmlFor="ea">APAAR ID</label><input id="ea" type="text" maxLength={12} value={form.apaarId} onChange={(e) => setForm({ ...form, apaarId: e.target.value.replace(/\D/g, '') })} /></div>
            </div>
            <div className="row" style={{ marginTop: '.75rem' }}>
              <div className="field"><label htmlFor="ec">Category</label>
                <select id="ec" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>{['GEN', 'EWS', 'OBC', 'SC', 'ST', 'NA'].map((x) => <option key={x}>{x}</option>)}</select></div>
              <div className="field"><label htmlFor="es">Seat type</label>
                <select id="es" value={form.seatType} onChange={(e) => setForm({ ...form, seatType: e.target.value })}>{['REGULAR', 'SUPERNUMERARY'].map((x) => <option key={x}>{x}</option>)}</select></div>
              <div className="field"><label htmlFor="er">Residency</label>
                <select id="er" value={form.residency} onChange={(e) => setForm({ ...form, residency: e.target.value })}>{['INDIAN', 'NRI', 'OCI', 'FN'].map((x) => <option key={x}>{x}</option>)}</select></div>
              <div className="field"><label htmlFor="et">Status</label>
                <select id="et" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>{['ACTIVE', 'WITHDRAWN', 'GRADUATED'].map((x) => <option key={x}>{x}</option>)}</select></div>
            </div>
            <div className="field" style={{ marginTop: '.75rem' }}><label htmlFor="rs">Reason for the correction <span className="hint">(required)</span></label>
              <input id="rs" type="text" value={reason} onChange={(e) => setReason(e.target.value)} required /></div>
            <div className="actions"><button className="btn">Save correction</button><button type="button" className="btn secondary" onClick={() => setEdit(null)}>Cancel</button></div>
          </form>
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

export function InstCases() {
  const st = useLoad(() => api('/api/institution/duplicates'));
  const [me] = useState(() => JSON.parse(localStorage.getItem('sdms_user') || '{}'));
  const [note, setNote] = useState({});
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  async function respond(id) {
    setErr(''); setMsg('');
    try {
      await api(`/api/institution/duplicates/${id}/respond`, { method: 'POST', body: { note: note[id] || '' } });
      setMsg('Response sent to AICTE.'); st.reload();
    } catch (e) { setErr(e.message); }
  }
  return (
    <>
      <PageHead title="Duplicate enrolment cases" sub="A student recorded as actively enrolled at your institution and another one. Explain the situation so AICTE can resolve it." />
      <Msg kind="ok">{msg}</Msg><Msg kind="err">{err}</Msg>
      <Loading state={st} />
      {st.data && (
        <Panel body={false}>
          <div className="tablewrap"><table>
            <caption className="sr">Duplicate cases</caption>
            <thead><tr><th scope="col">APAAR ID</th><th scope="col">Other institution</th><th scope="col">Due</th><th scope="col">Status</th><th scope="col">Your response</th></tr></thead>
            <tbody>
              {st.data.map((c) => {
                const mine = c.institutionAId === me.institutionId;
                return (
                  <tr key={c.id} className={c.overdue ? 'hl' : ''}>
                    <td><code>{c.apaarId}</code></td>
                    <td className="wrapcell">{mine ? c.institutionB : c.institutionA}<br /><span className="muted small">{mine ? c.courseB : c.courseA}</span></td>
                    <td>{fmtDay(c.deadline)}</td>
                    <td><Badge value={c.status} />{c.status === 'RESOLVED' && <><br /><span className="small">{c.resolution}</span></>}</td>
                    <td className="wrapcell">
                      {(mine ? c.responseA : c.responseB) && <p className="small">Sent: {mine ? c.responseA : c.responseB}</p>}
                      {c.status === 'OPEN' && (<>
                        <input type="text" aria-label="Response" placeholder="For example: student withdrew on 12 Aug" value={note[c.id] || ''} onChange={(e) => setNote({ ...note, [c.id]: e.target.value })} />
                        <button className="btn small" style={{ marginTop: '.4rem' }} onClick={() => respond(c.id)}>Send response</button>
                      </>)}
                    </td>
                  </tr>
                );
              })}
              {st.data.length === 0 && <tr><td colSpan={5} className="muted">No duplicate cases involve your institution.</td></tr>}
            </tbody>
          </table></div>
        </Panel>
      )}
    </>
  );
}
