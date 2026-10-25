import { Link } from 'react-router-dom';
import { PageHead } from '../components/ui.jsx';
import RoleArt from '../components/RoleArt.jsx';

const Arrow = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M4 12h16" /><path d="M14 6l6 6-6 6" /></svg>
);

const CARDS = [
  { kind: 'institution', title: 'Institution', line: 'Upload enrolment records and track your intake.', to: '/login/staff', cta: 'Institution login' },
  { kind: 'admin', title: 'AICTE administrator', line: 'Monitor alerts, duplicates and the audit log.', to: '/login/staff', cta: 'Administrator login' },
  { kind: 'student', title: 'Student', line: 'Check your record and decide who can see it.', to: '/login/student', cta: 'Student login' },
  { kind: 'requester', title: 'Employer or individual', line: 'Ask a student for access to named details.', to: '/login/access', cta: 'Employer / individual login' },
];

export default function SignIn() {
  return (
    <>
      <PageHead title="Login / Sign up" sub="Choose who you are. Each type of user has its own sign-in." />
      <ul className="si-grid">
        {CARDS.map((c) => (
          <li key={c.kind}>
            <Link to={c.to} className="si-card">
              <RoleArt kind={c.kind} />
              <span className="si-body">
                <strong>{c.title}</strong>
                <span>{c.line}</span>
                <span className="si-cta">{c.cta} <Arrow /></span>
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <section className="si-signup" aria-labelledby="si-su">
        <div>
          <h2 id="si-su">New here? Sign up</h2>
          <p>Employers and individuals can create an account in a minute. Students and institutions do not sign up: institutions are added by AICTE, and a student appears once an institution uploads their record.</p>
        </div>
        <div className="si-signup-btns">
          <Link className="btn" to="/login/access?tab=register">Create an account</Link>
          <Link className="btn secondary" to="/verify">Verify a certificate (no login)</Link>
        </div>
      </section>
    </>
  );
}
