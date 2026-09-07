/**
 * wattim Browser Extension - Themes Configuration
 * Color palettes and dynamic theme application.
 */

export const THEMES = {
  nord: {
    id: 'nord',
    name: 'Nord',
    icon: '🧊',
    description: 'Арктический минимализм',
    colors: {
      bgPrimary: '#2e3440',
      bgSurface: '#3b4252',
      textPrimary: '#eceff4',
      accent: '#88c0d0',
      accentGlow: 'rgba(136, 192, 208, 0.25)',
      accentSecondary: '#81a1c1',
      success: '#a3be8c',
      error: '#bf616a'
    }
  },
  catppuccin: {
    id: 'catppuccin',
    name: 'Catppuccin Mocha',
    icon: '🌸',
    description: 'Пастельный уют',
    colors: {
      bgPrimary: '#1e1e2e',
      bgSurface: '#313244',
      textPrimary: '#cdd6f4',
      accent: '#cba6f7',
      accentGlow: 'rgba(203, 166, 247, 0.25)',
      accentSecondary: '#b4befe',
      success: '#a6e3a1',
      error: '#f38ba8'
    }
  },
  dracula: {
    id: 'dracula',
    name: 'Dracula',
    icon: '🧛',
    description: 'Вампирский контраст',
    colors: {
      bgPrimary: '#282a36',
      bgSurface: '#44475a',
      textPrimary: '#f8f8f2',
      accent: '#bd93f9',
      accentGlow: 'rgba(189, 147, 249, 0.25)',
      accentSecondary: '#ff79c6',
      success: '#50fa7b',
      error: '#ff5555'
    }
  },
  gruvbox: {
    id: 'gruvbox',
    name: 'Gruvbox Dark',
    icon: '🍂',
    description: 'Теплый ретро-терминал',
    colors: {
      bgPrimary: '#282828',
      bgSurface: '#3c3836',
      textPrimary: '#ebdbb2',
      accent: '#fabd2f',
      accentGlow: 'rgba(250, 189, 47, 0.25)',
      accentSecondary: '#fe8019',
      success: '#b8bb26',
      error: '#fb4934'
    }
  },
  tokyo_night: {
    id: 'tokyo_night',
    name: 'Tokyo Night',
    icon: '🌃',
    description: 'Неоновый киберпанк',
    colors: {
      bgPrimary: '#1a1b26',
      bgSurface: '#24283b',
      textPrimary: '#c0caf5',
      accent: '#7aa2f7',
      accentGlow: 'rgba(122, 162, 247, 0.25)',
      accentSecondary: '#bb9af7',
      success: '#9ece6a',
      error: '#f7768e'
    }
  },
  cyber_terminal: {
    id: 'cyber_terminal',
    name: 'Cyber Terminal',
    icon: '⚡',
    description: 'Изумрудная матрица',
    colors: {
      bgPrimary: '#0a0e14',
      bgSurface: '#131924',
      textPrimary: '#e6edf3',
      accent: '#00ff66',
      accentGlow: 'rgba(0, 255, 102, 0.25)',
      accentSecondary: '#00cc55',
      success: '#00ff66',
      error: '#ff3344'
    }
  }
};

/**
 * Applies the theme to the document element.
 * @param {string} themeId
 */
export function applyTheme(themeId) {
  const selected = THEMES[themeId] ? themeId : 'nord';
  if (typeof document !== 'undefined' && document.documentElement) {
    document.documentElement.setAttribute('data-theme', selected);
  }
  return THEMES[selected];
}
