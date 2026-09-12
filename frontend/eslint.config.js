import js from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

export default [
    {
        ignores: ['build/**', 'dist/**', 'coverage/**', 'node_modules/**']
    },
    js.configs.recommended,
    {
        files: ['**/*.{js,jsx}'],
        languageOptions: {
            ecmaVersion: 'latest',
            sourceType: 'module',
            parserOptions: {
                ecmaFeatures: {
                    jsx: true
                }
            },
            globals: {
                ...globals.browser,
                ...globals.es2024
            }
        },
        settings: {
            react: {
                version: 'detect'
            }
        },
        plugins: {
            react,
            'react-hooks': reactHooks
        },
        rules: {
            ...react.configs.recommended.rules,
            ...react.configs['jsx-runtime'].rules,
            'no-unused-vars': [
                'error',
                {
                    argsIgnorePattern: '^_',
                    caughtErrorsIgnorePattern: '^_',
                    varsIgnorePattern: '^_'
                }
            ],
            'react/prop-types': 'off',
            'react-hooks/rules-of-hooks': 'error',
            'react-hooks/exhaustive-deps': 'warn'
        }
    },
    {
        files: ['**/*.test.{js,jsx}', 'src/test/**/*.{js,jsx}', 'vitest.config.js'],
        languageOptions: {
            globals: {
                ...globals.browser,
                ...globals.vitest,
                ...globals.node
            }
        }
    },
    {
        files: ['vite.config.js', 'postcss.config.js', 'tailwind.config.js'],
        languageOptions: {
            globals: {
                ...globals.node
            }
        }
    },
    eslintConfigPrettier
];
