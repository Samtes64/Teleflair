# Contributing to Teleflair

Thank you for your interest in contributing to **Teleflair**! I welcome contributions from developers, designers, and privacy advocates around the world.

---

## 1. Areas Where Help Is Most Needed

> **Note on UI/UX**: The initial release was built with architecture, rendering performance, and privacy as top priorities. I intentionally kept the UI straightforward so the open-source community can help make it shine!

I am especially looking for contributions in:
- **UI & UX Refinements**: Modern design themes, polished color palettes, keycap styles, and smooth micro-interactions.
- **Keyboard Themes**: New themes for OLED black, pastel gradients, dynamic Material You color matching.
- **Language Layouts**: Additional international QWERTY / QWERTZ / AZERTY variants and glide dictionaries.
- **Accessibility Improvements**: Screen reader descriptions and high-contrast accessibility modes.

---

## 2. Development Setup

### 2.1 Prerequisites
- **Flutter SDK**: 3.24.0 or newer
- **Android SDK**: API level 34 or newer
- **Node.js**: 20+ (for backend development)
- **Java**: JDK 17

### 2.2 Local Build Instructions

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Samtes64/Teleflair.git
   cd Teleflair
   ```

2. **Install Flutter dependencies**:
   ```bash
   flutter pub get
   ```

3. **Run automated test suites**:
   ```bash
   flutter test
   npm --prefix backend test
   ```

4. **Run static analysis**:
   ```bash
   flutter analyze
   ```

5. **Build local debug APK**:
   ```bash
   flutter build apk --debug
   ```

---

## 3. Pull Request Guidelines

1. **Feature Branches**: Create a descriptive branch (e.g., `feat/oled-theme` or `fix/glide-spline`).
2. **Maintain Security & Privacy**: Never add third-party tracking SDKs, analytics beacons, or invasive permissions.
3. **Verify Tests**: Ensure `flutter test` and `npm --prefix backend test` pass with 100% success before submitting.
4. **Clean Commits**: Write clear, imperative commit messages (`feat: ...`, `fix: ...`, `docs: ...`).

---

## 4. Code of Conduct

Be welcoming, respectful, and constructive in all discussions, pull requests, and issues.
