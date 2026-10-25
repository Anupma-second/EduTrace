import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useApp } from '../auth.jsx';
import { LogoMark } from '../components/Logo.jsx';
import RoleArt from '../components/RoleArt.jsx';
import '../home.css';

/* ---------- small inline icons (stroke, currentColor) ---------- */
const I = ({ children, size = 22 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{children}</svg>
);
const Tick = () => <I size={18}><path d="M5 12.5l4.2 4L19 7.5" /></I>;
const ICONS = {
  upload: <I><path d="M12 16V4" /><path d="M7 9l5-5 5 5" /><path d="M4 16v3a1 1 0 001 1h14a1 1 0 001-1v-3" /></I>,
  check: <I><path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z" /><path d="M9 12l2.2 2.2L15.5 10" /></I>,
  user: <I><circle cx="12" cy="8" r="3.6" /><path d="M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5" /></I>,
  eye: <I><path d="M2 12s3.5-6.5 10-6.5S22 12 22 12s-3.5 6.5-10 6.5S2 12 2 12z" /><circle cx="12" cy="12" r="2.8" /></I>,
  key: <I><circle cx="8" cy="15" r="4" /><path d="M11 12l9-9" /><path d="M17 6l3 3" /></I>,
  copy: <I><rect x="8" y="8" width="12" height="12" rx="1.5" /><path d="M16 8V5a1 1 0 00-1-1H5a1 1 0 00-1 1v10a1 1 0 001 1h3" /></I>,
  chart: <I><path d="M4 20V4" /><path d="M4 20h16" /><path d="M8 16v-4M12 16V8M16 16v-6" /></I>,
  log: <I><rect x="5" y="3" width="14" height="18" rx="1.5" /><path d="M9 8h6M9 12h6M9 16h4" /></I>,
  qr: <I><rect x="4" y="4" width="6" height="6" /><rect x="14" y="4" width="6" height="6" /><rect x="4" y="14" width="6" height="6" /><path d="M14 14h3v3h-3zM19 14v6M14 20h3" /></I>,
  lock: <I><rect x="5" y="11" width="14" height="9" rx="1.5" /><path d="M8 11V8a4 4 0 018 0v3" /></I>,
  warn: <I><path d="M12 4l9 16H3z" /><path d="M12 10v4M12 17.2v.1" /></I>,
  arrowDown: <I size={16}><path d="M12 5v14" /><path d="M6 13l6 6 6-6" /></I>,
  arrowRight: <I size={20}><path d="M4 12h16" /><path d="M14 6l6 6-6 6" /></I>,
  down: <I size={20}><path d="M12 4v14" /><path d="M6 13l6 6 6-6" /></I>,
};

/* ---------- Hero ---------- */
function HeroArt() {
  return (
    <svg className="hm-art" viewBox="0 0 600 450" role="img" aria-label="Illustration: an enrolment record card with a verified tick, a QR code and a padlock card">
      {/* soft disc with the EduTrace mark as a watermark */}
      <circle cx="390" cy="205" r="200" fill="#e4ebf6" />
      <circle cx="390" cy="205" r="187" fill="none" stroke="#fff" strokeWidth="1.5" opacity=".7" />
      <g opacity=".85" fill="none" stroke="#f6f9fd" strokeLinecap="round" strokeLinejoin="round">
        <path d="M390 78L492 122L390 166L288 122Z" fill="#f6f9fd" stroke="none" />
        <path d="M330 150V178Q390 204 450 178V150L390 172Z" fill="#edf2fa" stroke="none" />
        <path d="M486 128V176" strokeWidth="3" /><circle cx="486" cy="184" r="7" fill="#f6f9fd" stroke="none" />
        <path d="M330 214V340" strokeWidth="16" />
        <path d="M330 220H398L414 204H430" strokeWidth="9" /><circle cx="444" cy="204" r="13" strokeWidth="6" />
        <path d="M330 280H420" strokeWidth="9" /><circle cx="434" cy="280" r="13" strokeWidth="6" />
        <path d="M330 336H410" strokeWidth="9" /><circle cx="424" cy="336" r="13" strokeWidth="6" />
      </g>

      {/* enrolment record card */}
      <g className="hm-card">
        <rect x="24" y="140" width="320" height="180" rx="16" fill="#f7f9fc" stroke="#dbe3ef" />
        <path d="M24 182V156a16 16 0 0116-16h288a16 16 0 0116 16v26z" fill="#1f4e9c" />
        <text x="46" y="167" fontSize="13" fontWeight="700" fill="#fff" fontFamily="Noto Sans, sans-serif">Enrolment record</text>
        <circle cx="70" cy="225" r="24" fill="#e6eef9" /><circle cx="70" cy="225" r="9" fill="#1f4e9c" />
        <rect x="110" y="212" width="150" height="9" rx="4.5" fill="#c3cad6" />
        <rect x="110" y="232" width="100" height="9" rx="4.5" fill="#d6dce6" />
        <text x="46" y="276" fontSize="10.5" fill="#4e586b" fontFamily="Noto Sans, sans-serif" letterSpacing=".6">APAAR ID</text>
        <text x="46" y="298" fontSize="16" fontWeight="700" fill="#12284d" fontFamily="Noto Sans, sans-serif" letterSpacing=".4">1000 0000 0001</text>
        <rect x="278" y="258" width="44" height="44" rx="6" fill="#f7f9fc" stroke="#dbe3ef" />
        <rect x="286" y="266" width="11" height="11" fill="#183564" /><rect x="303" y="266" width="11" height="11" fill="#183564" />
        <rect x="286" y="283" width="11" height="11" fill="#183564" /><rect x="304" y="286" width="5" height="5" fill="#183564" /><rect x="311" y="291" width="4" height="4" fill="#183564" />
      </g>
      <g className="hm-tick">
        <circle cx="352" cy="170" r="30" fill="#e6f4ea" stroke="#17632d" strokeWidth="2" />
        <path d="M338 171l10 10 18-20" fill="none" stroke="#17632d" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
      </g>

      {/* padlock card, joined by a dashed line */}
      <path d="M148 322C152 360 170 386 226 390" fill="none" stroke="#183564" strokeWidth="1.8" strokeDasharray="4 5" />
      <g className="hm-lock">
        <rect x="226" y="346" width="170" height="86" rx="14" fill="#f7f9fc" stroke="#dbe3ef" />
        <rect x="248" y="382" width="32" height="30" rx="5" fill="#183564" />
        <path d="M254 382v-8a10 10 0 0120 0v8" fill="none" stroke="#183564" strokeWidth="4.5" />
        <rect x="298" y="375" width="74" height="8" rx="4" fill="#c3cad6" />
        <rect x="298" y="393" width="46" height="8" rx="4" fill="#d6dce6" />
      </g>
    </svg>
  );
}

function Hero() {
  const { t } = useApp();
  const go = (e) => {
    const el = document.getElementById('how');
    if (el) { e.preventDefault(); el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }); }
  };
  return (
    <section className="hm-hero" aria-labelledby="hm-h1">
      <div className="hm-hero-in">
        <div className="hm-hero-copy">
          <p className="hm-kicker">FOR AICTE-APPROVED INSTITUTIONS, STUDENTS AND VERIFIERS</p>
          <h1 id="hm-h1">Every AICTE student, traced to one <span>verified APAAR ID</span></h1>
          <p className="hm-lead">Institutions upload enrolment records. EduTrace checks them as they arrive. Students decide who else may see their details.</p>
          <ul className="hm-chips" aria-label="Key ideas">
            <li>One ID per student</li><li>Checked on arrival</li><li>Student consent</li>
          </ul>
          <a className="hm-seelink" href="#how" onClick={go}>{t('See how it works')} {ICONS.arrowDown}</a>
        </div>
        <HeroArt />
      </div>
      <div className="hm-facts">
        <ul>
          {['No Aadhaar, marks or phone number stored', 'Students approve every access request', "Audit log that can't be quietly edited"].map((f) => (
            <li key={f}><span className="hm-ticki"><Tick /></span>{f}</li>
          ))}
        </ul>
      </div>
    </section>
  );
}

/* ---------- How it works ---------- */
const STEPS = [
  { key: 'Upload', who: 'Institution', icon: 'upload', head: 'The institution uploads its records',
    pts: ['CSV or JSON, one row per student', 'A dry run shows problems before saving', 'No Aadhaar or marks are kept'],
    result: 'Records enter the system' },
  { key: 'Validate', who: 'EduTrace', icon: 'check', head: 'EduTrace checks every record',
    pts: ['APAAR ID and name matched to the registry', 'Same student in two colleges is flagged', 'Intake checked against approved seats'],
    result: 'Clean records saved, the rest held for review' },
  { key: 'Confirm', who: 'Student', icon: 'user', head: 'The student confirms or disputes',
    pts: ['Logs in with APAAR ID and email OTP', 'Confirms the record or raises a dispute', 'Gets a signed certificate with a QR code'],
    result: 'A student-confirmed record' },
  { key: 'Monitor', who: 'AICTE', icon: 'eye', head: 'AICTE watches the whole picture',
    pts: ['Intake against approved seats, college by college', 'Alerts for duplicates and spikes', 'Every action goes to a hash-chained log'],
    result: 'Problems surface early, with evidence' },
  { key: 'Access', who: 'Employer or individual', icon: 'key', head: 'Others get in only with consent',
    pts: ['A verified requester asks for named fields', 'The student approves, picks fields and a duration', 'Access ends by itself and every view is logged'],
    result: 'Only what the student allowed, only for as long' },
];

function HowItWorks() {
  const [i, setI] = useState(0);
  const [play, setPlay] = useState(true);
  const [seen, setSeen] = useState(false);
  const [visible, setVisible] = useState(false);
  const [hold, setHold] = useState(false);
  const secRef = useRef(null);
  const btnRefs = useRef([]);
  const reduce = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  useEffect(() => {
    const el = secRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') { setVisible(true); return undefined; }
    const io = new IntersectionObserver(([e]) => setVisible(e.isIntersecting), { threshold: 0.35 });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  useEffect(() => {
    if (!play || reduce || !visible || hold) return undefined;
    const id = setInterval(() => setI((n) => (n + 1) % STEPS.length), 4500);
    return () => clearInterval(id);
  }, [play, reduce, visible, hold]);
  function onKey(e) {
    const k = e.key;
    let n = null;
    if (k === 'ArrowRight' || k === 'ArrowDown') n = (i + 1) % STEPS.length;
    else if (k === 'ArrowLeft' || k === 'ArrowUp') n = (i - 1 + STEPS.length) % STEPS.length;
    else if (k === 'Home') n = 0;
    else if (k === 'End') n = STEPS.length - 1;
    if (n === null) return;
    e.preventDefault(); setI(n); setPlay(false); setSeen(true);
    if (btnRefs.current[n]) btnRefs.current[n].focus();
  }
  const pick = (n) => { setI(n); setPlay(false); setSeen(true); };
  const st = STEPS[i];
  return (
    <section id="how" className="hm-sec hm-howsec" aria-labelledby="how-h" ref={secRef}>
      <div className="hm-container">
        <div className="hm-sechead center">
          <p className="hm-kicker">HOW IT WORKS</p>
          <h2 id="how-h">From upload to consent, in five steps</h2>
          <p className="hm-sub">Click a step, use the arrow keys, or just watch it play.</p>
        </div>

        <ol className="hm-track" aria-label="Steps. Use the arrow keys to move between steps." style={{ '--n': STEPS.length, '--i': i }} onKeyDown={onKey}
          onMouseEnter={() => setHold(true)} onMouseLeave={() => setHold(false)} onFocus={() => setHold(true)} onBlur={() => setHold(false)}>
          <li className="hm-rail" aria-hidden="true"><i style={{ width: `${(i / (STEPS.length - 1)) * 100}%` }} /></li>
          {STEPS.map((s, n) => (
            <li key={s.key} className="hm-stepwrap">
              <button type="button" ref={(el) => { btnRefs.current[n] = el; }} tabIndex={n === i ? 0 : -1}
                className={n === i ? 'hm-step on' : n < i ? 'hm-step done' : 'hm-step'}
                aria-current={n === i ? 'step' : undefined} onClick={() => pick(n)}>
                <span className="hm-step-ic">{n < i ? <Tick /> : ICONS[s.icon]}</span>
                <strong>{s.key}</strong>
                <small>{s.who}</small>
              </button>
            </li>
          ))}
        </ol>

        <div className="hm-stage" key={i} aria-live="polite">
          <div className="hm-stage-head">
            <span className="hm-step-no">STEP {i + 1} OF 5</span>
            <h3>{st.head}</h3>
          </div>
          <ul className="hm-ticks">
            {st.pts.map((p) => <li key={p}><span className="hm-ticki"><Tick /></span>{p}</li>)}
          </ul>
          <p className="hm-result"><span>Result</span> {st.result}</p>
        </div>

        <div className="hm-ctrl">
          <button type="button" className="hm-cbtn" disabled={i === 0} onClick={() => pick(i - 1)}>Back</button>
          <button type="button" className="hm-cbtn play" aria-pressed={play} onClick={() => setPlay((p) => !p)} disabled={reduce}>
            {play ? 'Pause' : 'Play'}
          </button>
          <button type="button" className="hm-cbtn main" onClick={() => pick((i + 1) % STEPS.length)}>{i === STEPS.length - 1 ? 'Start again' : 'Next step'}</button>
        </div>
        {seen && <span className="sr-only" role="status">Step {i + 1} of 5: {st.key}</span>}
      </div>
    </section>
  );
}

function SecHead({ id, kicker, title }) {
  const { t } = useApp();
  return (
    <div className="hm-sechead">
      <p className="hm-kicker">{t(kicker).toUpperCase()}</p>
      <h2 id={id}>{t(title)}</h2>
    </div>
  );
}

/* ---------- Who can participate ---------- */
const ROLES = [
  { name: 'Institutions', line: 'AICTE-approved colleges that enrol the students.', to: '/login/staff', cta: 'Institution login',
    pts: ['Upload enrolment records in bulk', 'See errors before saving', 'Track intake against approved seats'], never: 'Cannot see another institution’s students.',
    art: 'institution' },
  { name: 'AICTE administrators', line: 'The regulator that watches the numbers.', to: '/login/staff', cta: 'Administrator login',
    pts: ['Alerts for duplicates and spikes', 'Review disputes and watchlist cases', 'Verify the audit trail'], never: 'Cannot edit a record or the audit log.',
    art: 'admin' },
  { name: 'Students', line: 'The people the data is about, in control of it.', to: '/login/student', cta: 'Student login',
    pts: ['Confirm or dispute the record', 'Approve or refuse each request', 'Download a certificate with QR'], never: 'Never needs to share Aadhaar or marks.',
    art: 'student' },
  { name: 'Employers and individuals', line: 'Anyone who needs to check a student’s details.', to: '/login/access', cta: 'Employer or individual login',
    pts: ['Verify a certificate with no login', 'Ask a student for named fields', 'See only what was approved'], never: 'Cannot browse or search students.',
    art: 'requester' },
];

function Who() {
  return (
    <section id="who" className="hm-sec alt" aria-labelledby="who-h">
      <div className="hm-container">
        <SecHead id="who-h" kicker="Who can participate" title="Four kinds of users" />
        <div className="hm-roles">
          {ROLES.map((r) => (
            <article key={r.name} className="hm-role">
              <RoleArt kind={r.art} />
              <div className="hm-role-body">
                <h3>{r.name}</h3>
                <p>{r.line}</p>
                <ul className="hm-ticks sm">{r.pts.map((p) => <li key={p}><span className="hm-ticki"><Tick /></span>{p}</li>)}</ul>
                <p className="hm-never"><I size={16}><circle cx="12" cy="12" r="9" /><path d="M5.6 5.6l12.8 12.8" /></I> {r.never}</p>
                <Link className="hm-rolelink" to={r.to}>{r.cta} {ICONS.arrowRight}</Link>
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ---------- Why it matters ---------- */
const WHY = [
  ['Double enrolment', 'The same student can be enrolled in two colleges at once.', 'Each APAAR ID is matched across institutions, and duplicates go to a review case.', 'copy'],
  ['Over-admission', 'Colleges can admit more students than their approved seats.', 'Intake is compared with approved seats, and a supernumerary alert is raised.', 'chart'],
  ['No consent', 'Records are shared by email or paper, with no consent.', 'Students approve each request, choose the fields and set an end date.', 'lock'],
  ['Forgery', 'A certificate can be forged, and edits leave no trace.', 'Certificates carry a signed QR code, and the audit log is hash-chained.', 'qr'],
];
function Why() {
  const [open, setOpen] = useState(0);
  return (
    <section id="why" className="hm-sec" aria-labelledby="why-h">
      <div className="hm-container">
        <div className="hm-sechead center">
          <p className="hm-kicker">WHY IT MATTERS</p>
          <h2 id="why-h">What goes wrong today, and the fix</h2>
          <p className="hm-sub">Pick a problem to see how EduTrace fixes it.</p>
        </div>
        <div className="hm-whygrid">
          {WHY.map(([name, gap, fix, ic], n) => {
            const on = open === n;
            return (
              <div key={name} className={on ? 'hm-whycard on' : 'hm-whycard'}>
                <button type="button" aria-expanded={on} aria-controls={`why-${n}`} onClick={() => setOpen(on ? -1 : n)}>
                  <span className="hm-why-ic">{ICONS[ic]}</span>
                  <span className="hm-why-tx"><span className="hm-tag red">Problem</span><strong>{name}</strong></span>
                  <span className="hm-why-chev" aria-hidden="true"><I size={18}><path d="M6 9l6 6 6-6" /></I></span>
                </button>
                <p className="hm-gap-t">{gap}</p>
                <div id={`why-${n}`} className="hm-fixwrap" hidden={!on}>
                  <div className="hm-fix"><span className="hm-tag green">With EduTrace</span><p>{fix}</p></div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* ---------- What it does ---------- */
const WHAT = [
  ['upload', 'Bulk upload', 'CSV or JSON, with a dry run first.'],
  ['check', 'Automatic checks', 'Registry match, format and seat limits.'],
  ['copy', 'Duplicate detection', 'Same APAAR ID in two places is caught.'],
  ['chart', 'Intake monitoring', 'Alerts for spikes and over-admission.'],
  ['user', 'Student confirmation', 'Confirm or dispute your own record.'],
  ['lock', 'Consent-based access', 'Per field, per person, with an end date.'],
  ['qr', 'Signed certificates', 'A QR code anyone can verify without login.'],
  ['log', 'Tamper-evident audit', 'Hash-chained log you can verify.'],
];
function What() {
  return (
    <section id="what" className="hm-sec alt" aria-labelledby="what-h">
      <div className="hm-container">
        <SecHead id="what-h" kicker="What it does" title="Eight things it does" />
        <ul className="hm-what">
          {WHAT.map(([ic, h, p]) => (
            <li key={h}><span className="hm-what-ic">{ICONS[ic]}</span><h3>{h}</h3><p>{p}</p></li>
          ))}
        </ul>
      </div>
    </section>
  );
}

function CtaBand() {
  const { t } = useApp();
  return (
    <section className="hm-cta" aria-labelledby="cta-h">
      <div className="hm-container hm-cta-in">
        <div>
          <h2 id="cta-h">Need to check a certificate?</h2>
          <p>Scan the QR code or open the link. EduTrace tells you whether the record is genuine and unaltered, with no login.</p>
        </div>
        <div className="hm-cta-btns">
          <Link className="btn" to="/verify">{t('Verify a certificate')}</Link>
          <Link className="btn secondary" to="/help">{t('Read the help guide')}</Link>
        </div>
      </div>
    </section>
  );
}

export function Home() {
  return (
    <div className="hm">
      <div className="hm-watermark" aria-hidden="true"><LogoMark size={520} decorative flat /></div>
      <Hero />
      <HowItWorks />
      <Who />
      <Why />
      <What />
      <CtaBand />
    </div>
  );
}
export default Home;
