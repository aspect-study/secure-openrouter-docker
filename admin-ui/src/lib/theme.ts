/** Apply theme to <html> and persist to localStorage */
export function applyTheme(dark: boolean) {
  if (dark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
  localStorage.setItem('theme', dark ? 'dark' : 'light')
}

export function isDarkMode(): boolean {
  return document.documentElement.classList.contains('dark')
}

export function loadSavedTheme() {
  const saved = localStorage.getItem('theme')
  applyTheme(saved === 'dark')
}
