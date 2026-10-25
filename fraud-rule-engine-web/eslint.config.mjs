import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['.angular/**', 'coverage/**', 'dist/**', 'playwright-report/**', 'test-results/**'] },
  {
    files: ['**/*.ts'],
    extends: [tseslint.configs.recommended],
  },
);
