import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useApp } from '../auth.jsx';
import { LogoMark, Wordmark, TAGLINE } from './Logo.jsx';
import { useDropdown } from './useDropdown.js';

const MENUS = {
  ADMIN: [
    ['/admin', 'Dashboard', true], ['/admin/institutions', 'Institutions'], ['/admin/students', 'Students'], ['/admin/alerts', 'Enrolment alerts'],
    ['/admin/cases', 'Duplicates and disputes'], ['/admin/watchlist', 'Watchlist'], ['/admin/reports', 'Reports'],
    ['/admin/requesters', 'Requesters'], ['/admin/policy', 'Policy rules'], ['/admin/audit', 'Audit log'],
  ],
  INSTITUTION: [
    ['/institution', 'Dashboard', true], ['/institution/upload', 'Upload students'], ['/institution/students', 'Students'],
    ['/institution/cases', 'Duplicate cases'],
  ],
  STUDENT: [
    ['/student/records', 'My records'], ['/student/requests', 'Access requests'], ['/student/activity', 'Activity'],
    ['/student/certificate', 'Certificate'],
  ],
  REQUESTER: [['/access/new', 'New request'], ['/access/requests', 'My requests']],
};

const ROLE_NAMES = { ADMIN: 'AICTE administrator', INSTITUTION: 'Institution', STUDENT: 'Student', REQUESTER: 'Student-Controlled Access' };

const TITLES = {
  '/admin': 'Dashboard', '/admin/institutions': 'Institutions', '/admin/students': 'Students', '/admin/alerts': 'Enrolment alerts', '/admin/cases': 'Duplicates and disputes',
  '/admin/watchlist': 'Watchlist', '/admin/reports': 'Reports', '/admin/requesters': 'Requesters', '/admin/policy': 'Policy rules',
  '/admin/audit': 'Audit log', '/institution': 'Dashboard', '/institution/upload': 'Upload students', '/institution/students': 'Students',
  '/institution/cases': 'Duplicate cases', '/student/records': 'My records', '/student/requests': 'Access requests',
  '/student/activity': 'Activity', '/student/certificate': 'Certificate', '/access/new': 'New request', '/access/requests': 'My requests',
  '/privacy': 'Privacy policy', '/accessibility': 'Accessibility statement', '/help': 'Help', '/terms': 'Terms of use',
  '/verify': 'Verify a signed record', '/signin': 'Login / Sign up', '/login/staff': 'Staff login', '/login/student': 'Student login',
  '/login/access': 'Student-Controlled Access',
};

const Caret = () => (
  <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true" focusable="false"><path d="M2 3.5L5 6.5L8 3.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
);
const Globe = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" /><path d="M3 12h18" /><path d="M12 3c2.6 2.6 3.9 5.6 3.9 9s-1.3 6.4-3.9 9c-2.6-2.6-3.9-5.6-3.9-9S9.4 5.6 12 3z" />
  </svg>
);

function LangMenu() {
  const { lang, setLang, t } = useApp();
  const dd = useDropdown();
  const opts = [['en', 'English'], ['hi', 'हिन्दी']];
  return (
    <div className="dd" ref={dd.ref}>
      <button type="button" data-trigger className="dd-btn lang-btn" aria-haspopup="true" aria-expanded={dd.open}
        aria-label={t('Choose language')} title={t('Choose language')} onClick={dd.toggle}>
        <Globe /><Caret />
      </button>
      {dd.open && (
        <ul className="dd-menu dd-lang" role="menu">
          {opts.map(([code, label]) => (
            <li key={code} role="none">
              <button type="button" role="menuitemradio" aria-checked={lang === code} lang={code}
                onClick={() => { setLang(code); dd.close(); }}>
                <span>{label}</span>
                {lang === code && (
                  <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M3 8.5l3.2 3L13 4.8" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function AuthMenu() {
  const { user, logout, t } = useApp();
  const nav = useNavigate();
  if (user) {
    return (
      <div className="userbox">
        <div><strong>{user.name}</strong></div>
        <div className="muted">
          {ROLE_NAMES[user.role]}{user.institutionName ? ' - ' + user.institutionName : ''}
          {user.role === 'STUDENT' ? ' - ' + user.apaarId : ''}
        </div>
        <div className="userbtns">
          <button type="button" className="btn small" onClick={() => { logout(); nav('/'); }}>{t('Sign out')}</button>
        </div>
      </div>
    );
  }
  return <Link className="btn auth-btn" to="/signin">{t('Login / Sign up')}</Link>;
}

export default function Layout() {
  const { user, t, size, setSize, contrast, setContrast } = useApp();
  const loc = useLocation();
  const menu = user ? MENUS[user.role] || [] : [];
  const title = TITLES[loc.pathname] || (loc.pathname.startsWith('/student/requests/') ? 'Access requests' : '');
  const isHome = loc.pathname === '/';

  // scroll to a section when the URL has a hash (e.g. /#how); otherwise to the top on page change
  useEffect(() => {
    if (loc.hash) {
      const el = document.getElementById(loc.hash.slice(1));
      if (el) { el.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' }); return; }
    }
    window.scrollTo(0, 0);
  }, [loc.pathname, loc.hash]);

  const sectionLink = (hash, label) => (
    <li key={hash}>
      <Link to={{ pathname: '/', hash }} className={isHome && loc.hash === hash ? 'active' : undefined} aria-current={isHome && loc.hash === hash ? 'location' : undefined}>{t(label)}</Link>
    </li>
  );

  return (
    <>
      <a className="skip" href="#main">{t('Skip to main content')}</a>

      <div className="topstrip">
        <div className="wrap">
          <span>EduTrace: APAAR ID-based student data verification for AICTE-approved institutions</span>
          <div className="tools" role="group" aria-label="Display options">
            <button type="button" onClick={() => setSize(Math.max(13, size - 1))} aria-label="Decrease text size">A-</button>
            <button type="button" onClick={() => setSize(16)} aria-label="Normal text size">A</button>
            <button type="button" onClick={() => setSize(Math.min(22, size + 1))} aria-label="Increase text size">A+</button>
            <button type="button" aria-pressed={contrast === 'high'} onClick={() => setContrast(contrast === 'high' ? 'normal' : 'high')}>High contrast</button>
            <LangMenu />
          </div>
        </div>
      </div>

      <header className="header">
        <div className="wrap">
          <Link to="/" className="brand" aria-label="EduTrace home">
            <LogoMark size={54} decorative />
            <span className="brand-txt">
              <Wordmark size={30} />
              <span className="brand-sub">{TAGLINE}</span>
            </span>
          </Link>
          <AuthMenu />
        </div>
      </header>

      <nav className="nav" aria-label="Main">
        <ul>
          <li><NavLink to="/" end className={({ isActive }) => (isActive && !loc.hash ? 'active' : undefined)}>{t('Home')}</NavLink></li>
          {!user && <>
            {sectionLink('#how', 'How it works')}
            {sectionLink('#who', 'Who can participate')}
            {sectionLink('#why', 'Why it matters')}
            {sectionLink('#what', 'What it does')}
            <li><NavLink to="/verify">{t('Verify a certificate')}</NavLink></li>
            <li><NavLink to="/help">{t('Help')}</NavLink></li>
          </>}
          {user && menu.map(([to, label, end]) => (
            <li key={to}><NavLink to={to} end={!!end}>{t(label)}</NavLink></li>
          ))}
        </ul>
      </nav>

      <main id="main" tabIndex={-1}>
        {isHome ? <Outlet /> : (
          <div className="wrap">
            <div className="crumbs" aria-label="Breadcrumb">
              <Link to="/">{t('Home')}</Link>{title && <> &rsaquo; <span aria-current="page">{t(title)}</span></>}
            </div>
            <div className="page"><Outlet /></div>
          </div>
        )}
      </main>

      <footer className="footer">
        <div className="wrap">
          <div className="cols">
            <div className="foot-brand">
              <div className="foot-logo"><LogoMark size={44} decorative /><Wordmark size={26} light /></div>
              <p>One verified APAAR ID for every AICTE student. Institutions upload, students consent, verifiers check.</p>
            </div>
            <div>
              <h2>About</h2>
              <ul>
                <li><Link to="/help">{t('Help')}</Link></li>
                <li><Link to="/privacy">{t('Privacy policy')}</Link></li>
                <li><Link to="/accessibility">{t('Accessibility statement')}</Link></li>
                <li><Link to="/terms">{t('Terms of use')}</Link></li>
                <li><Link to="/verify">{t('Verify a certificate')}</Link></li>
              </ul>
            </div>
            <div>
              <h2>{t('Sign in as')}</h2>
              <ul>
                <li><Link to="/login/staff">{t('Institution login')}</Link></li>
                <li><Link to="/login/staff">{t('AICTE administrator login')}</Link></li>
                <li><Link to="/login/student">{t('Student login')}</Link></li>
                <li><Link to="/login/access">{t('Employer or individual login')}</Link></li>
              </ul>
            </div>
          </div>
          <div className="legal">
            EduTrace - APAAR ID-based student data verification for AICTE-approved institutions. Not an official Government of India website.
            Last updated: {__BUILD_DATE__}.
          </div>
        </div>
      </footer>
    </>
  );
}
