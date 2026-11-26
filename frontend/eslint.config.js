import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import jsxA11y from 'eslint-plugin-jsx-a11y'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  {
    ignores: ['dist', 'node_modules', 'src/routeTree.gen.ts', 'coverage'],
  },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommendedTypeChecked],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        project: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.configs.recommended.rules,

      // Allow numbers and booleans in template expressions
      '@typescript-eslint/restrict-template-expressions': [
        'error',
        { allowNumber: true, allowBoolean: true },
      ],

      // Allow void for fire-and-forget promises like `void navigate(...)`
      'no-void': 'off',

      // TanStack Router throws redirect objects
      '@typescript-eslint/only-throw-error': 'off',

      // React 19 synthetic event deprecations in type definitions
      '@typescript-eslint/no-deprecated': 'off',

      // Allow async handlers for React onSubmit / onClick
      '@typescript-eslint/no-misused-promises': [
        'error',
        { checksVoidReturn: false },
      ],

      // Allow arrow function returning void expressions
      '@typescript-eslint/no-confusing-void-expression': 'off',

      // Unused vars: allow prefixed with _
      '@typescript-eslint/no-unused-vars': [
        'error',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],

      // Strict on any
      '@typescript-eslint/no-explicit-any': 'error',

      // No console.log in code (only warn and error permitted)
      'no-console': ['error', { allow: ['warn', 'error'] }],

      // Consistent type imports
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'separate-type-imports' },
      ],
    },
  },
  {
    files: ['src/test/**/*.{ts,tsx}', '**/*.test.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
    },
  },
)
