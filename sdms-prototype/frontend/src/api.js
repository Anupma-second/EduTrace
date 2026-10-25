const BASE = import.meta.env.VITE_API_BASE || '';

export function getToken() {
  return localStorage.getItem('sdms_token');
}

export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;
  let payload;
  if (form) payload = form;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(BASE + path, { method, headers, body: payload });
  } catch (e) {
    throw new Error('Cannot reach the server. Check that the backend is running on port 8080.');
  }
  if (res.status === 401 && token) {
    localStorage.removeItem('sdms_token');
    localStorage.removeItem('sdms_user');
    window.location.href = '/?session=expired';
    throw new Error('Your session has ended. Please sign in again.');
  }
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!res.ok) {
    const msg = data && typeof data === 'object' && data.error ? data.error : `Request failed (${res.status}).`;
    throw new Error(msg);
  }
  return data;
}

export async function download(path, filename) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = 'Bearer ' + token;
  const res = await fetch(BASE + path, { headers });
  if (!res.ok) throw new Error('Download failed.');
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
