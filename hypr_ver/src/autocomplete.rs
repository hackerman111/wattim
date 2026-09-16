use inquire::autocompletion::{Autocomplete, Replacement};
use inquire::CustomUserError;

/// Автодополнение по классам (WM_CLASS) окон Hyprland и установленным приложениям.
/// Фильтрует список по подстроке без учёта регистра.
#[derive(Clone, Debug)]
pub struct AppClassCompleter {
    known: Vec<String>,
}

impl AppClassCompleter {
    pub fn new(mut known: Vec<String>) -> Self {
        known.sort_by_key(|a| a.to_lowercase());
        known.dedup_by(|a, b| a.eq_ignore_ascii_case(b));
        Self { known }
    }

    pub fn known_classes(&self) -> &[String] {
        &self.known
    }
}

impl Autocomplete for AppClassCompleter {
    fn get_suggestions(&mut self, input: &str) -> Result<Vec<String>, CustomUserError> {
        let needle = input.to_lowercase();
        Ok(self
            .known
            .iter()
            .filter(|c| c.to_lowercase().contains(&needle))
            .cloned()
            .collect())
    }

    fn get_completion(
        &mut self,
        _input: &str,
        highlighted_suggestion: Option<String>,
    ) -> Result<Replacement, CustomUserError> {
        Ok(highlighted_suggestion)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn filters_suggestions_case_insensitively() {
        let items = vec![
            "org.telegram.desktop".to_string(),
            "Firefox".to_string(),
            "google-chrome".to_string(),
            "discord".to_string(),
        ];
        let mut completer = AppClassCompleter::new(items);

        let res = completer.get_suggestions("tele").unwrap();
        assert_eq!(res, vec!["org.telegram.desktop"]);

        let res_upper = completer.get_suggestions("FIRE").unwrap();
        assert_eq!(res_upper, vec!["Firefox"]);

        let res_all = completer.get_suggestions("").unwrap();
        assert_eq!(res_all.len(), 4);
    }

    #[test]
    fn deduplicates_ignoring_case() {
        let items = vec![
            "Firefox".to_string(),
            "firefox".to_string(),
            "discord".to_string(),
        ];
        let completer = AppClassCompleter::new(items);
        assert_eq!(completer.known_classes().len(), 2);
    }
}
