import { useCallback, useEffect, useRef, useState } from 'react';

export default function useAdminSectionFeedback() {
    const [sectionErrors, setSectionErrors] = useState({});
    const [sectionSuccess, setSectionSuccess] = useState({});
    const [fieldErrors, setFieldErrors] = useState({});
    const successTimersRef = useRef({});

    useEffect(
        () => () => {
            Object.values(successTimersRef.current).forEach((timerId) => clearTimeout(timerId));
        },
        []
    );

    const clearSectionError = useCallback((section) => {
        setSectionErrors((prev) => ({ ...prev, [section]: '' }));
    }, []);

    const setSectionError = useCallback((section, message) => {
        setSectionErrors((prev) => ({ ...prev, [section]: message }));
    }, []);

    const clearFieldErrors = useCallback((section) => {
        setFieldErrors((prev) => ({ ...prev, [section]: {} }));
    }, []);

    const setSectionFieldErrors = useCallback((section, errors) => {
        setFieldErrors((prev) => ({ ...prev, [section]: errors || {} }));
    }, []);

    const showSectionSuccess = useCallback((section, message) => {
        const existingTimer = successTimersRef.current[section];
        if (existingTimer) {
            clearTimeout(existingTimer);
        }

        setSectionSuccess((prev) => ({ ...prev, [section]: message }));
        successTimersRef.current[section] = setTimeout(() => {
            setSectionSuccess((prev) => ({ ...prev, [section]: '' }));
            delete successTimersRef.current[section];
        }, 3000);
    }, []);

    const clearFieldError = useCallback((section, field) => {
        setFieldErrors((prev) => ({
            ...prev,
            [section]: {
                ...(prev[section] || {}),
                [field]: ''
            }
        }));
    }, []);

    const extractFieldErrors = useCallback((err) => {
        return err?.response?.data?.fieldErrors || {};
    }, []);

    return {
        sectionErrors,
        setSectionErrors,
        sectionSuccess,
        setSectionSuccess,
        fieldErrors,
        setFieldErrors,
        clearSectionError,
        setSectionError,
        clearFieldErrors,
        setSectionFieldErrors,
        showSectionSuccess,
        clearFieldError,
        extractFieldErrors
    };
}
