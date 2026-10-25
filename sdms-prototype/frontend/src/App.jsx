import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import { useApp } from './auth.jsx';
import SignIn from './pages/SignIn.jsx';
import { Home, StaffLogin, StudentLogin, AccessLogin, VerifyPage, Privacy, Accessibility, Help, Terms } from './pages/public.jsx';
import { AdminDashboard, AdminInstitutions, AdminStudents, AdminAlerts, AdminCases, AdminWatchlist, AdminReports, AdminRequesters, AdminPolicy, AdminAudit } from './pages/admin.jsx';
import { InstDashboard, InstUpload, InstStudents, InstCases } from './pages/institution.jsx';
import { StudentRecords, StudentRequests, StudentActivity, StudentCertificate } from './pages/student.jsx';
import { RequestNew, RequestList } from './pages/requester.jsx';

function Guard({ role, children }) {
  const { user } = useApp();
  if (!user) return <Navigate to="/" replace />;
  if (user.role !== role) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login/staff" element={<StaffLogin />} />
        <Route path="/login/student" element={<StudentLogin />} />
        <Route path="/login/access" element={<AccessLogin />} />
        <Route path="/verify" element={<VerifyPage />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/accessibility" element={<Accessibility />} />
        <Route path="/help" element={<Help />} />
        <Route path="/terms" element={<Terms />} />

        <Route path="/admin" element={<Guard role="ADMIN"><AdminDashboard /></Guard>} />
        <Route path="/admin/institutions" element={<Guard role="ADMIN"><AdminInstitutions /></Guard>} />
        <Route path="/admin/students" element={<Guard role="ADMIN"><AdminStudents /></Guard>} />
        <Route path="/admin/alerts" element={<Guard role="ADMIN"><AdminAlerts /></Guard>} />
        <Route path="/admin/cases" element={<Guard role="ADMIN"><AdminCases /></Guard>} />
        <Route path="/admin/watchlist" element={<Guard role="ADMIN"><AdminWatchlist /></Guard>} />
        <Route path="/admin/reports" element={<Guard role="ADMIN"><AdminReports /></Guard>} />
        <Route path="/admin/requesters" element={<Guard role="ADMIN"><AdminRequesters /></Guard>} />
        <Route path="/admin/policy" element={<Guard role="ADMIN"><AdminPolicy /></Guard>} />
        <Route path="/admin/audit" element={<Guard role="ADMIN"><AdminAudit /></Guard>} />

        <Route path="/institution" element={<Guard role="INSTITUTION"><InstDashboard /></Guard>} />
        <Route path="/institution/upload" element={<Guard role="INSTITUTION"><InstUpload /></Guard>} />
        <Route path="/institution/students" element={<Guard role="INSTITUTION"><InstStudents /></Guard>} />
        <Route path="/institution/cases" element={<Guard role="INSTITUTION"><InstCases /></Guard>} />

        <Route path="/student/records" element={<Guard role="STUDENT"><StudentRecords /></Guard>} />
        <Route path="/student/requests" element={<Guard role="STUDENT"><StudentRequests /></Guard>} />
        <Route path="/student/requests/:id" element={<Guard role="STUDENT"><StudentRequests /></Guard>} />
        <Route path="/student/activity" element={<Guard role="STUDENT"><StudentActivity /></Guard>} />
        <Route path="/student/certificate" element={<Guard role="STUDENT"><StudentCertificate /></Guard>} />

        <Route path="/access/new" element={<Guard role="REQUESTER"><RequestNew /></Guard>} />
        <Route path="/access/requests" element={<Guard role="REQUESTER"><RequestList /></Guard>} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
