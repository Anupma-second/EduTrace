import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { translate } from './i18n.js';

const Ctx = createContext(null);

function readUser() {
  try { return JSON.parse(localStorage.getItem('sdms_user') || 'null'); } catch { return null; }
}

export function AppProvider({ children }) {
  const [user, setUser] = useState(readUser());
  const [lang, setLang] = useState(localStorage.getItem('sdms_lang') || 'en');
  const [size, setSize] = useState(Number(localStorage.getItem('sdms_size') || 16));
  const [contrast, setContrast] = useState(localStorage.getItem('sdms_contrast') || 'normal');

  useEffect(() => {
    document.documentElement.style.setProperty('--fs', size + 'px');
    document.documentElement.setAttribute('data-contrast', contrast);
    document.documentElement.lang = lang === 'hi' ? 'hi' : 'en';
    localStorage.setItem('sdms_size', String(size));
    localStorage.setItem('sdms_contrast', contrast);
    localStorage.setItem('sdms_lang', lang);
  }, [size, contrast, lang]);

  const login = useCallback((session) => {
    const { token, ...profile } = session;
    localStorage.setItem('sdms_token', token);
    localStorage.setItem('sdms_user', JSON.stringify(profile));
    setUser(profile);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('sdms_token');
    localStorage.removeItem('sdms_user');
    setUser(null);
  }, []);

  const value = useMemo(() => ({
    user, login, logout, lang, setLang, size, setSize, contrast, setContrast,
    t: (text) => translate(lang, text),
  }), [user, lang, size, contrast, login, logout]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useApp() {
  return useContext(Ctx);
}
