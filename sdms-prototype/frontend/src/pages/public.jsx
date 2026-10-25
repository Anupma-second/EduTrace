import { useEffect, useRef, useState } from 'react';
import jsQR from 'jsqr';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api } from '../api.js';
import { useApp } from '../auth.jsx';
import { Msg, PageHead, Panel, fmtDate, useLoad, Loading } from '../components/ui.jsx';

const HOME_BY_ROLE = { ADMIN: '/admin', INSTITUTION: '/institution', STUDENT: '/student/records', REQUESTER: '/access/new' };

export { Home } from './Home.jsx';

function useAfterLogin() {
  const { login } = useApp();
  const nav = useNavigate();
  return (session) => {
    login(session);
    nav(HOME_BY_ROLE[session.role] || '/');
  };
}

export function StaffLogin() {
  const done = useAfterLogin();
  const [f, setF] = useState({ username: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(e) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      const s = await api('/api/auth/login', { method: 'POST', body: f });
      if (s.role !== 'ADMIN' && s.role !== 'INSTITUTION') throw new Error('This page is for AICTE administrators and institutions. Use the other sign-in pages for students and requesters.');
      done(s);
    } catch (ex) {
      setErr(ex.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="narrow">
      <PageHead title="Staff sign-in" sub="AICTE administrators and institution registrars" />
      <Msg kind="err">{err}</Msg>
      <form onSubmit={submit} className="panel"><div className="panel-body">
        <div className="field"><label htmlFor="u">Username</label>
          <input id="u" type="text" autoComplete="username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} required /></div>
        <div className="field"><label htmlFor="p">Password</label>
          <input id="p" type="password" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required /></div>
        <button className="btn" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
      </div></form>
      <p className="muted small">Need help signing in? See the <Link to="/help">Help page</Link>.</p>
    </div>
  );
}

const groupId = (v) => v.replace(/(\d{4})(?=\d)/g, '$1 ');

export function StudentLogin() {
  const done = useAfterLogin();
  const [apaar, setApaar] = useState('');
  const [otp, setOtp] = useState('');
  const [step, setStep] = useState(1);
  const [info, setInfo] = useState({ message: '', name: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  async function send(e) {
    e.preventDefault();
    setErr('');
    if (apaar.length !== 12) { setErr('Enter all 12 digits of the APAAR ID.'); return; }
    setBusy(true);
    try {
      const r = await api('/api/auth/student/request-otp', { method: 'POST', body: { apaarId: apaar } });
      setInfo({ message: r.message || '', name: r.name || '' });
      setOtp('');
      setStep(2);
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  async function verify(e) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      done(await api('/api/auth/student/verify-otp', { method: 'POST', body: { apaarId: apaar, otp } }));
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }
  return (
    <div className="narrow">
      <PageHead title="Student sign-in" sub={step === 1 ? 'Enter your 12-digit APAAR ID to receive a one-time code.' : 'Enter the 6-digit one-time code to continue.'} />
      <Msg kind="err">{err}</Msg>
      {step === 1 ? (
        <form onSubmit={send} className="panel"><div className="panel-body">
          <div className="field"><label htmlFor="a">APAAR ID <span className="hint">(12 digits)</span></label>
            <input id="a" type="text" inputMode="numeric" autoComplete="off" autoFocus placeholder="1000 0000 0001" value={groupId(apaar)}
              onChange={(e) => setApaar(e.target.value.replace(/\D/g, '').slice(0, 12))} required aria-describedby="a-help" /></div>
          <p id="a-help" className="hint" style={{ margin: '-.4rem 0 1rem' }}>{apaar.length}/12 digits</p>
          <button className="btn" disabled={busy || apaar.length !== 12}>{busy ? 'Sending...' : 'Send one-time code'}</button>
        </div></form>
      ) : (
        <form onSubmit={verify} className="panel"><div className="panel-body">
          <p className="otp-for">Signing in as <strong>{info.name || groupId(apaar)}</strong><br /><span className="muted small">APAAR ID {groupId(apaar)}</span></p>
          <div className="field"><label htmlFor="o">One-time code</label>
            <input id="o" type="text" inputMode="numeric" maxLength={6} autoComplete="one-time-code" autoFocus value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} required /></div>
          <div className="actions">
            <button className="btn" disabled={busy || otp.length !== 6}>{busy ? 'Checking...' : 'Sign in'}</button>
            <button type="button" className="btn secondary" onClick={() => { setStep(1); setErr(''); }}>Use a different APAAR ID</button>
          </div>
        </div></form>
      )}
    </div>
  );
}

export function AccessLogin() {
  const done = useAfterLogin();
  const [qs] = useSearchParams();
  const [tab, setTab] = useState(qs.get('tab') === 'register' ? 'register' : 'signin');
  const [f, setF] = useState({ username: '', password: '' });
  const [r, setR] = useState({ fullName: '', email: '', password: '', organisation: '', orgRegId: '' });
  const [v, setV] = useState({ email: '', otp: '' });
  const [err, setErr] = useState('');
  const [ok, setOk] = useState('');

  async function signin(e) {
    e.preventDefault(); setErr(''); setOk('');
    try {
      const s = await api('/api/auth/login', { method: 'POST', body: f });
      if (s.role !== 'REQUESTER') throw new Error('This sign-in is for requesters. Staff and students use their own pages.');
      done(s);
    } catch (ex) { setErr(ex.message); }
  }
  async function register(e) {
    e.preventDefault(); setErr(''); setOk('');
    try {
      const m = await api('/api/auth/requester/register', { method: 'POST', body: r });
      setOk(m.message);
      setV({ email: r.email, otp: '' });
      setTab('verify');
    } catch (ex) { setErr(ex.message); }
  }
  async function verify(e) {
    e.preventDefault(); setErr(''); setOk('');
    try {
      const m = await api('/api/auth/requester/verify', { method: 'POST', body: v });
      setOk(m.message);
      setF({ username: v.email, password: '' });
      setTab('signin');
    } catch (ex) { setErr(ex.message); }
  }
  return (
    <div className="narrow">
      <PageHead title="Student-Controlled Access" sub="For employers, regulators and individuals. Students approve every request." />
      <div className="tabs" role="tablist">
        {[['signin', 'Sign in'], ['register', 'Register'], ['verify', 'Verify email']].map(([k, label]) => (
          <button key={k} role="tab" aria-selected={tab === k} onClick={() => { setTab(k); setErr(''); setOk(''); }}>{label}</button>
        ))}
      </div>
      <Msg kind="err">{err}</Msg>
      <Msg kind="ok">{ok}</Msg>
      {tab === 'signin' && (
        <form onSubmit={signin} className="panel"><div className="panel-body">
          <div className="field"><label htmlFor="su">Email address (username)</label>
            <input id="su" type="text" autoComplete="username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} required /></div>
          <div className="field"><label htmlFor="sp">Password</label>
            <input id="sp" type="password" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required /></div>
          <button className="btn">Sign in</button>
        </div></form>
      )}
      {tab === 'register' && (
        <form onSubmit={register} className="panel"><div className="panel-body">
          <div className="field"><label htmlFor="rn">Full name</label>
            <input id="rn" type="text" value={r.fullName} onChange={(e) => setR({ ...r, fullName: e.target.value })} required /></div>
          <div className="field"><label htmlFor="re">Email address</label>
            <input id="re" type="email" value={r.email} onChange={(e) => setR({ ...r, email: e.target.value })} required /></div>
          <div className="field"><label htmlFor="rp">Password <span className="hint">(at least 8 characters)</span></label>
            <input id="rp" type="password" autoComplete="new-password" value={r.password} onChange={(e) => setR({ ...r, password: e.target.value })} required /></div>
          <div className="field"><label htmlFor="ro">Organisation <span className="hint">(optional; needed for verified status)</span></label>
            <input id="ro" type="text" value={r.organisation} onChange={(e) => setR({ ...r, organisation: e.target.value })} /></div>
          <div className="field"><label htmlFor="rg">Organisation registration number <span className="hint">(optional)</span></label>
            <input id="rg" type="text" value={r.orgRegId} onChange={(e) => setR({ ...r, orgRegId: e.target.value })} /></div>
          <Msg kind="info">New accounts are "unverified individual" accounts and can request only limited information for a short time.
            An AICTE administrator can mark an organisation as verified.</Msg>
          <button className="btn">Create account</button>
        </div></form>
      )}
      {tab === 'verify' && (
        <form onSubmit={verify} className="panel"><div className="panel-body">
          <div className="field"><label htmlFor="ve">Email address</label>
            <input id="ve" type="email" value={v.email} onChange={(e) => setV({ ...v, email: e.target.value })} required /></div>
          <div className="field"><label htmlFor="vo">Verification code</label>
            <input id="vo" type="text" inputMode="numeric" maxLength={6} value={v.otp} onChange={(e) => setV({ ...v, otp: e.target.value.replace(/\D/g, '') })} required /></div>
          <button className="btn">Verify email</button>
        </div></form>
      )}
    </div>
  );
}

/* Reads the QR code out of an uploaded certificate image and returns its link parameters. */
function readQrFromImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      try {
        const scales = [1, 0.6, 1.5];
        for (const sc of scales) {
          const w = Math.min(2400, Math.round(img.width * sc));
          const h = Math.round(img.height * (w / img.width));
          const cv = document.createElement('canvas');
          cv.width = w; cv.height = h;
          const ctx = cv.getContext('2d', { willReadFrequently: true });
          ctx.drawImage(img, 0, 0, w, h);
          const code = jsQR(ctx.getImageData(0, 0, w, h).data, w, h, { inversionAttempts: 'attemptBoth' });
          if (code && code.data) {
            const u = new URL(code.data, window.location.origin);
            const p = u.searchParams.get('p'); const sg = u.searchParams.get('s');
            if (p && sg) { URL.revokeObjectURL(url); resolve({ p, s: sg }); return; }
            URL.revokeObjectURL(url); reject(new Error('A QR code was found, but it is not an EduTrace certificate.')); return;
          }
        }
        URL.revokeObjectURL(url);
        reject(new Error('No QR code could be read. Upload a clear, uncropped image that shows the whole QR code.'));
      } catch (e) { URL.revokeObjectURL(url); reject(new Error('That QR code could not be read as a certificate.')); }
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('That file could not be opened as an image. Use a PNG or JPG.')); };
    img.src = url;
  });
}

export function VerifyPage() {
  const [params] = useSearchParams();
  const p = params.get('p');
  const s = params.get('s');
  const [manual, setManual] = useState({ p: '', s: '' });
  const [target, setTarget] = useState(p && s ? { p, s } : null);
  const state = useLoad(() => (target ? api(`/api/public/verify?p=${encodeURIComponent(target.p)}&s=${encodeURIComponent(target.s)}`) : Promise.resolve(null)), [target && target.p, target && target.s]);
  const res = state.data;
  const [upErr, setUpErr] = useState('');
  const [reading, setReading] = useState(false);
  const fileRef = useRef(null);
  async function onFile(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    setUpErr(''); setReading(true);
    try { setTarget(await readQrFromImage(file)); } catch (ex) { setUpErr(ex.message); } finally { setReading(false); if (fileRef.current) fileRef.current.value = ''; }
  }
  return (
    <>
      <PageHead title="Verify a signed record" sub="Checks that a record issued by this system is genuine and has not been altered." />
      {!target && (
        <Panel title="Upload a certificate image">
          <p style={{ marginTop: 0 }}>Upload a photo or screenshot of the certificate. EduTrace reads the QR code on it and checks the signature with the server. No login is needed.</p>
          <div className="field"><label htmlFor="cimg">Certificate image <span className="hint">(PNG or JPG, showing the whole QR code)</span></label>
            <input id="cimg" ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={onFile} /></div>
          {reading && <p className="muted" role="status">Reading the QR code...</p>}
          <Msg kind="err">{upErr}</Msg>
        </Panel>
      )}
      {!target && (
        <Panel title="Or paste a record">
          <form onSubmit={(e) => { e.preventDefault(); setTarget({ p: manual.p.trim(), s: manual.s.trim() }); }}>
            <div className="field"><label htmlFor="mp">Encoded record</label><textarea id="mp" value={manual.p} onChange={(e) => setManual({ ...manual, p: e.target.value })} required /></div>
            <div className="field"><label htmlFor="ms">Signature</label><input id="ms" type="text" value={manual.s} onChange={(e) => setManual({ ...manual, s: e.target.value })} required /></div>
            <button className="btn">Verify</button>
          </form>
          <p className="muted small" style={{ marginTop: '.75rem' }}>Normally you arrive here by scanning a QR code, which fills these values in for you.</p>
        </Panel>
      )}
      {target && <Loading state={state} />}
      {res && res.valid && !res.expired && (
        <Msg kind="ok" title="Genuine record. ">The signature matches. The details below were issued by this system and have not been changed.</Msg>
      )}
      {res && res.valid && res.expired && <Msg kind="warn" title="Genuine but no longer valid. ">{res.message}</Msg>}
      {res && !res.valid && <Msg kind="err" title="Not valid. ">{res.message}</Msg>}
      {res && res.payload && (
        <Panel title="Record contents"><pre>{JSON.stringify(res.payload, null, 2)}</pre></Panel>
      )}
      {target && <p><button className="linkbtn" onClick={() => setTarget(null)}>Check another record</button></p>}
    </>
  );
}

export function Privacy() {
  return (
    <>
      <PageHead title="Privacy policy" sub="How EduTrace handles personal data." />
      <Panel title="What is collected">
        <p>Only what is needed to monitor enrolment against approved intake: APAAR ID, name, date of birth, gender, course, admission year, admission category,
          seat type, residency status (Indian, NRI, OCI, foreign national), admission mode, enrolment status and the institution's roll number.</p>
        <p>The system does not collect Aadhaar numbers, marks, addresses or phone numbers. Email addresses are taken from the APAAR registry for notifications and are not stored in enrolment records.</p>
      </Panel>
      <Panel title="Who can see it">
        <ul>
          <li><strong>Institutions</strong> see only their own students.</li>
          <li><strong>AICTE administrators</strong> see institution-level and record-level data needed for monitoring; every action is logged.</li>
          <li><strong>Students</strong> see their own records and a log of every access to them.</li>
          <li><strong>Employers, regulators and individuals</strong> see nothing unless the student approves a specific request. A verified organisation can be offered a few fields for up to 30 days. An unverified individual can be offered only whether the student is enrolled and at which institution, for up to one day.</li>
        </ul>
      </Panel>
      <Panel title="Consent and control">
        <p>Every request names the requester, their trust level, the purpose, the fields and the duration. Silence is never consent: unanswered requests expire after 48 hours.
          Students can narrow the fields, shorten the period, revoke an approval later and block a requester.</p>
      </Panel>
      <Panel title="Audit trail">
        <p>Uploads, corrections, approvals and views are written to an append-only log in which each entry carries a hash of the previous one, so later alteration can be detected.</p>
      </Panel>
    </>
  );
}

export function Accessibility() {
  return (
    <>
      <PageHead title="Accessibility statement" />
      <Panel title="Our approach">
        <p>The interface is built toward the Guidelines for Indian Government Websites and Apps (GIGW 3.0) and WCAG 2.1 level AA.</p>
        <ul>
          <li>A "skip to main content" link and a logical heading structure</li>
          <li>Full keyboard operation with a visible focus outline</li>
          <li>Text size controls (A-, A, A+) and a high-contrast display option</li>
          <li>English and Hindi labels for navigation and key headings, with a Devanagari-capable font</li>
          <li>Status shown in words as well as colour; data presented in real tables with headers</li>
          <li>Responsive layout for phones and tablets</li>
        </ul>
      </Panel>
      <Msg kind="info" title="Feedback. ">If something on this site is hard to use, please tell your institution or AICTE help desk. Hindi translation currently covers navigation and key headings, not every message.</Msg>
    </>
  );
}

export function Help() {
  return (
    <>
      <PageHead title="Help" sub="Who can sign in, and how each part of EduTrace works." />
      <Panel title="Signing in">
        <ul>
          <li><strong>Institutions and AICTE administrators</strong> use the staff sign-in with the username and password issued to them.</li>
          <li><strong>Students</strong> sign in with their 12-digit APAAR ID and a one-time code. You do not need to register: your record appears once your institution uploads it.</li>
          <li><strong>Employers and individuals</strong> create an account, verify it with a code, and then ask a student for access.</li>
        </ul>
        <p><Link className="btn" to="/signin">Login / Sign up</Link></p>
      </Panel>
      <Panel title="Common questions">
        <dl className="faq">
          <dt>How do I check a certificate?</dt>
          <dd>Scan the QR code printed on it, or open <Link to="/verify">Verify a certificate</Link> and paste the link. No login is needed.</dd>
          <dt>Who can see my details?</dt>
          <dd>Only your institution and AICTE. Anyone else must send you a request under "Access requests". You choose which details to share and for how long, and you can withdraw access at any time.</dd>
          <dt>My record is wrong. What do I do?</dt>
          <dd>Open "My records" and choose "Report a mismatch". Your institution and AICTE are asked to correct it.</dd>
          <dt>I appear at two institutions.</dt>
          <dd>Both institutions and AICTE are asked to resolve it within 14 days. You can follow the case under "My records".</dd>
          <dt>What happens to an access request I ignore?</dt>
          <dd>It expires after 48 hours. Silence is never treated as consent.</dd>
        </dl>
      </Panel>
    </>
  );
}

export function Terms() {
  return (
    <>
      <PageHead title="Terms of use" />
      <Panel>
        <p>EduTrace helps AICTE-approved institutions, students and verifiers work from one verified enrolment record linked to an APAAR ID. It is not an official Government of India website.</p>
        <p>Use the service only for the purpose you state. Do not try to access records you have not been given access to. All access is logged.</p>
      </Panel>
    </>
  );
}
